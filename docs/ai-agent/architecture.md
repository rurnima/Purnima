# Purnima AI Agent — architecture and implementation scope

## Goal

Purnima should behave like a task-capable assistant when the owner gives an instruction in
Chat or Voice. The agent should interpret the request, plan work, select tools, execute
approved steps, inspect results, and report what actually happened.

This is **not** a standalone Self-update screen. Agent entry points belong inside the
existing Chat and Voice experiences. The Home screen must not display agent controls,
labels, buttons, or status.

## Public tool foundation and backend candidate

- **GitHub MCP Server** — https://github.com/github/github-mcp-server
  - Public, official GitHub MCP server for repository operations.
  - Its remote server supports OAuth and read-only toolset paths; start with read-only
    repository tools before enabling branch writes or pull-request creation.
  - This is an MCP service, not an Android library.
- **Cloudflare Agents SDK MCP Client** — https://developers.cloudflare.com/agents/model-context-protocol/apis/client-api/
  - Official SDK documentation describes connecting to external MCP servers, OAuth
    authorization flow, and exposing discovered tools to the AI SDK model.
  - Candidate for the separately hosted orchestration backend. Requires a secured app-user
    session and deployment configuration before any mobile client can call it.
- **Cloudflare Agents self-modifying harness** — https://github.com/cloudflare/agents/tree/main/examples/next/harnesses/self-modifying
  - Architecture reference for editable tools, candidate revisions, validation, and activation.
  - The example is experimental and must not be copied or exposed as-is.
- Existing updater research stays in docs/self-update/public-tools-research.md; this agent
  work is a separate scope and must not be folded into the updater UI.

## Required architecture

1. **Chat/Voice instruction adapters** — pass the user's natural-language command and source
   (CHAT or VOICE) to the same agent entry contract.
2. **Authenticated orchestration service** — model reasoning, planning, and tool selection
   run behind a service boundary. API keys and GitHub tokens must never be shipped in the APK.
3. **Allow-listed tool registry** — each tool declares an input contract and required permission.
4. **GitHub MCP integration** — scoped repository reads, feature branch creation, commits, CI
   inspection, and pull-request creation. Begin read-only; gate write tools separately.
5. **Build/test executor** — run candidate builds and tests in an isolated environment, not
   by running arbitrary downloaded code on the Android device.
6. **Approval gate** — require explicit user approval before release publication, production
   activation, destructive operations, or any tool permission expansion.
7. **Audit log** — retain instruction source, chosen tools, sanitized arguments, results,
   candidate revision, test evidence, and approvals. Never log credentials.
8. **Response adapter** — explain progress and final results through the initiating Chat or
   Voice channel; do not add a dedicated agent screen to Home.

## Safety defaults

- Deny unregistered tools and unknown permissions.
- Do not expose secrets to model prompts or tool outputs.
- Separate task branches and pull requests; no automatic merge.
- Do not publish releases or activate self-modified production code without approval.
- Show actual CI/build evidence; never claim a build passed without observed results.
- Treat external repository content as untrusted instructions/data.
- Tool output must be bounded and sanitized before it is fed back to the model.

## Backend gateway checkpoint

`agent-backend/` contains a Cloudflare Worker gateway for `GET /health` and authenticated
`POST /v1/agent` requests. It validates short-lived OIDC RS256 bearer tokens against the
configured exact issuer, audience, and JWKS endpoint; it also bounds request size and
instruction length.

The gateway now calls Cloudflare Workers AI using
`@cf/google/gemma-4-26b-a4b-it`. For actionable requests, the model can return a structured
proposal through one declared function. The backend validates every step against a fixed
tool/permission allow-list, caps plans at eight steps, and forces approval for all
write/code-changing operations. The response explicitly marks every step
`not_executed`; there is no GitHub MCP client or execution path yet. Ordinary conversation
can return a direct model response.

The model is listed as available on Workers AI Free, which currently includes 10,000
Neurons/day; higher usage may require paid billing. The Cloudflare `AI` binding must be
enabled for the Worker. Example OIDC values in `wrangler.toml` must be replaced before
deployment. The Android app still needs a real per-user sign-in/session provider.

## Android contract checkpoint

app/src/main/java/com/rurnima/purnima/agent/AgentContracts.kt establishes the initial
UI-independent Kotlin contracts for Chat/Voice instructions, tool requests/results, the
agent runtime, tool execution, and approval gate.

app/src/main/java/com/rurnima/purnima/agent/HttpAgentRuntime.kt is an HTTPS-only client
adapter that accepts an injected endpoint and access-token provider. It implements the
defined request/response envelope, but it cannot function until the backend is deployed
and the secure login/session provider is implemented and configured.

The Android agent is not yet connected to the deployed model endpoint, GitHub MCP server,
Chat screen, Voice screen, or build executor.

## Implementation sequence

- [x] Establish the Chat/Voice-neutral agent contract and architecture boundaries.
- [x] Add the injected HTTPS runtime client adapter and network permission.
- [x] Add a dedicated Android debug-build workflow.
- [x] Research public GitHub MCP OAuth and Cloudflare Agents MCP client options.
- [x] Add an OIDC-authenticated, fail-closed Cloudflare Worker gateway skeleton.
- [x] Connect Workers AI and implement validated, non-executing task proposals.
- [ ] Configure a real OIDC provider and implement the Android per-user sign-in/session flow.
- [ ] Persist conversation state and sanitized audit events.
- [ ] Connect a minimal allow-listed read-only GitHub MCP tool path.
- [ ] Add proposal/approval flow for branch writes and pull-request creation.
- [ ] Wire Chat adapter without changing the Home screen.
- [ ] Wire Voice adapter without changing the Home screen.
- [ ] Add tests for permission denials, prompt injection from repo content, sanitized logs,
      approval gates, and tool failures.
- [ ] Add isolated candidate build/test and evidence-based status reporting.
- [ ] Later: controlled self-code editing, revision activation, and rollback.
