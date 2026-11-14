type VectorMetadata = Record<string, string | number>;

export interface Env {
  AI: {
    run(model: string, input: { text: string[] }): Promise<{ data?: number[][] }>;
  };
  VECTORIZE: {
    upsert(vectors: Array<{ id: string; values: number[]; metadata: VectorMetadata }>): Promise<unknown>;
    deleteByIds(ids: string[]): Promise<unknown>;
    query(
      vector: number[],
      options: { topK: number; filter: Record<string, string>; returnMetadata: "all" },
    ): Promise<{ matches?: Array<{ id: string; score?: number; metadata?: unknown }> }>;
  };
  AUTH: { fetch(request: Request): Promise<Response> };
}

interface AuthenticatedUser {
  id: string;
}

interface SearchRequest {
  query: string;
  limit?: number;
}

interface IngestRequest {
  documentId: string;
  title: string;
  text: string;
}

const AUTH_ISSUER = "https://purnima-auth.purnima.workers.dev";
const AUTH_AUDIENCE = "purnima-api";
const EMBEDDING_MODEL = "@cf/baai/bge-m3";
const DEFAULT_LIMIT = 6;
const MAX_LIMIT = 12;
const MAX_DOCUMENT_CHARS = 80_000;
const CHUNK_SIZE = 1_600;
const CHUNK_OVERLAP = 200;
const MAX_CHUNKS = 57;

