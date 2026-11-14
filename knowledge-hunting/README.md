# Knowledge Hunting

Knowledge Hunting is a separately scoped research/tool-discovery capability. It is developed on `feature/knowledge-hunting`; it must not be mixed into Home Screen, Chat Screen, APK updater, or other feature branches. Main is the only coordination point. Do not merge without the user's explicit approval.

## Goal

Discover public GitHub tools and research sources for knowledge/deep research, web search and crawling, document extraction, AI agents/tool calling, and tool integration. For every candidate, assess its license, maintenance/activity, API or CLI, Android/cloud compatibility, and whether it can be used free (including self-hosting requirements).

## Candidate source/tool inventory

| Candidate | License / cost model | Intended role | Integration notes |
|---|---|---|---|
| [Crawl4AI](https://github.com/unclecode/crawl4ai) | Apache-2.0; open-source self-hosting | LLM-friendly crawling and extraction | Cloud backend/self-hosting; not a direct Android library |
| [SearXNG](https://github.com/searxng/searxng) | AGPL-3.0; self-hosted metasearch | Web search provider | Must assess AGPL obligations before distributing modifications |
| [SearXNG + Crawl4AI (searxNcrawl)](https://github.com/DasDigitaleMomentum/searxNcrawl) | Reported MIT; verify upstream repository metadata before adoption | Search + crawl pipeline | Cloud-hosted service or self-hosted backend |
| [Browser Use](https://github.com/browser-use/browser-use) | Open-source; verify exact license/version before adoption | Browser-agent automation | Python/TS runtime; better suited to a cloud agent than Android-native |
| [OpenAlex](https://openalex.org/) | Free API access subject to current rate limits/terms | Scholarly discovery | API-based provider |
| [Crossref](https://www.crossref.org/) | Public REST API | DOI/metadata lookup | API-based provider |
| [arXiv](https://arxiv.org/) | Public research metadata/API; content remains subject to paper rights | Preprint discovery | API-based provider |
| [Wikipedia](https://www.wikipedia.org/) | Content under applicable CC licenses; API available | General knowledge lookup | API-based provider |

## Implemented API

Cloudflare Worker: `purnima-knowledge-hunting`

- `POST /api/knowledge/search` — research search with provider fallback. Request JSON: `{"query":"...","limit":5}`.
- `POST /api/knowledge/github-tools` — GitHub public repository search returning stars, forks, language, license, update/push dates, topics, archived/fork status, and open issues.
- `GET /health` — health response.
- IP rate limiting: 30 requests/minute per address through the Durable Object binding.
- Search fallback providers include Wikipedia, Crossref, OpenAlex and arXiv; SearXNG is optional and must be configured with an owner-controlled HTTPS instance. Optional upstream WebSearchFree and Firecrawl credentials are Worker bindings; none are embedded in Android.
- Worker source and deployed version were checked in Cloudflare. The external workers.dev health request could not be confirmed from this environment, so live endpoint reachability remains an open verification item.

## Android UI

- `app/src/main/java/com/rurnima/purnima/knowledge/KnowledgeHuntingScreen.kt` — isolated Jetpack Compose screen with Research/GitHub Tools modes, query field, loading/error/empty states, normalized result cards and source-open callback.
- Screen has intentionally not been wired into `MainActivity`; integration needs the app baseline and INTERNET permission in the app manifest. Keep this feature separate until its PR is reviewed.

## Implementation checklist

- [x] Dedicated feature branch checked against existing branches and open PRs (no duplicate found).
- [x] Initial scope and provider inventory documented.
- [x] Worker API routes/provider fallback implementation inspected.
- [x] Isolated Android Hunt screen source added.
- [ ] Verify Worker endpoint from a reachable client and test rate-limit/error paths.
- [ ] Wire screen into app navigation only after app-project baseline is available; add INTERNET permission.
- [ ] Add tests for query validation, provider failures, normalization, and license metadata.
- [ ] Run Android build/tests and inspect UI; this feature branch is currently based on the repository Main snapshot, which does not yet contain the Android Gradle project.
- [ ] Keep one draft PR #6 updated; do not merge without approval.
