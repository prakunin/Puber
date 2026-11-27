---
slug: manual-quality-hls4
title: "Play manual quality from the hls4 stream with a max video size, keeping manifest subtitles and audio names"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/manual-quality-hls4
worktree: /home/dev/orca/workspaces/Puber/manual-quality-hls4
base: 0823528b
spec: docs/superpowers/specs/2026-11-27-manual-quality-hls4.md
plan: docs/superpowers/plans/2026-11-27-manual-quality-hls4.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-14
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Manual quality: pin the picked quality or only cap it? | yes | answered | pin: max size = picked size + setForceHighestSupportedBitrate(true) (recommended) |
| Q2 | Keep option C label guard (localized fallback label, no placeholder audio preference saved) in this run? | yes | answered | yes (recommended) |
| Q3 | If the corner-draw glitch shows with the new stream, fix it here? | no | answered | no: record it, separate run unless this change causes it (recommended) |
| D1 | Clean up "Track 1" audio labels already saved on devices? | no | answered | no (recommended) |
| D2 | Localize the hardcoded "Unknown" language in API audio rows (PlayerUIMapper.kt:24)? | no | answered | no, separate run (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: pin the picked quality | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: label guard in this run | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: corner-draw glitch out of scope unless caused here | owner (standing instruction: recommended) |
| 2026-11-27 | D1: no cleanup of saved "Track 1" labels; D2: "Unknown" localization out of scope | owner (standing instruction: recommended) |
| 2026-11-27 | Instrumented tests (AC-2, AC-3) run on the emulator host.docker.internal:5555 (com.kino.puber.instrumentation is not allowed on the box), as in player-hermetic-fixtures Q1; dev-APK device checks M1-M6 on the box | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 CONCERNS: AC-6 manifest origin not verified; pinned variant may rebuffer, owner choice Q1) | owner (standing instruction: recommended) |

## Log
- 2026-10-14 created (stage idea) from subtitle-sideload-merge design delta 2 (Q5, recommended answer taken under the owner's standing instruction): manual quality plays `url.hls` first (PlayerInteractor.kt:238-246), which has no subtitle renditions and no audio names ("Track 1"), so manifest subtitles vanish on 720p/480p. Proposed: play hls4 with a max video size. Also seen on device: on 480p/720p the new stream was once drawn at native size in the top-left corner over a frozen old frame. Context: ../subtitle-sideload-merge/design-architect-2.md, qa-report.md round 2.
- 2026-11-27 folded in run player-audio-track-label-fallback (source: player-callback-gate): research.md (researcher, options A/B/C, recommended C = hls4 with max video size + localized fallback label that never saves a placeholder audio preference) and baseline-master.md + baseline-master-shots/ (qa-device on box, master 0823528b: first start lists 4 named audio tracks; after Auto -> 720p and 720p -> 480p only one track "Track 1")
- 2026-11-27 preflight: base 0823528b; research.md carried over from player-audio-track-label-fallback; stage triage
- 2026-11-27 brief.md (bug, M, 10 ACs, 3 questions answered on recommended); stage design
- 2026-11-27 Orca worktree /home/dev/orca/workspaces/Puber/manual-quality-hls4, branch prakunin/manual-quality-hls4
- 2026-11-27 design-architect.md (8 decisions, 7 risks)
- 2026-11-27 test-design.md (P0 17, P1 7, P2 2, manual 6)
- 2026-11-27 spec assembled; gate 1 approved (standing instruction); stage plan
- 2026-11-27 plan (5 tasks) written
- 2026-11-27 readiness-1 READY; stage impl
- 2026-11-27 impl done: bb17958a..006b2a9d; check.fast green (conductor); itest-1 11/0; stage verify
- 2026-11-27 qa-report round 1: check.full pass, AC-1..AC-10 PASS (AC-6 caveat: manifest origin of subtitles not visible; AC-9 guard unit-only); manual: listen to UKR after switch (M5); stage review
- 2026-11-27 review round 1 (6 lenses): triage-1: 1 major + 3 minor to fix
- 2026-11-27 fix round 1: 13613065, 07aba956 (head 07aba956); itest-fix1 3x12/0; check.fast exit 0
- 2026-11-27 review round 2 (edge, tests, codex, antigravity): 1 minor accepted; qa round 2: AC-2, AC-3, AC-5 PASS, check.full pass; new idea run player-audio-und-label (pre-existing "und" label); stage judge
- 2026-11-27 judge-1 CONCERNS (no must-fix, manual M5); gate 2 ship (standing instruction); stage handover
- 2026-11-27 squashed to 4 commits 3c8acbe1..25f4e8b1 (tree ce5b0036 unchanged)
- 2026-11-27 shipped: merge commit 268764b1 (--no-ff) into master; Orca worktree removed; stage done
