---
slug: subtitle-switch-text-disable
title: "Disable text during switchStream as upstream 7d5addd2 does, proven by instrumented PlaybackController tests on the P6-A fixtures"
stage: done
size: S
tracks: []
kind: improvement
branch: prakunin/subtitle-switch-text-disable
worktree: /home/dev/orca/workspaces/Puber/subtitle-switch-text-disable
base: 7a9fd580
spec:
plan:
rounds: {verify: 1, review: 1, judge: 1}
blocked_by: []
created: 2026-10-15
migrated:
updated: 2026-11-28
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Drop the upstream switchStream disable (parent AC-13) and ship only tests that pin current behaviour (option B)? | yes | answered | yes (recommended) |
| Q2 | Also cover a DEFAULT=YES subtitle rendition? | no | answered | no (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-15 | Split from subtitle-restore-model as Part 2 (option C); starts after Part 1 and P6-A | owner |
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1 option B: no port of upstream 7d5addd2, tests pin current behaviour; parent AC-13 dropped. Q2 no DEFAULT=YES case | owner (standing instruction: recommended) |
| 2026-11-27 | Instrumented runs on the emulator host.docker.internal:5555 | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 PASS) | owner (standing instruction: recommended) |

## Log
- 2026-10-15 created (stage idea) as Part 2 of subtitle-restore-model (owner chose option C, then the split). Scope: switchStream text disable (PlaybackController.kt:501-521, upstream 7d5addd2) and instrumented tests on P6-A fixtures; ACs AC-13..AC-15 in ../subtitle-restore-model/brief.md. Open risk from the analyst: confirm PlayerVM re-sends the subtitle after switchStream, otherwise the disable leaves subtitles off after every quality switch.
- 2026-11-27 preflight: base 7a9fd580; stage triage
- 2026-11-27 research.md (upstream bug already blocked here; option A = controller disable + VM re-send, M)
- 2026-11-27 brief.md (improvement, S, 5 ACs, tests only); stage design
- 2026-11-27 test-design.md (P0 4 guards, P1 1); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/subtitle-switch-text-disable; stage impl
- 2026-11-27 impl: bb8a704b, f143b7d9 (tests and docs only); 4 guards fail under their mutations; NetworkTracks 3x16/0; check.full pass; stage verify
- 2026-11-27 qa-report round 1: AC-1..AC-5 PASS; stage review
- 2026-11-27 review round 1 (blind, tests, codex; antigravity skipped: quota): triage-1: 2 fixes, 3 rejected (incl. blind F1 major: scope by Q1)
- 2026-11-27 fix round 1: bd057686 (tests and docs); 2x16/0 on emulator; stage judge
- 2026-11-27 judge-1 PASS; 4 commits, no squash needed; gate 2 ship (standing instruction); stage handover
- 2026-11-28 shipped: merge 55140ec1 into master; worktree removed; stage done
