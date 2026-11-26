#!/usr/bin/env bash
# Tests for the baseline profile generation route: the make target baseline-profile and
# tools/generate-baseline-profile.sh. No device, no network, no Gradle: adb, sleep and Gradle are fakes
# first on PATH, and make runs in a sandbox copy of the repo so the checked-in profile is never touched.
# Usage: bash tools/tests/test-baseline-profile-tooling.sh   (from the worktree root)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEVICE_SERIAL="emu:5555"
PROFILE_REL="app/src/main/generated/baselineProfiles/baseline-prof.txt"
STARTUP_REL="app/src/main/generated/baselineProfiles/startup-prof.txt"

OUT=""
CODE=0

# Fresh sandbox in $T: repo copy, fakes, profile. Runs inside each test's subshell.
setup() {
  export T
  mkdir -p "$T/repo/app/src/main/generated/baselineProfiles" "$T/repo/tools" "$T/bin" "$T/logs"
  cp "$ROOT/Makefile" "$T/repo/Makefile"
  cp "$ROOT/tools/generate-baseline-profile.sh" "$T/repo/tools/generate-baseline-profile.sh"
  chmod +x "$T/repo/tools/generate-baseline-profile.sh"
  printf '%s\n' 'HSPLcom/x/A;->m()V' 'HSPLcom/x/B;->n()V' 'SPLcom/x/C;->o()V' 'Lcom/x/A;' >"$T/repo/$PROFILE_REL"
  cp "$T/repo/$PROFILE_REL" "$T/orig"
  printf '%s\n' 'Lcom/x/Startup;->s()V' >"$T/repo/$STARTUP_REL"
  cp "$T/repo/$STARTUP_REL" "$T/startup.orig"
  sed -E '/->/ s/^H([SP]*L)/\1/' "$T/orig" >"$T/orig.stripped"

  cat >"$T/bin/adb" <<'FAKE'
#!/usr/bin/env bash
echo "port=${ANDROID_ADB_SERVER_PORT:-} args=$*" >>"$T/adb.log"
if [[ "${1:-}" == -s ]]; then shift 2; fi
next_state() {
  local n=0 line
  [[ -f "$T/states.idx" ]] && n="$(<"$T/states.idx")"
  n=$((n + 1))
  echo "$n" >"$T/states.idx"
  if [[ -f "$T/states" ]]; then
    line="$(sed -n "${n}p" "$T/states")"
    [[ -n "$line" ]] || line="$(tail -n 1 "$T/states")"
    echo "$line"
  else
    echo device
  fi
}
case "${1:-}" in
  devices)
    echo "List of devices attached"
    state="$(next_state)"
    [[ "$state" == absent ]] || printf '%s\t%s\n' "$DEVICE_SERIAL" "$state"
    [[ ! -f "$T/extra" ]] || cat "$T/extra"
    ;;
  get-state)
    state="$(next_state)"
    if [[ "$state" == device ]]; then echo device; else echo "error: device $state" >&2; exit 1; fi
    ;;
  shell)
    if [[ "${2:-}" == getprop ]]; then
      key="${3:-}"
      value="$(grep -m1 "^$key=" "$T/props" 2>/dev/null | cut -d= -f2-)"
      if [[ -z "$value" ]]; then
        case "$key" in
          ro.build.version.sdk) value=36 ;;
          ro.boot.qemu) value=1 ;;
        esac
      fi
      printf '%s\r\n' "$value"
    fi
    ;;
esac
exit 0
FAKE
  cat >"$T/bin/sleep" <<'FAKE'
#!/usr/bin/env bash
echo "sleep $*" >>"$T/sleep.log"
FAKE
  cat >"$T/bin/gradle" <<'FAKE'
#!/usr/bin/env bash
echo "${ANDROID_ADB_SERVER_PORT:-}" >"$T/gradle.port"
cp app/src/main/generated/baselineProfiles/baseline-prof.txt "$T/gradle.profile"
# FAKE_GRADLE_MERGE=1 acts like the plugin's merge task: it replaces both outputs with unmarked rules
# (or deletes the startup profile with FAKE_GRADLE_MERGE=delete-startup).
if [[ -n "${FAKE_GRADLE_MERGE:-}" ]]; then
  printf '%s\n' 'SPLcom/x/Gen;->g()V' 'HSPLcom/x/GenHot;->h()V' 'Lcom/x/Gen;' >app/src/main/generated/baselineProfiles/baseline-prof.txt
  if [[ "$FAKE_GRADLE_MERGE" == delete-startup ]]; then
    rm -f app/src/main/generated/baselineProfiles/startup-prof.txt
  else
    printf '%s\n' 'Lcom/x/GenStartup;->s()V' >app/src/main/generated/baselineProfiles/startup-prof.txt
  fi
