# Triage round 2: baseline-mock-protocol-parity

Reviewed: f04f6e36..8bda22ae. Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (clean), review-antigravity-2.md (clean); qa round 2: AC-1..AC-8 PASS, evidence b2a8c42f (audited).
Round 2 rule: fix blockers and majors only.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Evidence file missing | tests F1 | major | reject | Resolved: qa round 2 wrote and audited docs/superpowers/evidence/2026-11-26-baseline-mock-protocol-parity-device.md (commit b2a8c42f) after the reviewed head |
| 2 | Browse's required items/2 depends on a fixed 1.5 s sleep vs a 750 ms production dwell | edge F1, codex, tests F3 | minor | accept | Fails loudly (missing=[...], exit 2), never commits a wrong profile; run 4 11/11. Listed; a bounded journal wait is a cheap follow-up |
| 3 | Playback marks its movie bookmarked via the folder lookup | codex | minor | accept | Bookmark icon only; Play path unaffected. Listed |
| 4 | Folder fixture test checks non-empty, not the watch-later title | edge F2, tests unproven | minor | accept | Content test AC-6 guards the fill rules. Listed |
| 5 | browseScenario_requiresItemFoldersRoute not traced in the test design | tests F2 | minor | accept | Documentation; listed |
| 6 | Stale test name itemFixtures_carryWatchedAndBookmarkedMovie | edge F3, tests F4 | nit | accept | Listed |
| 7 | dispatching race | codex rejected | - | reject | Set and read inside the same lock |

Deferred: none new (baseline-player-track-coverage from round 1). Accepted: #2-#6.
