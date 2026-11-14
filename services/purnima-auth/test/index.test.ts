import { describe, expect, it } from "vitest";
import worker from "../src/index";

const env = {
  DB: {} as never,
  AUTH_PRIVATE_KEY_PKCS8: "not-used",
  AUTH_PUBLIC_JWK: JSON.stringify({ kty: "RSA", n: "AQ", e: "AQAB" }),
  AUTH_KEY_ID: "test-key",
};

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

  it("does not expose unimplemented routes", async () => {
    const response = await worker.fetch(new Request("https://auth.example/v1/login", { method: "POST" }), env);
    expect(response.status).toBe(404);
  });
});
