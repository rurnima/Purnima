# Purnima Persistent Learning

This isolated service stores user-approved facts and preferences that Purnima may reuse in later conversations. It is not a general document-search index; Knowledge Access owns RAG ingestion and Vectorize retrieval.

## Storage model

- Cloudflare D1 stores structured memory records, ownership, category, source, consent timestamp, and lifecycle timestamps.
- `memory_items` is always queried by authenticated `user_id`.
- `memory_events` records lifecycle event types only; it must never duplicate memory content.
- Large source documents and embeddings are out of scope for this service.

## Consent and controls

- A memory is created only after an explicit, affirmative user action or an equivalent clearly documented product consent flow.
- Do not silently convert every conversation into permanent memory.
- Provide list, update, archive, and delete operations.
- Delete removes the memory content row; event history may retain only a non-content deletion event.
- Never store credentials, authentication tokens, financial account identifiers, or other secrets as memories.
- The Android client must not send an arbitrary user ID. The trusted AUTH service supplies the canonical identity.

## D1 migration

Apply `migrations/0001_create_memory_items.sql` to a dedicated Persistent Learning D1 database. Do not reuse the Knowledge Hunting D1/DO resources or the AI Agent Runtime storage.

## API contract to implement

- `GET /v1/memories`: list active memories for the authenticated user.
- `POST /v1/memories`: create one memory with explicit consent.
- `PATCH /v1/memories/:id`: update content/category or archive.
- `DELETE /v1/memories/:id`: permanently delete the content row.

All mutations must constrain by both `id` and authenticated `user_id`; use parameterized D1 statements.

## Deployment gates

- Provision a dedicated D1 database and bind it as `DB`.
- Configure the trusted private `AUTH` service binding.
- Add Worker implementation and tests for consent, tenant isolation, update, archive, and deletion before app integration.
- No production data migration or Cloudflare resource creation is performed by this PR.

## Official references

- [Cloudflare D1](https://developers.cloudflare.com/d1/)
- [D1 migrations](https://developers.cloudflare.com/d1/reference/migrations/)
