# AI Agent backend readiness

Updated 2026-11-14 after implementing the login-free Worker planner and Android plan-response parsing.

## Scope isolation

- Continue Agent Runtime only in PR #7, branch `feature/ai-agent-runtime`.
- Keep Knowledge Hunting, Self-update, Chat UI, and Home Screen work separate.
- Agent entry points belong in existing Chat and Voice experiences; do not add a standalone Home control or screen.

## Current implementation

- [x] Dedicated Cloudflare Worker gateway source exists under `agent-backend/`.
- [x] Worker uses the server-side Workers AI binding; no model key is placed in the APK.
- [x] Mobile requests do not require user login, bearer tokens, or manual API-key entry.
- [x] Worker validates bounded input and model plans against an allow-list.
- [x] Proposed write/code-changing actions require approval and are marked `not_executed`.
- [x] Android contracts now represent structured plan steps, approval requirement, and execution status.
- [x] Android HTTP adapter now parses the Worker `plan`, `executionEnabled`, and `executionStatus` fields.

## Not implemented / not verified

- [ ] Chat and Voice screens are not yet wired to the runtime.
- [ ] No GitHub MCP tool execution is connected; proposals are not executed.
- [ ] No persisted conversation state or sanitized audit store.
- [ ] No in-app approval flow for write actions.
- [ ] No isolated build/test executor.
- [ ] Cloudflare Worker deployment and live endpoint behavior have not been verified.
- [ ] Cloudflare edge rate limits, abuse controls, and usage alerts have not been verified.

## Security requirements

The login-free mobile endpoint is public. Do not deploy it for production use until Cloudflare edge rate limiting, request controls, and usage alerts are configured and verified. Never put GitHub or model credentials in Android code, APK assets, or repository files. The user must not be asked to enter tokens or sign in for this Agent feature.

## Evidence

The previous CI run #22 passed Android debug APK assembly and backend TypeScript typecheck for commit `c0288c8b4a9b16027ba7af107ea3a1cb416c14a8`. That evidence applies only to that earlier commit; rerun CI after these changes before treating the new code as build-verified.
