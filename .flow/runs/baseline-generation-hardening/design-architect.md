# Design: baseline-generation-hardening

Scope and ACs: [brief.md](brief.md). Code read in the worktree at b27d8e40.

## Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Which adb server does generation use? | New `BASELINE_ADB_PORT ?= 5039` and `BASELINE_ADB` in `Makefile`, next to `ITEST_ADB_PORT` (`Makefile:124-125`); every `$(ITEST_ADB)` and `ANDROID_ADB_SERVER_PORT=$(ITEST_ADB_PORT)` in `baseline-profile` (`Makefile:141-179`) switches to it. The target refuses first when `BASELINE_ADB_PORT` equals `ITEST_ADB_PORT` or 5037 | AC-6; a concurrent box `itest` on 5038 is never disconnected or killed |
| D2 | When does the script kill the generation server? | Flag `generation_started=1` set just before `make` (`tools/generate-baseline-profile.sh:68`); `cleanup` (`:47-54`) kills `ANDROID_ADB_SERVER_PORT=${BASELINE_ADB_PORT:-5039}` only when set; the script passes `BASELINE_ADB_PORT` to make | AC-5: a preflight refusal (`:78`) kills nothing |
| D3 | How is "offline" seen? | Poll every 2 s for 30 s; state is the second column of `adb devices` for `$DEVICE` (`get-state` reports `offline` only as an error on stderr); `adb connect` is re-issued each poll while the state is neither `device` nor `offline`; on timeout exit 2 naming the last state and `ssh mac-emu start` | AC-4; local emulator only, no KinoPub traffic |
| D4 | Leftover `.bak` | Recipe starts: if `$f.bak` exists, print a notice (restored from an interrupted run; a generation finished just before that interruption is discarded) and `mv -f` it over `$f`; then `cp` | AC-1 |
| D5 | Restore on interruption | One `restore` path in an `EXIT` trap (`[ -f $b ] && mv -f $b $f`, then `$(BASELINE_ADB) kill-server`); `HUP`/`INT`/`TERM` traps only `exit 129/130/143`; success path re-marks `H` then `rm -f $b`, so EXIT finds nothing to restore; failure path leaves it to EXIT | AC-2; replaces `Makefile:166-179`. Recipe runs in dash (no `SHELL` set), which runs the EXIT trap after `exit` from a signal trap |
| D6 | Ignore rule | `/app/src/main/generated/baselineProfiles/*.bak` in `.gitignore` | AC-3; narrowest path |
| D7 | Rail check | `railShows(titleRes)` (`BaselineProfileGenerator.kt:190-196`) waits for `By.text(title).focused(true)`; the expected title is the entry just opened, `RAIL_TITLE_RES[index]` (replaces `:135`), since LEFT restores rail focus to the selected tab (`focusRestorer(fallbackFocusItem)`, `MainScreenComponent.kt:274-276,291`) | AC-7: the Series "4K" section header (`TabTypeConfig.kt:97-104`) is not focused, and the last entry needs no special case |
| D8 | Fallback if UiAutomator does not report `focused` on the merged rail item | `Modifier.semantics { testTagsAsResourceId = true }.testTag("main_rail")` on the rail `Column` (`MainScreenComponent.kt:285-292`, pattern `HistoryScreenContent.kt:157`); check `By.res("main_rail").hasDescendant(By.text(title))`; then a box rail check on 192.168.1.110:5555 | Brief Q3; production source only if the emulator run shows D7 cannot see focus |
| D9 | API 34 reason text | "generation needs API 34 or newer, where the benchmark library resets compilation in place instead of reinstalling the app" in `Makefile:157` and `tools/generate-baseline-profile.sh:38`; `docs/release.md:112` same reason | AC-9; reason per `docs/superpowers/plans/2026-11-23-release-startup-compilation.md:138` |

## Changes by layer

