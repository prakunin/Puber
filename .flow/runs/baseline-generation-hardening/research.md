# Research: baseline-generation-hardening

## 1. Request

> the remaining F2-F5 items in /home/dev/Puber/.flow/runs/baseline-generation-hardening/state.md Log (Makefile baseline-profile backup safety: restore a leftover .bak, trap EXIT/HUP, ignore *.bak; preflight waits for a just-connected emulator still offline (poll get-state); stop the private adb server only if generation started it; railShows false pass after Series - look the title up inside the rail). F1 and the "not paired" warning were removed by hermetic-baseline-generation (merged e52da694) - verify what still applies on master after that merge (Makefile, tools/generate-baseline-profile.sh, BaselineProfileGenerator). Source review: /home/dev/Puber/.flow/runs/release-startup-compilation/review-edge-2.md.

## 2. Understanding

Four deferred minor findings (edge review round 2 of release-startup-compilation, F2-F5) against the baseline profile generation route still need fixing. The hermetic merge (6cd733f4, merged e52da694) rewrote the generator journeys and stripped pairing from the script but left the Makefile target untouched and kept the rail check, the preflight and the cleanup as they were. All four findings still apply on master at b27d8e40; only their line numbers moved. Generation is tooling: no production source, data shape or contract changes unless the rail fix adds a test tag to the rail.

## 3. Current state in code

| Finding | Still applies | Where on master | What the code does now |
|---|---|---|---|
| F3 backup / trap | yes, unchanged | `Makefile:166-179` | `cp $$f $$f.bak` unconditionally, then `trap '...; exit 130' INT TERM`; no HUP, no EXIT, no check for a leftover `.bak`; success path re-marks `H` and `rm -f` the `.bak`, failure path `mv -f` it back |
| F3 ignore | yes | `.gitignore` | no `*.bak` rule; `app/src/main/generated/baselineProfiles/` holds only `baseline-prof.txt`, `startup-prof.txt` now |
| F4 preflight offline | yes | `tools/generate-baseline-profile.sh:16-26` | one `adb connect`, one `get-state`; anything but `device` exits 2 with "Start the emulator and try again" |
| F5 kill-server | yes | `tools/generate-baseline-profile.sh:47-54`, trap `:74` | `cleanup` runs `ANDROID_ADB_SERVER_PORT=5038 adb kill-server` on every exit, including a preflight refusal (`:78` runs before `:79`) |
| F2 rail check | yes | `baselineprofile/.../BaselineProfileGenerator.kt:128-144`, `railShows` `:190-196` | after entry `index`, waits up to 5 s for `By.text(<title of index+1>)` anywhere on screen; for index 5 (4K, last) it waits for the 4K title itself |

Supporting facts:

