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

## Proposed request flow

1. Android Hunt UI sends a query to the dedicated Knowledge API.
2. Cloudflare Worker routes requests to GitHub public repository search and the configured research providers.
3. Return normalized results with title, source, URL, summary/snippet, and provider metadata.
4. Android displays results and opens the source URL in a browser; no provider credentials are embedded in the APK.
5. Keep provider adapters independently testable and preserve source attribution/license metadata.

## API surface (planned)

- `GET /api/knowledge/search?q=...` — research/web search with provider fallback.
- `GET /api/knowledge/github-tools?q=...` — search public GitHub repositories/tools and return repository metadata (stars, forks, language, license, updated/pushed dates, topics, archive/fork status, and open issues).

## Implementation checklist

- [x] Dedicated feature branch checked against existing branches and open PRs (no duplicate found).
- [x] Initial scope and provider inventory documented.
- [ ] Implement/verify Worker provider adapters and consistent response schema.
- [ ] Implement Android Hunt screen and search/results states on this isolated branch.
- [ ] Add tests for query validation, provider failures, normalization, and license metadata.
- [ ] Run Android build/tests and verify UI; report any runtime/network limitation.
- [ ] Open/update one draft PR; do not merge without approval.
