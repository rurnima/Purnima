export interface Env {
  DB: D1Database;
  AUTH: Fetcher;
}

interface User {
  id: string;
}

async function authenticate(request: Request, env: Env): Promise<User | null> {
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return null;
  const response = await env.AUTH.fetch("https://auth.internal/v1/session/verify", {
    method: "POST",
    headers: { Authorization: authorization },
  });
  if (!response.ok) return null;
  const body = (await response.json()) as { userId?: unknown };
  return typeof body.userId === "string" && body.userId ? { id: body.userId } : null;
}

function json(data: unknown, status = 200): Response {
  return Response.json(data, { status, headers: { "Cache-Control": "no-store" } });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ service: "purnima-persistent-learning", status: "ok" });
    }
    if (!url.pathname.startsWith("/v1/memories")) return json({ error: "Not found" }, 404);

    try {
      const user = await authenticate(request, env);
      if (!user) return json({ error: "Unauthorized" }, 401);

      if (request.method === "GET" && url.pathname === "/v1/memories") {
        const result = await env.DB.prepare(
          "SELECT id, content, category, source, consented_at, created_at, updated_at FROM memory_items WHERE user_id = ? AND archived_at IS NULL ORDER BY updated_at DESC LIMIT 200"
        ).bind(user.id).all();
        return json({ memories: result.results });
      }

      if (request.method === "POST" && url.pathname === "/v1/memories") {
        const body = (await request.json()) as {
          content?: unknown; category?: unknown; source?: unknown; consent?: unknown;
        };
        if (body.consent !== true) return json({ error: "Explicit consent is required" }, 400);
        if (typeof body.content !== "string" || !body.content.trim() || body.content.length > 12_000) {
          return json({ error: "content is required and must be at most 12000 characters" }, 400);
        }
        const id = crypto.randomUUID();
        const category = typeof body.category === "string" ? body.category.slice(0, 80) : "general";
        const source = typeof body.source === "string" ? body.source.slice(0, 80) : "conversation";
        const now = new Date().toISOString();
        await env.DB.batch([
          env.DB.prepare(
            "INSERT INTO memory_items (id, user_id, content, category, source, consented_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
          ).bind(id, user.id, body.content.trim(), category, source, now, now, now),
          env.DB.prepare(
            "INSERT INTO memory_events (id, user_id, memory_id, event_type) VALUES (?, ?, ?, 'created')"
          ).bind(crypto.randomUUID(), user.id, id),
        ]);
        return json({ id, status: "stored" }, 201);
      }

      const match = url.pathname.match(/^\/v1\/memories\/([a-zA-Z0-9-]+)$/);
      if (!match) return json({ error: "Not found" }, 404);
      const id = match[1];

      if (request.method === "PATCH") {
        const body = (await request.json()) as {
          content?: unknown; category?: unknown; archived?: unknown;
        };
        const current = await env.DB.prepare(
          "SELECT id FROM memory_items WHERE id = ? AND user_id = ?"
        ).bind(id, user.id).first();
        if (!current) return json({ error: "Memory not found" }, 404);

        if (body.archived === true) {
          await env.DB.batch([
            env.DB.prepare(
              "UPDATE memory_items SET archived_at = ?, updated_at = ? WHERE id = ? AND user_id = ?"
            ).bind(new Date().toISOString(), new Date().toISOString(), id, user.id),
            env.DB.prepare(
              "INSERT INTO memory_events (id, user_id, memory_id, event_type) VALUES (?, ?, ?, 'archived')"
            ).bind(crypto.randomUUID(), user.id, id),
          ]);
          return json({ id, status: "archived" });
        }

        if (body.content !== undefined && (typeof body.content !== "string" || !body.content.trim() || body.content.length > 12_000)) {
          return json({ error: "content must be a non-empty string of at most 12000 characters" }, 400);
        }
        if (body.category !== undefined && typeof body.category !== "string") {
          return json({ error: "category must be a string" }, 400);
        }
        const content = typeof body.content === "string" ? body.content.trim() : null;
        const category = typeof body.category === "string" ? body.category.slice(0, 80) : null;
        await env.DB.batch([
          env.DB.prepare(
            "UPDATE memory_items SET content = COALESCE(?, content), category = COALESCE(?, category), updated_at = ? WHERE id = ? AND user_id = ?"
          ).bind(content, category, new Date().toISOString(), id, user.id),
          env.DB.prepare(
            "INSERT INTO memory_events (id, user_id, memory_id, event_type) VALUES (?, ?, ?, 'updated')"
          ).bind(crypto.randomUUID(), user.id, id),
        ]);
        return json({ id, status: "updated" });
      }

      if (request.method === "DELETE") {
        await env.DB.batch([
          env.DB.prepare("DELETE FROM memory_items WHERE id = ? AND user_id = ?").bind(id, user.id),
          env.DB.prepare(
            "INSERT INTO memory_events (id, user_id, memory_id, event_type) VALUES (?, ?, ?, 'deleted')"
          ).bind(crypto.randomUUID(), user.id, id),
        ]);
        return json({ id, status: "deleted" });
      }

      return json({ error: "Method not allowed" }, 405);
    } catch {
      return json({ error: "Persistent learning request failed" }, 500);
    }
  },
};
