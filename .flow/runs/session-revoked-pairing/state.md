---
slug: session-revoked-pairing
title: "A revoked or banned session sends the user to pairing instead of offering a mirror change"
stage: done
size: M
tracks: [ui]
kind: bug
branch: prakunin/session-revoked-pairing
worktree: /home/dev/orca/workspaces/Puber/session-revoked-pairing
base: b53bf631
spec: /home/dev/orca/workspaces/Puber/session-revoked-pairing/docs/superpowers/specs/2026-11-26-session-revoked-pairing.md
plan: /home/dev/orca/workspaces/Puber/session-revoked-pairing/docs/superpowers/plans/2026-11-26-session-revoked-pairing.md
rounds: {verify: 4, review: 3, judge: 1}
blocked_by: []
source:
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
Done. Owner: AC-10 on the TV (master build), mirror dialog double Back vs master.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1: 401 that reaches apiCall on a bearer request ends the session; 403 only for error=user_banned; extend the list only from errors seen in ordinary use (recommended, owner standing instruction: run on recommended answers) | owner |
| 2026-11-26 | Q2: distinct blocked-account message on Auth, own string; plain expiry keeps error_unauthorized (recommended) | owner |
| 2026-11-26 | Q3: do not fold in session-expiry-delivery / session-logout-interactor; reuse onSessionExpired and the existing handler (recommended) | owner |
| 2026-11-26 | Q4: Home all-Unauthorized error keeps error_generic (recommended) | owner |
| 2026-11-26 | Q5: no design stand for the pairing screen / error views; accept ux-designer numbers instead of a stand (recommended; escalate_also rule answered by the standing instruction) | owner |
| 2026-11-26 | Gate 1: approved (M, ui) on the standing instruction | owner |
| 2026-11-26 | Plan questions 1-3 accepted on recommendations; readiness note 2 (Ktor bearer cache clear) taken into Task 1 | conductor |
| 2026-11-26 | Q6: keep Auth's 20 s mirror hint when blocked (recommended) | owner (standing instruction) |
| 2026-11-26 | Q7: blocked line while Auth loads: same style, centred, padding(bottom = 160.dp) (recommended) | owner (standing instruction) |
| 2026-11-26 | AC-7 amended (triage-2 #3): a refusal that did not end the session keeps the mirror action and the generic message; only a session-ending refusal (which routes to Auth) drops it. Recommended by the conductor; taken on the standing instruction, reported to the owner at gate 2 | owner (standing instruction) |
| 2026-11-26 | Review caps lifted (memory: no round caps); round 3 runs on the regression fix | conductor |
| 2026-11-26 | Gate 2: ship (judge-1 PASS; AC-10 and 2 manual items left for the owner) | owner (standing instruction: recommended) |
| 2026-11-26 | Start /task:run on the bug report with the conductor's root-cause notes | owner |

## Log
- 2026-11-26 created (stage triage) from the owner's bug report
- 2026-11-26 research.md written (option A recommended, no duplicate)
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/session-revoked-pairing created by orca (branch prakunin/session-revoked-pairing)
- 2026-11-26 brief.md written: bug, M, track ui, 10 ACs; Q1-Q3 answered on recommendations
- 2026-11-26 design-architect.md, design-ux.md, test-design.md written; spec assembled and committed; gate 1 approved
- 2026-11-26 plan written; readiness-1 READY; bearer cache clear added to Task 1; stage impl
- 2026-11-26 impl done: 7349f238, a76c87ba, d3d3f6b3; check.full green (implementer), check.fast green (conductor); named-tests: AuthScreenTest flagged MISSING is the row's alternative name, ScreensImplTest.auth_carriesTheSessionEndReasonIntoTheScreenKey holds it
- 2026-11-26 qa round 1: check.full green; AC-1..4, AC-7 (unit), AC-9 PASS; AC-5, AC-6 (device), AC-8 FAIL - session-end event lost on cold start (no subscriber), Home spinner forever; AC-10 NOT CHECKED. Evidence e8c81571
- 2026-11-26 architect revision for qa round 1: plan Tasks 4-6 (no refresh without refresh token; pending session-end replay; blocked line on Auth loading)
- 2026-11-26 fix round: 5ad691e2, e679abbf, a823c57c; check.full green (implementer)
- 2026-11-26 qa round 2: check.full green; AC-1..AC-9 PASS (device M-1 on the box, DeviceSettingsContentTest itest green); AC-10 NOT CHECKED (owner's call). Evidence da172bdf
- 2026-11-26 review round 1: 7 lenses (antigravity clean); triage-1: fix 8 minors, defer 2 (session-refresh-lock-race, home-error-retry-focus-test), reject 1 codex claim
- 2026-11-26 fix round 1: fa8b3b23, 42c898dd, 3968eeaa; check.full green (implementer)
- 2026-11-26 note: 4 commits (da172bdf, fa8b3b23, 42c898dd, 3968eeaa) carry a Co-Authored-By line; to be removed by the squash before handover (owner wants <5 commits)
- 2026-11-26 qa round 3: AC-1..9 PASS incl. persisted blocked line after force-stop; AC-10 NOT CHECKED. Evidence 79a85925
- 2026-11-26 review round 2: triage-2: fix 4 (claim-before-clears regression, handler test, AC-7 amendment, preview canvas)
- 2026-11-26 fix round 2: 831d6ccf; spec AC-7 amended 672d3618
- 2026-11-26 qa round 4 on the final code (APK 672d3618): AC-1..9 PASS, AC-10 NOT CHECKED; evidence 8be284f2
- 2026-11-26 review round 3: triage-3: 1 fixed (device re-run), 1 deferred (session-end-cleanup-overlap), 1 accepted
- 2026-11-26 judge-1 PASS (MANUAL 3); gate 2: ship on the standing instruction
- 2026-11-26 squashed to 4 commits (fb9411db, ce705d2e, 510d2cf5, 50323280), tree 641a1f9f unchanged, no Co-Authored-By, check.fast green
- 2026-11-26 reworded the Home/Settings commit; /task:ship cannot be started by the conductor (user-invoked only)
- 2026-11-26 shipped: merge 13f501f5 into master (--no-ff); worktree removed
