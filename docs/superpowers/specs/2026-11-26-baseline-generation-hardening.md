# Harden the baseline profile generation route

Status: approved
Date: 2026-11-26
Slug: baseline-generation-hardening
Kind: improvement
Size: M
Tracks: none

<!--
Each section has exactly one owning role; nobody else edits it. The conductor
copies each section verbatim from its owner's output and commits this file in
the worktree after each stage. Cite code as `path:line`; a claim with no
source is a guess.
-->

## Problem / Why

### Request

Fix the deferred findings F2-F5 of release-startup-compilation review-edge-2.md on the baseline profile generation route. F1 and the "not paired" warning are gone (hermetic-baseline-generation).

### Risks (analyst)

- Backup lives in the plugin's generated dir; whether baselineprofile 1.5.0 `mergeIntoMain` touches extra files there is unverified (no failure seen in two prior runs).
- Restoring a leftover .bak after a run killed between re-marking `H` and `rm -f` discards a finished generation; notice must say so.
- UiAutomator `focused` on the merged rail item node is unverified; needs the emulator run. Fallback adds production `testTag` plus `testTagsAsResourceId`, then a box check of the rail (192.168.1.110:5555, stage package).
- The proving run rewrites both profile files; commit or restore deliberately. Order with baseline-mock-protocol-parity is free but both rewrite the same profile.
- Device rules: generation and its evidence only on the emulator (`ssh mac-emu start`, stop after if no other lease); no live KinoPub API calls in scripts; generation hits only the loopback mock, no loops or retries beyond the preflight poll.

## Acceptance criteria

- AC-1. Given a leftover `baseline-prof.txt.bak` or `startup-prof.txt.bak`, when `make baseline-profile` starts, then one decision is made from `baseline-prof.txt` for both files: only if it exists, is non-empty and has no method rule without H, both .bak files are removed with an "out of date" notice and the profiles are kept; if it is missing, empty, or has a method rule without H (stripped copy or unfinished merge), each existing .bak is moved back over its profile and a notice says so; only then new backups are made (absence of a file is a valid state).
- AC-2. Given generation is interrupted by INT, TERM, HUP or a normal failure, when the target exits, then `baseline-prof.txt` equals its pre-run content and no .bak remains (observed with a killed run; SIGKILL excepted, covered by AC-1 on the next start).
- AC-3. Given `*.bak` in `app/src/main/generated/baselineProfiles/`, when `git status` runs, then it is not listed as untracked.
- AC-4. Given the emulator was just connected and reports `offline` for a few seconds, when the script runs, then it polls `get-state` up to about 30 s and proceeds on `device`; given it never comes up, it exits 2 naming the last state seen and `ssh mac-emu start`.
- AC-5. Given the script refuses in preflight, when it exits, then no adb server is killed; given generation ran, then the generation server is killed and a concurrent server on 5038 is untouched (check `adb -P 5038 devices` before and after).
- AC-6. Given `make baseline-profile`, when it connects, disconnects and kills, then it does so only on the generation port (5039 or the override), never on `ITEST_ADB_PORT`.
- AC-7. Given the Series screen shows a "4K" section, when the Series entry is left with LEFT but the rail did not reopen, then the journey fails instead of passing; given the rail reopened, it passes, including after the last entry.
- AC-8. Given a full generation on the emulator against the loopback mock, when it ends, then it exits 0, `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` passes on the result, and the egress journal shows no request to a real host.
- AC-9. Given API 34 refusals, when printed or documented, then the reason text no longer claims "the benchmark reset keeps app data" (Makefile, script, docs/release.md).

## Out of scope

Mock protocol parity (baseline-mock-protocol-parity). Any probe of the live KinoPub API. Device checks other than the emulator generation run (and the box only if the rail testTag fallback is needed).

## Design

Scope and ACs: [brief.md](brief.md). Code read in the worktree at b27d8e40.

### Decisions

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

### Changes by layer

