---
slug: startup-splash-preload
title: "Use the splash as loading time and defer startup background work"
stage: done
size: M
tracks: [ui, perf]
kind: improvement
branch: prakunin/startup-splash-preload
worktree: /home/user1/orca/workspaces/Puber/startup-splash-preload
base: a22c03bc
spec: /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/specs/2026-11-21-startup-splash-preload.md
plan: /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/plans/2026-11-21-startup-splash-preload.md
rounds: {verify: 2, review: 3, judge: 1}
blocked_by: []
source:
created: 2026-11-21
migrated:
updated: 2026-11-21
---

## Next
Done. Real-TV acceptance of startup performance continues in startup-first-input-on-tv.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Owner looks only at release builds; measure a release-type build too | owner |
| 2026-11-21 | Research Q1: split; this run is P0 only, P1 is run startup-settled-signal | owner (standing instruction: recommended) |
| 2026-11-21 | Research Q2-Q5 as recommended in research.md (reveal readiness rule with 2.5 s cap; drop D-pad/OK keys while covered, Back passes; no dwell on warm start; keep Compose SplashContent, no core-splashscreen) | owner (standing instruction: recommended) |
| 2026-11-21 | Brief Q1: AC-13 target on devBenchmarkRelease: median revealed <= before median first_content + 300 ms, key_lat p95 in first 3 s after reveal lower than before | owner (standing instruction: recommended) |
| 2026-11-21 | Brief Q2: fold app-coroutine-scope idea in as the warm-up owner; close that idea as absorbed | owner (standing instruction: recommended) |
| 2026-11-21 | Brief Q3: install devBenchmarkRelease over emulator devDebug with install-preserve only after confirming same signer and versionCode; otherwise stop and ask | owner (standing instruction: recommended) |
| 2026-11-21 | Design stand waived (ux escalate_also); UX-Q2 D-pad live from fade start; A-Q1 window background kept; A-Q2 TvHomeSync out of scope | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1: approved (spec /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/specs/2026-11-21-startup-splash-preload.md) | owner (standing instruction: recommended) |
| 2026-11-21 | Codex review required on every change and every review round (incl. incremental fix diffs) | owner |
| 2026-11-21 | Plan Q1: add StartupKeyFilterTest.upOfAKeyPressedWhileCoveredIsDroppedAfterReveal (AC-7, unit) | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1 delta (architect delta 1): reveal waits for image requests in flight within the cap; AC-4/6/12 amended, AC-15 added; delta Q2 bitmapFactoryMaxParallelism(2) as next fallback; delta Q3 idea card-image-size-tier | owner (standing instruction: recommended) |
| 2026-11-21 | AC-13 and AC-15: unmet on the emulator and WAIVED (not passed); real-TV performance acceptance stays outstanding; follow-up idea startup-first-input-on-tv. Owner was asked, answered "consult Codex and decide yourselves"; Codex (codex-consult-ac13-ac15.md) and conductor chose option A, no further code change | owner (delegated to conductor with Codex consult) |
| 2026-11-21 | Gate 2: ship (judge-1 CONCERNS: AC-13/AC-15 waived, Round 2b observations deferred to startup-first-input-on-tv, TV checks manual) | owner (standing instruction: recommended) |
| 2026-11-21 | Verify/review round caps lifted | owner |
| 2026-11-21 | Baseline profile / R8 / post-update compilation are a separate run | owner |

