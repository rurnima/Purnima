# Purnima Knowledge Access

This service is the isolated retrieval capability for Purnima. It does not add a Search screen and does not implement persistent personal learning.

## Current implementation

- Cloudflare Worker endpoints: `POST /v1/knowledge/ingest` and `POST /v1/knowledge/search`.
- Workers AI multilingual embeddings via `@cf/baai/bge-m3`.
- Vectorize stores chunk embeddings and metadata; search is filtered by the authenticated user's canonical ID.
- Authentication is delegated to a private `AUTH` Worker service binding. The client cannot choose its own tenant/user ID.
- Chunking is bounded; ingestion reports accepted indexing work and callers must allow for Vectorize's eventual query visibility.

## Development resources provisioned

- Vectorize index: `purnima-knowledge-access-dev`
- Index configuration: 1024 dimensions, cosine distance.
- Metadata filter index: `userId` (string), verified present.
- Workers AI model `@cf/baai/bge-m3` was called successfully with Bengali text and returned shape `[1, 1024]`.

## Deployment readiness

The Worker configuration is present in `wrangler.toml` and binds the development Vectorize index and Workers AI. The private `AUTH` service binding points to `purnima-auth`, but that trusted authentication Worker does not yet exist in the Cloudflare account. The only existing Worker is the separate Knowledge Hunting service; it must not be reused for this feature.

Therefore this service is intentionally **not deployed yet**. Deployment must wait until the Purnima authentication service is implemented and can validate the same short-lived bearer tokens issued to the app. Never replace the service binding with a user ID supplied by the Android client.

## Local verification

Run from this directory:

- `npm install`
- `npm run typecheck`
- `npm test`

The isolated GitHub Actions workflow runs type-checking and unit tests on PR updates. Current verified result: TypeScript check passed and all 6 unit tests passed.

## API

- `POST /v1/knowledge/ingest`: authenticated JSON body `{ documentId, title, text }`.
- `POST /v1/knowledge/search`: authenticated JSON body `{ query, limit? }`.
- `GET /health`: liveness only; exposes no account data.

## Security and scope

- User scope comes only from the trusted AUTH service.
- Vector metadata filtering is not a substitute for authorization; every request is authenticated first.
- No API keys or LiveKit credentials belong in Android resources or the repository.
- This feature does not change Home, Chat, Voice UI, Agent Runtime, or D1 learning records.

## Verification still required

- Add a deployment config after the Vectorize index and AUTH service are provisioned.
- Add Worker unit tests for auth failure, tenant isolation, invalid input, chunking, and eventual indexing behavior.
- Run local type-check/tests and deploy to a non-production environment before enabling it in the app.

## Official references

- [Cloudflare Vectorize + Workers AI](https://developers.cloudflare.com/vectorize/get-started/embeddings/)
- [Cloudflare RAG tutorial](https://developers.cloudflare.com/workers-ai/guides/tutorials/build-a-retrieval-augmented-generation-ai/)
- [Vectorize metadata filtering](https://developers.cloudflare.com/vectorize/reference/metadata-filtering/)
