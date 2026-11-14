import { createPublicKey, generateKeyPairSync, randomUUID, sign, verify } from "node:crypto";

const AUTH_URL = "https://purnima-auth.purnima.workers.dev";
const KNOWLEDGE_URL = "https://purnima-knowledge-access.purnima.workers.dev";
const headers = { "content-type": "application/json", "user-agent": "Purnima-Knowledge-Access-CI/1.0" };

async function post(base, path, body, bearerToken) {
  const requestHeaders = bearerToken ? { ...headers, authorization: `Bearer ${bearerToken}` } : headers;
  const response = await fetch(base + path, { method: "POST", headers: requestHeaders, body: JSON.stringify(body) });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error(`${path}: HTTP ${response.status}: ${text.slice(0, 300)}`); }
  return { status: response.status, data };
}

async function createSession() {
  const pair = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKey = pair.publicKey.export({ format: "jwk" });
  const registration = await post(AUTH_URL, "/v1/device/register", { publicKey });
  if (registration.status !== 201) throw new Error("Device registration failed");
  const deviceId = registration.data.deviceId;
  const challenge = await post(AUTH_URL, "/v1/device/challenge", { deviceId });
  if (challenge.status !== 200) throw new Error("Challenge creation failed");
  const timestamp = Math.floor(Date.now() / 1000);
  const proof = `${deviceId}.${challenge.data.challenge}.${timestamp}`;
  const signature = sign("sha256", Buffer.from(proof), { key: pair.privateKey, dsaEncoding: "ieee-p1363" }).toString("base64url");
  const token = await post(AUTH_URL, "/v1/device/token", {
    deviceId, challenge: challenge.data.challenge, timestamp, signature,
  });
  if (token.status !== 200 || !token.data.accessToken) throw new Error("Device token issuance failed");
  return { deviceId, accessToken: token.data.accessToken };
}

console.log("Testing live Knowledge Access with global_fetch_strictly_public enabled.");
const health = await fetch(KNOWLEDGE_URL + "/health", { headers });
if (!health.ok || (await health.json()).status !== "ok") throw new Error("Knowledge Access health check failed");

const first = await createSession();
const tokenParts = first.accessToken.split(".");
const tokenHeader = JSON.parse(Buffer.from(tokenParts[0], "base64url").toString("utf8"));
const tokenClaims = JSON.parse(Buffer.from(tokenParts[1], "base64url").toString("utf8"));
const publishedJwks = await fetch(AUTH_URL + "/.well-known/jwks.json", { headers });
const jwksData = await publishedJwks.json();
const publicSigningJwk = jwksData.keys?.find((key) => key.kid === tokenHeader.kid);
if (!publicSigningJwk || tokenClaims.iss !== AUTH_URL || tokenClaims.aud !== "purnima-api") {
  throw new Error("Issued device token has unexpected issuer, audience, or key id");
}
const publicSigningKey = createPublicKey({ key: publicSigningJwk, format: "jwk" });
if (!verify("RSA-SHA256", Buffer.from(`${tokenParts[0]}.${tokenParts[1]}`), publicSigningKey, Buffer.from(tokenParts[2], "base64url"))) {
  throw new Error("Issued device token does not verify against public JWKS");
}
console.log("Auth token claims verified:", { iss: tokenClaims.iss, aud: tokenClaims.aud, kid: tokenHeader.kid });
const jwksDebug = await fetch(KNOWLEDGE_URL + "/__debug/jwks", { headers });
console.log("Worker JWKS fetch diagnostic:", await jwksDebug.json());
console.log("Checking strict JWT verifier conditions on the live Worker.");
const tokenDebug = await fetch(KNOWLEDGE_URL + "/__debug/auth", { headers: { ...headers, authorization: `Bearer ${first.accessToken}` } });
console.log("Worker token verification diagnostic:", await tokenDebug.json());
const documentId = `ci-${randomUUID()}`;
const uniqueText = `Purnima knowledge integration verification ${randomUUID()}. বাংলা জ্ঞান অনুসন্ধানের বাস্তব পরীক্ষা।`;
const ingested = await post(KNOWLEDGE_URL, "/v1/knowledge/ingest", {
  documentId, title: "CI integration verification", text: uniqueText,
}, first.accessToken);
if (ingested.status !== 202 || ingested.data.status !== "indexed") throw new Error(`Authenticated knowledge ingestion failed: HTTP ${ingested.status} ${JSON.stringify(ingested.data)}`);

let found = false;
for (let attempt = 0; attempt < 10; attempt += 1) {
  const searched = await post(KNOWLEDGE_URL, "/v1/knowledge/search", { query: uniqueText, limit: 6 }, first.accessToken);
  if (searched.status !== 200) throw new Error("Authenticated knowledge search failed");
  if (searched.data.matches?.some((match) => match.metadata?.documentId === documentId)) {
    found = true;
    break;
  }
  await new Promise((resolve) => setTimeout(resolve, 1_500));
}
if (!found) throw new Error("Ingested document did not become searchable in Vectorize");

const second = await createSession();
const isolated = await post(KNOWLEDGE_URL, "/v1/knowledge/search", { query: uniqueText, limit: 6 }, second.accessToken);
if (isolated.status !== 200) throw new Error("Second-device search failed");
if (isolated.data.matches?.some((match) => match.metadata?.documentId === documentId)) {
  throw new Error("Knowledge Access leaked one device's document to another device");
}

console.log("Knowledge Access E2E passed: silent device auth, private JWKS service binding, authenticated ingestion, Vectorize retrieval, and device isolation.");
