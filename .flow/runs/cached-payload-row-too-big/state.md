---
slug: cached-payload-row-too-big
title: "A cached_payload row over the 2 MB CursorWindow breaks the 4K «Все» shelf and grid (SQLiteBlobTooBigException)"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/cached-payload-row-too-big
worktree: /home/dev/orca/workspaces/Puber/cached-payload-row-too-big
base: 8e005395
spec: /home/dev/orca/workspaces/Puber/cached-payload-row-too-big/docs/superpowers/specs/2026-11-24-cached-payload-row-too-big.md
plan: /home/dev/orca/workspaces/Puber/cached-payload-row-too-big/docs/superpowers/plans/2026-11-24-cached-payload-row-too-big.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: catalogue-browse-all
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-24
---

## Next
nothing (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | codex: required on every round (at usage limit: skip, do not wait); antigravity on every round | owner (standing instruction) |
| 2026-11-24 | all gates on recommended answers, no questions | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved as designed (size M, no tracks, AC-1..AC-6) | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 2: ship (judge-1 PASS); squash to <=4 commits before /task:ship | owner (standing instruction: recommended) |
| 2026-11-24 | Q1 per-row limit 1 MiB of UTF-8 bytes, one constant next to the store; Q2 oversized details record kept in memory only this run, split/trim is a follow-up idea; Q3 read guard = SQL length(CAST(payload AS BLOB)) filter plus delete of over-limit keys in the same store call | owner (standing instruction: recommended) |

## Log
- 2026-11-24 created (stage idea) from catalogue-browse-all qa round 1 defect 2: on the TV box the 4K tab's «Все» shelf and the browse grid show «Не удалось выполнить запрос»; Retry fails in ~0.25 s with no network request; logcat: SQLiteBlobTooBigException: Row too big to fit into CursorWindow on SELECT * FROM cached_payload WHERE key IN (...). Paths: data/db/CachedPayloadDao.kt:13-14 (readAll SELECT *), data/repository/PersistentPayloadStore.kt:60-64, data/cache/CachedFeed.kt:360 (peekAll). Config TabTypeConfig.for4kSections unchanged since e4d75215, so it predates catalogue-browse-all. Options: cap payload size on write, or read in pieces; which key is oversized is not confirmed. Clearing app data needs explicit owner authorization.
- 2026-11-24 /task:run started; base 8e005395; stage triage (research first)
- 2026-11-24 research.md and brief.md written; size M, kind bug, no tracks; Q1-Q3 on recommended answers; worktree /home/dev/orca/workspaces/Puber/cached-payload-row-too-big (branch prakunin/cached-payload-row-too-big)
- 2026-11-24 design-architect.md and test-design.md written; spec assembled and committed; gate 1 approved (standing instruction); stage plan
- 2026-11-24 plan written; readiness-1 READY; stage impl
- 2026-11-24 impl done: fd6e8233, 6b9594ad, a0c0648a, af7d459a; check.fast green; named tests 14/14 present; stage verify
- 2026-11-24 qa-device round 1: AC-1..AC-6 PASS, R12 NOT CHECKED, no defects; evidence a4b27373; stage review
- 2026-11-24 review round 1: triage-1 T1-T4 fix, T5/T6 deferred to cached-item-card-only-fallback, T7-T9 rejected; spec risk rows corrected (architect); fix commits 23dfb968, 69c61413
- 2026-11-24 review round 2: codex major resolved by qa round 2 (AC-1/2/6 PASS at 69c61413, evidence 6f3ba67c); 3 minors accepted, 2 rejected; stage judge
- 2026-11-24 judge-1 PASS; gate 2 ship (standing instruction); squash before ship
- 2026-11-24 squashed to ce4dfbaa, d84fec93 (tree unchanged, check.fast green); waiting for the owner's /task:ship
- 2026-11-24 shipped: merge ba679aea; worktree removed
