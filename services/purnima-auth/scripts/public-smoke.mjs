import { createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify } from "node:crypto";

const baseUrl = (process.env.AUTH_BASE_URL || "https://purnima-auth.purnima.workers.dev").replace(/\/$/, "");
const headers = { "content-type": "application/json", "user-agent": "Purnima-CI/1.0" };

async function request(path, body) {
  const response = await fetch(baseUrl + path, {
    method: body === undefined ? "GET" : "POST",
    headers,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error(`${path}: HTTP ${response.status}: ${text.slice(0, 300)}`); }
  return { status: response.status, data };
}

const health = await request("/health");
if (health.status !== 200 || health.data.status !== "ok") throw new Error("Public health check failed");

const jwksBefore = await request("/.well-known/jwks.json");
if (jwksBefore.status !== 200 || !Array.isArray(jwksBefore.data.keys)) throw new Error("Public JWKS check failed");

const deviceKeys = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const publicJwk = deviceKeys.publicKey.export({ format: "jwk" });
const registration = await request("/v1/device/register", { publicKey: publicJwk });
if (registration.status !== 201 || !registration.data.deviceId) throw new Error("Device registration failed");
const deviceId = registration.data.deviceId;

const challengeResult = await request("/v1/device/challenge", { deviceId });
if (challengeResult.status !== 200 || !challengeResult.data.challenge) throw new Error("Challenge creation failed");
const timestamp = Math.floor(Date.now() / 1000);
const proof = `${deviceId}.${challengeResult.data.challenge}.${timestamp}`;
const signature = sign("sha256", Buffer.from(proof), {
  key: deviceKeys.privateKey,
  dsaEncoding: "ieee-p1363",
}).toString("base64url");

const issued = await request("/v1/device/token", {
  deviceId,
  challenge: challengeResult.data.challenge,
  timestamp,
  signature,
});
if (issued.status !== 200 || !issued.data.accessToken) throw new Error("Token issuance failed");

const [encodedHeader, encodedPayload, encodedSignature] = issued.data.accessToken.split(".");
const decode = (value) => JSON.parse(Buffer.from(value, "base64url").toString("utf8"));
const jwtHeader = decode(encodedHeader);
const claims = decode(encodedPayload);
const jwks = await request("/.well-known/jwks.json");
const signingJwk = jwks.data.keys.find((key) => key.kid === jwtHeader.kid);
if (!signingJwk || jwtHeader.alg !== "RS256") throw new Error("JWT signing key not published in JWKS");
const signingKey = createPublicKey({ key: signingJwk, format: "jwk" });
const valid = verify("RSA-SHA256", Buffer.from(`${encodedHeader}.${encodedPayload}`), signingKey,
  Buffer.from(encodedSignature, "base64url"));
if (!valid) throw new Error("JWT signature verification failed");
if (claims.sub !== deviceId || claims.aud !== "purnima-api" || claims.iss !== baseUrl ||
    typeof claims.exp !== "number" || claims.exp <= Math.floor(Date.now() / 1000)) {
  throw new Error("JWT claims validation failed");
}

const replay = await request("/v1/device/token", {
  deviceId,
  challenge: challengeResult.data.challenge,
  timestamp,
  signature,
});
if (replay.status !== 401) throw new Error("Single-use challenge replay protection failed");

console.log("Public device-auth E2E passed: register, challenge, P-256 proof, RS256 token, JWKS verification, replay rejection.");