fi
touch "$T/gradle.merged"
if [[ "${FAKE_GRADLE_BLOCK:-}" == 1 ]]; then
  while :; do /bin/sleep 0.1; done
fi
exit "${FAKE_GRADLE_EXIT:-0}"
FAKE
  # cp fails on a .bak destination with FAKE_CP_FAIL=1 after writing half of it; sed fails on its
  # FAKE_SED_FAIL_ON-th in-place call. Both hand every other call to the real tool.
  local real_cp real_sed
  real_cp="$(command -v cp)"
  real_sed="$(command -v sed)"
  cat >"$T/bin/cp" <<FAKE
#!/usr/bin/env bash
if [[ ( "\${FAKE_CP_FAIL:-}" == 1 || ( "\${FAKE_CP_FAIL:-}" == startup && "\${!#}" == *startup-prof* ) ) && "\${!#}" == *.bak ]]; then
  head -c 10 "\$1" >"\${!#}"
  exit 1
fi
exec $real_cp "\$@"
FAKE
  # mv fails on a destination that is not a backup with FAKE_MV_FAIL=1 (a restore that cannot complete).
  local real_mv
  real_mv="$(command -v mv)"
  cat >"$T/bin/mv" <<FAKE
#!/usr/bin/env bash
if [[ "\${FAKE_MV_FAIL:-}" == 1 && "\${!#}" != *.bak ]]; then
  exit 1
fi
exec $real_mv "\$@"
FAKE
  cat >"$T/bin/sed" <<FAKE
#!/usr/bin/env bash
if [[ -n "\${FAKE_SED_FAIL_ON:-}" && " \$* " == *" -i "* ]]; then
  n=0
  [[ -f "\$T/sed.count" ]] && n="\$(<"\$T/sed.count")"
  n=\$((n + 1))
  echo "\$n" >"\$T/sed.count"
  [[ "\$n" -eq "\$FAKE_SED_FAIL_ON" ]] && exit 1
fi
exec $real_sed "\$@"
FAKE
  chmod +x "$T/bin/adb" "$T/bin/sleep" "$T/bin/gradle" "$T/bin/cp" "$T/bin/sed" "$T/bin/mv"
  export DEVICE_SERIAL
  PATH="$T/bin:$PATH"
  export PUBER_BASELINE_LOG_DIR="$T/logs"
}

# run_make [make-args...]: make baseline-profile in the sandbox.
run_make() {
  OUT="$(make -C "$T/repo" DEVICE="$DEVICE_SERIAL" GRADLE="$T/bin/gradle" "$@" baseline-profile 2>&1)"
  CODE=$?
}

# run_script: the generation script in the sandbox.
run_script() {
  OUT="$(DEVICE="$DEVICE_SERIAL" GRADLE="$T/bin/gradle" "$T/repo/tools/generate-baseline-profile.sh" 2>&1)"
  CODE=$?
}

# run_signalled <SIG> [make-args...]: make baseline-profile with a blocked Gradle as its own process
# group; the signal goes to the whole group, as a terminal hangup or Ctrl-C would.
run_signalled() {
  local sig="$1"
  shift
  rm -f "$T/gradle.merged"
  set -m
  FAKE_GRADLE_MERGE="${FAKE_GRADLE_MERGE:-1}" FAKE_GRADLE_BLOCK=1 make -C "$T/repo" DEVICE="$DEVICE_SERIAL" GRADLE="$T/bin/gradle" "$@" baseline-profile >"$T/out" 2>&1 &
  local pid=$!
  local i
  for i in $(seq 1 100); do
    [[ -f "$T/gradle.merged" ]] && break
    /bin/sleep 0.1
  done
  if [[ ! -f "$T/gradle.merged" ]]; then
    kill -KILL -- "-$pid" 2>/dev/null
    set +m
    OUT="gradle never started: $(cat "$T/out")"
    CODE=99
    return
  fi
  kill "-$sig" -- "-$pid"
  wait "$pid" 2>/dev/null
  CODE=$?
  set +m
  OUT="$(cat "$T/out")"
}

