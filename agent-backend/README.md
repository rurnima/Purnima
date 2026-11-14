# Purnima Agent Gateway

This Cloudflare Worker receives Chat and Voice instructions and asks Workers AI to produce a bounded action proposal. The Android app does not ask users to sign in or enter a token/API key.

## Current behavior

- `GET /health` is a minimal liveness check.
- `POST /v1/agent` accepts Chat/Voice requests without a user-entered bearer token.
- The model is `@cf/google/gemma-4-26b-a4b-it` through the Workers AI binding; model access stays on the server.
- Request bodies are limited to 16 KiB and instructions to 8,000 characters.
- Model output is validated against a fixed tool and permission registry.
- Plans are capped at eight steps and all write/code-changing steps require approval.
- All proposed actions are marked `not_executed`. No GitHub, shell, build, branch, commit, or PR tool is called.
- Credentials and instruction contents are not logged.

## Important deployment security

This endpoint is intentionally login-free for the mobile user, so it is a public API endpoint. **Do not deploy it to production until Cloudflare edge rate-limiting and abuse controls are configured** for `POST /v1/agent`, with conservative per-IP limits and an account usage budget/alert. Apply a request-size limit at the edge as well. The Worker validates body size and instruction length, but those checks alone do not prevent automated abuse or unexpected Workers AI charges.

Workers AI is accessed through the server-side `AI` binding. No Workers AI API key, GitHub token, or shared secret is placed in the APK or requested from the user.

## Configure

1. Enable the Workers AI `AI` binding for this Worker.
2. Configure Cloudflare rate-limiting/WAF rules for `POST /v1/agent` before public deployment.
3. Set an account-level usage alert/budget appropriate for the project.
4. Run `npm install`, then `npm run typecheck`.
5. Use `npx wrangler dev` for local development. Deploy only after the edge protections are verified.

## Not implemented yet

GitHub MCP integration and tool execution, persisted conversations/audit records, in-app approval UI, Chat/Voice wiring, and isolated build/test execution remain separate milestones. This endpoint is a model-backed planner only, not yet a task-executing agent.
