# Purnima Agent Gateway

This Cloudflare Worker receives Chat and Voice instructions, authenticates the caller with OIDC, and asks Workers AI to produce a bounded action proposal.

## Current behavior

- `GET /health` is a minimal liveness check.
- `POST /v1/agent` requires an OIDC RS256 bearer token.
- Issuer, JWKS URL, and audience are deployment configuration, not mobile secrets.
- Tokens are checked for signature, exact issuer, audience, expiry, and not-before time.
- Request bodies are limited to 16 KiB and instructions to 8,000 characters.
- The model is `@cf/google/gemma-4-26b-a4b-it` through the Workers AI binding.
- The model can return a structured plan using a single allow-listed proposal function.
- Model output is validated against a fixed tool and permission registry.
- All proposed actions are marked `not_executed`. No GitHub, shell, build, branch, commit, or PR tool is called.
- Write/code-changing steps always require approval in the returned plan; model output cannot disable that requirement.
- Credentials, bearer tokens, and instruction contents are not logged.

## Model usage and cost

The selected Gemma 4 model is listed as available on Workers AI Free. Workers AI currently includes 10,000 Neurons per day at no charge; usage beyond that requires Workers Paid billing. Check current model pricing and account usage before enabling production traffic. The model and binding can be changed after evaluating alternatives.

## Configure

1. Choose an OIDC provider that issues short-lived user access tokens for this API.
2. Set `OIDC_ISSUER`, `OIDC_JWKS_URL`, and `OIDC_AUDIENCE` to the provider's exact values.
3. Ensure the provider publishes RS256 signing keys and the API audience is present in tokens.
4. Confirm Workers AI is enabled for the Cloudflare account and the `AI` binding is available.
5. Run `npm install`, then `npm run typecheck`.
6. Use `npx wrangler dev` for local development and `npm run deploy` only after review.

Do not use a long-lived shared bearer secret in the Android APK. Do not deploy with the
example identity-provider values. The Android token provider must obtain a per-user,
short-lived access token from the chosen sign-in flow.

## Not implemented yet

GitHub MCP OAuth and tool execution, persisted conversations/audit records, user approval UI, real OIDC provider selection, Android sign-in/session flow, and Chat/Voice wiring remain separate milestones. This endpoint is a model-backed planner only, not yet a task-executing agent.
