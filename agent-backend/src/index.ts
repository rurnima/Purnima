export interface Env {
  OIDC_ISSUER: string;
  OIDC_JWKS_URL: string;
  OIDC_AUDIENCE: string;
}

type InstructionSource = "CHAT" | "VOICE";

interface AgentRequest {
  instruction: string;
  source: InstructionSource;
}

const MAX_BODY_BYTES = 16 * 1024;
const MAX_INSTRUCTION_CHARS = 8_000;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function decodeBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function decodeJsonSegment(value: string): Record<string, unknown> {
  return JSON.parse(new TextDecoder().decode(decodeBase64Url(value))) as Record<string, unknown>;
}

function audienceMatches(tokenAudience: unknown, expected: string): boolean {
  return typeof tokenAudience === "string"
    ? tokenAudience === expected
    : Array.isArray(tokenAudience) && tokenAudience.includes(expected);
}

async function verifyAccessToken(token: string, env: Env): Promise<boolean> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return false;

    const header = decodeJsonSegment(parts[0]);
    const claims = decodeJsonSegment(parts[1]);
    if (header.alg !== "RS256" || typeof header.kid !== "string") return false;

    const issuer = env.OIDC_ISSUER.replace(/\/$/, "");
    if (claims.iss !== issuer || !audienceMatches(claims.aud, env.OIDC_AUDIENCE)) return false;

    const now = Math.floor(Date.now() / 1000);
    if (typeof claims.exp !== "number" || claims.exp <= now) return false;
    if (typeof claims.nbf === "number" && claims.nbf > now) return false;

    const jwksResponse = await fetch(env.OIDC_JWKS_URL, {
      headers: { accept: "application/json" },
      cf: { cacheTtl: 300, cacheEverything: true },
    });
    if (!jwksResponse.ok) return false;

    const jwks = await jwksResponse.json() as { keys?: JsonWebKey[] };
    const jwk = jwks.keys?.find((key) => (key as JsonWebKey & { kid?: string }).kid === header.kid);
    if (!jwk) return false;

    const publicKey = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    return crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      decodeBase64Url(parts[2]),
      new TextEncoder().encode(parts.slice(0, 2).join(".")),
    );
  } catch {
    return false;
  }
}

function validateRequest(value: unknown): AgentRequest | null {
  if (!value || typeof value !== "object") return null;
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.instruction !== "string") return null;
  const instruction = candidate.instruction.trim();
  if (!instruction || instruction.length > MAX_INSTRUCTION_CHARS) return null;
  if (candidate.source !== "CHAT" && candidate.source !== "VOICE") return null;
  return { instruction, source: candidate.source };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ status: "ok", service: "purnima-agent-gateway" });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/agent") {
      return json({ error: "not_found" }, 404);
    }

    const authorization = request.headers.get("authorization") ?? "";
    const match = /^Bearer\s+(.+)$/i.exec(authorization);
    if (!match || !(await verifyAccessToken(match[1], env))) {
      return json({ error: "unauthorized" }, 401);
    }

    const declaredLength = Number(request.headers.get("content-length") ?? "0");
    if (declaredLength > MAX_BODY_BYTES) return json({ error: "payload_too_large" }, 413);

    let payload: unknown;
    try {
      const body = await request.text();
      if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) {
        return json({ error: "payload_too_large" }, 413);
      }
      payload = JSON.parse(body);
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const instruction = validateRequest(payload);
    if (!instruction) return json({ error: "invalid_instruction" }, 400);

    // Deliberately fail closed until the model provider, allow-listed MCP registry,
    // per-tool permissions, and approval/audit services are configured.
    return json({
      error: "orchestrator_not_configured",
      summary: "Purnima's agent gateway is authenticated, but no execution provider is configured yet.",
      accepted: false,
    }, 503);
  },
};
