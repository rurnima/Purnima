import { describe, expect, it } from "vitest";
import worker from "../src/index";

const env = {
  DB: {} as never,
  AUTH_SIGNING_KEY: {} as CryptoKey,
  AUTH_PUBLIC_JWK: JSON.stringify({ kty: "RSA", n: "AQ", e: "AQAB" }),
  AUTH_KEY_ID: "test-key",
};


function createMemoryDb(): Env["DB"] {
  const devices = new Map<string, { id: string; public_key: string }>();
  const challenges = new Map<string, { deviceId: string; nonce: string; expiresAt: number }>();
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
              deviceId: String(values[0]), nonce: String(values[1]), expiresAt: Number(values[2]),
            });
          } else if (query.startsWith("DELETE FROM challenges WHERE expires_at")) {
            for (const [key, value] of challenges) if (value.expiresAt <= Number(values[0])) challenges.delete(key);
          }
          return {};
        },
        async first<T>() {
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
    const response = await worker.fetch(new Request("https://auth.example/health"), env);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ service: "purnima-auth", status: "ok" });
  });

  it("publishes only the configured public signing key", async () => {
    const response = await worker.fetch(new Request("https://auth.example/.well-known/jwks.json"), env);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      keys: [{ kty: "RSA", n: "AQ", e: "AQAB", kid: "test-key", alg: "RS256", use: "sig" }],
    });
  });

  it("rejects registration without a valid P-256 public key", async () => {
    const response = await worker.fetch(new Request("https://auth.example/v1/device/register", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ publicKey: { kty: "RSA" } }),
    }), env);
    expect(response.status).toBe(400);
  });


  it("registers a device, verifies its one-time proof, and issues a signed short-lived token", async () => {
    const deviceKeys = await crypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"],
    );
    const publicJwk = await crypto.subtle.exportKey("jwk", deviceKeys.publicKey);
    const signingKeys = await crypto.subtle.generateKey(
      { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
      true, ["sign", "verify"],
    );
    const testEnv: Env = {
      DB: createMemoryDb(),
      AUTH_SIGNING_KEY: signingKeys.privateKey,
      AUTH_PUBLIC_JWK: JSON.stringify(await crypto.subtle.exportKey("jwk", signingKeys.publicKey)),
      AUTH_KEY_ID: "test-key",
    };

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
    expect(decodeBase64UrlText(header).kid).toBe("test-key");
    const claims = decodeBase64UrlText(payload);
    expect(claims.sub).toBe(deviceId);
    expect(claims.aud).toBe("purnima-api");
    expect(claims.iss).toBe("https://auth.example");
    expect(Number(claims.exp)).toBeGreaterThan(Math.floor(Date.now() / 1000));

    const signingPublicKey = await crypto.subtle.importKey(
      "jwk", await crypto.subtle.exportKey("jwk", signingKeys.publicKey),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"],
    );
    const signedBytes = new TextEncoder().encode(header + "." + payload);
    const signatureBytes = Uint8Array.from(atob(encodedSignature.replace(/-/g, "+").replace(/_/g, "/") + "=="), c => c.charCodeAt(0));
    expect(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", signingPublicKey, signatureBytes, signedBytes)).toBe(true);

    const replay = await worker.fetch(new Request("https://auth.example/v1/device/token", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId, challenge, timestamp, signature: encodeBase64Url(new Uint8Array(signature)) }),
    }), testEnv);
    expect(replay.status).toBe(401);
  });

  it("does not expose unimplemented routes", async () => {
    const response = await worker.fetch(new Request("https://auth.example/v1/login", { method: "POST" }), env);
    expect(response.status).toBe(404);
  });
});
