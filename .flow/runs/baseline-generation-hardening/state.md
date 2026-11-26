---
slug: baseline-generation-hardening
title: "Harden the baseline profile generation route: backup safety, preflight wait, private adb server, rail check, auth log presence"
stage: done
size: M
tracks: []
kind: improvement
branch: prakunin/baseline-generation-hardening
worktree:
base: b27d8e40
spec: docs/superpowers/specs/2026-11-26-baseline-generation-hardening.md
plan: docs/superpowers/plans/2026-11-26-baseline-generation-hardening.md
rounds: {verify: 1, review: 5, judge: 2}
blocked_by: []
source: release-startup-compilation
created: 2026-11-23
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run baseline-generation-hardening

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q2 | (plan) test_term_to_group_restores_profile marked red but make re-raises TERM on base too; keep as written and record guard if green on base? | no | answered | Yes |
| Q1 | AC-4 "polls get-state": read device state from adb devices instead (get-state reports offline only as an error)? | no | answered | Yes, same check (device state polled ~30 s) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved (M, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-26 | Analyst decisions as recommended: own adb port 5039 for generation; restore a leftover .bak with a notice; rail check via focused item first, testTag only as fallback; commit the proving run profiles only if BaselineProfileContentTest passes and the egress journal is empty | owner (standing instruction: recommended) |

## Log
- 2026-11-23 created (stage idea) from release-startup-compilation review round 2 (triage-2.md, deferred minors; details in .flow/runs/release-startup-compilation/review-edge-2.md F1-F5): AuthEventLog still logs a cleared token (sealed empty string) as present - fix #6 of round 1 did not hold, decode length > IV+tag instead; railShows can pass falsely after Series (4K section title on the Series screen) - look the title up inside the rail; Makefile baseline-profile backup unsafe after an aborted run and HUP not trapped - restore a leftover .bak, trap EXIT/HUP, ignore *.bak; preflight refuses a just-connected emulator still offline - poll get-state; cleanup kills the 5038 private adb server even when generation never started - kill only when started. Also the script's false "not paired after the run" warning seen in generation run 3
- 2026-11-26 related: hermetic-baseline-profiles (mock-backend generation from upstream) may make the auth-related parts moot; decide in triage which run owns what.
- 2026-11-26 narrowed by the hermetic-baseline-profiles split: F1 (AuthEventLog token presence) and the false "not paired" warning move to hermetic-baseline-generation (the auth code is deleted there). Remaining scope, size S, after hermetic-baseline-generation (same files): Makefile .bak restore and EXIT/HUP trap, wait for an emulator still offline, stop the private adb server only if started, railShows false pass after Series.
- 2026-11-26 /task:run started (base b27d8e40); research
- 2026-11-26 research.md (F2-F5 still apply; recommended B incl. own adb port 5039 and API-34 wording). Analyst.
- 2026-11-26 brief.md: M (profile risk area), 9 ACs, no blocking questions. Stage design.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/baseline-generation-hardening; architect design
- 2026-11-26 design-architect.md (9 decisions, 7 risks); Q1 as recommended. test-designer.
- 2026-11-26 test-design.md (P0 14, P1 5, P2 1, manual 5); spec assembled and committed; gate 1 approved. Stage plan.
- 2026-11-26 plan (5 tasks); Q2 as recommended. Readiness.
- 2026-11-26 readiness-1 READY (Q2: TERM test is a guard; M2 note on a collapsed rail keeping focus). Stage impl.
- 2026-11-26 impl done: 516d23c8, e07e338f, fd08c9ee, b0b52852, ddbe66c7. D8 fired (focused selector could not see the rail on the emulator): main_rail testTag + testTagsAsResourceId on the rail Column in MainScreenComponent.kt -> box check M4 needed. Proving run clean (logcat /tmp/puber-baseline-profile/20261126-183120-logcat.txt); M2 scratch edit fails the new check; base did not false-pass on device. Shell tests 15 PASS; check.full green. qa waits for the box lease (details qa running).
- 2026-11-26 qa round 1: check.full green, shell tests 15/15, AC-1..AC-9 PASS, M4 on the box PASS (tagged vs base identical); evidence 6063ddec. Side finding (pre-existing) -> rail-left-focus-nearest-entry. Review round 1 at 6063ddec.
- 2026-11-26 review round 1 at 6063ddec: blind 2 minor, edge 3 minor, tests 4 minor, codex 2 minor, antigravity clean. triage-1.md: fix #1-#7, reject #8. Implementer fix round.
- 2026-11-26 fix round 1: 05501d77, 0a7757b8, 4071d839; shell suite 21 PASS (4 new red on base); #1 reworded (no working selected-entry selector; LEFT landing is flaky - rail-left-focus-nearest-entry); implementer saw baseline-prof.txt modified and startup-prof.txt deleted after failed scratch runs (restored by git checkout) - asked round 2 to check startup-prof.txt protection. Review round 2 on 6063ddec..4071d839.
- 2026-11-26 review round 2: codex 1 major + 2 minor, antigravity unavailable (reviewer's read: 1 major + 1 minor), edge 3 minor, tests 3 minor + 1 nit. triage-2.md: fix #1-#6. Implementer fix round 2; analyst/architect amend AC-1/D4.
- 2026-11-26 fix round 2: 9a3c3a12, 91a9b7ba (#1-#4, #6); shell suite 27 PASS (10 red on base); AC-1 and D4 amended (#5), spec re-assembled. Review round 3 on 4071d839..91a9b7ba.
- 2026-11-26 review round 3: codex 2 minor (stale discriminator wrong - confirmed: merged output has 1183 H rules), edge 2 minor + 1 nit, tests 2 minor + 1 nit, antigravity 1 minor. triage-3.md: fix #1-#5 (caps lifted), reject #6. Implementer fix round 3; analyst/architect amend AC-1/D10.
- 2026-11-26 fix round 3: 017e975e (#1-#3, #5); AC-1 and D10 amended (#4); spec re-assembled. Review round 4 on 91a9b7ba..017e975e.
- 2026-11-26 review round 4: tests clean; edge unproven only; codex and antigravity 1 minor regression (missing file treated as stale). triage-4.md: fix #1. Implementer fix round 4.
- 2026-11-26 fix round 4: 123eb814 (missing/empty profile -> restore); shell suite 30 PASS (2 red on base). Review round 5 on 017e975e..123eb814.
- 2026-11-26 review round 5: all clean (one spec-wording minor fixed: AC-1 amended, spec re-copied). Stage judge.
- 2026-11-26 judge-1 FAIL: (1) docs/release.md:143-145 states the round-2 restore rule; (2) triage-1 #7 open - shell suite in no gate. Conductor appended "&& bash tools/tests/test-baseline-profile-tooling.sh" to check.full in .flow/config.yaml (uncommitted, next to the owner's config edits; the script exists on master after this run merges). Implementer fixes the docs.
- 2026-11-26 docs fix (reworded by the conductor to drop a Co-Authored-By line the implementer added); co-authored 0. Judge round 2.
- 2026-11-26 judge-2 PASS (check.full incl. shell suite: exit 0, 30 PASS). Gate 2: ship (standing instruction). Squash.
- 2026-11-26 squashed to 4 commits (135309c7, bd870796, 882e2197, f40138d8); tree identical; co-authored 0. Next: /task:ship baseline-generation-hardening (owner); then baseline-mock-protocol-parity.
- 2026-11-26 shipped: merged prakunin/baseline-generation-hardening into master as 5fcdb277 (--no-ff); compile green on merged master; Orca worktree removed.
