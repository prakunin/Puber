# Readiness 1: baseline-generation-hardening

Verdict: **READY**

Spec and plan read in /home/dev/orca/workspaces/Puber/baseline-generation-hardening at e03c3edd (plan uncommitted). Base b27d8e40.

## AC coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | Task 1 | `test_leftover_bak_is_restored_before_backup` |
| AC-2 | Task 1 | `test_failed_generation_restores_profile`, `test_hup_to_group_restores_profile`, `test_term_to_group_restores_profile`, `test_int_to_group_restores_profile`, `test_successful_generation_marks_hot_and_removes_bak`; M3 |
| AC-3 | Task 1 | `test_bak_in_generated_dir_is_git_ignored` |
| AC-4 | Task 2, Task 5 | `test_preflight_waits_for_offline_device`, `test_preflight_times_out_naming_last_state`, `test_preflight_reconnects_missing_device`; M1 |
| AC-5 | Task 2, Task 5 | `test_preflight_refusal_kills_no_server`, `test_generation_kills_only_baseline_server`; M1 steps 3, 7 |
| AC-6 | Task 1 | `test_make_target_uses_only_baseline_port`, `test_baseline_port_equal_to_itest_or_default_refused` |
| AC-7 | Task 3, Task 5 | M1 (guard), M2 (red) |
| AC-8 | Task 5 | M1; `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` |
| AC-9 | Task 2, Task 4 | `test_api34_refusal_reason_text`; M5 |

## Citations sampled (worktree at b27d8e40 content)

| Citation | Resolves to | OK |
|----------|-------------|----|
| `Makefile:22` | `ADB ?= $(shell command -v adb ...)` | yes |
| `Makefile:124-125` | `ITEST_ADB_PORT ?= 5038`, `ITEST_ADB` | yes |
| `Makefile:157` | API 34 refusal with "benchmark reset keeps app data" | yes |
| `Makefile:166-179` | `cp $$f $$f.bak`, `trap ... INT TERM`, restore/re-mark | yes |
| `tools/generate-baseline-profile.sh:16-26` | one `adb connect`, one `get-state`, exit 2 | yes |
| `tools/generate-baseline-profile.sh:38`, `:47-54`, `:68` | API 34 text; `cleanup` killing 5038 unconditionally; `make ... baseline-profile` | yes |
| `BaselineProfileGenerator.kt:128-144`, `:135`, `:190-196`, `:225-232`, `:238-239` | rail loop, `expectedTitle = getOrNull(index + 1)`, `railShows` by text anywhere, `RAIL_TITLE_RES`, `SEARCH_INDEX`, `RAIL_WAIT_MS` | yes |
| `MainScreenComponent.kt:274-276,291`, `:285-292` | `fallbackFocusItem`, `.focusRestorer(fallbackFocusItem)`, rail `Column` | yes |
| `TabTypeConfig.kt:97-104` | `SectionConfig` `4k_$type` with `titleRes = R.string.main_tabs_f4k` | yes |
| `HistoryScreenContent.kt:157` | `.semantics { testTagsAsResourceId = true }` | yes |
| `BaselineProfileContentTest.kt:47`, `:76` | `baselineProfileMethodRulesAreHot`, `profilesCoverProdColdStartPaths` | yes |
| `app/build.gradle.kts:450-463` | `baselineProfile { ... mergeIntoMain = true }` | yes |
| `tools/tests/test-verify-apk-signer.sh:146-158` ("`test_*` loop") | test bodies; the loop is `:161-172` | off by lines, harmless |

Environment facts the plan relies on: GNU Make 4.3, `/bin/sh` is dash, no `SHELL` in `Makefile`; `:baselineprofile` has the `instrumentation` flavor, so `compileInstrumentationNonMinifiedReleaseKotlin` is the name the previous run used.

## Order and build

Task 1 (port, backup, harness) -> Task 2 (script, consumes `BASELINE_ADB_PORT` and the fakes) -> Task 3 (generator, independent) -> Task 4 (docs) -> Task 5 (device run, consumes 1-4). Each task ends on the fast gate; Task 3 adds the generator compile, which `check.fast` does not cover. No task leaves the build red.

## P0 rows

Every P0 shell row names its defect, its `red`/`guard` state and its backticked test. The device P0 rows M1 (AC-7 guard, AC-8) and M2 (AC-7 red, scratch mutation) run the automated `BaselineProfileGenerator.railNavigationProfile`; accepted as device-by-nature.

## Scope and questions

Plan stays inside Out of scope (`itest` recipe `Makefile:129-137` untouched; production source only through D8). Q1 and Q2 answered, neither blocks.

## Notes for the implementer and qa

1. Q2: `test_term_to_group_restores_profile` will pass on base (make re-raises TERM, base already restores on TERM); record it as `guard` in the Red run. Its exit-code assertion cannot see the recipe's own exit code.
2. `MainScreenComponent.kt:313-319`: a collapsed rail can keep an item focused during a handoff. M2 must show the new selector failing when LEFT is skipped after Series; if it passes there, D7 is not enough and the run returns to design, D8 does not apply.
3. If D8 fires, M2 is repeated with the D8 selector, since `hasDescendant(By.text(title))` does not test focus.