| Layer | Change |
|-------|--------|
| API / repository; ViewModel; screen and DI | N/A: tooling and test-APK only |
| Compose content | None unless D8 fires: `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:285-292` |
| Resources | N/A |
| Build tooling | `Makefile:124-125` add `BASELINE_ADB_PORT`/`BASELINE_ADB` (D1); `Makefile:139-179` port switch, port guard, D4/D5 recipe, D9 text, comment `:139-140` names the own port. `tools/generate-baseline-profile.sh:16-26` poll (D3), `:38` (D9), `:45-54` flag and port (D2), `:68` passes `BASELINE_ADB_PORT`. `.gitignore` (D6) |
| Generator (`:baselineprofile`) | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:128-144` expected title = current entry, kept SEARCH double-BACK (`:136-140`) with that title; `:190-196` focused selector (D7); comment `:124-127` updated |
| Docs | `docs/release.md:111-116`: generation port 5039 (`BASELINE_ADB_PORT`), 30 s wait for `device`, D9 reason; `:134-136`: leftover `.bak` restored with a notice at the next start |
| Profiles | Proving run rewrites `app/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`: committed only if `BaselineProfileContentTest` passes and the egress journal is empty, else `git checkout` (brief Q4) |

## Alternatives rejected

- Keep 5038 and only flag the kill (research option A): the make target's `disconnect` (`Makefile:143`) still drops a concurrent `itest` device.
- Refuse on a leftover `.bak`: adds a manual step (brief Q2).
- Backup outside the generated dir (`app/build/`): contradicts AC-1/AC-3 paths and is lost on `clean`.
- Waiting for the next title anywhere on screen with a longer timeout: the false pass is about where the text is, not when.
- `testTag` first: touches production source and needs a box check without evidence that it is needed.

## Risks

| Risk | Where | Mitigation |
|------|-------|------------|
| Baseline profile: the proving run rewrites both checked-in profiles; a wrong or stripped profile ships in the next release | `app/src/main/generated/baselineProfiles/`, `Makefile` recipe (D5) | `BaselineProfileContentTest.kt:47` (all `H`) and `:76` (cold-start guards) in `check.full`; commit rule above |
| Signal delivery: dash defers a TERM/HUP trap until the foreground Gradle client returns, so a TERM sent to the shell pid alone restores only after Gradle ends | `Makefile` recipe (D5), `tools/generate-baseline-profile.sh:68` | AC-2 observation sends the signal to the process group (as terminal Ctrl-C or hangup do); SIGKILL covered by D4 |
| UiAutomator may not expose `focused` on the merged `NavigationDrawerItem` node | `BaselineProfileGenerator.kt:190-196`, `MainScreenComponent.kt:337-356` | Emulator run decides; D8 fallback |
| Restore of a leftover `.bak` after a kill between re-marking `H` and `rm -f` discards a finished generation | `Makefile` recipe (D4/D5) | Notice says so; window is two commands |
| `mergeIntoMain` (baselineprofile 1.5.0) handling of an extra file in the generated dir is unverified | `app/build.gradle.kts:450-463` | Two prior runs completed with the `.bak` there; the proving run repeats it |
| A port override equal to `ITEST_ADB_PORT` or 5037 would bring the conflict back | `Makefile` (D1) | Refusal at the top of the target |
| Egress: generation must reach only the loopback mock | `BaselineProfileTestControl` journal checks (unchanged) | AC-8 reads the journal; no new network code |

## Open questions

1. AC-4 says "polls `get-state`"; D3 reads the state from `adb devices` because `get-state` reports `offline` only as an error. Recommended: accept as the same observable (device state polled for 30 s). Blocks: no.

## Round 2 delta

Triage: [triage-1.md](triage-1.md) #4, [triage-2.md](triage-2.md) #1-#4. Round 1 above stands except D4 and the D5 trap order.

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D4 (replaces round 1) | What happens to a leftover `.bak`? | Conditional: restore it over the profile when the profile is the stripped copy (no `H` marker) or has no H-marked rule (SIGKILL between merge and re-mark); otherwise the profile is newer, so remove the `.bak` and print an "out of date" notice. The restore notice says a generation finished just before the interruption is discarded | AC-1; triage-1 #4 (stale `.bak` over a newer HEAD profile) and triage-2 #2 (the unconditional "out of date" rule deleted the original after a kill between merge and re-mark) |
| D10 | Which files are backed up? | Both `baseline-prof.txt` and `startup-prof.txt`, each copied to `<name>.bak` through `.bak.tmp` then `mv` (checked). Absence of either is a valid state: restore deletes a file the run created when its original was absent. D4's condition and every restore apply per file | triage-2 #1: the merge rewrites or deletes both; a signal or post-merge failure otherwise leaves a new startup profile beside an old baseline |
| D11 | When is the server-kill trap installed? | Before the backup steps, so a failed `cp`/`mv` exit also runs `$(BASELINE_ADB) kill-server`; the file-restore part of the trap acts only on backups that exist | triage-2 #4 |
| D12 | Daemon merge window | Not closed: a Gradle daemon still merging can write after the trap restored. Named in the recipe comment and in `docs/release.md:134-136`; `--no-daemon` only if trivial for that call | triage-2 #3 |

Changes: `Makefile:139-179` recipe (D4, D10, D11, D12 comment); `docs/release.md:111-136` (D4 wording, both files, window). Tests (harness): fake Gradle rewrites or deletes `startup-prof.txt`; failure and signal tests assert both files; a test for a `.bak` beside a profile with no H-marked rule; the failed-copy test asserts `.bak.tmp` is removed.

Risks added:

| Risk | Where | Mitigation |
|------|-------|------------|
| Profile with no H-marked rule misread as stripped or current | D4 condition in the `Makefile` recipe | Test on both sides of the condition |
| Writer after restore (daemon merge) leaves a half-new pair of profiles | D12 | `BaselineProfileContentTest` in `check.full`; documented |

## Round 3 delta

Triage: [triage-3.md](triage-3.md) #1-#4. Rounds 1-2 stand except as below.

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D4 (replaces round 2) | When is a leftover backup restored? | Discriminator: `baseline-prof.txt` has at least one non-H method rule -> restore; none -> drop the backups with the "out of date" notice. A completed run re-marks every rule, so a finished profile has only H rules; a merged but not re-marked one has mixed rules (1183 H and 29970 non-H in the merged output) | triage-3 #1: "has an H rule" misread a kill between merge and re-mark as stale |
| D10 (amends) | Per file or one decision? | One decision, taken from `baseline-prof.txt`, applies to both backups (no half-restored pair). Temp files are `*.new.bak`. AC-1 and D10 wording say "decided once", not "per file" | triage-3 #4 |
| D13 | Success-path signal window | Before deleting the backups, re-arm the EXIT trap without the restore part (kill-server only); then `rm -f` both | triage-3 #2: a signal between the two `rm` calls restored one file over a finished profile |
| D14 | Failed restore | `restore` returns non-zero if any `mv` fails; the leftover branch runs `restore || exit 1` before the new `cp`, so the original backup is never overwritten | triage-3 #3 |

Changes: `Makefile:139-179` recipe (D4, D10, D13, D14); harness fake merge emits mixed H and non-H rules; tests: stale case seeds both backups, absent-startup case checks exit code and notice; spec AC-1 and D10 amended by analyst.

Risks added:

| Risk | Where | Mitigation |
|------|-------|------------|
| Profile with only H rules and a lost re-mark misclassified | D4 discriminator | Only a completed re-mark yields all-H; test both sides |
| Restore failure hides the original | D14 | Loud exit before any overwrite |
