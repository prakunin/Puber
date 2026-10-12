---
slug: tmdb-result-handling
title: "`runCatchingCancellable` instead of `apiResult`"
stage: done
size: M
tracks: []
kind: refactor
branch: tmdb-result-handling
worktree: /home/user1/orca/workspaces/Puber/tmdb-result-handling
base: e569e055
spec: docs/superpowers/specs/2026-10-12-tmdb-result-handling.md
plan: docs/superpowers/plans/2026-10-12-tmdb-result-handling.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-12
updated: 2026-10-12
---

## Next
Nothing; shipped in ae416c2a.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Log the TMDB failure in SkipSegmentService? | yes | answered | no (B) |
| Q2 | Scope A or B? | yes | answered | B |
| Q3 | Smoke without TMDB token enough? | no | answered | yes, limitation recorded |
| Q4 | emulator-5554 holds a corrupted dev APK after install-preserve (app crashes, re-install blocked signer_unknown): how to repair, or accept AC-10 NOT CHECKED? | yes | answered | conductor runs `adb -s emulator-5554 install -r` of the worktree APK |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-12 | Q1: no logging in SkipSegmentService; privacy guards stay untouched (option B) | owner |
| 2026-10-12 | Q2: scope B - runCatchingCancellable swap plus one private find with fail-on-non-2xx | owner |
| 2026-10-12 | Size M (full cycle), as proposed by the analyst | owner |
| 2026-10-12 | Q3: device smoke without a TMDB token plus unit tests is enough; limitation recorded in evidence | owner |
| 2026-10-12 | Gate 1: spec approved | owner |
| 2026-10-12 | Gate 2: ship (judge-1 CONCERNS accepted; AC-10 M2 stays NOT CHECKED) | owner |
| 2026-10-12 | Q4: authorize `adb -s emulator-5554 install -r` of the worktree dev APK over signer_unknown (no uninstall/clear) | owner |

## Log
- 2026-10-12 research.md written (no duplicate; options A/B/C)
- 2026-10-12 triage: brief.md, size M, kind refactor; owner answered Q1-Q3
- 2026-10-12 worktree created (orca) at /home/user1/orca/workspaces/Puber/tmdb-result-handling; stage design
- 2026-10-12 design: design-architect.md, test-design.md; spec assembled and committed in the worktree; awaiting gate 1
- 2026-10-12 gate 1 approved by owner; stage plan
- 2026-10-12 plan written and committed; readiness check 1
- 2026-10-12 readiness-1 NOT READY (test count 8->9); architect fixed; readiness-2 READY, pasted into plan; stage impl
- 2026-10-12 impl done: 2e4a127a, 78ecad0d, 0f153791; conductor re-ran check.fast (green), diff --stat matches the plan; stage verify
- 2026-10-12 qa-device round 1: check.full green, AC-1..9 PASS, AC-10 NOT CHECKED; on-device APK corrupted after install (sha mismatch), app crashes at start, second install blocked signer_unknown; no uninstall; stage blocked on Q4
- 2026-10-12 Q4 answered; conductor leased emulator-5554, auth-save, `install -r` Success, on-device sha256 4270beef... matches the build, app data present; lease released; qa round 2 (smoke only)
- 2026-10-12 qa round 2: M1 PASS (schedule shows TMDB-not-set-up state), M2 partial: playback ok, IntroDB skip prompt NOT CHECKED (mid-episode, no seek to avoid progress change); short playback reported to account watch progress; evidence c2b93ed0; verify cap reached; stage review
- 2026-10-12 review round 1: blind 2, edge 0, tests 2, codex 1 (adversarial); triage-1: 1 fix (tests for getTvDetails/getTvSeasonDetails), 3 rejected, AC-10 M2 carried to gate 2
- 2026-10-12 fix 7e0b94f3 (tests only); review round 2: edge, tests, codex clean; triage-2 no findings; stage judge
- 2026-10-12 judge-1 CONCERNS (no must-fix; check.full re-run green at 7e0b94f3); co-authored-by count 0; stage handover, awaiting gate 2
- 2026-10-12 gate 2: ship; next /task:ship tmdb-result-handling
- 2026-10-12 shipped: merge ae416c2a (--no-ff) into master; orca worktree removed; stage done
- 2026-10-12 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