expect() { # expect <code> [grep-pattern...]
  local want="$1"
  shift
  [[ "$CODE" -eq "$want" ]] || { echo "  exit $CODE, wanted $want; output: $OUT"; return 1; }
  local p
  for p in "$@"; do
    grep -qE -- "$p" <<<"$OUT" || { echo "  output lacks /$p/: $OUT"; return 1; }
  done
}

same_file() { # same_file <a> <b> <what>
  cmp -s "$1" "$2" || { echo "  $3 differs: $(cat "$1")"; return 1; }
}

no_bak() {
  local leftover
  leftover="$(ls "$T/repo/app/src/main/generated/baselineProfiles" | grep -E '\.bak$' || true)"
  [[ -z "$leftover" ]] || { echo "  backup files left behind: $leftover"; return 1; }
}

both_restored() { # both_restored <what>: the profile and the startup profile are the originals
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "profile $1" || return 1
  same_file "$T/repo/$STARTUP_REL" "$T/startup.orig" "startup profile $1" || return 1
  no_bak
}

test_leftover_bak_is_restored_before_backup() {
  cp "$T/orig" "$T/repo/$PROFILE_REL.bak"
  cp "$T/orig.stripped" "$T/repo/$PROFILE_REL"
  FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  grep -qE 'restored .*baseline-prof\.txt from .*\.bak left by an interrupted run; a generation that finished just before that interruption is discarded' <<<"$OUT" \
    || { echo "  no restore notice: $OUT"; return 1; }
  same_file "$T/gradle.profile" "$T/orig.stripped" "profile gradle saw" || return 1
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "final profile" || return 1
  no_bak
}

test_stale_bak_does_not_overwrite_newer_profile() {
  cp "$T/orig" "$T/repo/$PROFILE_REL.bak"
  printf '%s\n' 'HSPLcom/x/New;->m()V' 'Lcom/x/New;' >"$T/repo/$PROFILE_REL"
  printf '%s\n' 'SPLcom/x/New;->m()V' 'Lcom/x/New;' >"$T/new.stripped"
  local d="$T/repo/app/src/main/generated/baselineProfiles"
  cp "$T/startup.orig" "$d/startup-prof.txt.bak"
  printf '%s\n' 'Lcom/x/NewStartup;->s()V' >"$d/startup-prof.txt"
  cp "$d/startup-prof.txt" "$T/startup.new"
  FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  grep -qE 'baseline-prof\.txt\.bak is out of date' <<<"$OUT" || { echo "  no out-of-date notice: $OUT"; return 1; }
  ! grep -q 'restored ' <<<"$OUT" || { echo "  stale .bak was restored: $OUT"; return 1; }
  same_file "$T/gradle.profile" "$T/new.stripped" "profile gradle saw" || return 1
  [[ "$(grep -c 'New' "$T/repo/$PROFILE_REL")" -eq 2 ]] || { echo "  newer profile lost: $(cat "$T/repo/$PROFILE_REL")"; return 1; }
  same_file "$d/startup-prof.txt" "$T/startup.new" "newer startup profile" || return 1
  no_bak
}

test_leftover_bak_is_restored_when_profile_is_missing() {
  cp "$T/orig" "$T/repo/$PROFILE_REL.bak"
  rm -f "$T/repo/$PROFILE_REL"
  FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  grep -qE 'restored .*baseline-prof\.txt from' <<<"$OUT" || { echo "  no restore notice: $OUT"; return 1; }
  ! grep -q 'out of date' <<<"$OUT" || { echo "  backup dropped as out of date: $OUT"; return 1; }
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "final profile" || return 1
  no_bak
}

test_leftover_bak_is_restored_when_profile_is_empty() {
  cp "$T/orig" "$T/repo/$PROFILE_REL.bak"
  : >"$T/repo/$PROFILE_REL"
  FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  ! grep -q 'out of date' <<<"$OUT" || { echo "  backup dropped as out of date: $OUT"; return 1; }
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "final profile" || return 1
  no_bak
}

test_failed_backup_copy_aborts_before_gradle() {
  FAKE_CP_FAIL=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "profile after failed backup" || return 1
  no_bak
  [[ ! -e "$T/repo/$PROFILE_REL.new.bak" ]] || { echo "  temp copy left behind"; return 1; }
  # The private adb server is stopped although the run never reached Gradle.
  grep -q '^port=5039 args=kill-server' "$T/adb.log" || { echo "  no kill-server on 5039 after a failed backup"; return 1; }
  # A second run must not restore a half-written copy over the profile.
  run_make
  expect 0 || return 1
  [[ "$(grep -c -- '->' "$T/repo/$PROFILE_REL")" -eq 3 ]] || { echo "  rule count changed"; return 1; }
}

