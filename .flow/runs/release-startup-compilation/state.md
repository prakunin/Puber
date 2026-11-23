---
slug: release-startup-compilation
title: "Refresh the baseline profile through a compliant generation target and prove an update carries it"
stage: done
size: M
tracks: [perf]
kind: improvement
branch: prakunin/release-startup-compilation
worktree: /home/dev/orca/workspaces/Puber/release-startup-compilation-2
base: e4c3b5aa
spec: docs/superpowers/specs/2026-11-23-release-startup-compilation.md
plan: docs/superpowers/plans/2026-11-23-release-startup-compilation.md
rounds: {verify: 4, review: 2, judge: 1}
blocked_by: []
source: startup-splash-preload
created: 2026-11-21
migrated:
updated: 2026-11-23
---

## Next
Owner runs /task:ship release-startup-compilation.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-23 | Old-host decisions are the spec's Decisions section (gate 1 approved AC-1..AC-8; codex required; round caps lifted; device evidence on the box only; box compile-state commands and emulator generation authorised). The old state.md did not survive the move to this host | owner (via spec) |
| 2026-11-23 | A restart by mistake on this host (new research, brief, L split, emulator-only, absorb startup-benchmark-entry) is withdrawn: resume the old run on prakunin/release-startup-compilation. Restart docs kept in restart-2026-11-23/ as reference; install-time-profile-compile deleted as a duplicate of update-install-dex-metadata; r8-keep-narrowing kept | owner |
| 2026-11-23 | Profile generation runs on an emulator, never the box (reaffirmed when the owner asked why the box is not used) | owner |
| 2026-11-23 | Task 5 replanned (plan 8d95e4f1): no token seeding; owner pairs an API 34+ emulator once; generation refuses other devices | architect, conductor |
| 2026-11-23 | AC-2 is read with DEVICE=host.docker.internal:5555 (AVD puber_tv_36 on the Mac host, API 36) instead of emulator-5554; intent unchanged | owner |
| 2026-11-23 | playbackProfile writing a watch position to the owner's account (up to 15 per generation) accepted as the gate-1 CUJ | owner |
| 2026-11-23 | AVD data persistence across ssh mac-emu stop/start unknown: pairing and generation run in one emulator session | owner |
| 2026-11-23 | Exported nonMinifiedRelease token provider: follow-up idea run baseline-auth-provider-presence (standing instruction: recommended); deleting .todo/baseline-profile-auth.env is the owner's call | conductor |
| 2026-11-23 | Plan delta (no AC change): tools/android-apk-install-preserve CERT_DIGEST regex makes the colon after 'Signer #N' optional; apksigner 36.0.0 prints 'Signer #1 certificate SHA-256 digest:' and the tool refused every APK (verified by the conductor). Separate commit on this branch; signer check unchanged | conductor |
| 2026-11-23 | AC-4 fix round 1 (plan delta, no AC change): per design-ac4-diagnosis.md, mark every method rule of baseline-prof.txt hot (H) via a Makefile step after generation, apply once to the checked-in file, guard with baselineProfileMethodRulesAreHot (test-design row AC-4 unit P0), one docs/release.md sentence. Go straight to E3 (rebuild and re-measure M5/M6/M7); E1/E2 run only if E3 misses the gate | conductor (standing instruction: recommended) |
| 2026-11-23 | Generation flake after the long rail journey: cap railNavigationProfile at maxIterations = 5 (like playback) and retry; no AC change | owner |
| 2026-11-23 | Generation flake traced (one supporting run) to the all-H profile embedded in the generation target; durable fix in this run: the baseline-profile make target strips the added H flags from the checked-in file before the Gradle run (backup and restore on failure), regenerates, then marks every method rule H. Plan delta, no AC change | conductor (standing instruction: recommended) |
| 2026-11-23 | AC-4 measurement: add 30 more S1 starts today with the same method and compute p95 over all 45 (the 15 round-3 starts are kept); threshold unchanged | owner |
| 2026-11-23 | Codex skipped for review round 2 (usage limit until 2026-11-25 07:38); the standing rule stays for later rounds | owner |
| 2026-11-23 | Gate 2: ship (judge CONCERNS accepted; AC-2 manual end-to-end generation check left for the next profile refresh) | owner |

