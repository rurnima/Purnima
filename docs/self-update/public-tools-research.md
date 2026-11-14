# Public GitHub tool research — Self-update and self-development

Scope: this inventory is limited to Purnima's Self-update work. It is a research checkpoint on the existing `feature/apk-updater` branch / PR #5; it does not combine this work with Home, Chat, or Knowledge Hunting.

## Public projects inspected

| Project | Public source | Relevant capability | Fit / caution |
|---|---|---|---|
| Android Update Checker | https://github.com/Bosaj/android-update-checker | Android update checking library | Candidate for comparison only. Verify license, compatibility, signing/install behavior, and maintenance before dependency adoption. PR #5 already has a direct GitHub Releases checker, so avoid replacing it without a demonstrated benefit. |
| APK-MultiUpdate | https://github.com/alltechdev/APK-MultiUpdate | Android release discovery, APK download and installation workflows | Candidate for implementation patterns. Verify its license and exact code/API compatibility before copying or adding as a dependency. |
| Cloudflare Agents self-modifying harness | https://github.com/cloudflare/agents/tree/main/examples/next/harnesses/self-modifying | Agent-editable TypeScript harness/custom tools, source snapshots, isolated candidate bundling/checking, activation of validated revisions, restore previous revisions | Strong architecture reference for self-development, but it is an experimental example using internal SDK APIs. The sample has no authentication; do not expose as-is. It is a separate cloud/TypeScript runtime, not a drop-in Android Kotlin library. |
| Obtainium | https://github.com/ImranR98/Obtainium | Track updates from upstream source releases | GPL-3.0; useful reference application for release-source/update UX. Do not import code without checking copyleft implications. |
| APKUpdater | https://github.com/rumboalla/apkupdater | Android release/update discovery | GPL-3.0; reference only unless project licensing compatibility is deliberately assessed. |
| Tern | https://github.com/munzzyy/tern | APK source, checksum and signer verification before installation | Reference for integrity-first updater behavior. Evaluate the implementation and license before reuse. |

## Capability gap inventory

The current PR #5 implementation covers only the basic Android GitHub Releases check, APK download, and Android installer handoff. The following capabilities remain separate, unimplemented work items:

1. **Integrity verification:** compare a release-published SHA-256 checksum and validate APK signing certificate continuity before handing the file to Android installer.
2. **Self-code editor:** a controlled workspace/patch tool for proposing code changes, not direct writes to the live app.
3. **Custom tool creator/registry:** define, validate, version, and register new tools with schemas and permissions.
4. **Build tool:** compile a candidate revision in an isolated CI/build environment.
5. **Automated test/validation tool:** run unit tests, static checks, and candidate smoke tests before activation.
6. **Revision activation and rollback:** activate only a successful validated candidate; preserve and restore the prior known-good revision.
7. **GitHub workflow tools:** scoped repository/branch/commit/PR operations with branch isolation and audit trail.
8. **Human approval and security policy:** require explicit approval for sensitive code, permissions, network access, release publication, and activation; deny secrets access by default.
9. **Audit trail:** record change proposal, diff, test evidence, approval, activation revision, and rollback event.

## Recommended implementation sequence

- First: harden the Android updater with checksum/signing-certificate validation. Keep it inside the existing PR #5 branch.
- Next: design the self-development runtime separately from Android UI. Cloudflare's example is an architecture reference only; first establish authentication, least-privilege tool access, isolated candidate execution, approval gates, and reliable revision rollback.
- Then: implement tool registry + build/test orchestration + GitHub branch/PR tools as independently scoped feature work. No merge or production activation without explicit approval.
- Before adopting any dependency: verify repository license, maintenance, API compatibility, Android minSdk, and security behavior from its source and LICENSE file.

## Research notes

- No external source project has been copied into Purnima.
- No new dependency has been added based solely on search results.
- This document is a checkpoint, not a claim that the remaining capabilities are complete.
