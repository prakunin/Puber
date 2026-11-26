---
slug: hermetic-baseline-generation
title: "Generate baseline profiles against a loopback mock backend with synthetic auth instead of a real account"
stage: done
size: M
tracks: []
kind: improvement
branch: prakunin/hermetic-baseline-generation
worktree:
base: 5cd3e37e
spec: docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md
plan: docs/superpowers/plans/2026-11-26-hermetic-baseline-generation.md
rounds: {verify: 2, review: 2, judge: 0}
blocked_by: []
source: hermetic-baseline-profiles
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run hermetic-baseline-generation

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q5 | (Q-D2) Accept small main-source changes so prod cold-start paths stay in the profile (D21 pinned-endpoint walk, D22 pinnable DoH resolver, D24 GitHub API base URL parameter; defaults = today, pinned by JVM tests)? | yes | answered | Yes |
| Q6 | AC-9: amend to what the box can show, or install a release-type dev build on the box? | yes | answered | Amend: dev build with the new flavor launches on the box; embedded-profile launch is checked in the release procedure |
| Q7 | (Q-D3, Q-D4, tests F3) Reword AC-1 (all-H only baseline-prof.txt), AC-3 (host outside the mock's three origins), AC-4 (+ prod cold-start path rules, D27)? | no | answered | Yes |
| Q4 | (architect Q-D1) StartupBenchmarks now targets the instrumentation package with no mock; fix here? | no | answered | No; startup-benchmark-entry owns it; this run only notes it in docs/release.md |
| Q1 | Posters and TLS against the mock: option A, B or C? | yes | answered | B: HTTPS loopback mock with a committed loopback-only test CA (20+ years, SAN IP:127.0.0.1) trusted by an instrumentation-flavor network_security_config.xml |
| Q2 | Synthetic stream format and how it is produced (no ffmpeg in the box)? | no | answered | HLS, H.264 + AAC, ~320x180, 20-30 s, under 300 KB, built once with ffmpeg on the Mac host and committed with a recipe script |
| Q3 | Add the instrumentation variant to the static gate? | yes | answered | Yes: its unit test, detekt and lint tasks go into check.full; PR checks unchanged |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Device policy update: TV box 192.168.1.110:5555 for launch checks; emulator host.docker.internal:5555 (API 36) for baseline profile generation and its evidence only | owner |
| 2026-11-26 | Q1-Q3 as recommended; codex and antigravity every round; caps lifted; API volume at user pace only | owner (standing instruction: recommended) |
| 2026-11-26 | Q4 as recommended; D18: conductor edits .flow/config.yaml check.full at ship time (after merge), not before | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved (M, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-26 | Q5-Q7 as recommended (owner declined the question form and said "продолжай"); gate 1 delta approved | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-26 | No device for now: neither the emulator nor the TV box (Q1 of hermetic-baseline-profiles); device-dependent ACs stay NOT CHECKED until the owner allows a device | owner |

## Log
- 2026-11-26 created (stage idea), part of the L split of hermetic-baseline-profiles (see /home/dev/Puber/.flow/runs/hermetic-baseline-profiles/brief.md and research.md).
- Split item 2, after hermetic-baseline-seams. Instrumentation flavor, synthetic auth, egress blocker that fails the run on any non-loopback request, mock backend for all four journeys incl. playback, cache reset; delete the auth provider, AuthEventLog and the pairing steps; rewrite the profile section of docs/release.md; regenerate the committed profiles. Brief AC-4..AC-9. Re-port of upstream-sync-triage 0b6253a9, c17847cc.
- Absorbs baseline-auth-provider-presence (moot once the provider is deleted) and baseline-generation-hardening F1 (auth log) plus the false "not paired" warning.
- Q2 (as recommended): playback against the mock uses a tiny synthetic stream in :baselineprofile assets so ExoPlayer paths stay in the profile.
- Blocked on a device: generation needs API 34+ (box is API 29); owner said no emulator and no box for now, so profile regeneration (AC-4..AC-6) cannot run until the owner allows a device.
- 2026-11-26 owner: emulator host.docker.internal:5555 allowed for profile generation and its evidence; other device checks on the box. Supersedes the no-device decision for this run.
- 2026-11-26 /task:run started (base 5cd3e37e, includes hermetic-baseline-seams 8a7adbe0); research
- 2026-11-26 research.md written (recommended B: HTTPS loopback mock with an instrumentation-only test CA; 3 questions; ffmpeg not in the dev box). Analyst.
- 2026-11-26 brief.md: M, 9 ACs, no split; Q1-Q3 as recommended. Stage design.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/hermetic-baseline-generation (prakunin/hermetic-baseline-generation); architect design
- 2026-11-26 design-architect.md (19 decisions, 12 risks); Q4 as recommended. test-designer.
- 2026-11-26 test-design.md (P0 13, P1 7, P2 1, manual 7; failing-first BaselineProfileContentTest.profilesHaveNoInstrumentationOrGeneratorClasses); spec assembled and committed; gate 1 approved (standing instruction). Stage plan.
- 2026-11-26 plan written (8 tasks; implementer regenerates profiles on the emulator in Task 8). Readiness.
- 2026-11-26 readiness-1 NOT READY (mark 4 manual P0 rows guard; AC-9 in a task Covers line). test-designer + architect round 2.
- 2026-11-26 readiness-2 READY, pasted into the plan. Stage impl.
- 2026-11-26 implementer blocked on Task 6 (no ffmpeg; ssh mac-emu is start|stop|status only). Commits 05c735cf..b1db0cec (Tasks 1-5, 7). Conductor got a static ffmpeg 4.2.2 (libx264, aac) from the PyPI wheel imageio-ffmpeg 0.5.1 into the scratchpad and ran tools/make-baseline-mock-media.sh: 24 s HLS (6 x 4 s), poster.jpg, wide.jpg, 313,697 bytes total (target under 300 KB). Implementer resumes Task 6 and Task 8.
- 2026-11-26 impl done: Tasks 1-8 (05c735cf..15dea3ac incl. bfeb2eb6 media, 8328331d cert backdate, e3cc80d7 device/info route + genres not required, 24ea13b4 rail journey start, 15dea3ac regenerated profiles). Generation on the emulator: 37 iterations, missing=[] unknown=[], empty egress journal; BaselineMockSelfTest 11 run, 0 failures. Spec drift to review: D14 genres, D7 validity 31 years. Conductor check: check.fast BUILD SUCCESSFUL in 839ms; co-authored 0. Stage verify.
- 2026-11-26 qa round 1: extended check.full green (2714 + 9 tests); AC-1..AC-9 PASS (AC-1 note: startup-prof.txt keeps S/P flags as at base; AC-9 caveat: no master screenshot); evidence 7d639a9b. Review round 1 at 7d639a9b.
- 2026-11-26 review round 1 at 7d639a9b: blind 1 major + 2 minor, edge 1 minor + 1 nit, tests 4 minor, codex 1 minor (5 rejected), antigravity clean. triage-1.md: fix #1-#6, reject #7-#10. Architect design delta for #1 and #6.
- 2026-11-26 design-architect.md round 2 delta (D20-D27, D7/D14/R15 rows). Q5-Q7 taken as recommended (owner: "продолжай"). Analyst amends ACs, test-designer updates the test design, then spec re-assembly, plan delta, readiness.
- 2026-11-26 brief AC delta: AC-1, AC-3, AC-4, AC-9 amended; AC-10 (unpinned defaults) and AC-11 (pinned walk) added; 11 ACs. test-designer round 3.
- 2026-11-26 test-design round 3 (P0 25, P1 17, P2 2, manual 7); spec re-assembled and committed. Architect plan delta.
- 2026-11-26 plan round 3: Tasks 9-17 (Task 16 = emulator regeneration). Architect Q: test-design wording (OkHttp interceptor instead of Ktor MockEngine; history.json in HistoryEntryResponse shape) - yes as recommended (standing instruction); test-designer fixes the wording.
- 2026-11-26 readiness-3 READY (notes: D25 shape in spec design, Task 9 rename, Task 16 runtime-dependent guards -> block, do not loosen). Implementer fix round (Tasks 9-17).
- 2026-11-26 fix round (Tasks 9-17): 7f3917e8..3e79f660; regeneration on the emulator: 35 iterations clean, 4 late checks clean, D27 guard 11/11 at base counts; extended check.full green; check.fast BUILD SUCCESSFUL in 765ms. Implementer synced spec D25 to the wire shape (Task 17). qa round 2 + review round 2 on 7d639a9b..3e79f660.
- 2026-11-26 qa round 2: AC-1..AC-11 PASS (box launch OK, release APKs clean), evidence 811fde6b. Review round 2: codex, antigravity clean; edge 1 minor, tests 1 minor, codex 1 minor, antigravity 1 minor. triage-2.md: accept #1-#4, defer #5 (http1/serialization profile drop) -> baseline-mock-protocol-parity. Stage judge.
- 2026-11-26 judge-1 PASS (no manual items). Notes: D18 check.full edit at ship; http1 profile loss deferred; stale Makefile API-34 reason text. Gate 2: ship (standing instruction). Squash.
- 2026-11-26 squashed to 4 commits (f0c210c2, 6cd733f4, b17f7ba8, ee850bfa); tree 152a12fc identical; co-authored 0. Master moved to ea833653 (catalogue-filters-country-year-status shipped in parallel): git merge-tree with the branch is clean (only shared file KinoPubApiClient.kt); the new /countries request fires only when the filter panel opens, which no generator journey does, and the mock ignores the new query params. Next: /task:ship (edit check.full per D18 at ship).
- 2026-11-26 shipped: merged prakunin/hermetic-baseline-generation into master as e52da694 (--no-ff); :app:compileDevDebugKotlin green on the merged master; Orca worktree removed. D18: .flow/config.yaml check.full extended with the instrumentation unit test, detekt and lint tasks (left uncommitted with the owner's other config edits).