test_failed_startup_backup_leaves_nothing_behind() {
  FAKE_CP_FAIL=startup run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
  both_restored "after failed startup backup"
}

test_failed_final_remark_restores_profile() {
  FAKE_GRADLE_MERGE=1 FAKE_SED_FAIL_ON=2 FAKE_GRADLE_EXIT=0 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  grep -q 'restoring the original baseline-prof.txt and startup-prof.txt' <<<"$OUT" || { echo "  no restore wording: $OUT"; return 1; }
  both_restored "after failed re-mark"
}

test_failed_generation_restores_profile() {
  FAKE_GRADLE_MERGE=1 FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  both_restored "after failure"
}

test_failed_generation_restores_deleted_startup_profile() {
  FAKE_GRADLE_MERGE=delete-startup FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  both_restored "after the merge deleted it"
}

test_failed_generation_removes_startup_profile_that_did_not_exist() {
  rm -f "$T/repo/$STARTUP_REL"
  FAKE_GRADLE_MERGE=1 FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  [[ ! -e "$T/repo/$STARTUP_REL" ]] || { echo "  generated startup profile left: $(cat "$T/repo/$STARTUP_REL")"; return 1; }
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "profile" || return 1
  no_bak
}

test_success_keeps_generated_startup_profile() {
  FAKE_GRADLE_MERGE=1 run_make
  expect 0 || return 1
  grep -q 'GenStartup' "$T/repo/$STARTUP_REL" || { echo "  generated startup profile lost: $(cat "$T/repo/$STARTUP_REL")"; return 1; }
  grep -q '^HSPLcom/x/Gen;' "$T/repo/$PROFILE_REL" || { echo "  generated rule not hot: $(cat "$T/repo/$PROFILE_REL")"; return 1; }
  no_bak
}

test_sigkill_after_merge_before_remark_restores_original() {
  # State a SIGKILL leaves between the merge and the re-mark: the .bak files hold the originals, the
  # outputs are the unmarked merge result.
  local d="$T/repo/app/src/main/generated/baselineProfiles"
  cp "$T/orig" "$d/baseline-prof.txt.bak"
  cp "$T/startup.orig" "$d/startup-prof.txt.bak"
  printf '%s\n' 'SPLcom/x/Gen;->g()V' 'HSPLcom/x/GenHot;->h()V' 'Lcom/x/Gen;' >"$d/baseline-prof.txt"
  printf '%s\n' 'Lcom/x/GenStartup;->s()V' >"$d/startup-prof.txt"
  FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  grep -q 'restored .*baseline-prof.txt from' <<<"$OUT" || { echo "  no restore notice: $OUT"; return 1; }
  ! grep -q 'out of date' <<<"$OUT" || { echo "  original treated as out of date: $OUT"; return 1; }
  both_restored "after a kill between merge and re-mark"
}

test_leftover_bak_restores_absent_startup_profile() {
  local d="$T/repo/app/src/main/generated/baselineProfiles"
  cp "$T/orig" "$d/baseline-prof.txt.bak"
  : >"$d/startup-prof.txt.none.bak"
  printf '%s\n' 'SPLcom/x/Gen;->g()V' >"$d/baseline-prof.txt"
  FAKE_GRADLE_EXIT=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  grep -q 'restored .*baseline-prof.txt from' <<<"$OUT" || { echo "  no restore notice: $OUT"; return 1; }
  [[ ! -e "$d/startup-prof.txt" ]] || { echo "  startup profile exists: $(cat "$d/startup-prof.txt")"; return 1; }
  same_file "$d/baseline-prof.txt" "$T/orig" "profile" || return 1
  no_bak
}

