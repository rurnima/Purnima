import { beforeEach, describe, expect, it, vi } from "vitest";
import worker, { type Env } from "../src/index";

function makeEnv(userId = "user-123") {
  const aiRun = vi.fn(async (_model: string, _input: unknown) => ({
    data: [Array.from({ length: 1024 }, (_, index) => index / 1024)],
  }));
  const upsert = vi.fn(async (_vectors: unknown[]) => ({ count: 1 }));
  const deleteByIds = vi.fn(async (_ids: string[]) => ({ count: 0 }));
  const query = vi.fn(async (_vector: number[], _options: unknown) => ({
    matches: [{ id: "chunk-1", score: 0.91, metadata: { userId, content: "নমুনা তথ্য" } }],
  }));
  const authFetch = vi.fn(async () => Response.json({ userId }));

  const env = {
    AI: { run: aiRun },
    VECTORIZE: { upsert, deleteByIds, query },
    AUTH: { fetch: authFetch },
  } as unknown as Env;

  return { env, aiRun, upsert, deleteByIds, query, authFetch };
}

function post(path: string, body: string, token = "valid-token") {
  return new Request(`https://knowledge.test${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body,
  });
}

describe("Purnima Knowledge Access Worker", () => {
  beforeEach(() => vi.clearAllMocks());

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
    expect(aiRun).toHaveBeenCalledWith("@cf/baai/bge-m3", {
      text: ["Purnima-এর জন্য একটি বাংলা জ্ঞান নথি।"],
    });
    expect(upsert).toHaveBeenCalledTimes(1);
    expect(deleteByIds).toHaveBeenCalledTimes(1);
    expect(deleteByIds.mock.calls[0][0]).toHaveLength(56);
  });

  it("scopes vector search to the authenticated user", async () => {
    const { env, query } = makeEnv("account-A");
    const response = await worker.fetch(
      post("/v1/knowledge/search", JSON.stringify({ query: "বাংলা জ্ঞান", limit: 5 })),
      env,
    );

    expect(response.status).toBe(200);
    expect(query).toHaveBeenCalledWith(
      expect.any(Array),
      expect.objectContaining({ topK: 5, filter: { userId: "account-A" }, returnMetadata: "all" }),
    );
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
