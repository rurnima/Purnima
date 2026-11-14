export interface Env {
  DB: D1Database;
  AUTH_PRIVATE_KEY_PKCS8: string;
  AUTH_PUBLIC_JWK: string;
  AUTH_KEY_ID: string;
}
interface DeviceRow { id: string; public_key: string; created_at: number; }
const TOKEN_TTL_SECONDS = 900;
const CHALLENGE_TTL_SECONDS = 300;
const AUDIENCE = "purnima-api";
function json(data: unknown, status = 200): Response {
  return Response.json(data, { status, headers: { "Cache-Control": "no-store", "Access-Control-Allow-Origin": "*" } });
}
function decodeBase64Url(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
function base64Url(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function encodeJson(value: unknown): string { return base64Url(new TextEncoder().encode(JSON.stringify(value))); }
async function issueToken(deviceId: string, issuer: string, env: Env): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = encodeJson({ alg: "RS256", typ: "JWT", kid: env.AUTH_KEY_ID });
  const payload = encodeJson({ iss: issuer, aud: AUDIENCE, sub: deviceId, iat: now, exp: now + TOKEN_TTL_SECONDS });
  const unsigned = header + "." + payload;
  const privateKey = await crypto.subtle.importKey("pkcs8", decodeBase64Url(env.AUTH_PRIVATE_KEY_PKCS8),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, new TextEncoder().encode(unsigned));
  return unsigned + "." + base64Url(new Uint8Array(signature));
}
async function register(request: Request, env: Env): Promise<Response> {
  let body: { publicKey?: JsonWebKey };
  try { body = await request.json() as { publicKey?: JsonWebKey }; } catch { return json({ error: "Invalid JSON" }, 400); }
  const key = body.publicKey;
  if (!key || key.kty !== "EC" || key.crv !== "P-256" || typeof key.x !== "string" || typeof key.y !== "string")
    return json({ error: "A P-256 public key is required" }, 400);
  try { await crypto.subtle.importKey("jwk", key, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]); }
  catch { return json({ error: "Invalid public key" }, 400); }
  const id = crypto.randomUUID();
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare("INSERT INTO devices (id, public_key, created_at, last_seen_at) VALUES (?, ?, ?, ?)")
    .bind(id, JSON.stringify(key), now, now).run();
  return json({ deviceId: id, tokenType: "Bearer", expiresIn: TOKEN_TTL_SECONDS }, 201);
}
async function createChallenge(request: Request, env: Env): Promise<Response> {
  let body: { deviceId?: string };
  try { body = await request.json() as { deviceId?: string }; } catch { return json({ error: "Invalid JSON" }, 400); }
  if (typeof body.deviceId !== "string" || !body.deviceId) return json({ error: "deviceId is required" }, 400);
  const device = await env.DB.prepare("SELECT id FROM devices WHERE id = ?").bind(body.deviceId).first<{ id: string }>();
  if (!device) return json({ error: "Unknown device" }, 404);
  const nonce = base64Url(crypto.getRandomValues(new Uint8Array(32)));
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare("INSERT INTO challenges (device_id, nonce, expires_at) VALUES (?, ?, ?)")
    .bind(body.deviceId, nonce, now + CHALLENGE_TTL_SECONDS).run();
  return json({ challenge: nonce, expiresAt: now + CHALLENGE_TTL_SECONDS });
}
async function createToken(request: Request, env: Env): Promise<Response> {
  let body: { deviceId?: string; challenge?: string; timestamp?: number; signature?: string };
  try { body = await request.json() as typeof body; } catch { return json({ error: "Invalid JSON" }, 400); }
  if (typeof body.deviceId !== "string" || typeof body.challenge !== "string" ||
      typeof body.signature !== "string" || typeof body.timestamp !== "number")
    return json({ error: "deviceId, challenge, timestamp and signature are required" }, 400);
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - body.timestamp) > 120) return json({ error: "Expired proof" }, 401);
  const challenge = await env.DB.prepare(
    "DELETE FROM challenges WHERE device_id = ? AND nonce = ? AND expires_at > ? RETURNING nonce",
  ).bind(body.deviceId, body.challenge, now).first<{ nonce: string }>();
  if (!challenge) return json({ error: "Challenge expired or already used" }, 401);
  const device = await env.DB.prepare("SELECT id, public_key FROM devices WHERE id = ?")
    .bind(body.deviceId).first<DeviceRow>();
  if (!device) return json({ error: "Unknown device" }, 404);
  try {
    const publicKey = await crypto.subtle.importKey("jwk", JSON.parse(device.public_key) as JsonWebKey,
      { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    const proof = body.deviceId + "." + body.challenge + "." + body.timestamp;
    const valid = await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, publicKey,
      decodeBase64Url(body.signature), new TextEncoder().encode(proof));
    if (!valid) return json({ error: "Invalid device proof" }, 401);
  } catch { return json({ error: "Invalid device proof" }, 401); }
  await env.DB.prepare("UPDATE devices SET last_seen_at = ? WHERE id = ?").bind(now, body.deviceId).run();
  const accessToken = await issueToken(body.deviceId, new URL(request.url).origin, env);
  return json({ accessToken, tokenType: "Bearer", expiresIn: TOKEN_TTL_SECONDS });
}
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return new Response(null, { headers: {
      "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type", "Access-Control-Max-Age": "86400",
    }});
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/health") return json({ service: "purnima-auth", status: "ok" });
      if (request.method === "GET" && url.pathname === "/.well-known/jwks.json") {
        const publicKey = JSON.parse(env.AUTH_PUBLIC_JWK) as JsonWebKey;
        return json({ keys: [{ ...publicKey, kid: env.AUTH_KEY_ID, alg: "RS256", use: "sig" }] });
      }
      if (request.method === "POST" && url.pathname === "/v1/device/register") return await register(request, env);
      if (request.method === "POST" && url.pathname === "/v1/device/challenge") return await createChallenge(request, env);
      if (request.method === "POST" && url.pathname === "/v1/device/token") return await createToken(request, env);
      return json({ error: "Not found" }, 404);
    } catch { return json({ error: "Authentication service unavailable" }, 500); }
  },
};