- `Makefile` sets no `SHELL`, so recipes run in `/bin/sh` = dash in the dev box (`readlink -f /bin/sh`). dash supports `trap ... EXIT HUP INT TERM`.
- The make target itself shares port 5038 with `itest` (`Makefile:124-125`): it `disconnect`s every device on that server at start (`:143`) and kills it on each of its four refusals (`:150,153,157,162`) and at the end (`:172`). So F5 in the script is not the only place that disturbs a concurrent `make itest`.
- Series uses `standardSections` (`app/.../contentlist/model/TabTypeConfig.kt:54-57`), which includes a section titled `R.string.main_tabs_f4k` (`:96-103`): the false pass the review describes is real.
- Rail items: `MainSideMenuItem` (`app/.../main/component/MainScreenComponent.kt:327-357`) is a TV `NavigationDrawerItem` with a `Text(stringResource(tab.type.title))`; the rail `Column` (`:286-296`) has no `testTag`. `testTagsAsResourceId` is set only in `HistoryScreenContent.kt` and `PlayerScreen.kt`; the player check `By.res("player")` (`BaselineProfileGenerator.kt:178`) shows the pattern works.
- The drawer scope passes `hasFocus = currentValue == Open` (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:361`). Whether `NavigationDrawerItem` keeps the label in the tree when the drawer is closed is `unverified` (no web; library source not read).
- Safety net already in place: `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` (`app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:47`) fails `check.full` on a committed S/P-only profile.
- Stale text, flagged by hermetic-baseline-generation judge-1 (`.flow/runs/hermetic-baseline-generation/judge-1.md:53`): the API 34 refusal says "the benchmark reset keeps app data" (`Makefile:157`, `tools/generate-baseline-profile.sh:38`, `docs/release.md:112-113`); with synthetic auth that is no longer the reason.
- Versions: AGP 9.5.1, `androidx.baselineprofile` / benchmark 1.5.0, UiAutomator 2.4.1 (`gradle/libs.versions.toml:2,28,30,48`); `baselineProfile { saveInSrc = true; mergeIntoMain = true }` (`app/build.gradle.kts:450-463`).

## 4. Related work

| Item | Relation |
|---|---|
| release-startup-compilation (a0121189) | introduced the Makefile backup/trap, preflight, cleanup and `railShows`; review-edge-2.md F1-F5; triage-2.md rows 1-5 deferred them here |
| hermetic-baseline-generation (6cd733f4, merged e52da694) | took F1 and the "not paired" warning (code deleted); spec `docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md:77` lists F2-F5 as out of scope "after this run"; rewrote `collectJourney` and rail start (24ea13b4 in its log) but not the rail check |
| baseline-auth-provider-presence | moot (provider deleted by hermetic-baseline-generation) |
| baseline-mock-protocol-parity (idea) | same generator and profile; a regeneration there would also exercise the rail check. Order between the two runs is free, but each regeneration rewrites the same profile files |
| startup-benchmark-entry | touches `StartupBenchmarks.kt`, not these files |

Duplicate: none.

## 5. External findings

External findings: off (local research only).

## 6. Options

| | A: the four findings as reviewed | B (recommended): A plus a separate private adb port and the stale text | C: shell only (F3-F5), F2 deferred |
|---|---|---|---|
| F3 | leftover `.bak` restored with a message before copying; `trap ... EXIT HUP INT TERM` (TERM 143); `*.bak` ignored in that directory | same | same |
| F4 | poll `get-state` for about 30 s, print the last state seen, message names `ssh mac-emu start` | same | same |
| F5 | flag set just before `make`; `cleanup` kills 5038 only when set | generation gets its own port (for example `BASELINE_ADB_PORT ?= 5039`) in the Makefile and script, so neither the make target's `disconnect`/refusals nor the script's cleanup can reach `itest`'s 5038; the flag is still kept | flag only |
| F2 | rail check scoped to the rail: `By.text(current).focused(true)` after LEFT, or a `testTag` on the rail `Column` searched with `hasDescendant` | same | deferred |
| Stale API-34 reason | untouched | reworded in Makefile, script and `docs/release.md` | untouched |
| Files | Makefile, `.gitignore`, script, generator (+ `MainScreenComponent.kt` if a tag is needed) | A + `docs/release.md` | Makefile, `.gitignore`, script |
| Device | emulator generation run (loopback mock only) to prove the rail check and the preflight wait | same | emulator for F4 only |
| Size | S | S | S |
| Trade-off | leaves generation and `itest` sharing one port: with the owner's split (generation on the emulator, every other check on the box) a box `itest` and an emulator generation can overlap, and generation's `disconnect` on 5038 drops the box mid-run | one more port to document; removes the shared-server conflict at its source | F2 false pass stays; the next regeneration may profile fewer tabs silently |

Recommendation: B. The rail fix should first try the focused-item check, which stays inside `:baselineprofile`; a `testTag` on the rail (a production-source change that would need a box check of the rail) only if the focused check cannot see the item.

## 7. Risks

- **Restore of a leftover `.bak`**: if a run is killed after the success path re-marks `H` but before `rm -f`, the next start restores the older checked-in profile over the new one. Safe (old profile is valid) but discards a finished generation; the message must say so.
- **Backup inside the generated source dir**: whether the baselineprofile plugin (1.5.0, `mergeIntoMain`) deletes or merges extra files in `src/main/generated/baselineProfiles/` is `unverified` (review "Unproven" item). Release-startup-compilation and hermetic runs completed with the `.bak` there, so no observed failure.
- **SIGKILL**: no trap runs; only the start-of-run restore and the content test cover it.
- **Focused-item check**: whether UiAutomator reports `focused` on the node that carries the rail item text (merged Compose semantics) is `unverified`; it needs the emulator run.
- **Regeneration**: proving F2 means a full generation, which rewrites both profile files; the run must commit them or restore them, and must hit only the loopback mock (egress journal empty).

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Give generation its own private adb port instead of sharing 5038 with `itest`? | Yes (option B), 5039 overridable from the make command line |
| Q2 | Leftover `.bak` at start: restore it or refuse? | Restore with a printed notice; refusing only adds a manual step |
| Q3 | Rail check: focused-item check first, `testTag` on the rail only as a fallback? | Yes |
| Q4 | Commit the profiles the proving generation run writes? | Yes if `BaselineProfileContentTest` passes and the run is clean; otherwise restore them with `git checkout` |

## 9. Sources

- Files: `Makefile:18-26,63-66,112-179`; `tools/generate-baseline-profile.sh` (whole); `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt` (whole); `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:130-357`; `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:361`; `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/TabTypeConfig.kt:30-130`; `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`; `app/build.gradle.kts:450-463`; `gradle/libs.versions.toml`; `.gitignore`; `docs/release.md:99-140`.
- Commits: 6cd733f4 (`git show` of script and generator), a0121189, e52da694, b27d8e40; `git log -S railShows`, `git log -S "trap 'mv -f" -- Makefile`.
- Runs: release-startup-compilation (review-edge-2.md, triage-2.md), hermetic-baseline-generation (state.md, judge-1.md, spec), hermetic-baseline-profiles, baseline-mock-protocol-parity, baseline-auth-provider-presence, hermetic-baseline-seams.
- URLs: none.