| Layer | Change |
|-------|--------|
| API / repository; ViewModel; screen and DI | N/A: tooling and test-APK only |
| Compose content | None unless D8 fires: `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:285-292` |
| Resources | N/A |
| Build tooling | `Makefile:124-125` add `BASELINE_ADB_PORT`/`BASELINE_ADB` (D1); `Makefile:139-179` port switch, port guard, D4/D5 recipe, D9 text, comment `:139-140` names the own port. `tools/generate-baseline-profile.sh:16-26` poll (D3), `:38` (D9), `:45-54` flag and port (D2), `:68` passes `BASELINE_ADB_PORT`. `.gitignore` (D6) |
| Generator (`:baselineprofile`) | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:128-144` expected title = current entry, kept SEARCH double-BACK (`:136-140`) with that title; `:190-196` focused selector (D7); comment `:124-127` updated |
| Docs | `docs/release.md:111-116`: generation port 5039 (`BASELINE_ADB_PORT`), 30 s wait for `device`, D9 reason; `:134-136`: leftover `.bak` restored with a notice at the next start |
| Profiles | Proving run rewrites `app/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`: committed only if `BaselineProfileContentTest` passes and the egress journal is empty, else `git checkout` (brief Q4) |

### Alternatives rejected

- Keep 5038 and only flag the kill (research option A): the make target's `disconnect` (`Makefile:143`) still drops a concurrent `itest` device.
- Refuse on a leftover `.bak`: adds a manual step (brief Q2).
- Backup outside the generated dir (`app/build/`): contradicts AC-1/AC-3 paths and is lost on `clean`.
- Waiting for the next title anywhere on screen with a longer timeout: the false pass is about where the text is, not when.
- `testTag` first: touches production source and needs a box check without evidence that it is needed.

### Risks

| Risk | Where | Mitigation |
|------|-------|------------|
| Baseline profile: the proving run rewrites both checked-in profiles; a wrong or stripped profile ships in the next release | `app/src/main/generated/baselineProfiles/`, `Makefile` recipe (D5) | `BaselineProfileContentTest.kt:47` (all `H`) and `:76` (cold-start guards) in `check.full`; commit rule above |
| Signal delivery: dash defers a TERM/HUP trap until the foreground Gradle client returns, so a TERM sent to the shell pid alone restores only after Gradle ends | `Makefile` recipe (D5), `tools/generate-baseline-profile.sh:68` | AC-2 observation sends the signal to the process group (as terminal Ctrl-C or hangup do); SIGKILL covered by D4 |
| UiAutomator may not expose `focused` on the merged `NavigationDrawerItem` node | `BaselineProfileGenerator.kt:190-196`, `MainScreenComponent.kt:337-356` | Emulator run decides; D8 fallback |
| Restore of a leftover `.bak` after a kill between re-marking `H` and `rm -f` discards a finished generation | `Makefile` recipe (D4/D5) | Notice says so; window is two commands |
| `mergeIntoMain` (baselineprofile 1.5.0) handling of an extra file in the generated dir is unverified | `app/build.gradle.kts:450-463` | Two prior runs completed with the `.bak` there; the proving run repeats it |
| A port override equal to `ITEST_ADB_PORT` or 5037 would bring the conflict back | `Makefile` (D1) | Refusal at the top of the target |
| Egress: generation must reach only the loopback mock | `BaselineProfileTestControl` journal checks (unchanged) | AC-8 reads the journal; no new network code |

### Open questions

1. AC-4 says "polls `get-state`"; D3 reads the state from `adb devices` because `get-state` reports `offline` only as an error. Recommended: accept as the same observable (device state polled for 30 s). Blocks: no.

### Round 2 delta

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

### Round 3 delta

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

## Test design

Scope and ACs: [brief.md](brief.md). Decisions D1-D9: [design-architect.md](design-architect.md). Base b27d8e40. Track perf: off.

### Harness

New `tools/tests/test-baseline-profile-tooling.sh`, run as `bash tools/tests/test-baseline-profile-tooling.sh` from the worktree root (style of `tools/tests/test-verify-apk-signer.sh`: `test_*` functions, `run`/`expect`, a temp dir `$T` per test). No device, no network, no Gradle.

| Piece | What it does |
|---|---|
| Sandbox repo | `$T/repo` with copies of the worktree `Makefile`, `tools/generate-baseline-profile.sh` and a small `app/src/main/generated/baselineProfiles/baseline-prof.txt` holding `HSPL...->m()` rules; make runs as `make -C $T/repo`, so the checked-in profile is never touched |
| Fake `adb` (first on `PATH`) | Appends `port=$ANDROID_ADB_SERVER_PORT args=...` to `$T/adb.log`; `devices` prints the next state from `$T/states` (last one repeats); `get-state` prints `device` or, like real adb, `error: device offline` on stderr with exit 1; `getprop` answers from `$T/props` (default sdk 36, `ro.boot.qemu=1`) |
| Fake `sleep` (on `PATH`) | Logs and returns at once, so the 30 s poll runs in milliseconds |
| Fake `GRADLE` | Records its env port and a copy of `baseline-prof.txt` as seen at call time in `$T/gradle.*`; exits `$FAKE_GRADLE_EXIT` or, with `FAKE_GRADLE_BLOCK=1`, waits until killed |
| Signals | The target is started as its own process group (`set -m`, background job); the signal goes to the group (`kill -HUP -- -$pgid`), as a terminal hangup or Ctrl-C would (design risk: dash defers a trap while the foreground child runs) |

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Leftover `.bak` overwritten by a stripped copy; original profile lost | 2 | 3 | 6 | `test_leftover_bak_is_restored_before_backup` |
| R2 | Interrupted run leaves the S/P-only profile in source, later committed | 2 | 3 | 6 | AC-2 tests; `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` in check.full |
| R3 | Proving run commits a wrong or stripped profile | 2 | 3 | 6 | M1 commit rule; `BaselineProfileContentTest` (all tests) in check.full |
| R4 | Generation disconnects or kills a concurrent `itest` server on 5038 | 2 | 2 | 4 | AC-5, AC-6 tests; M1 step 3/9 |
| R5 | Rail check passes with the rail closed (Series "4K" header); profile silently misses tabs | 2 | 2 | 4 | M1, M2 |
| R6 | UiAutomator does not report `focused` on the merged rail item; D8 fallback touches production source | 2 | 2 | 4 | M1 decides; M4 if D8 fires |
| R7 | dash defers TERM/HUP until the Gradle client returns; restore late or skipped | 2 | 2 | 4 | `test_hup_to_group_restores_profile`, `test_term_to_group_restores_profile`; M3 |
| R8 | `mergeIntoMain` (baselineprofile 1.5.0) trips on the `.bak` in the generated dir | 1 | 3 | 3 | M1 (run completes with the `.bak` present) |
| R9 | Generation reaches a real host | 1 | 3 | 3 | Existing egress journal checks; M1 step 7 |
| R10 | Port override equal to 5038 or 5037 restores the conflict | 1 | 3 | 3 | `test_baseline_port_equal_to_itest_or_default_refused` |
| R11 | Restoring a leftover `.bak` discards a generation finished just before a kill | 1 | 1 | 1 | Accepted; notice text asserted in `test_leftover_bak_is_restored_before_backup` |

### Traceability

Level `shell` = the harness above (project levels: `unit`, `instrumented`, `device`; the shell level is the cheapest one that runs the Makefile recipe and the script). State: `red` fails on b27d8e40, `guard` passes there and must stay green.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | shell | P0 red | `test_leftover_bak_is_restored_before_backup` | `.bak`=ORIG, profile=STRIPPED-NEW, gradle exits 1: output has the restore notice (mentions a discarded generation), gradle saw ORIG stripped, final profile = ORIG, no `.bak`. Fails if the leftover check is removed or runs after `cp` (base: final = NEW) |
| AC-2 | shell | P0 guard | `test_failed_generation_restores_profile` | Gradle exits 1: profile byte-equal to pre-run, no `.bak`, make exits non-zero. Fails if the EXIT-trap restore replaces the failure path and misses it |
| AC-2 | shell | P0 red | `test_hup_to_group_restores_profile` | Blocked gradle, HUP to the group: profile byte-equal to pre-run, no `.bak`, exit 129. Fails without HUP in the trap list (base) |
| AC-2 | shell | P0 red | `test_term_to_group_restores_profile` | Same with TERM: restored, no `.bak`, exit 143 (base exits 130). Fails if TERM restore or its exit code regresses |
| AC-2 | shell | P1 guard | `test_int_to_group_restores_profile` | Same with INT: restored, no `.bak`, exit 130 |
| AC-2 | shell | P1 guard | `test_successful_generation_marks_hot_and_removes_bak` | Gradle exits 0: every `->` rule starts with `H`, no `.bak`, exit 0. Fails if the EXIT trap restores the backup over a successful run |
| AC-3 | shell | P1 red | `test_bak_in_generated_dir_is_git_ignored` | In the worktree: `git check-ignore -q app/src/main/generated/baselineProfiles/baseline-prof.txt.bak` exits 0 and `git check-ignore -q .../baseline-prof.txt` exits 1. Fails if the rule is missing or so broad it hides the profile |
| AC-4 | shell | P0 red | `test_preflight_waits_for_offline_device` | States `offline offline device`: script proceeds to make (gradle called), at least two `sleep` calls logged. Fails if the single `get-state` check returns (base exits 2) |
| AC-4 | shell | P0 red | `test_preflight_times_out_naming_last_state` | States always `offline`: exit 2, output names `offline` and `ssh mac-emu start`, gradle never called, total logged sleep about 30 s. Fails if the poll has no bound or drops the hint |
| AC-4 | shell | P1 red | `test_preflight_reconnects_missing_device` | States `(absent) device`: `adb connect` logged more than once, script proceeds. Fails if connect is issued only once (D3) |
| AC-5 | shell | P0 red | `test_preflight_refusal_kills_no_server` | Props sdk 33 (and separately `ro.boot.qemu=0`, `ro.hardware=x`): exit 2, `adb.log` has no `kill-server`. Fails if `cleanup` kills unconditionally (base kills 5038) |
| AC-5 | shell | P0 red | `test_generation_kills_only_baseline_server` | Full script run, gradle exits 0: `adb.log` has `kill-server` on port 5039 and no line with port 5038. Fails if the kill goes back to `ITEST_ADB_PORT` |
| AC-6 | shell | P0 red | `test_make_target_uses_only_baseline_port` | `make baseline-profile` with default and with `BASELINE_ADB_PORT=5041`: every `adb.log` line and the gradle env carry 5039 / 5041, none 5038, including on each refusal path. Fails if any `$(ITEST_ADB)` is left in the target |
| AC-6 | shell | P0 red | `test_baseline_port_equal_to_itest_or_default_refused` | `BASELINE_ADB_PORT=5038` and `=5037`: non-zero exit, `adb.log` empty, profile untouched. Fails if the D1 guard is missing or runs after the first adb call |
| AC-7 | device | P0 red | M2 | Rail left closed after Series: generation fails with "The rail did not reopen after entry ..." for that entry. Fails if `railShows` matches the title anywhere on screen (base passes on the "4K" header) |
| AC-7 | device | P0 guard | M1 | Real rail journey passes all entries, including the last one (`PuberGen` rail iterations logged). Fails if the focused selector never matches |
| AC-8 | device | P0 guard | M1 | Exit 0, content test green on the new profile, egress journal empty in every `PuberGen` line |
| AC-8 | unit | P0 guard | `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` (and the rest of the class) | Fails on an S/P-only or partial profile written by the proving run |
| AC-9 | shell | P1 red | `test_api34_refusal_reason_text` | Props sdk 33 through the script and through `make baseline-profile`: output lacks `keeps app data` and contains `resets compilation in place`. Fails if either refusal keeps the old reason |
| AC-9 | manual | P2 | M5 | `docs/release.md` names the new reason, port 5039, the 30 s wait and the leftover-`.bak` notice |

### Bugs: failing-first test

Base code read with `git -C <wt> show b27d8e40:Makefile` (`:166-179`) and `:tools/generate-baseline-profile.sh` (`:16-26`, `:47-54`). Written first: `test_leftover_bak_is_restored_before_backup` (base `cp $$f $$f.bak` overwrites the original). Every other `red` row above is run on the unchanged recipe/script before the change and must fail there.

### Manual checks

Emulator `host.docker.internal:5555` only (lease via `tools/emulator-resource-lock.sh`, `ssh mac-emu start`, `ssh mac-emu stop` after if no other lease). Generation reaches only the loopback mock; no other KinoPub traffic.

**M1. Proving generation (AC-4, AC-5, AC-7 pass path, AC-8; R3, R8, R9).**
1. Take the emulator lease. `ssh mac-emu start`; do not wait after `ready`.
2. Note `git status --short app/src/main/generated/baselineProfiles` (clean).
3. `ANDROID_ADB_SERVER_PORT=5038 adb start-server`; record `ss -ltn | grep -c :5038` (1).
4. Immediately: `DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh`.
5. Expected: exit 0; if the emulator was `offline` the script waited instead of refusing; `baseline profile run sees:` lists only the emulator.
6. Logcat file: rail iterations complete (no "did not reopen"); per-iteration and `late` `PuberGen` lines report an empty journal and no unknown route.
7. `ss -ltn | grep -c :5038` still 1; `ss -ltn | grep -c :5039` 0.
8. `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.BaselineProfileContentTest'` passes; no `*.bak` in `git status` or on disk.
9. Commit both profiles if 6 and 8 hold, else `git checkout -- app/src/main/generated/baselineProfiles`. `ANDROID_ADB_SERVER_PORT=5038 adb kill-server` (the server from step 3).
Must not happen: refusal on a just-started emulator, a request outside the mock, the 5038 server gone, a `.bak` left, an S/P-only rule committed.