test_leftover_restore_failure_keeps_original_backup() {
  cp "$T/orig" "$T/repo/$PROFILE_REL.bak"
  cp "$T/orig.stripped" "$T/repo/$PROFILE_REL"
  FAKE_MV_FAIL=1 run_make
  [[ "$CODE" -ne 0 ]] || { echo "  make exited 0"; return 1; }
  [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
  same_file "$T/repo/$PROFILE_REL.bak" "$T/orig" "original backup" || return 1
}

test_hup_to_group_restores_profile() {
  run_signalled HUP
  [[ "$CODE" -eq 129 ]] || { echo "  exit $CODE, wanted 129: $OUT"; return 1; }
  both_restored "after HUP"
}

test_term_to_group_restores_profile() {
  run_signalled TERM
  [[ "$CODE" -eq 143 ]] || { echo "  exit $CODE, wanted 143: $OUT"; return 1; }
  both_restored "after TERM"
}

test_int_to_group_restores_profile() {
  run_signalled INT
  [[ "$CODE" -eq 130 ]] || { echo "  exit $CODE, wanted 130: $OUT"; return 1; }
  both_restored "after INT"
}

test_successful_generation_marks_hot_and_removes_bak() {
  FAKE_GRADLE_EXIT=0 run_make
  expect 0 || return 1
  local cold
  cold="$(grep -- '->' "$T/repo/$PROFILE_REL" | grep -v '^H' || true)"
  [[ -z "$cold" ]] || { echo "  rules not hot: $cold"; return 1; }
  [[ "$(grep -c -- '->' "$T/repo/$PROFILE_REL")" -eq 3 ]] || { echo "  rule count changed"; return 1; }
  no_bak
}

test_bak_in_generated_dir_is_git_ignored() {
  git -C "$ROOT" check-ignore -q "${PROFILE_REL}.bak" || { echo "  .bak is not ignored"; return 1; }
  if git -C "$ROOT" check-ignore -q "$PROFILE_REL"; then echo "  the profile itself is ignored"; return 1; fi
}

test_make_target_uses_only_baseline_port() {
  local port
  for port in 5039 5041; do
    rm -f "$T/adb.log" "$T/gradle.port"
    if [[ "$port" == 5039 ]]; then run_make; else run_make BASELINE_ADB_PORT="$port"; fi
    expect 0 || return 1
    [[ "$(<"$T/gradle.port")" == "$port" ]] || { echo "  gradle saw port $(<"$T/gradle.port"), wanted $port"; return 1; }
    grep -q "^port=$port " "$T/adb.log" || { echo "  adb never used port $port"; return 1; }
    ! grep -v "^port=$port " "$T/adb.log" || { echo "  adb calls on another port (above)"; return 1; }
    # Refusal paths: unsupported API, then a real device.
    printf '%s\n' 'ro.build.version.sdk=33' >"$T/props"
    rm -f "$T/adb.log"
    run_make BASELINE_ADB_PORT="$port"
    expect 2 'refused' || return 1
    ! grep -v "^port=$port " "$T/adb.log" || { echo "  sdk refusal used another port (above)"; return 1; }
    printf '%s\n' 'ro.boot.qemu=0' 'ro.hardware=x' >"$T/props"
    rm -f "$T/adb.log"
    run_make BASELINE_ADB_PORT="$port"
    expect 2 'not an emulator' || return 1
    ! grep -v "^port=$port " "$T/adb.log" || { echo "  emulator refusal used another port (above)"; return 1; }
    rm -f "$T/props"
  done
}

test_make_refuses_when_device_never_appears() {
  printf '%s\n' offline >"$T/states"
  run_make
  expect 2 'did not show up on the private adb server within 60 s' || return 1
  [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
  grep -q '^port=5039 args=kill-server' "$T/adb.log" || { echo "  no kill-server on 5039"; return 1; }
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "profile" || return 1
  no_bak
}

test_make_refuses_extra_serial_on_private_server() {
  printf 'other:5555\tdevice\n' >"$T/extra"
  run_make
  expect 2 'refused: the private adb server lists other:5555 besides emu:5555' || return 1
  [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
  grep -q '^port=5039 args=kill-server' "$T/adb.log" || { echo "  no kill-server on 5039"; return 1; }
  same_file "$T/repo/$PROFILE_REL" "$T/orig" "profile" || return 1
  no_bak
}

test_script_refuses_itest_port_before_any_server_is_touched() {
  local port
  for port in 5038 5037; do
    rm -f "$T/adb.log"
    BASELINE_ADB_PORT="$port" run_script
    expect 2 "refused: BASELINE_ADB_PORT=$port is the itest port \(5038\) or the default adb server \(5037\)" || return 1
    [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
    ! grep -q kill-server "$T/adb.log" 2>/dev/null || { echo "  kill-server after the port refusal: $(cat "$T/adb.log")"; return 1; }
  done
}

test_baseline_port_equal_to_itest_or_default_refused() {
  local port
  for port in 5038 5037; do
    rm -f "$T/adb.log"
    run_make BASELINE_ADB_PORT="$port"
    [[ "$CODE" -ne 0 ]] || { echo "  port $port accepted"; return 1; }
    grep -q "refused: BASELINE_ADB_PORT=$port is the itest port (5038) or the default adb server (5037)" <<<"$OUT" \
      || { echo "  no port refusal text: $OUT"; return 1; }
    [[ ! -s "$T/adb.log" ]] || { echo "  adb was called: $(cat "$T/adb.log")"; return 1; }
    same_file "$T/repo/$PROFILE_REL" "$T/orig" "profile" || return 1
  done
}

test_preflight_waits_for_offline_device() {
  printf '%s\n' offline offline device >"$T/states"
  run_script
  expect 0 || return 1
  [[ -f "$T/gradle.port" ]] || { echo "  gradle was never called: $OUT"; return 1; }
  [[ "$(grep -c '^sleep' "$T/sleep.log")" -ge 2 ]] || { echo "  fewer than two sleeps: $(cat "$T/sleep.log")"; return 1; }
}

test_preflight_times_out_naming_last_state() {
  printf '%s\n' offline >"$T/states"
  run_script
  expect 2 "did not reach 'device' within 30 s \(last state: 'offline'\)" 'ssh mac-emu start' || return 1
  [[ ! -f "$T/gradle.port" ]] || { echo "  gradle was called"; return 1; }
  local total
  total="$(awk '/^sleep/ {s += $2} END {print s + 0}' "$T/sleep.log")"
  [[ "$total" -ge 28 && "$total" -le 34 ]] || { echo "  total sleep $total s, wanted about 30"; return 1; }
}

test_preflight_reconnects_missing_device() {
  printf '%s\n' absent device >"$T/states"
  run_script
  expect 0 || return 1
  # Preflight talks to the default server (no port); the make target's connect is on 5039.
  [[ "$(grep -c '^port= args=connect ' "$T/adb.log")" -gt 1 ]] || { echo "  preflight connected once only: $(grep connect "$T/adb.log")"; return 1; }
  [[ -f "$T/gradle.port" ]] || { echo "  gradle was never called"; return 1; }
}

test_preflight_refusal_kills_no_server() {
  printf '%s\n' 'ro.build.version.sdk=33' >"$T/props"
  run_script
  expect 2 'refused' || return 1
  ! grep -q kill-server "$T/adb.log" || { echo "  kill-server after sdk refusal: $(grep kill-server "$T/adb.log")"; return 1; }
  printf '%s\n' 'ro.boot.qemu=0' 'ro.hardware=x' >"$T/props"
  rm -f "$T/adb.log"
  run_script
  expect 2 'not an emulator' || return 1
  ! grep -q kill-server "$T/adb.log" || { echo "  kill-server after emulator refusal: $(grep kill-server "$T/adb.log")"; return 1; }
}

test_generation_kills_only_baseline_server() {
  run_script
  expect 0 || return 1
  grep -q '^port=5039 args=kill-server' "$T/adb.log" || { echo "  no kill-server on 5039: $(cat "$T/adb.log")"; return 1; }
  ! grep -q '^port=5038 ' "$T/adb.log" || { echo "  a call on 5038: $(grep '^port=5038 ' "$T/adb.log")"; return 1; }
}

test_api34_refusal_reason_text() {
  printf '%s\n' 'ro.build.version.sdk=33' >"$T/props"
  run_script
  expect 2 'refused' 'resets compilation in place instead of reinstalling the app' || return 1
  ! grep -q 'keeps app data' <<<"$OUT" || { echo "  script keeps the old reason: $OUT"; return 1; }
  run_make
  expect 2 'refused' 'resets compilation in place instead of reinstalling the app' || return 1
  ! grep -q 'keeps app data' <<<"$OUT" || { echo "  make keeps the old reason: $OUT"; return 1; }
}

failed=0
for name in $(declare -F | awk '{print $3}' | grep '^test_'); do
  T="$(mktemp -d)"
  if result="$(setup; "$name")"; then
    echo "PASS $name"
  else
    failed=1
    echo "FAIL $name"
    [[ -n "$result" ]] && echo "$result"
  fi
  rm -rf "$T"
done
exit "$failed"
