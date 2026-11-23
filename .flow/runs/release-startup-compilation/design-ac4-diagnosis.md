# AC-4 diagnosis: why new S1 misses the first-key gate

Worktree head 45d5c3d5. Readings: `qa-report.md` round 1, evidence `docs/superpowers/evidence/2026-11-23-release-startup-compilation-device.md`, `baseline.md`, raw starts in the conductor scratchpad `m/s1/*.jsonl`. Build intermediates read from `app/build/` of the worktree; the APK there has sha256 `f4d34870…331e`, which is the one measured.

## Findings

| # | Finding | Evidence |
|---|---|---|
| F1 | The latency is not in the frame or in the input queue. It is on the main thread between dispatch and the next frame, so it is the key handling itself or main-thread work queued after it | `lat_ms = frame start + ui time - eventTime` (`core/telemetry/KeyProbe.kt:125`); `wait_ms` is dispatch minus eventTime (`:116`); `dispatchNs` is taken before `super.dispatchKeyEvent` (`:149-150`). New S1: `wait_ms` 3-11, `frame_ms` 2-26, `lat_ms` 143-845. S0, for contrast: `frame_ms` 500-1200 |
| F2 | The whole S1 startup runs slower than S2, not only the key. The key lands while that work is still running | Medians, base S1 vs S2: ready_req 1963 vs 1349, ready 2355 vs 1393, revealed 2669 (cap) vs 1866 (ready). New S1 is the same: ready_req 1965, revealed 2664, cap in 13 of 15 (`RevealGate.kt:9`, cap 2500 ms) |
| F3 | Most generated rules are not marked hot. ART `speed-profile` compiles only methods flagged `H`. `S` and `P` rules only add classes and dex layout, so they leave the code interpreted or JIT-compiled | `app/src/main/generated/baselineProfiles/baseline-prof.txt` method rules by flag: SP 26730, P 8826, HSP 1168, HP 171. Under `com/kino/puber`, 41 of 6572 rules are hot. The old 1.0.0 profile had the same shape (SP 17128, P 288, HSP 77), which is why regeneration only moved S1 from 520/941 to 434/845. ART source (AOSP `CompilerDriver::ShouldCompileBasedOnProfile`, android10): "Compile only hot methods, it is the profile saver's job to decide what startup methods to mark as hot". This source is outside the repo; E2 checks it on the box |
| F4 | After R8 and the merge with library profiles, the hot rules are mostly Compose's own profiles. The app's startup path and its network and image stack stay mostly not hot | `app/build/intermediates/combined_art_profile/devBenchmarkRelease/…/baseline-prof.txt`, mapped through `outputs/mapping/devBenchmarkRelease/mapping.txt`. Hot / not hot: compose.ui 3528/337, compose.foundation 1918/207, compose.runtime 1019/113; `ui.feature.home` 9/136, `core.startup` 0/44, `core.telemetry` 0/46, `MainActivity` 0/7, coil3 68/1005, io.ktor 54/1733, okhttp3 29/1262, org.koin 3/232, voyager 12/264, kotlinx.serialization 41/188 |
| F5 | S2 is an AOT state too: `speed-profile -f`, cur profile empty. Its JIT samples were only input to the profile, and the API 29 profile saver marks the box's hot methods `H`. So the gate can be reached from a profile; what S1 lacks is hot flags on the startup path | `baseline.md` S2: `[reason=unknown]`, cur "ProfileInfo: empty", reference dump 57339 lines vs S1's 43358. ProfileInstaller also wrote the embedded profile at the S0 launches |
| F6 | Excluded: R8 name mismatch, a profile that was not installed, and dev vs prod names | AGP rewrites the profile through R8 (`r8_art_profile/devBenchmarkRelease/minify…/baseline-prof.txt`, obfuscated names; 62556 rules after wildcard expansion become 24504 after R8). `prof` = 1 on 15 of 15 new S1 starts, and `[status=speed-profile] [reason=unknown]`. Rules name classes, not the application id; `mergeIntoMain` (`app/build.gradle.kts:449`) feeds every variant |
| F7 | Excluded as the main cause: the startup journey sends no key. The DPAD_DOWN path is profiled by other journeys, only not as hot | `startupProfile` (`BaselineProfileGenerator.kt:36-48`) has no key. `browseAndDetailsProfile` presses DOWN on Home (`:58-61`), and Compose's beyond-bounds and focus classes are present and hot through library rules (`LazyLayoutBeyondBounds` 22/0, `compose.ui.focus` 95/27) |
| F8 | Second-order, not yet measured: code paths that run only on API 29 never ran on the API 36 generator, so they have no rule at all | E2 step 4 measures how large this is |

## Decisions