**M2. Rail negative (AC-7 red).**
1. Scratch-only edit to `BaselineProfileGenerator.kt`: after the Series entry skip the LEFT (rail stays closed); `@Ignore` the other journeys to shorten the run.
2. Run as M1 step 4.
3. Expected: run fails with "The rail did not reopen after entry N" for the Series entry; profile byte-equal to pre-run, no `.bak` (AC-2 normal failure on device).
4. Run once on the base selector (b27d8e40 `railShows`) with the same scratch edit: it passes. Record both outcomes.
5. Revert: `git diff --quiet baselineprofile` is true.
Must not happen: the journey passes with the new selector; the scratch edit committed.

**M3. Interrupted run on device (AC-2, R7).**
1. Start as M1 step 4 (as a foreground terminal job); once `Generator events go to` prints and Gradle is running, press Ctrl-C (INT to the group).
2. Expected: `git diff --quiet app/src/main/generated/baselineProfiles` true; no `.bak`; nothing listens on 5039; exit 130.
Must not happen: stripped profile left in source; leftover `.bak`.

**M4. Box rail check, only if D8 fires (production `testTag` added).** TV box `192.168.1.110:5555`, `com.kino.puber.stage` only, worktree dev APK via `tools/android-apk-install-preserve`.
1. Launch; `KEYCODE_DPAD_LEFT` opens the rail; `KEYCODE_DPAD_DOWN` through every entry, `KEYCODE_DPAD_CENTER` on Series, `KEYCODE_DPAD_LEFT` back.
2. Expected: rail opens, focus on the selected entry, rail looks as before (screenshot before/after).
Must not happen: focus loss, rail not reopening, visual change.

**M5. Docs (AC-9).** `docs/release.md` generation section: new API 34 reason, port 5039 / `BASELINE_ADB_PORT`, 30 s wait for `device`, leftover `.bak` restored with a notice; no "keeps app data".

### Perf

N/A: track perf off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | AC-4 "polls get-state": read device state from adb devices instead? | no | answered | Yes, same check (~30 s poll) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Analyst decisions as recommended: adb port 5039, restore leftover .bak with a notice, focused-item rail check first, commit proving-run profiles only if the content test passes and the journal is empty | owner (standing instruction: recommended) |
| 2026-11-26 | Q1 as recommended; Gate 1: spec approved | owner (standing instruction: recommended) |