## Log
- 2026-11-21 created (stage idea) from the static perf audit handed to startup-splash-preload (not absorbed by its plan)
- 2026-11-23 /task:run started, base e4c3b5aa; researcher spawned
- 2026-11-23 research.md written (no duplicate; recommends split into 3 runs); analyst spawned
- 2026-11-23 brief.md written (size L, split into 3 parts, 9 ACs for part 1); owner answered Q2 (emulator only)
- 2026-11-23 owner answered Q1 (split) and Q3 (absorb benchmark entry); size M; stage design
- 2026-11-23 Orca worktree created at /home/dev/orca/workspaces/Puber/release-startup-compilation; base moved to b371653d (master gained a hook chmod commit); architect spawned (design)
- 2026-11-23 handoff from the old host found in the run folder; owner chose to resume the old run. Restart worktree and branch removed; Orca worktree release-startup-compilation-2 created and switched to prakunin/release-startup-compilation (a0d21716); stage impl, Task 5
- 2026-11-23 architect replanned Task 5 (root cause: shellQuote quotes became part of seeded tokens); plan committed 8d95e4f1; owner answered AC-2 serial, playback CUJ, AVD session questions
- 2026-11-23 readiness-3 NOT READY (2 must-fix), architect fixed (43453de1), readiness-4 READY; readiness pasted into the plan and spec deltas 4-7 recorded (d01974d9); implementer spawned for Task 5a
- 2026-11-23 Task 5a committed f166fbec; check.fast green (conductor); no Co-Authored-By lines; waiting for the emulator for 5b
- 2026-11-23 5b blocked: install-preserve could not parse apksigner 36 output (no device change); conductor verified and approved a one-line regex fix as a plan delta; implementer resumed
- 2026-11-23 Task 5b done: 17f663d1 (install-preserve regex), 0d2fad28 (profiles regenerated on host.docker.internal:5555, 44 journeys paired=true, no logout); BaselineProfileContentTest green, M2 APKs carry baseline.prof/.profm, check.full green (implementer); no Co-Authored-By; emulator lease released; stage verify
- 2026-11-23 verify blocked: owner chose to verify on the box now; adb connect 192.168.1.110:5555 refused from the container (no LAN route); SSH forward through mac-emu refused (administratively prohibited, forced-command key). A stale offline adb entry 127.0.0.1:5565 remains (disconnect needs owner authorization)
- 2026-11-23 box reachable again (192.168.1.110:5555, KM6 API 29, authorised by the owner); qa-device spawned for verify
- 2026-11-23 qa-device verify round 1: AC-4 FAIL (S1 434/845 ms vs base 520/941; gate p95<=199), AC-3/5/6/7/8 PASS, AC-1 cited, AC-2 implementer-only; evidence 45d5c3d5; conductor ran S1 compile steps and bg-dexopt-job; box lease released
- 2026-11-23 architect diagnosis: profile rules mostly S/P not H, so API 29 speed-profile leaves the startup path uncompiled (design-ac4-diagnosis.md); implementer spawned for fix round 1
- 2026-11-23 fix round 1 committed 1733ce48 + dbe5fe89 (36895 method rules all H; baselineProfileMethodRulesAreHot red then green; check.full green; baseline.prof 21309 -> 29117 B). Note: 45d5c3d5 (qa evidence) carries a Co-Authored-By line; remove at the pre-ship squash. qa-device spawned for E3 re-measure
- 2026-11-23 qa-device verify round 2: AC-4 PASS (S1 50/131 ms; 14 of 15 starts below the key_lat report threshold, counted as 50), AC-3/5/6/8 PASS, AC-1/7 carried, AC-2 implementer-only; compile cost 4320 -> ~15.4k ms; evidence a4c19ba4; lease released; stage review
- 2026-11-23 review round 1: blind (1 major, 3 minor), edge (1 major, 1 minor), tests (3 minor, check.full green), codex (1 minor; adversarial cut by usage limit); triage-1.md: 10 fix, 0 defer, 2 codex claims rejected
- 2026-11-23 fix round 1 phase A committed cdc7d97e, 171382fe, dba016e9; phase B regeneration failed 3 times on the emulator: after the long rail journey (375-511 s, rail tab checks pass) the next journey dies at the library's profile reset ('Failed to compile (out=)', benchmark process gone); manual compile succeeds; pairing intact. Escalated to the owner
- 2026-11-23 owner chose to cap the rail journey at 5 iterations; implementer resumed
- 2026-11-23 phase B done on run 5 (pre-H profile embedded): 540c4ee4 regenerated profiles (favorites 109, history 317, contentlist 366 rules; 0 method rules without H); BaselineProfileContentTest green; check.full green; emulator lease released and emulator stopped
- 2026-11-23 qa-device verify round 3 (regenerated profile 540c4ee4, APK 8251ff30): AC-4 FAIL as measured, S1 50/215 ms (one cap-cause start at 215; next highest 90), AC-3/5/6/7/8 PASS, AC-1 carried, AC-2 implementer-only; bg-dexopt 16.5 s; evidence 2d1c7395; lease released. Incremental review round 2 started; AC-4 reading asked of the owner
- 2026-11-23 codex round 2 unavailable (usage limit until 2026-11-25 07:38, both models); asked the owner
- 2026-11-23 review round 2: edge 5 minor, tests 3 minor, codex skipped (owner); triage-2.md: 5 deferred to baseline-generation-hardening, 3 accepted; qa-device spawned for the AC-4 extra sample
- 2026-11-23 qa-device round 4 (AC-4 extra sample): 45 starts median 50, p95 90 ms (rank 43), 2 cap-cause starts over 199 (215, 292); evidence fbe81235; stage judge
- 2026-11-23 final-judge gate round 1: CONCERNS, must-fix none, MANUAL 1 (AC-2 end-to-end generation through the committed recipe); raw logcat and profile dump moved from the run folder to .todo/release-startup-compilation/; idea run player-back-press-count filed; branch squashed to 4 commits (f2213c20, 23a0b26b, bf9ca1e4, a0121189; tree identical to fbe81235; backup ref backup/release-startup-compilation-pre-squash); Co-Authored-By count 0; stage handover
- 2026-11-23 gate 2: ship (owner)
- 2026-11-23 shipped: merged into master as d0720e7c (--no-ff); worktree removed; branch prakunin/release-startup-compilation and backup/release-startup-compilation-pre-squash kept
