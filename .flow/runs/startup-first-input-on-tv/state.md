---
slug: startup-first-input-on-tv
title: "Measure and fix the first D-pad response after the splash reveal on a real TV"
stage: done
size: M
tracks: [perf]
kind: improvement
branch: prakunin/startup-first-input-on-tv
worktree: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv
base: c506d0bd
spec: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/specs/2026-11-22-startup-first-input-on-tv.md
plan: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/plans/2026-11-22-startup-first-input-on-tv.md
rounds: {verify: 5, review: 2, judge: 2}
blocked_by: []
source: startup-splash-preload
created: 2026-11-21
migrated:
updated: 2026-11-23
---

## Next
Done. Follow-ups: release-startup-compilation, startup-reveal-gate-on-slow-tv, home-hero-late-insert.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| D2-Q1 | Authorise `cmd package compile -m speed-profile -f com.kino.puber.stage` on the box for the compiled half of AC-6? | no | answered | yes (owner, 2026-11-22, "да, разрешаю") |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | codex: required on every round (standing) | owner |
| 2026-11-22 | caps lifted (standing) | owner |
| 2026-11-22 | Q1 box targets: AC-5/AC-6 as written; if baseline already meets them ship AC-1, AC-2, AC-7 with numbers | owner (standing instruction: recommended) |
| 2026-11-22 | Q2 keep the image-aware hold, bounded to requests sized by ready plus grace G | owner (standing instruction: recommended) |
| 2026-11-22 | Q3 fade stays 250 ms; bitmapFactoryMaxParallelism(2) only as box experiment, adopted if it improves AC-5/AC-6 without worsening AC-8 | owner (standing instruction: recommended) |
| 2026-11-22 | Base moved to c506d0bd (master advanced during preflight); brief AC-3 "2fd851a4" read as base c506d0bd | conductor |
| 2026-11-22 | Perf baseline (AC-3) needs AC-1/AC-2 telemetry, so it runs inside impl after those tasks, before AC-4/AC-7 code; G and Q fixed in the plan/spec from it | conductor |
| 2026-11-22 | Design Q1: grace-expiry reveal reported as a fourth cause `grace` in phase B (AC-1 cause list amended: ready/cap/forced/grace) | owner (standing instruction: recommended) |
| 2026-11-22 | Design Q2: if AC-8 worsens at chosen G, use the smallest G restoring AC-8 while AC-5 holds; none -> both numbers to gate 2 | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1: approved (size M, perf, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1 delta: AC-4, AC-7, AC-8 removed (to startup-reveal-gate-on-slow-tv, home-hero-late-insert); AC-5, AC-6 become observations; AC-1, AC-2, AC-9 amended; grace cause withdrawn; G/Q rules and parallelism experiment dropped | owner (standing instruction: recommended) |
| 2026-11-22 | Scrolled Home at reveal not fixed here: deferred to home-hero-late-insert | owner (standing instruction: recommended) |
| 2026-11-22 | speed-profile compile on the box NOT authorised (not on the standing list; not asked under the no-questions instruction): AC-6 compiled-state half is NOT CHECKED; first-key target handed to release-startup-compilation | conductor |
| 2026-11-23 | Judge-2 CONCERNS (compiled-vs-installed comparison is an observation, confounded by box load and no same-day control); Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-22 | Device evidence, baseline and re-measure on TV box 192.168.1.110:5555 (standing, AGENTS.md 2026-11-22) | owner |
| 2026-11-22 | Judge CONCERNS: AC-6 B/C NOT MET as specified, compiled half NOT CHECKED; no must-fix | final-judge |
| 2026-11-22 | Gate 2: ship (squash to <= 4 commits first) | owner (standing instruction: recommended) |
| 2026-11-22 | speed-profile compile on the box authorised; conductor ran it (lease, compile, release); auth-save not possible on the non-debuggable benchmark build | owner |

## Log
- 2026-11-21 created (stage idea) from the AC-13/AC-15 waiver of startup-splash-preload
- 2026-11-21 from startup-settled-signal baseline (its baseline.md): at 7bcbfd83 on emulator-5554, devBenchmarkRelease, 8 of 8 cold starts reveal only at the 2.5 s cap (revealed median 2857 ms) although Home Content ready and LaunchFocusSettled land 1.0-1.6 s in; find why the reveal gate holds
- 2026-11-22 /task:run started, base 2fd851a4, stage research
- 2026-11-22 research.md written (option B recommended, 4 questions)
- 2026-11-22 brief.md: improvement, M, perf, 9 ACs; questions answered on recommendations
- 2026-11-22 worktree /home/user1/orca/workspaces/Puber/startup-first-input-on-tv (orca), base c506d0bd; architect design started
- 2026-11-22 design-architect.md (17 decisions, 2 questions answered on recommendations); test-designer started
- 2026-11-22 test-design.md (35 P0, 14 P1, 5 manual); spec assembled; gate 1 approved (standing); stage plan
- 2026-11-22 plan written (12 tasks, phase A 1-5, baseline task 6, G/Q task 7); readiness round 1 started
- 2026-11-22 readiness-1 NOT READY (2 must-fix), architect round 2, readiness-2 READY; stage impl (phase A tasks 1-5)
- 2026-11-22 phase A done (93e215a2..f48beb86), check.fast green, check.full green per implementer; qa-device baseline (plan Task 6) started on the box
- 2026-11-22 baseline on box (5fb5de17): 36/36 reveal by cap, ready never before the line, revealed median 3335 ms, first key_lat median ~3.2 s at all anchors, JIT busy 5.3-5.7 s; phase B premise (bounded image wait) does not hold on the box -> architect design round 2
- 2026-11-22 design round 2: narrowed scope, gate-1 delta approved (standing); follow-up runs startup-reveal-gate-on-slow-tv, home-hero-late-insert created; release-startup-compilation noted
- 2026-11-22 brief amended (6 live ACs), test-design round 2, spec recopied; architect plan round 3
- 2026-11-22 plan round 3; readiness-3 NOT READY (spec test design dropped by conductor recopy; AC-9 reference) fixed; readiness-4 READY; impl Tasks 7-9
- 2026-11-22 Tasks 7-9 done (1f33a6e1, ada007cf, ed92c932); check.fast green; 45/45 named tests present (own grep; check-named-tests.py parsed 0 names); no Co-Authored-By; qa-device verify + Task 10 re-measure started
- 2026-11-22 qa verify round 1 (6eb30d7c): AC-1/2/3/5/9 PASS, AC-6 as-installed PASS, compiled half NOT CHECKED; installed filter already speed-profile yet first key 2.9-3.6 s; review round 1 started
- 2026-11-22 review round 1 at 6eb30d7c: 1 major, 4 minor/nit fixed, 1 rejected (design risk 6); fix round started
- 2026-11-22 fix round 1: 9eec4d24, 9fe426e0, 874594d5, 9d454407 (check.full green per implementer); qa round 2 + incremental review round 2 (6eb30d7c..9d454407) started
- 2026-11-22 qa round 2 (80ca6fb6): AC-1/AC-9 PASS, AC-6 A PASS, B/C NOT MET as specified, compiled half NOT CHECKED; review round 2 clean; triage-2 -> judge
- 2026-11-22 judge-1 CONCERNS; gate 2 ship (standing); squash of 21 commits started, tree 79afebd3b65f0f3b355b85609f0448aef1e28f3b
- 2026-11-22 squashed to 3 commits (a6e04570, 6d793b81, ce3d859e), tree 79afebd3b65f0f3b355b85609f0448aef1e28f3b unchanged; earlier SHAs in reports are pre-squash; invoking /task:ship
- 2026-11-22 /task:ship has disable-model-invocation; handover left for the owner
- 2026-11-22 owner authorised the compile; conductor ran `cmd package compile -m speed-profile -f com.kino.puber.stage` on 192.168.1.110:5555: filter speed-profile, reason install -> unknown; lease released; qa round 3 (AC-6 check 4) started
- 2026-11-22 qa round 3 (276ddbee): AC-6 compiled half NOT CHECKED, KinoPub API HTTP 502 in 42/48 starts; in the 6 content starts the key at A/B (B now hit at +185..394 ms) wrote no key_lat (<= 50 ms or unhandled, not screenshotted)
- 2026-11-22 resumed: qa round 4 (AC-6 check 4 repeat) started
- 2026-11-22 qa round 4 (e7515c84): API still 502, 1 of 6 starts reached content; AC-6 compiled half NOT CHECKED; blocked on the external API. Branch now has 5 commits: fold the two evidence commits into ce3d859e before ship
- 2026-11-23 owner: API answers; unblocked, qa round 5 (AC-6 check 4) started
- 2026-11-23 qa round 5 (ea26f4f1): AC-6 compiled half PASS (observation): 15/15 content, 14/15 keys <= 50 ms, 1 at 67 ms; cause ready 13/15; first_content median 830 ms, revealed 2151 ms; all anchors met. Caveat: round-2 vs round-5 also differ in API health; judge round 2 started
- 2026-11-23 judge-2 CONCERNS, no must-fix; evidence wording fixed (de38e719) and folded: 3 commits, tree ed0dd5ac52b2c1df184159253860ce5971e4fc91; gate 2 ship (standing)
- 2026-11-23 shipped: merge bc96b491 on master (--no-ff); compileDevDebugKotlin green on the merge; Orca worktree removed
