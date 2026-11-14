import { describe, expect, it } from "vitest";
import worker, { type Env } from "../src/index";

function createMemoryDb(): Env["DB"] {
  const devices = new Map<string, { id: string; public_key: string }>();
  const challenges = new Map<string, { nonce: string; expiresAt: number }>();
  let signingRow: { private_key_pkcs8: string; public_jwk: string; key_id: string } | null = null;
  return {
    prepare(query: string) {
      let values: unknown[] = [];
      return {
        bind(...args: unknown[]) { values = args; return this; },
        async run() {
          if (query.startsWith("INSERT INTO devices")) {
            devices.set(String(values[0]), { id: String(values[0]), public_key: String(values[1]) });
          } else if (query.startsWith("INSERT INTO challenges")) {
            challenges.set(String(values[0]) + ":" + String(values[1]), {
              nonce: String(values[1]), expiresAt: Number(values[2]),
            });
          } else if (query.startsWith("DELETE FROM challenges WHERE expires_at")) {
            for (const [key, value] of challenges) if (value.expiresAt <= Number(values[0])) challenges.delete(key);
          } else if (query.startsWith("INSERT OR IGNORE INTO auth_keys") && !signingRow) {
            signingRow = {
              private_key_pkcs8: String(values[0]),
              public_jwk: String(values[1]),
              key_id: String(values[2]),
            };
          }
          return {};
        },
        async first<T>() {
          if (query.startsWith("SELECT private_key_pkcs8")) return signingRow as T | null;
          if (query.startsWith("SELECT id FROM devices")) {
            const row = devices.get(String(values[0]));
            return (row ? { id: row.id } : null) as T | null;
          }
          if (query.startsWith("DELETE FROM challenges")) {
            const key = String(values[0]) + ":" + String(values[1]);
            const row = challenges.get(key);
            if (!row || row.expiresAt <= Number(values[2])) return null;
            challenges.delete(key);
            return { nonce: row.nonce } as T;
          }
          if (query.startsWith("SELECT id, public_key FROM devices")) {
            return (devices.get(String(values[0])) ?? null) as T | null;
          }
          return null;
        },
      };
    },
  } as unknown as Env["DB"];
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function decodeBase64UrlText(value: string): Record<string, unknown> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  return JSON.parse(atob(padded)) as Record<string, unknown>;
}

describe("Purnima device auth routes", () => {
  it("reports service health without a login flow", async () => {
    const response = await worker.fetch(new Request("https://auth.example/health"), { DB: createMemoryDb() });
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ service: "purnima-auth", status: "ok" });
  });

  it("creates and publishes its signing key automatically on first JWKS request", async () => {
    const response = await worker.fetch(new Request("https://auth.example/.well-known/jwks.json"), { DB: createMemoryDb() });
    expect(response.status).toBe(200);
    const result = await response.json() as { keys: Array<Record<string, unknown>> };
    expect(result.keys).toHaveLength(1);
    expect(result.keys[0].kty).toBe("RSA");
    expect(result.keys[0].kid).toBe("purnima-auth-rs256-v1");
    expect(result.keys[0].alg).toBe("RS256");
  });

  it("rejects registration without a valid P-256 public key", async () => {
    const response = await worker.fetch(new Request("https://auth.example/v1/device/register", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ publicKey: { kty: "RSA" } }),
    }), { DB: createMemoryDb() });
    expect(response.status).toBe(400);
  });

  it("registers a device, verifies its one-time proof, and issues a signed short-lived token", async () => {
    const deviceKeys = await crypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"],
    );
    const publicJwk = await crypto.subtle.exportKey("jwk", deviceKeys.publicKey);
    const testEnv: Env = { DB: createMemoryDb() };

    const registration = await worker.fetch(new Request("https://auth.example/v1/device/register", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ publicKey: publicJwk }),
    }), testEnv);
    expect(registration.status).toBe(201);
    const { deviceId } = await registration.json() as { deviceId: string };

    const challengeResponse = await worker.fetch(new Request("https://auth.example/v1/device/challenge", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId }),
    }), testEnv);
    const { challenge } = await challengeResponse.json() as { challenge: string };
    const timestamp = Math.floor(Date.now() / 1000);
    const proof = deviceId + "." + challenge + "." + timestamp;
    const signature = await crypto.subtle.sign(
      { name: "ECDSA", hash: "SHA-256" }, deviceKeys.privateKey, new TextEncoder().encode(proof),
    );

    const tokenResponse = await worker.fetch(new Request("https://auth.example/v1/device/token", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId, challenge, timestamp, signature: encodeBase64Url(new Uint8Array(signature)) }),
    }), testEnv);
    expect(tokenResponse.status).toBe(200);
    const { accessToken } = await tokenResponse.json() as { accessToken: string };
    const [header, payload, encodedSignature] = accessToken.split(".");
    expect(decodeBase64UrlText(header).kid).toBe("purnima-auth-rs256-v1");
    const claims = decodeBase64UrlText(payload);
    expect(claims.sub).toBe(deviceId);
    expect(claims.aud).toBe("purnima-api");
    expect(claims.iss).toBe("https://auth.example");
    expect(Number(claims.exp)).toBeGreaterThan(Math.floor(Date.now() / 1000));

    const jwksResponse = await worker.fetch(new Request("https://auth.example/.well-known/jwks.json"), testEnv);
    const jwks = await jwksResponse.json() as { keys: JsonWebKey[] };
    const signingPublicKey = await crypto.subtle.importKey(
      "jwk", jwks.keys[0], { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"],
    );
    const signedBytes = new TextEncoder().encode(header + "." + payload);
    const paddedSignature = encodedSignature.replace(/-/g, "+").replace(/_/g, "/") + "==";
    const signatureBytes = Uint8Array.from(atob(paddedSignature), c => c.charCodeAt(0));
    expect(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", signingPublicKey, signatureBytes, signedBytes)).toBe(true);

    const replay = await worker.fetch(new Request("https://auth.example/v1/device/token", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId, challenge, timestamp, signature: encodeBase64Url(new Uint8Array(signature)) }),
    }), testEnv);
    expect(replay.status).toBe(401);
  });

  it("does not expose unimplemented routes", async () => {
    const response = await worker.fetch(new Request("https://auth.example/v1/login", { method: "POST" }), { DB: createMemoryDb() });
    expect(response.status).toBe(404);
  });
});
