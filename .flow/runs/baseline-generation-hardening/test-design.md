# Test design: baseline-generation-hardening

Scope and ACs: [brief.md](brief.md). Decisions D1-D9: [design-architect.md](design-architect.md). Base b27d8e40. Track perf: off.

## Harness

New `tools/tests/test-baseline-profile-tooling.sh`, run as `bash tools/tests/test-baseline-profile-tooling.sh` from the worktree root (style of `tools/tests/test-verify-apk-signer.sh`: `test_*` functions, `run`/`expect`, a temp dir `$T` per test). No device, no network, no Gradle.

| Piece | What it does |
|---|---|
| Sandbox repo | `$T/repo` with copies of the worktree `Makefile`, `tools/generate-baseline-profile.sh` and a small `app/src/main/generated/baselineProfiles/baseline-prof.txt` holding `HSPL...->m()` rules; make runs as `make -C $T/repo`, so the checked-in profile is never touched |
| Fake `adb` (first on `PATH`) | Appends `port=$ANDROID_ADB_SERVER_PORT args=...` to `$T/adb.log`; `devices` prints the next state from `$T/states` (last one repeats); `get-state` prints `device` or, like real adb, `error: device offline` on stderr with exit 1; `getprop` answers from `$T/props` (default sdk 36, `ro.boot.qemu=1`) |
| Fake `sleep` (on `PATH`) | Logs and returns at once, so the 30 s poll runs in milliseconds |
| Fake `GRADLE` | Records its env port and a copy of `baseline-prof.txt` as seen at call time in `$T/gradle.*`; exits `$FAKE_GRADLE_EXIT` or, with `FAKE_GRADLE_BLOCK=1`, waits until killed |
| Signals | The target is started as its own process group (`set -m`, background job); the signal goes to the group (`kill -HUP -- -$pgid`), as a terminal hangup or Ctrl-C would (design risk: dash defers a trap while the foreground child runs) |

## Risk profile

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

## Traceability

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

## Bugs: failing-first test

Base code read with `git -C <wt> show b27d8e40:Makefile` (`:166-179`) and `:tools/generate-baseline-profile.sh` (`:16-26`, `:47-54`). Written first: `test_leftover_bak_is_restored_before_backup` (base `cp $$f $$f.bak` overwrites the original). Every other `red` row above is run on the unchanged recipe/script before the change and must fail there.

## Manual checks

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

## Perf

N/A: track perf off.

## UNTESTABLE

None.
