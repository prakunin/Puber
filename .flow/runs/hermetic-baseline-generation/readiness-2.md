# Readiness 2: hermetic-baseline-generation

Verdict: **READY**

Spec and plan at worktree HEAD c6e8d77c (base 5cd3e37e; no code change since base, `git diff 5cd3e37e..HEAD -- app baselineprofile tools Makefile gradle` is empty): `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md`, `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/plans/2026-11-26-hermetic-baseline-generation.md`.

## Round 1 fixes

| Fix | Status |
|---|---|
| 1. Manual P0 rows marked | Done: spec Traceability AC-1 (M1), AC-6 (M3), AC-7 (M5), AC-9 (M6) read `P0 guard`; `test-design.md` has no P0 row without `red`/`guard` |
| 2. AC-9 in a Covers line | Done: plan Task 8 Covers ends "AC-9 (qa M6, after Task 8)" |
| Note: private companion | Taken: Task 5 drops `private` from the companion (`BaselineProfileGenerator.kt:202`) so the `@JvmStatic @BeforeClass`/`@AfterClass` are public |

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass: AC-1..AC-9 each in a Covers line (Tasks 1-8) and a Traceability row |
| Citations resolve (re-sampled at HEAD) | Pass: `app/build.gradle.kts:438-444` comment + `devReleaseBaselineProfile` block, `:446-453` `baselineProfile {}`; `ICryptoPreferenceRepository.kt:17-19` three session-end methods; `libs.versions.toml:14` okhttp, `:22` koin, `:88` `okhttp-doh`; `AndroidManifest.xml:22` `android:name=".PuberApp"`; `emulator-resource-lock.sh:17-28` usage; `Makefile:141` `baseline-profile` target; `gradle.properties` 4 lines, no trailing newline; `BaselineProfileContentTest.kt:12-17`, `:19`; round 1 table still holds (code unchanged) |
| No task leaves the build red; dependency order | Pass, unchanged from round 1 |
| P0 rows: defect named, `red`/`guard`, test in backticks | Pass: automated rows name their test in backticks; manual/device rows reference M1-M7 and are `guard` |
| Inside Out of scope; blocking questions answered | Pass: Makefile logic, `railShows`, `StartupBenchmarks`, PR workflows untouched; Q1, Q3 answered in `state.md` |