| Question | Decision | Why |
|---|---|---|
| Most likely cause | F3/F4: the embedded profile marks the app's startup and first-key code `S`/`P`, not `H`, so `speed-profile` does not compile it | Matches F1, F2 and F5. Regeneration changed the content, not the flags, and the gain was small |
| Fix | Add `H` to every method rule of the generated `baseline-prof.txt` that lacks it. Do it as a deterministic step after `:app:generateBaselineProfile`, apply it once to the checked-in file (no emulator run, no re-pairing), and guard it with a content test | Uses the coverage the journeys already record. Survives the next regeneration. Smallest change; no AC changes |
| `startup-prof.txt` | Unchanged | R8 uses it only for dex layout; its flags do not reach ART |
| Settle before the fix lands | E1 and E2 below, run by the conductor with the lease. They need no new build | E1 shows whether compiled code can meet the gate at all on this box; E2 confirms F3 on the device |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository, ViewModel, screen and DI, Compose content, resources | N/A: no runtime source changes |
| Build tooling | `Makefile:164-166`: after `:app:generateBaselineProfile` succeeds, run `sed -E -i '/->/ s/^([SP]*)L/H\1L/' app/src/main/generated/baselineProfiles/baseline-prof.txt`, or a small `tools/` script that does the same. Lines that already start with `H` and class lines are left as they are |
| Profiles | `app/src/main/generated/baselineProfiles/baseline-prof.txt`: the same rewrite, applied once and committed. About 36.9k method rules, all hot afterwards |
| Tests | `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`: new `baselineProfileMethodRulesAreHot`: every line containing `->` starts with `H`. Fails on today's file and on a regeneration that skips the step. Needs a test-design row (AC-4, unit, P0 guard); this is a plan delta, not an AC change |
| Docs | `docs/release.md`, Refresh Baseline Profiles: one sentence saying the refresh marks every method rule hot and why |

## Device experiments (conductor; box 192.168.1.110:5555, lease held, package com.kino.puber.stage only)

`S=192.168.1.110:5555; P=com.kino.puber.stage`. Count the sections of a profile dump with:
`awk '/post startup methods:/{s="post";next} /startup methods:/{s="startup";next} /hot methods:/{s="hot";next} /classes:/{s="";next} s&&NF{c[s]++} END{for(k in c)print k,c[k]}'`.
If the dump prints method indices on one line instead of one method per line, count the comma-separated entries.

| # | Steps | Expected if F3 holds | If not |
|---|---|---|---|
| E2 | 1. S1 recipe on the measured APK `f4d34870…`: install-preserve, broadcast `INSTALL_PROFILE`, force-stop, `cmd package compile -m speed-profile -f $P`. 2. `adb -s $S shell cmd package dump-profiles $P`, then `adb -s $S shell cat /data/misc/profman/$P-primary.prof.txt > {run}/e2-s1-ref.txt`; count the sections. 3. Five uncounted starts with `run.sh` (DPAD_DOWN at +2.44 s, 14 s wait), then force-stop. 4. Dump again into `{run}/e2-s1-cur.txt`; map the hot entries of the cur section to original classes through `app/build/outputs/mapping/devBenchmarkRelease/mapping.txt` | Step 2: hot about 9.6k (the hot rules of the combined profile), startup and post-startup together about 24k. Step 4: the cur hot set is mostly `com.kino.puber`, coil3, io.ktor, okhttp3, org.koin, voyager methods that the profile carries as `S`/`P` | Step 2 hot about 24k: ART compiles `S`/`P` too, so F3 is wrong. Step 4 mostly methods with no rule at all: F8 dominates, so add F9 |
| E1 | Continuing from E2: `cmd package compile -m speed -f $P`; `dumpsys package dexopt` shows `[status=speed]`; one uncounted start, then 15 counted with the M5 method | p95 <= 199 ms: compiled code is enough, and the fix should land between S1 and this | p95 > 199 ms with everything compiled: no profile can meet the gate on this box (see Q1) |
| E3 | After the fix: rebuild `devBenchmarkRelease` in `wt`, then repeat M5 (S1 and S0, 15 starts each) and M6. Also record `dumpsys package dexopt` "Compiler stats" and the hot count from step 2 of E2 | hot about 24k; new S1 p95 <= 199 ms; compile time recorded against today's 4320 ms | Between E1 and the gate: add F9 |

These are compile-state commands under the existing box authorisation. The conductor runs them itself, because qa refuses relayed consent.

## Alternatives rejected

| Alternative | Why rejected |
|---|---|
| F9: hand-written wildcard rules (`HSPLcom/kino/puber/**->**(**)**`, plus coil3, io/ktor, okhttp3, org/koin, voyager) | Kept as a fallback if E3 lands between E1 and the gate. Broader compile cost, and AGP's flag merge with the generated duplicates is unverified |
| Generate on API 29 | The owner keeps generation off the box; below API 34 the library uninstalls the app before every journey |
| More journey iterations so that ART marks methods hot | Depends on ART's sample thresholds on the emulator; not deterministic |
| Lowering the gate to the new S1 | S2 (F5) shows the gate can be reached from AOT code |

## Risks

| # | Risk | Where | Mitigation |
|---|---|---|---|
| 1 | Correctness of the diagnosis: the latency comes from main-thread work that compiled code cannot shorten (class init, network or image timing at the cap reveal) | F2; `RevealGate.kt:109` | E1 decides before any build |
| 2 | Performance and rollout: about 2.5x more compiled methods (about 9.6k to 24.5k after R8). This makes install and background dexopt on the box longer and the oat file larger, and could stretch the AC-8 `bg-dexopt-job` | box dexopt, `docs/release.md` | E3 records Compiler stats; re-run M7 on the new APK |
| 3 | Rollout: a later regeneration that skips the rewrite quietly brings the regression back | `Makefile:164-166` | `baselineProfileMethodRulesAreHot` fails `check.full` |
| 4 | Gaps that appear only on API 29 (F8) remain after the fix | profile content | E2 step 4 sizes them; F9 is the fallback |

## Open questions

1. Q1: E1 with full `speed` compilation misses 199 ms p95. Then no profile content can meet AC-4 on this box. Recommended: the owner changes AC-4's gate to the E1 p95 plus a margin, or replaces it with "new S1 p95 <= base S1 p95 / 2". Blocks: no (only if E1 fails).
