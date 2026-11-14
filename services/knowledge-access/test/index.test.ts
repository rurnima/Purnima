import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import worker, { type Env } from "../src/index";

let token = "";
let publicJwk: JsonWebKey & { kid: string };

function base64Url(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

beforeAll(async () => {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  publicJwk = { ...await crypto.subtle.exportKey("jwk", pair.publicKey), kid: "test-key" };
  const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT", kid: "test-key" })));
  const claims = base64Url(new TextEncoder().encode(JSON.stringify({
    iss: "https://purnima-auth.purnima.workers.dev",
    aud: "purnima-api",
    sub: "user-123",
    exp: Math.floor(Date.now() / 1000) + 300,
  })));
  const signingInput = `${header}.${claims}`;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", pair.privateKey, new TextEncoder().encode(signingInput));
  token = `${signingInput}.${base64Url(new Uint8Array(signature))}`;
});

function makeEnv(userId = "user-123") {
  const aiRun = vi.fn(async (_model: string, _input: unknown) => ({
    data: [Array.from({ length: 1024 }, (_, index) => index / 1024)],
  }));
  const upsert = vi.fn(async (_vectors: unknown[]) => ({ count: 1 }));
  const deleteByIds = vi.fn(async (_ids: string[]) => ({ count: 0 }));
  const query = vi.fn(async (_vector: number[], _options: unknown) => ({
    matches: [{ id: "chunk-1", score: 0.91, metadata: { userId, content: "নমুনা তথ্য" } }],
  }));
  const env = {
    AI: { run: aiRun },
    VECTORIZE: { upsert, deleteByIds, query },

  } as unknown as Env;
  return { env, aiRun, upsert, deleteByIds, query, authFetch };
}

function post(path: string, body: string, bearer = token) {
  return new Request(`https://knowledge.test${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
    },
    body,
  });
}

describe("Purnima Knowledge Access Worker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Signing keys are retrieved through the internal auth service binding.
  });

  it("serves a health response without requiring user authentication", async () => {
    const { env } = makeEnv();
    const response = await worker.fetch(new Request("https://knowledge.test/health"), env);
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ service: "purnima-knowledge-access", status: "ok" });
  });

  it("rejects requests without a bearer token", async () => {
    const { env, authFetch } = makeEnv();
    const response = await worker.fetch(post("/v1/knowledge/search", JSON.stringify({ query: "পরীক্ষা" }), ""), env);
    expect(response.status).toBe(401);
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("sends BGE-M3 input as an array and replaces obsolete document chunks", async () => {
    const { env, aiRun, upsert, deleteByIds } = makeEnv();
    const response = await worker.fetch(
      post("/v1/knowledge/ingest", JSON.stringify({
        documentId: "doc-1",
        title: "পরীক্ষার নথি",
        text: "Purnima-এর জন্য একটি বাংলা জ্ঞান নথি।",
      })),
      env,
    );
    expect(response.status).toBe(202);
    expect(aiRun).toHaveBeenCalledWith("@cf/baai/bge-m3", { text: ["Purnima-এর জন্য একটি বাংলা জ্ঞান নথি।"] });
    expect(upsert).toHaveBeenCalledTimes(1);
    expect(deleteByIds).toHaveBeenCalledTimes(1);
    expect(deleteByIds.mock.calls[0][0]).toHaveLength(56);
  });

  it("scopes vector search to the verified OIDC subject", async () => {
    const { env, query } = makeEnv("user-123");
    const response = await worker.fetch(
      post("/v1/knowledge/search", JSON.stringify({ query: "বাংলা জ্ঞান", limit: 5 })),
      env,
    );
    expect(response.status).toBe(200);
    expect(query).toHaveBeenCalledWith(
      expect.any(Array),
      expect.objectContaining({ topK: 5, filter: { userId: "user-123" }, returnMetadata: "all" }),
    );
  });

  it("rejects a token with the wrong audience", async () => {
    const { env } = makeEnv();
    const response = await worker.fetch(post("/v1/knowledge/search", JSON.stringify({ query: "পরীক্ষা" }), "bad.audience.token"), env);
    expect(response.status).toBe(401);
  });

  it("returns 400 for malformed JSON instead of an internal server error", async () => {
    const { env } = makeEnv();
    const response = await worker.fetch(post("/v1/knowledge/search", "{"), env);
    expect(response.status).toBe(400);
  });

  it("rejects empty document identifiers and titles", async () => {
    const { env } = makeEnv();
    const response = await worker.fetch(
      post("/v1/knowledge/ingest", JSON.stringify({ documentId: " ", title: " ", text: "নথি" })),
      env,
    );
    expect(response.status).toBe(400);
  });
});
