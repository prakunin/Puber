---
slug: cache-storage-bounds
title: "Bound the caches: main player cache read-only, cached_payload swept at idle with a cap, legacy-cleanup flag persisted, Coil memory cache capped in bytes"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [perf]
# Kind: bug | feature | improvement | refactor | idea
kind: refactor
branch: prakunin/cache-storage-bounds
worktree:
base: a22c03bc
spec: /home/user1/orca/workspaces/Puber/cache-storage-bounds/docs/superpowers/specs/2026-11-21-cache-storage-bounds.md
plan: /home/user1/orca/workspaces/Puber/cache-storage-bounds/docs/superpowers/plans/2026-11-21-cache-storage-bounds.md
rounds: {verify: 2, review: 3, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cache-layer-rework
created: 2026-11-21
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-21
---

## Next
triage with /task:run cache-storage-bounds

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Legacy rows: delete once in a Room 3->4 migration, or runtime cleanup behind a saved prefs flag? | yes | answered | Room 3->4 migration (recommended) |
| Q2 | cached_payload cap by rows or bytes, and value? | no | answered | Rows, start at 2,000, confirm from the emulator row count (AC-11) before review (recommended) |
| Q3 | Coil memory limit? | no | answered | 48 MB fixed; raise if Home rows visibly reload posters (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Gate 2: ship (judge CONCERNS accepted: AC-1 7-min run, PSS single samples, downgrade risk, trim shown with a 128 MiB build; M6 poster reloads left for the owner) | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1: approved as written | owner (standing instruction: recommended) |
| 2026-11-21 | Q1: legacy rows deleted once in a Room 3->4 migration; Q2: cached_payload capped by rows at 2,000, confirmed from the emulator row count; Q3: Coil memory cache 48 MB fixed | owner (standing instruction: recommended) |
| 2026-11-21 | Fold content-cache-legacy-cleanup into this run (strict subset per research.md); media-cache half of player-cache-save-churn moves here | owner (standing instruction: recommended) |
| 2026-11-21 | caps lifted (owner) | owner |
| 2026-11-21 | codex: required (owner) | owner |
| 2026-11-21 | Owner decisions inherited from cache-layer-rework: main player reads the media cache only, the warm-up player writes; cap 256 MB after the device check | owner |

## Log
- 2026-11-21 created (stage idea) as split item B3 of cache-layer-rework (owner Q1, 2026-11-21); scope, seed ACs and risks in .flow/runs/cache-layer-rework/brief.md and research.md. No order constraint. Q2: main player read-only, warm-up player writes; cap lowered to 256 MB after the device check.
- 2026-11-21 /task:run started; base a22c03bc; stage triage
- 2026-11-21 research.md written (DUPLICATE_OF content-cache-legacy-cleanup, folded in; recommended option A)
- 2026-11-21 triage: brief.md, refactor, size M, track perf, 11 ACs, device verification yes; Q1-Q3 answered with the recommendations
- 2026-11-21 Orca worktree /home/user1/orca/workspaces/Puber/cache-storage-bounds (branch prakunin/cache-storage-bounds) at a22c03bc; stage design
- 2026-11-21 baseline.md (qa-device; note: the baseline playback added a South Park S1E1 history entry on the account), design-architect.md, test-design.md; spec assembled and committed; gate 1 approved (standing instruction); stage plan
- 2026-11-21 plan written (6 tasks); readiness-1 READY; spec Out of scope corrected (the in-scope table moved under Problem); stage impl
- 2026-11-21 impl done: 6 commits f223bc19..ae127475; conductor check: diff --stat ok, check.fast green, named tests present (check-named-tests.py parsed 0 names; checked by grep), no Co-Authored-By; record lists bbf13622 for the last commit, actual ae127475 (amend); stage verify
- 2026-11-21 verify round 1: qa-report.md all 11 ACs PASS (AC-5 cap by tests only), check.full green, evidence dfad8b7c; account mutation: episodes 7-8 of one series played (resume position saved); six legacy fixture rows seeded via run-as; stage review
- 2026-11-21 review round 1 on dfad8b7c: blind/edge/tests/codex saved; triage-1.md: 6 fix (4 minor, 2 nit), 0 blocker/major; codex clean
- 2026-11-21 review round 2 on a8fdd8d1: edge clean, codex clean, tests 1 major + 2 minor; qa round 2: check.full red (lint 17 UnsafeOptInUsageError, detekt 1), AC-3 trim shown with a temporary 128 MiB build, evidence b28b9369; qa replaced a non-debuggable build of the package on emulator-5554 (likely puber-ea's), puber-ea warned about the Room v4 downgrade risk; triage-2: 3 fix, 2 accept
- 2026-11-21 fix round 2 7c6868fd; review round 3: edge, tests, codex clean (codex notes the 7-min AC-1 run, accepted and listed for the owner); conductor check.full green on 7c6868fd; stage judge
- 2026-11-21 judge-1 CONCERNS, no must-fix; Co-Authored-By count 0; gate 2 ship; next squash to <5 commits then /task:ship
- 2026-11-21 squashed to 3 commits b161b061, d52b5800, 3a1828e5; tree 524a915b unchanged; check.fast green
- 2026-11-21 shipped: merged --no-ff into master as 8428c946; Orca worktree removed; branch prakunin/cache-storage-bounds deleted (merged, -d)