function decodeBase64Url(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function decodeJsonSegment(value: string): Record<string, unknown> {
  return JSON.parse(new TextDecoder().decode(decodeBase64Url(value))) as Record<string, unknown>;
}

function audienceMatches(value: unknown, expected: string): boolean {
  return typeof value === "string"
    ? value === expected
    : Array.isArray(value) && value.includes(expected);
}

async function authenticate(request: Request, env: Env): Promise<AuthenticatedUser | null> {
  const authorization = request.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(authorization);
  if (!match || match[1].length > 8_192) return null;

  try {
    const parts = match[1].split(".");
    if (parts.length !== 3) return null;
    const header = decodeJsonSegment(parts[0]);
    const claims = decodeJsonSegment(parts[1]);
    if (header.alg !== "RS256" || typeof header.kid !== "string") return null;
    if (claims.iss !== AUTH_ISSUER) { console.log("PURNIMA_AUTH_DIAG_ISSUER_MISMATCH"); return null; }
    if (!audienceMatches(claims.aud, AUTH_AUDIENCE)) { console.log("PURNIMA_AUTH_DIAG_AUDIENCE_MISMATCH"); return null; }

    const now = Math.floor(Date.now() / 1_000);
    if (typeof claims.exp !== "number" || claims.exp <= now) return null;
    if (typeof claims.nbf === "number" && claims.nbf > now) return null;
    if (typeof claims.sub !== "string" || !claims.sub.trim()) return null;

    const jwksResponse = await env.AUTH.fetch(new Request(`${AUTH_ISSUER}/.well-known/jwks.json`, {
      headers: { accept: "application/json", "user-agent": "Purnima-Knowledge-Access/0.1" },
    }));
    console.log("PURNIMA_AUTH_DIAG_JWKS_STATUS", jwksResponse.status);
    if (!jwksResponse.ok) return null;
    const jwks = await jwksResponse.json() as { keys?: Array<JsonWebKey & { kid?: string; alg?: string; use?: string }> };
    const jwk = jwks.keys?.find((key) =>
      key.kid === header.kid && (!key.alg || key.alg === "RS256") && (!key.use || key.use === "sig")
    );
    console.log("PURNIMA_AUTH_DIAG_JWK_FOUND", Boolean(jwk));
    if (!jwk) return null;

    const publicKey = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      decodeBase64Url(parts[2]),
      new TextEncoder().encode(parts.slice(0, 2).join(".")),
    );
    console.log("PURNIMA_AUTH_DIAG_SIGNATURE_VALID", valid);
    return valid ? { id: claims.sub } : null;
  } catch {
    return null;
  }
}
function json(data: unknown, status = 200): Response {
  return Response.json(data, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

function chunkText(text: string): string[] {
  const normalized = text.replace(/\s+/g, " ").trim();
  const chunks: string[] = [];
  let start = 0;
  while (start < normalized.length) {
    const end = Math.min(start + CHUNK_SIZE, normalized.length);
    const chunk = normalized.slice(start, end).trim();
    if (chunk) chunks.push(chunk);
    if (end === normalized.length) break;
    start = end - CHUNK_OVERLAP;
  }
  return chunks;
}

async function embed(text: string, env: Env): Promise<number[]> {
  const result = await env.AI.run(EMBEDDING_MODEL, { text: [text] });
  const vector = result.data?.[0];
  if (!vector?.length) throw new Error("Embedding model returned no vector");
  if (vector.length !== 1024) throw new Error(`Embedding dimension mismatch: expected 1024, received ${vector.length}`);
  return vector;
}

async function stableId(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function ingest(request: Request, env: Env, user: AuthenticatedUser): Promise<Response> {
  let body: Partial<IngestRequest>;
  try {
    body = (await request.json()) as Partial<IngestRequest>;
  } catch {
    return json({ error: "Request body must be valid JSON" }, 400);
  }
  if (!body || typeof body !== "object") {
    return json({ error: "Request body must be a JSON object" }, 400);
  }
  if (
    typeof body.documentId !== "string" ||
    !body.documentId.trim() ||
    body.documentId.length > 200 ||
    typeof body.title !== "string" ||
    !body.title.trim() ||
    typeof body.text !== "string" ||
    body.text.trim().length === 0 ||
    body.text.length > MAX_DOCUMENT_CHARS
  ) {
    return json({ error: "documentId, title and text are required; text limit is 80000 characters" }, 400);
  }

  const chunks = chunkText(body.text);
  const vectors = [];
  for (let index = 0; index < chunks.length; index += 1) {
    const content = chunks[index];
    const values = await embed(content, env);
    vectors.push({
      id: await stableId(`${user.id}:${body.documentId}:${index}`),
      values,
      metadata: {
        userId: user.id,
        documentId: body.documentId,
        title: body.title.slice(0, 200),
        chunkIndex: index,
        content,
      },
    });
  }

  // Vectorize writes are eventually visible to queries; callers must tolerate a
  // short indexing delay after a successful ingest response.
  await env.VECTORIZE.upsert(vectors);

  // Re-ingesting a document replaces its previous chunk set. Remove trailing
  // deterministic chunk IDs only after the new vectors have been accepted.
  const obsoleteIds = await Promise.all(
    Array.from({ length: MAX_CHUNKS - chunks.length }, (_, offset) =>
      stableId(`${user.id}:${body.documentId}:${chunks.length + offset}`),
    ),
  );
  if (obsoleteIds.length > 0) await env.VECTORIZE.deleteByIds(obsoleteIds);

  return json({ documentId: body.documentId, chunksIndexed: vectors.length, status: "indexed" }, 202);
}

async function search(request: Request, env: Env, user: AuthenticatedUser): Promise<Response> {
  let body: Partial<SearchRequest>;
  try {
    body = (await request.json()) as Partial<SearchRequest>;
  } catch {
    return json({ error: "Request body must be valid JSON" }, 400);
  }
  if (!body || typeof body !== "object") {
    return json({ error: "Request body must be a JSON object" }, 400);
  }
  if (typeof body.query !== "string" || !body.query.trim() || body.query.length > 2_000) {
    return json({ error: "query is required and must be at most 2000 characters" }, 400);
  }
  if (body.limit !== undefined && (typeof body.limit !== "number" || !Number.isFinite(body.limit))) {
    return json({ error: "limit must be a finite number" }, 400);
  }

  const limit = Math.max(1, Math.min(MAX_LIMIT, Math.floor(body.limit ?? DEFAULT_LIMIT)));
  const vector = await embed(body.query.trim(), env);
  const result = await env.VECTORIZE.query(vector, {
    topK: limit,
    filter: { userId: user.id },
    returnMetadata: "all",
  });

  const matches = (result.matches ?? []).map((match) => ({
    id: match.id,
    score: match.score,
    metadata: match.metadata,
  }));
  return json({ matches });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ service: "purnima-knowledge-access", status: "ok" });
    }

    if (request.method !== "POST" || !["/v1/knowledge/ingest", "/v1/knowledge/search"].includes(url.pathname)) {
      return json({ error: "Not found" }, 404);
    }

    try {
      const user = await authenticate(request, env);
      if (!user) return json({ error: "Unauthorized" }, 401);
      return url.pathname.endsWith("/ingest")
        ? await ingest(request, env, user)
        : await search(request, env, user);
    } catch {
      return json({ error: "Knowledge service request failed" }, 500);
    }
  },
};
