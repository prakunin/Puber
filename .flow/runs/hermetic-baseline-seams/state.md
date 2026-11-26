---
slug: hermetic-baseline-seams
title: "Add seams for hermetic profile generation: pinned API endpoint, DoH bypass when pinned, PuberApp hook before DI"
stage: done
size: M
tracks: []
kind: improvement
branch: prakunin/hermetic-baseline-seams
worktree:
base: b53bf631
spec: docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md
plan: docs/superpowers/plans/2026-11-26-hermetic-baseline-seams.md
rounds: {verify: 1, review: 2, judge: 0}
blocked_by: []
source: hermetic-baseline-profiles
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run hermetic-baseline-seams

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Port all three PuberApp hooks (shouldInitializeRuntime, configureRuntime, runtimeModules)? | yes | answered | Yes, all three |
| Q2 | Does shouldInitializeRuntime()==false also skip TelemetryRuntime.install and StartupReveal? | no | answered | Yes; gate right after super.onCreate() |
| Q3 | Keep ApiEndpointMode and make getState() report the pinned domain? | no | answered | Drop ApiEndpointMode; getState() unchanged |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | No device for now: neither the emulator nor the TV box (Q1 of hermetic-baseline-profiles); device-dependent ACs stay NOT CHECKED until the owner allows a device | owner |
| 2026-11-26 | Q1-Q3 as recommended; codex and antigravity every round; caps lifted | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved (M, 6 ACs) | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship; AC-6 (device launch) NOT CHECKED by the owner's no-device decision | owner (standing instruction: recommended) |
| 2026-11-26 | Device policy update: TV box 192.168.1.110:5555 for launch checks; emulator host.docker.internal:5555 (API 36) for baseline profile generation and its evidence only | owner |

## Log
- 2026-11-26 created (stage idea), part of the L split of hermetic-baseline-profiles (see /home/dev/Puber/.flow/runs/hermetic-baseline-profiles/brief.md and research.md).
- Split item 1 (first). Pinned endpoint in KinoPubConfig plus guards in the seven ApiDomainInteractor mutators; DoH bypass in HttpStack (data/api/network/HttpStack.kt:24-37) when pinned; PuberApp hook that runs before DI starts. Dev and prod behave as today. Brief AC-1..AC-3. Re-port of upstream-sync-triage a57a3238 and 183f2b7b onto master's reworked KinoPubConfig snapshot, HttpStack and AppStartup.
- Q3 (as recommended, adjusted for no device): verified by seam unit tests (pinned and unpinned) and an APK packaging check; the dev-build launch on the box and real-backend login are NOT VERIFIED until a device and the account are available.
- 2026-11-26 /task:run started (base b53bf631); research
- 2026-11-26 research.md written (recommended A; hand port of a57a3238/183f2b7b; 4 questions). Analyst.
- 2026-11-26 brief.md: M, 6 ACs (AC-6 device launch NOT CHECKED by owner decision); Q1-Q3 as recommended. Stage design.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/hermetic-baseline-seams (prakunin/hermetic-baseline-seams); architect design
- 2026-11-26 design-architect.md (10 decisions, 7 risks, no questions); test-designer
- 2026-11-26 test-design.md (P0 19, P1 4, P2 1, manual 4); spec assembled and committed; gate 1 approved (standing instruction). Stage plan.
- 2026-11-26 plan written (4 tasks); readiness
- 2026-11-26 readiness-1 NOT READY (3 test-design must-fix); test-designer round 2, then architect syncs the plan
- 2026-11-26 test-design and plan synced; readiness-2 READY, pasted into the plan. Stage impl.
- 2026-11-26 impl done: cb770a09, 5f65975b, 73de7415, 40be4cb6, 60495cb5, c50f506c; conductor check: check.fast BUILD SUCCESSFUL in 1s; named tests: checked 0 name(s): 0 missing, 0 skipped; co-authored 0. Stage verify (no device).
- 2026-11-26 qa round 1: check.full green (2651 tests), both assembles green; AC-1..AC-5 PASS (AC-4 from the diff), AC-6 NOT CHECKED (no device, owner). Review round 1 at c50f506c.
- 2026-11-26 review round 1 at c50f506c: blind 1 minor + 1 nit, tests 2 minor, codex 1 minor, edge/antigravity clean. triage-1.md: fix #1-#5, reject #6-#9. Implementer fix round.
- 2026-11-26 fix round 1: 0d62af49, f5adb9c9, 5a688a1b, 4268236c (triage-1 #1-#5); implementer check.full + assembleProdRelease green. Review round 2 on c50f506c..4268236c (no runtime code change besides profile rule text; qa not re-run).
- 2026-11-26 review round 2: edge, tests, codex, antigravity clean. triage-2.md: nothing to fix. Stage judge.
- 2026-11-26 judge-1 CONCERNS: AC-3 test-design row and plan still name HttpStackTest.create_usesSystemDnsAndKeepsTheDnsCache_withoutDoh (renamed to create_usesSystemDns_withoutDoh in fix round 1); judge ran check.full at 4268236c (2654 tests, green). Test-designer and architect fix the names.
- 2026-11-26 judge concern resolved: test-design row and plan renamed to create_usesSystemDns_withoutDoh, fix-round tests listed as P1 guards; spec re-copied. Gate 2: ship (standing instruction; device-less by owner decision). Squash.
- 2026-11-26 squashed to 4 commits (9a6f680f, 201cb1b0, 3a7f245b, 23fb886d); tree identical to 3f39b360; co-authored 0. Next: /task:ship hermetic-baseline-seams (owner).
- 2026-11-26 owner allowed the box for AC-6 before ship; qa round 2 on the box (launch check). Stage back to verify; gate 2 stands if AC-6 passes.
- 2026-11-26 qa round 2 on the TV box: AC-6 PASS (install-preserve compatible_replace, sha256 match, cold launch to Main, relaunch, 0 crashes; no API calls). Evidence 4dddadd2, being folded into the docs commit. Gate 2 ship stands.
- 2026-11-26 evidence folded: 4 commits (9a6f680f, 201cb1b0, 3a7f245b, 268f464e), tree f0554625 unchanged, co-authored 0. Next: /task:ship hermetic-baseline-seams (owner).
- 2026-11-26 shipped: merged prakunin/hermetic-baseline-seams into master as 8a7adbe0 (--no-ff); Orca worktree removed.