## Log
- 2026-11-21 created (stage triage) from the owner's startup-responsiveness analysis; request: Cold-start sequencing and responsiveness (release builds on Android TV). Today the splash is idle time: LauncherScreen waits a fixed 500 ms (MIN_VISIBLE_MS), LauncherVM decrypts the access token through AndroidKeyStore on the main thread, and Home data requests start only after Main/Home are composed, i.e. after the splash is gone; the user then sees Home assembling for 1-3 s while D-pad input lags (key_lat up to ~1 s under load). Everything starts at once with no priority: 8 HomeVM section collectors each republish and recompose the whole Home (publishSections), MainVM.reportDeviceInformation, TvHomeSyncCoordinator.requestRefresh(immediate = true), UpdatePromptVM auto update check, image loads. SplashContent in MainActivity stays composed under App() forever (full-screen overdraw every frame). Goal: (P0) use the splash as loading time - drop the fixed dwell, move token check off main, start Home data (disk cache + network for hero/Hot and ContinueWatching) at process start, compose Main+Home under the splash and reveal it with a fade once the first visible content is ready (hero data, first row, focus claimed) with a hard cap (~2.5 s) so a bad network never hangs the splash; swallow D-pad keys while the splash covers the tree; remove SplashContent from the tree after reveal. (P1) a single "startup settled" signal that defers device info report, TV home sync, update check, details prefetch and the watch-state sync (instead of its 5 s heuristic) until after reveal, staggered; coalesce Home section publishes that arrive close together into one publish. Out of scope: baseline profile / R8 / post-update compilation (separate run). Measure before/after with telemetry startup marks (first_content), key_lat and frames on the emulator; owner only uses release builds, so measure a release-type build (devBenchmarkRelease or equivalent) as well. No physical TV without explicit authorization. Prior numbers: docs/superpowers/evidence/2026-10-15-telemetry-core-device.md, 2026-10-16-telemetry-frames-keys-memory-device.md.
- 2026-11-21 research.md written (option B recommended); triage: brief.md, size M, tracks ui+perf, 14 ACs; P1 split to startup-settled-signal (stage idea)
- 2026-11-21 Orca worktree /home/user1/orca/workspaces/Puber/startup-splash-preload (branch prakunin/startup-splash-preload) at a22c03bc; stage design
- 2026-11-21 design: design-architect.md, design-ux-designer.md, test-design.md, baseline.md (release first_content 2202 ms, key_lat p95 199 ms); peer audit saved as perf-audit-2026-11-21.md; spec committed; gate 1 approved; stage plan
- 2026-11-21 plan written (8 tasks); readiness next
- 2026-11-21 readiness-1: READY (no must-fix); plan committed; stage impl
- 2026-11-21 audit items not absorbed filed as idea runs: home-decode-off-main, content-cache-legacy-cleanup, home-sections-parallel-fetch, http-stack-consolidation, release-startup-compilation, network-timeouts-failover, startup-main-thread-io, focus-recomposition-scope, player-cache-save-churn, collections-loadmore-guard
- 2026-11-21 impl done: 9 commits 010e9649..0c4aa01c, check.full green (implementer), check.fast green (conductor), named tests 0 missing/0 skipped; stage verify
- 2026-11-21 qa round 1: all ACs PASS/NOT CHECKED except AC-13 key_lat FAIL on devBenchmarkRelease (p95 560-1068 vs 199; host load higher than baseline, not interleaved); evidence 8bfc008f
- 2026-11-21 qa rounds 1b/1c: interleaved A/B (base APK from clean worktree startup-splash-preload-base); old anchor key_lat p95 head 190 vs base 156 (meets AC-13); fixed anchor (keys 2.0-4.5 s after launch) head p95 1760 vs base 934: first keys after the earlier reveal queue behind a Coil decode burst (3840x2160 wide posters decoded to 533x300, ~3.2 s CPU) plus shader compile; evidence 11624077. Routed to architect (design delta) as a defect of the goal.
- 2026-11-21 peer note: emulator-5554 now has cache-storage-bounds APK with Room v4; our branch is Room v3, so installing ours would be a schema downgrade with destructive fallback (watch_state + cache dropped). Next device verify must check on-device DB version first and must not install over v4 without owner authorization; alternatives: a second AVD or waiting for cache-storage-bounds to land and rebasing.
- 2026-11-21 design delta 1 accepted (gate 1 delta), spec updated and committed; idea card-image-size-tier recorded; stage back to plan
- 2026-11-21 plan delta (Tasks 9-10) READY (readiness-2); stage impl
- 2026-11-21 delta impl: 51352276, 952c26f2, 4d159b7f; check.full green (implementer); device rows deferred (Room v4 on emulator); review round 1 started at 4d159b7f ahead of device verify of the delta
- 2026-11-21 review round 1 at 4d159b7f: ux no findings; codex 3 minor; blind 1; edge 2; tests 3 -> triage-1.md: 7 fix (all minor), 6 rejected unproven; cache-storage-bounds merged as 8428c946 (Room v4) -> rebase before fixes
- 2026-11-21 fix round 1: rebased onto master 8428c946 (old head 4d159b7f -> 8f8793d3), T1-T7 fixed (5ee1bd54..522fa7be), check.full green (implementer); head e6e4431c
- 2026-11-21 review round 2 (incremental 8f8793d3..e6e4431c): codex clean; edge 1 blocker (walk join spin/ANR) + 1 minor; tests 1 blocker (frame hook Looper in JVM tests, order-dependent suite) + 3 minor -> triage-2.md: fix T8-T10, accept A1-A3. Base APK for A/B built from master 88fa7303 (code identical to 8428c946; only .flow files differ), sha256 d6ce2eaa...
- 2026-11-21 fix round 2: c2acd654 (T8+T10), 313a0434 (T9), e5a579a1; check.full green (implementer); head e5a579a1; review round 3 started
- 2026-11-21 review round 3 (e6e4431c..e5a579a1): edge, tests, codex all clean -> triage-3.md, no fix items
- 2026-11-21 qa round 2 (APKs from e6e4431c, evidence 4b5c1ec5): AC-4 amended, AC-6/T4, T1 PASS on device; AC-12 NOT CHECKED (no newer release); AC-13 FAIL (head revealed median 2789 vs target 2292; 5/12 starts at the 2.5 s cap; head first_content 1015 vs base 1992; old-anchor key_lat p95 112 vs base 193); AC-15 FAIL on share (14/17 head keys >=199 ms after reveal vs 16/36 base) though p95 passes (919 vs 1133); decode burst now before reveal, remaining post-reveal cost not decode. Escalated to the owner (same finding after its fix).
- 2026-11-21 owner delegated the AC-13/AC-15 decision; Codex consult recommends A (waive emulator thresholds, validate on TV); recorded; idea startup-first-input-on-tv
- 2026-11-21 qa round 2b at e5a579a1 (evidence fbf959c5): cold start, content link, T1 PASS; 1/9 cold starts revealed with skeletons before the cap under load (cause unknown, possibly image requests starting after the two-frame wait); content-link reveal 4693 ms once (unanalysed); trace: first frame after reveal 1.5 s, ~1.4 s of it swiftshader shader_compile. Spec waiver committed; head 2b1e9f74; stage judge
- 2026-11-21 judge-1: CONCERNS, no must-fix; Round 2b observations triaged (defer Q1, Q2; accept Q3); gate 2 ship per standing instruction; stage handover
- 2026-11-21 squashed 35 -> 4 commits (50e791be, b3cc6c5e, fbc40e12, 0b5c0669), tree 53a166aa identical, check.fast green, 0 Co-Authored-By; /task:ship next
- 2026-11-21 shipped: merged into master as 8c6fc926 (--no-ff); Orca worktrees startup-splash-preload and startup-splash-preload-base removed; stage done
