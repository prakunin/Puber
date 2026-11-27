---
slug: player-corrupt-cache-recovery
title: "A corrupt cached media container fails playback with Source error instead of recovering from the network"
stage: done
size: S
tracks: []
kind: bug
branch: prakunin/player-corrupt-cache-recovery
worktree: /home/dev/orca/workspaces/Puber/player-corrupt-cache-recovery
base: 8ffdbca1
spec:
plan:
rounds: {verify: 2, review: 1, judge: 1}
blocked_by: []
source: player-hermetic-fixtures
created: 2026-11-27
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Accept failover to the next candidate (A) or also stop warm-up committing a non-media body (B, M)? | yes | answered | A (recommended); B only as a separate idea run if failovers show up in real use |
| Q2 | Rename the "Recovers" test? | no | answered | no (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1 option A (assert NEXT_URL failover, test-only); Q2 keep the test name | owner (standing instruction: recommended) |
| 2026-11-27 | Instrumented runs on the emulator host.docker.internal:5555 | owner (standing instruction: recommended, as player-hermetic-fixtures Q1) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 CONCERNS: evidence lagged the head; fixed before ship by qa round 2) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-hermetic-fixtures: device-run-5.md: corrupt cache entry reaches READY then ExoPlaybackException Source error; FLAG_IGNORE_CACHE_ON_ERROR (PlaybackController.kt:643) covers IO errors only. Whether this matters in real use (cache corruption frequency) is unproven; triage should decide. Test: PlaybackControllerResourcesTest corrupt-entry case, outcome currently not asserted.
- 2026-11-27 preflight: base 8ffdbca1; stage triage
- 2026-11-27 research.md (option A recommended: assert NEXT_URL failover, no production change)
- 2026-11-27 brief.md (bug, S, 5 ACs); stage design
- 2026-11-27 test-design.md (P0 3, P1 2, manual 4); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/player-corrupt-cache-recovery; stage impl
- 2026-11-27 impl: 000beab1, 4ce45e30; mutant red, 3x3/0 on emulator, check.full pass; stage verify
- 2026-11-27 qa-report round 1: AC-1..AC-5 PASS; stage review
- 2026-11-27 review round 1 (blind, tests, codex; antigravity skipped: quota): triage-1: 2 cheap test fixes, 1 rejected (Q2)
- 2026-11-27 fix round 1: a8607ccc (tests only); 3x3/0 on emulator; stage judge
- 2026-11-27 judge-1 CONCERNS (no must-fix); evidence brought to head a8607ccc by qa round 2; gate 2 ship (standing instruction); stage handover
- 2026-11-27 squashed to 2 commits 659730ac, 68ea69f5 (tree unchanged)
- 2026-11-27 shipped: merge commit bf7651bf (--no-ff) into master; Orca worktree removed; stage done
