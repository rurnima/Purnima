# Purnima Agent Gateway

This Cloudflare Worker is the first backend boundary for Chat and Voice agent requests.
It does not run model prompts or GitHub tools yet.

## Security behavior

- `GET /health` is a minimal liveness check.
- `POST /v1/agent` requires an OIDC RS256 bearer token.
- Issuer, JWKS URL, and audience are deployment configuration, not mobile secrets.
- Tokens are checked for signature, issuer, audience, expiry, and not-before time.
- Request bodies are limited to 16 KiB and instructions to 8,000 characters.
- Credentials and instruction contents are not logged.
- Until the model provider, allow-listed MCP tools, approval gate, and audit service exist,
  authenticated requests fail closed with HTTP 503. No tool is executed.

## Configure

1. Choose an OIDC provider that issues short-lived user access tokens for this API.
2. Set `OIDC_ISSUER`, `OIDC_JWKS_URL`, and `OIDC_AUDIENCE` in the Worker environment.
3. Confirm the provider publishes RS256 signing keys and the API audience is present in tokens.
4. Run `npm install`, then `npm run typecheck`.
5. Use `npx wrangler dev` for local development and `npm run deploy` only after review.

Do not use a long-lived shared bearer secret in the Android APK. Do not deploy with the
example identity-provider values. The Android token provider must obtain a per-user,
short-lived access token from the chosen sign-in flow.

## Not implemented yet

Model orchestration, GitHub MCP OAuth, tool allow-list, per-action permission checks,
approval UI, audit persistence, and Android Chat/Voice wiring remain separate milestones.
