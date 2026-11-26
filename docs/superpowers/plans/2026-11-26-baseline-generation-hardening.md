# Harden the baseline profile generation route - plan

**Spec:** `docs/superpowers/specs/2026-11-26-baseline-generation-hardening.md`
**Goal:** Give baseline generation its own adb port, a leftover-safe and signal-safe profile backup, a preflight that waits for a just-started emulator, and a rail check that only passes when the rail really reopened.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug`.
- Shell tests: `bash tools/tests/test-baseline-profile-tooling.sh` from the worktree root (no device, no network, no Gradle).
- Generator compile: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin`.
- One task = green fast gate + green tests of the touched code + one commit, message per `commit_style` (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec (Design D1-D9, Test design) and the files cited below, nothing else.
- Work in `/home/dev/orca/workspaces/Puber/baseline-generation-hardening`; never `./gradlew` there.
- No production source change unless D8 fires in Task 5.
- Never send a request to the live KinoPub API or any non-loopback host from a test, script or device run. Only Task 5 touches a device: the emulator `host.docker.internal:5555`, generation against the loopback mock. qa owns M2-M5 (and M4 on the box only if D8 fires).
- Out of scope: mock protocol parity (baseline-mock-protocol-parity), `StartupBenchmarks`, `itest` (`Makefile:129-137` stays as is).

## Shared definitions

| Name | Value |
|---|---|
| Generation port | `BASELINE_ADB_PORT ?= 5039`, `BASELINE_ADB = ANDROID_ADB_SERVER_PORT=$(BASELINE_ADB_PORT) $(ADB)` (Makefile, after `ITEST_ADB` at `Makefile:125`) |
| Port guard message | `refused: BASELINE_ADB_PORT=<p> is the itest port (<ITEST_ADB_PORT>) or the default adb server (5037)`, exit 1, before any adb call |
| Leftover notice | `restored <f> from <f>.bak left by an interrupted run; a generation that finished just before that interruption is discarded` |
| API 34 reason (D9) | `generation needs API 34 or newer, where the benchmark library resets compilation in place instead of reinstalling the app` |
| Preflight timeout message (D3) | `refused: <DEVICE> did not reach 'device' within 30 s (last state: '<state or absent>'); start the emulator with 'ssh mac-emu start' and try again.`, exit 2 |

---

## Tasks

### Task 1: Shell harness, own adb port, leftover-safe and signal-safe backup in the make target

**Covers:** AC-1, AC-2, AC-3, AC-6
**Files:**
- Create: `tools/tests/test-baseline-profile-tooling.sh` - harness per spec Test design "Harness", structure of `tools/tests/test-verify-apk-signer.sh` (`run`/`expect` `:33-58`, `test_*` loop with a fresh `$T` per test `:146-158`). Pieces:
  - sandbox `$T/repo`: copies of the worktree `Makefile` and `tools/generate-baseline-profile.sh` (executable), `app/src/main/generated/baselineProfiles/baseline-prof.txt` with a few `HSPLcom/x/A;->m()V` rules and one class line; make runs as `make -C "$T/repo"` with `DEVICE=emu:5555 GRADLE=$T/bin/gradle`
  - `$T/bin` first on `PATH` (the Makefile resolves `ADB` with `command -v adb`, `Makefile:22`): fake `adb` appends `port=$ANDROID_ADB_SERVER_PORT args=$*` to `$T/adb.log`; `devices` prints `List of devices attached` then `<DEVICE>\t<state>` for the next line of `$T/states` (a line `absent` prints no row; the last line repeats; default `device`); `get-state` prints `device` or `error: device offline` on stderr with exit 1; `shell getprop <k>` answers from `$T/props` (defaults `ro.build.version.sdk=36`, `ro.boot.qemu=1`); `logcat` exits 0; everything else exits 0
  - fake `sleep` logs `sleep <n>` to `$T/sleep.log` and returns
  - fake `gradle` writes `$ANDROID_ADB_SERVER_PORT` to `$T/gradle.port`, copies the profile to `$T/gradle.profile`, then exits `${FAKE_GRADLE_EXIT:-0}`; with `FAKE_GRADLE_BLOCK=1` it loops on `/bin/sleep 0.1` (absolute path, not the fake) until killed
  - signal helper: `set -m`, start make as a background job (own process group), wait until `$T/gradle.profile` exists, `kill -<SIG> -- -$pgid`, `wait` for the exit code
  - `PUBER_BASELINE_LOG_DIR=$T/logs` for script runs
- Test: in that file, the AC-1, AC-2, AC-3 and AC-6 rows of the spec Traceability table, names as listed there: `test_leftover_bak_is_restored_before_backup`, `test_failed_generation_restores_profile`, `test_hup_to_group_restores_profile`, `test_term_to_group_restores_profile`, `test_int_to_group_restores_profile`, `test_successful_generation_marks_hot_and_removes_bak`, `test_bak_in_generated_dir_is_git_ignored` (runs `git check-ignore` in the worktree root, not the sandbox), `test_make_target_uses_only_baseline_port`, `test_baseline_port_equal_to_itest_or_default_refused`. Each asserts exactly what its "Proves" cell says
- Modify: `Makefile:124-125` - add the generation port definitions (Shared definitions) after `ITEST_ADB`
- Modify: `Makefile:139-179` (`baseline-profile`):
  - comment `:139-140`: its own private adb server on `BASELINE_ADB_PORT`, so a concurrent `itest` on `ITEST_ADB_PORT` keeps its devices
  - new first recipe line: port guard (Shared definitions) as `case "$(BASELINE_ADB_PORT)" in $(ITEST_ADB_PORT)|5037) ...; exit 1;; esac`
  - `:142-163` and `:170`: every `$(ITEST_ADB)` becomes `$(BASELINE_ADB)`, `ANDROID_ADB_SERVER_PORT=$(ITEST_ADB_PORT)` becomes `=$(BASELINE_ADB_PORT)`; refusal texts otherwise unchanged (D9 is Task 2)
  - `:166-179` replaced per D4/D5: `f=...; b=$$f.bak`; if `$$b` exists print the leftover notice and `mv -f $$b $$f`; then `cp $$f $$b`; `trap 'if [ -f $$b ]; then mv -f $$b $$f; fi; $(BASELINE_ADB) kill-server >/dev/null 2>&1' EXIT`; `trap 'exit 129' HUP; trap 'exit 130' INT; trap 'exit 143' TERM`; strip `H` (`:169` sed unchanged); Gradle as `:170-171` on the generation port; `status=$$?`; on 0 re-mark `H` (`:174` sed unchanged) and `rm -f $$b`; `exit $$status`. No restore or kill outside the EXIT trap
- Modify: `.gitignore` - append `/app/src/main/generated/baselineProfiles/*.bak` with a one-line comment (D6)

**Consumes:** `Makefile:22` `ADB`, `:63-68` `require-device`, `:124` `ITEST_ADB_PORT`
**Produces:** `BASELINE_ADB_PORT`, `BASELINE_ADB` (Task 2 passes the port); the harness and its fakes (Task 2 adds tests)

- [x] Write the harness and the nine tests first; run them on the unchanged `Makefile` and `.gitignore` and record the red run (P0/P1 `red` rows must fail). Note: GNU make 4.3 dies by the signal it received after its recipe shell ends, so the observed exit of a signalled `make` is 128+signal whatever the recipe exits; if `test_term_to_group_restores_profile` passes on base, record it as guard in the Red run (see Open question 1)
- [x] Makefile and `.gitignore` changes
- [x] `bash tools/tests/test-baseline-profile-tooling.sh` passes; `git status --short` shows no `.bak` and an untouched `app/src/main/generated/baselineProfiles/`
- [x] Fast gate; commit ("Give baseline generation its own adb port and a backup that survives leftovers and hangups")

**Done when:** the shell tests pass, `rg -n 'ITEST' Makefile` shows no hit between the `baseline-profile:` line and the end of its recipe except the port guard, and the fast gate exits 0.

---

### Task 2: Script preflight waits for the device, kills only the generation server, new API 34 reason

**Covers:** AC-4, AC-5, AC-9 (Makefile and script)
**Files:**
- Test: `tools/tests/test-baseline-profile-tooling.sh` - add `test_preflight_waits_for_offline_device`, `test_preflight_times_out_naming_last_state`, `test_preflight_reconnects_missing_device`, `test_preflight_refusal_kills_no_server` (cases sdk 33; `ro.boot.qemu=0` with `ro.hardware=x`), `test_generation_kills_only_baseline_server`, `test_api34_refusal_reason_text` (through the script and through `make baseline-profile`), asserting their spec "Proves" cells. Script runs: `DEVICE=emu:5555 GRADLE=$T/bin/gradle "$T/repo/tools/generate-baseline-profile.sh"`
- Modify: `tools/generate-baseline-profile.sh:16-26` (D3) - initial `adb connect` for a `host:port` serial (`:17-19` as is); then up to 15 polls: state = second column of `adb devices` for `$DEVICE` (`awk -v d="$DEVICE" '$1==d {print $2}'`), break on `device`; if empty (absent) and the serial is `host:port`, `adb connect` again; `sleep 2`. Not `device` after the loop: timeout message (Shared definitions) on stderr, exit 2. `get-state` is no longer called. Comment `:14-15` names the 30 s wait
- Modify: `tools/generate-baseline-profile.sh:38` and `Makefile:157` - D9 reason (Shared definitions) replaces `where the benchmark reset keeps app data`
- Modify: `tools/generate-baseline-profile.sh:43-54` (D2) - `generation_started=""` next to `logcat_pid`; `cleanup` kills `ANDROID_ADB_SERVER_PORT="${BASELINE_ADB_PORT:-5039}" adb kill-server` only when `generation_started` is set; comment `:45-46` names the generation port and that a preflight refusal kills nothing
- Modify: `tools/generate-baseline-profile.sh:68` - `generation_started=1` just before; `make -C "$ROOT_DIR" DEVICE="$DEVICE" GRADLE="$GRADLE" BASELINE_ADB_PORT="${BASELINE_ADB_PORT:-5039}" baseline-profile`
- Unchanged: traps `:74-76`, `run_generation` structure `:56-72`

**Consumes:** Task 1 `BASELINE_ADB_PORT`, harness fakes (`devices`/`states`, fake `sleep`)
**Produces:** script behaviour Task 5 relies on (wait on `offline`, port 5039)

- [x] Write the six tests; run them on the Task 1 script and Makefile and record the red run
- [x] Script and `Makefile:157` changes
- [x] `bash tools/tests/test-baseline-profile-tooling.sh` passes (all tests); `bash -n tools/generate-baseline-profile.sh`; `rg -n 'keeps app data|ITEST_ADB_PORT' tools/generate-baseline-profile.sh` prints nothing; `rg -n 'keeps app data' Makefile` prints nothing
- [x] Fast gate; commit ("Wait for a just-started emulator and kill only the generation adb server in the baseline script")

**Done when:** the shell test file passes in full and the fast gate exits 0.

---

### Task 3: Rail check waits for the focused entry just opened

**Covers:** AC-7 (code; proved on device in Task 5 and by qa M2)
**Files:**
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:128-144` (D7) - `forEachIndexed { index, titleRes -> ... }`; drop `expectedTitle` (`:135`); the Search double-BACK (`:136-140`) and the `check` (`:141-143`) use `railShows(titleRes)`; message unchanged
- Modify: `BaselineProfileGenerator.kt:190-196` - `railShows` waits for `By.text(title).focused(true)` (`Until.hasObject`, `RAIL_WAIT_MS` `:239` unchanged); KDoc: LEFT restores rail focus to the selected tab (`focusRestorer(fallbackFocusItem)`, `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:274-276,291`), so the opened entry is focused only when the rail is open; an unfocused title elsewhere (the Series "4K" section, `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/TabTypeConfig.kt:97-104`) does not count
- Modify: comment `BaselineProfileGenerator.kt:124-127` - "the rail must have the entry just opened focused" instead of "show the next title"

**Consumes:** `RAIL_TITLE_RES` `:225-232`, `SEARCH_INDEX` `:238`
**Produces:** `railShows(titleRes): Boolean` with the focused selector (Task 5, D8 fallback replaces its selector only)

- [x] Edit
- [x] Generator compile (Global constraints) exits 0; `rg -n 'getOrNull\(index \+ 1\)' baselineprofile` prints nothing
- [x] Fast gate; commit ("Pass the baseline rail check only when the opened entry is focused in the rail")

**Done when:** the generator compile and the fast gate exit 0.

---

### Task 4: Release guide describes the hardened route

**Covers:** AC-9 (docs, qa M5)
**Files:**
- Modify: `docs/release.md:111-115` - the make target uses its own adb server on port 5039 (`BASELINE_ADB_PORT`, refused if equal to `ITEST_ADB_PORT` or 5037), so a concurrent `make itest` keeps its devices; the script waits up to 30 s for `$DEVICE` to reach `device` (a just-started emulator reports `offline` first) and otherwise exits 2 naming the last state and `ssh mac-emu start`; API 34 reason as D9; a refusal kills no adb server
- Modify: `docs/release.md:134-136` - the backup `baseline-prof.txt.bak` (git-ignored) is restored on failure, Ctrl-C, TERM or hangup; a `.bak` left by a killed run is restored with a notice at the next start, discarding a generation that finished just before the kill

**Consumes:** Tasks 1-2 behaviour
**Produces:** nothing later tasks call

- [x] Edit; `awk '/^## Refresh Baseline Profiles/,/^## Verification/' docs/release.md | grep -nE '5039|30 s|\.bak|resets compilation in place'` shows each; `grep -n 'keeps app data' docs/release.md` prints nothing
- [x] Fast gate; commit ("Describe the baseline generation port, device wait and backup restore in the release guide")

**Done when:** both greps behave as stated and the fast gate exits 0.

---

### Task 5: Proving generation on the emulator (M1)

**Covers:** AC-4, AC-5 (device), AC-7 (pass path), AC-8
Device run is the implementer's, as in hermetic-baseline-generation Task 8, because its output is the committed profile. qa reads this run's logcat for M1 and owns M2-M5.
**Files:**
- Modify (generated): `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt` - only if the commit rule below holds
- Modify only if D8 fires: `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:285-292` - the rail `Column` modifier gets `.semantics { testTagsAsResourceId = true }.testTag("main_rail")` (pattern `app/src/main/java/com/kino/puber/ui/feature/history/component/HistoryScreenContent.kt:157`, imports `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.testTagsAsResourceId`, `androidx.compose.ui.platform.testTag`); `BaselineProfileGenerator.kt` `railShows` selector becomes `By.res("main_rail").hasDescendant(By.text(title))`. Record under Deviations; qa then runs M4 on the box

**Consumes:** Tasks 1-4
**Produces:** regenerated profiles; logcat path for qa

- [x] Lease: `tools/emulator-resource-lock.sh acquire host.docker.internal:5555` (keep the token); `ssh mac-emu start` (prints `ready`); do not wait after `ready`
- [x] `git status --short app/src/main/generated/baselineProfiles` is empty
- [x] `ANDROID_ADB_SERVER_PORT=5038 adb start-server`; `ss -ltn | grep -c :5038` is 1
- [x] At once, with no `.env`: `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh`; expect exit 0, a wait instead of a refusal if the emulator was `offline`, `baseline profile run sees:` listing only the emulator
- [x] In the printed logcat file: no "did not reopen"; every iteration and every `late after=` `PuberGen` line has an empty journal and `unknown=[]`
- [x] D8 decision: if the rail journey fails with "The rail did not reopen after entry 1" on every iteration while the run is otherwise clean, apply the D8 change, generator compile, rerun the step above once. Any other failure: stop, restore (`git checkout -- app/src/main/generated/baselineProfiles`), record under Blocked
- [x] `ss -ltn | grep -c :5038` still 1; `ss -ltn | grep -c :5039` is 0; `ANDROID_ADB_SERVER_PORT=5038 adb kill-server`
- [x] Release the lease with its token; `ssh mac-emu stop` only if `tools/emulator-resource-lock.sh status` shows no other lease
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.BaselineProfileContentTest'` passes; `grep -c '^[^H].*->' app/src/main/generated/baselineProfiles/baseline-prof.txt` is 0; no `*.bak` on disk (`ls app/src/main/generated/baselineProfiles`)
- [x] Commit rule (brief Q4): the content test passes and the journal checks above are clean, then commit both profiles; otherwise `git checkout -- app/src/main/generated/baselineProfiles` and record under Blocked
- [x] Full gate; `bash tools/tests/test-baseline-profile-tooling.sh`; commit ("Regenerate baseline profiles through the hardened generation route"; with D8 the same commit carries the rail tag and selector)
- [x] Implementation record: logcat path, the `ss` counts before and after, whether the preflight waited, whether D8 fired

**Done when:** generation exits 0, `BaselineProfileContentTest` passes on the regenerated files, the 5038 server survived, and the full gate exits 0.

---

## Open questions

1. `test_term_to_group_restores_profile` is marked `red` with "base exits 130", but GNU make re-raises the signal it received, so `make` itself exits 143 on base too and base already restores on TERM. Recommended: keep the test as written (it pins restore plus exit 143) and record it as guard in the Red run if it passes on base. Blocks: no.

## Implementation record

<!-- owner: implementer -->

**Commits:** 516d23c8 (Task 1), e07e338f (Task 2), fd08c9ee (Task 3), b0b52852 (Task 4), the last commit (Task 5: record, profiles, D8 tag and selector).

**Red run** (P0 `red` tests on the unchanged code):

- red: test_leftover_bak_is_restored_before_backup - no restore notice, base overwrites the .bak with the new copy
- red: test_hup_to_group_restores_profile - profile after HUP differs (stripped profile left in source)
- red: test_bak_in_generated_dir_is_git_ignored - .bak is not ignored
- red: test_baseline_port_equal_to_itest_or_default_refused - port 5038 accepted
- red: test_make_target_uses_only_baseline_port - gradle saw port 5038, wanted 5039
- red: test_preflight_waits_for_offline_device - exit 2, 'not reachable (get-state none)'
- red: test_preflight_times_out_naming_last_state - output lacks "did not reach 'device' within 30 s"
- red: test_preflight_reconnects_missing_device - exit 2 after one connect
- red: test_preflight_refusal_kills_no_server - kill-server on 5038 after the sdk refusal
- red: test_generation_kills_only_baseline_server - a kill-server call on 5038
- red: test_api34_refusal_reason_text - output lacks 'resets compilation in place'
- green on base: test_term_to_group_restores_profile - GNU make re-raises TERM so base already restores and exits 143 (plan Q1; kept as guard)
- green on base: test_failed_generation_restores_profile, test_int_to_group_restores_profile, test_successful_generation_marks_hot_and_removes_bak - guard rows, as designed

**Deviations from the plan:**

- D8 fired. The first proving run (D7 selector `By.text(title).focused(true)`) failed `railNavigationProfile` with "The rail did not reopen after entry 1 of 6" while startup, browse and playback passed with empty journals. Applied D8: `main_rail` test tag with `testTagsAsResourceId` on the rail `Column` (`MainScreenComponent.kt`) and `By.res("main_rail").hasDescendant(By.text(title))` in `railShows`; the KDoc and the loop comment now say "shows", not "focused". qa runs M4 on the box.
- Test harness: a signalled or refused `make` exits 2 (make's own recipe-failure code), so the refusal tests expect 2, not 1.

**Files touched outside the plan:**

- None beyond D8's `MainScreenComponent.kt` and the generator selector (both named in the plan).

**For the reviewers:**

- M1 (D8 selector), generation exit 0 on host.docker.internal:5555: logcat `/tmp/puber-baseline-profile/20261126-183120-logcat.txt`; no "did not reopen"; every `iteration` and `late after=` PuberGen line has `missing=[] unknown=[]` and an empty journal (the `Broadcasting: Intent` text is the `am broadcast` echo, `data=""`); "baseline profile run sees" listed only the emulator; all four journeys passed (rail 5 iterations).
- adb servers (no `ss` on the host; counted listeners on :5038 and :5039 from /proc/net/tcp): before the run 5038=1, 5039=0; during the run 5038=1, 5039=1; after the run 5038=1, 5039=0. The 5038 server was started by hand and killed at the end.
- The preflight did not visibly wait: the emulator was already `device` when the run started (about 25 s after `ready`), so the wait path is proved by the shell tests only.
- M2 (scratch, uncommitted, reverted; the other three journeys `@Ignore`d, LEFT skipped after Series): D8 selector fails with "The rail did not reopen after entry 5 of 6" and profile byte-equal to its pre-run content, no .bak. Base `railShows` (b27d8e40) with the same edit did not reproduce a false pass: it failed at entry 5 on the second run and at entry 2 on the first (flaky, unrelated to LEFT); so the "4K header on the Series screen satisfies the base check" claim was not observed on device. The D8 selector scopes the check to the rail either way.
- Also on device: the failed D7 run restored the checked-in profile (AC-2, git status clean, no .bak).
- `hasDescendant(By.text(...))` in the rail does not test focus; it relies on the collapsed rail not exposing item text, which M2 confirms for Series.
- `BaselineProfileContentTest` passes on the regenerated files; `grep -c '^[^H].*->'` on baseline-prof.txt is 0; no .bak on disk. Lease released, `ssh mac-emu stop` run (no other lease on the emulator).

### Fix round 1 (triage-1 #1-#7)

| # | Result | Commit |
|---|---|---|
| 1 | Reworded, not fixed: the check proves the rail reopened, not which entry is selected (below) | 0a7757b8 |
| 2 | Fixed: the script refuses the itest port and 5037 with exit 2 before any adb call or flag | 05501d77 |
| 3 | Fixed: backup through `baseline-prof.txt.new.bak` then `mv`, abort before the trap on a failed copy; failed strip or re-mark sed exits 1 and the EXIT trap restores | 05501d77 |
| 4 | Fixed: a leftover .bak is restored only when the profile equals its stripped copy; otherwise removed with an "is out of date" notice | 05501d77 |
| 5 | Fixed: counts only preflight connects (`port= args=connect`) | 05501d77 |
| 6 | Fixed: tests for the 60 s device wait and the extra serial refusals | 05501d77 |
| 7 | Fixed: `chmod +x` on the tooling test (mode 100755); the conductor wires it into check.full | 05501d77 |

Red run (fix round 1, on the unchanged recipe and script):

- red: test_failed_backup_copy_aborts_before_gradle - make exited 0 (failed cp ignored)
- red: test_failed_final_remark_restores_profile - make exited 0 (failed re-mark ignored)
- red: test_script_refuses_itest_port_before_any_server_is_touched - kill-server on 5038 after the port refusal
- red: test_stale_bak_does_not_overwrite_newer_profile - no out-of-date notice, base restored the stale .bak
- green on base: test_make_refuses_when_device_never_appears, test_make_refuses_extra_serial_on_private_server - the refusals exist, the tests only pin them (guard)
- test_preflight_reconnects_missing_device: green on base by design; with the reconnect branch removed from the script (scratch) it now fails ("preflight connected once only"), before it passed on the make target's own connect
- test_leftover_bak_is_restored_before_backup now sets the profile to the stripped copy, the state a real interruption leaves

**#1 device runs** (emulator host.docker.internal:5555, loopback mock only, scratch edits reverted, `git diff --quiet baselineprofile` true after; lease released, `ssh mac-emu stop` run):

- `By.text(t).selected(true)`: fails at entry 1; the rail item is a `View` (checkable, clickable) with a `TextView` child, and `selected` is false on both. The selected state is `checked` on the View.
- `By.res("main_rail").hasDescendant(By.checked(true).hasDescendant(By.text(t)))`: discriminates, but the real journey fails at "entry 5 of 6" (dump: Settings checked). A probe logging checked/focused per rail row after each LEFT showed LEFT landing on Settings (index 11) from entry 1 on in one run, on the selected entry in another: the journey is flaky about where LEFT lands (rail-left-focus-nearest-entry, already deferred). The text-only check passed those runs although the journey had opened other tabs.
- Consequence: no selected-entry selector can pass the journey today. The comment and KDoc now say "the rail reopened"; AC-7 (rail closed after Series fails, rail open passes) holds as proved by M2; "the opened entry is the expected one" is unproved and is the job of rail-left-focus-nearest-entry. No regeneration: the check's behaviour is unchanged, so the committed profiles stand.
- The scratch runs (other journeys `@Ignore`d, so Gradle wrote a partial profile) left `baseline-prof.txt` modified and `startup-prof.txt` deleted in the worktree after the failed runs; `git checkout -- app/src/main/generated` restored them. Not investigated whether the make recipe or Gradle wrote them.

### Fix round 2 (triage-2 #1-#4, #6)

| # | Result | Commit |
|---|---|---|
| 1 | Fixed: `startup-prof.txt` is backed up (`.bak`, or the marker `.none.bak` when absent) and restored with `baseline-prof.txt`; the re-mark failure text names both files; docs/release.md says so | 9a3c3a12 |
| 2 | Fixed: leftover backups are out of date only when `baseline-prof.txt` holds an H-marked rule; otherwise restored (covers the stripped copy and an unmarked merge result) | 9a3c3a12 |
| 3 | Documented in the recipe comment and docs/release.md: a daemon can write after the restore; no `--no-daemon` | 9a3c3a12 |
| 4 | Fixed: the EXIT trap is installed before the leftover handling and the backups, so an early exit stops the 5039 server and restores | 9a3c3a12 |
| 5 | Not mine: spec AC-1/D4 for the analyst and architect | - |
| 6 | Fixed: the failed-copy test asserts the temp copy is gone | 9a3c3a12 |

Red run (fix round 2, on the unchanged recipe):

- red: test_failed_generation_restores_profile, test_hup/term/int_to_group_restores_profile - startup profile after the merge differs (not restored)
- red: test_failed_generation_restores_deleted_startup_profile - startup profile missing after the merge deleted it
- red: test_failed_generation_removes_startup_profile_that_did_not_exist - generated startup profile left
- red: test_leftover_bak_restores_absent_startup_profile - startup profile exists after the restore
- red: test_sigkill_after_merge_before_remark_restores_original - base reported "out of date" and kept the merge result
- red: test_failed_final_remark_restores_profile - restore wording lacks both files
- red: test_failed_backup_copy_aborts_before_gradle - no kill-server on 5039 after a failed backup
- red: test_failed_startup_backup_leaves_nothing_behind - make exited 0 on base (no startup backup attempted)
- green on base: test_success_keeps_generated_startup_profile - guard for the success path

Harness: the fake Gradle gained FAKE_GRADLE_MERGE (rewrites or deletes both outputs like the plugin merge) and signalled tests now wait for the merge. Shell suite only, no device or Gradle generation run.

### Fix round 3 (triage-3 #1-#3, #5)

| # | Result | Commit |
|---|---|---|
| 1 | Fixed: leftover backups are out of date only when `baseline-prof.txt` has no method rule without H; a merge result carries both kinds, so it is restored | see git log |
| 2 | Fixed: the success path re-arms the EXIT trap without the restore before it deletes the backups | see git log |
| 3 | Fixed: `restore` returns non-zero on a failed mv or rm; the leftover branch exits 1 and keeps the original backup | see git log |
| 4 | Not mine: spec AC-1/D10 for the analyst and architect | - |
| 5 | Fixed: the stale test seeds and checks the startup backups; the absent-startup test checks exit and notice | see git log |

Red run (fix round 3, on the unchanged recipe):

- red: test_sigkill_after_merge_before_remark_restores_original - the fake merge now emits mixed H and non-H rules; base reported "out of date" and dropped the originals
- red: test_leftover_restore_failure_keeps_original_backup - make exited 0 (base ignored the failed mv)
- green on base: the added startup and exit/notice assertions in test_stale_bak_does_not_overwrite_newer_profile and test_leftover_bak_restores_absent_startup_profile (guard); #2 has no test (a signal between two rm calls cannot be aimed)

Shell suite only, no device or generation run.

Fix round 4: triage #1 fixed. The leftover backups are out of date only when `baseline-prof.txt` exists, is non-empty and has no non-H method rule; a missing or empty profile restores them.

- red: test_leftover_bak_is_restored_when_profile_is_missing - no restore notice (base took the missing file as stale and dropped the backups)
- red: test_leftover_bak_is_restored_when_profile_is_empty - backup dropped as out of date

**Blocked:** none.

## Readiness

Verdict: **READY**

Spec and plan read in /home/dev/orca/workspaces/Puber/baseline-generation-hardening at e03c3edd (plan uncommitted). Base b27d8e40.

### AC coverage

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

### Citations sampled (worktree at b27d8e40 content)

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

### Order and build

Task 1 (port, backup, harness) -> Task 2 (script, consumes `BASELINE_ADB_PORT` and the fakes) -> Task 3 (generator, independent) -> Task 4 (docs) -> Task 5 (device run, consumes 1-4). Each task ends on the fast gate; Task 3 adds the generator compile, which `check.fast` does not cover. No task leaves the build red.

### P0 rows

Every P0 shell row names its defect, its `red`/`guard` state and its backticked test. The device P0 rows M1 (AC-7 guard, AC-8) and M2 (AC-7 red, scratch mutation) run the automated `BaselineProfileGenerator.railNavigationProfile`; accepted as device-by-nature.

### Scope and questions

Plan stays inside Out of scope (`itest` recipe `Makefile:129-137` untouched; production source only through D8). Q1 and Q2 answered, neither blocks.

### Notes for the implementer and qa

1. Q2: `test_term_to_group_restores_profile` will pass on base (make re-raises TERM, base already restores on TERM); record it as `guard` in the Red run. Its exit-code assertion cannot see the recipe's own exit code.
2. `MainScreenComponent.kt:313-319`: a collapsed rail can keep an item focused during a handoff. M2 must show the new selector failing when LEFT is skipped after Series; if it passes there, D7 is not enough and the run returns to design, D8 does not apply.
3. If D8 fires, M2 is repeated with the D8 selector, since `hasDescendant(By.text(title))` does not test focus.
