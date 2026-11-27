#!/usr/bin/env bash
# Tests for the make target itest: it must not report green when the instrumented run was incomplete.
# No device, no Gradle: adb and Gradle are fakes first on PATH, and make runs on a sandbox copy of the Makefile.
# Usage: bash tools/tests/test-itest-tooling.sh   (from the worktree root)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEVICE_SERIAL="emu:5555"
RESULTS_REL="app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation"
XML_NAME="TEST-fake device - 16.xml"

OUT=""
CODE=0

setup() {
  export T
  mkdir -p "$T/repo" "$T/bin"
  cp "$ROOT/Makefile" "$T/repo/Makefile"
  mkdir -p "$T/repo/app/build/outputs/apk"
  : >"$T/repo/app/build/outputs/apk/sentinel.apk"

  cat >"$T/bin/adb" <<'FAKE'
#!/usr/bin/env bash
echo "port=${ANDROID_ADB_SERVER_PORT:-} args=$*" >>"$T/adb.log"
if [[ "${1:-}" == devices ]]; then
  echo "List of devices attached"
  printf '%s\tdevice\n' "$DEVICE_SERIAL"
fi
exit 0
FAKE
  # FAKE_XML picks what a connected*AndroidTest task writes: incomplete, zero, passing, or nothing (default).
  cat >"$T/bin/gradle" <<'FAKE'
#!/usr/bin/env bash
case "$*" in
  *connected*)
    dir=app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation
    mkdir -p "$dir"
    case "${FAKE_XML:-}" in
      incomplete)
        cat >"$dir/TEST-fake device - 16.xml" <<'XML'
<?xml version='1.0' encoding='UTF-8' ?>
<testsuites tests="1" failures="1" errors="0" skipped="0" time="0.000">
  <testsuite name="android-test" tests="1" failures="1" errors="0" skipped="0" time="0.000">
    <testcase name="testRunFailed" classname="android-test" time="0.000">
      <failure>Test run failed to complete. No test results</failure>
    </testcase>
  </testsuite>
</testsuites>
XML
        ;;
      incomplete_swapped)
        cat >"$dir/TEST-fake device - 16.xml" <<'XML'
<?xml version='1.0' encoding='UTF-8' ?>
<testsuites tests="1" failures="1" errors="0" skipped="0" time="0.000">
  <testsuite name="android-test" tests="1" failures="1" errors="0" skipped="0" time="0.000">
    <testcase classname="android-test" name="testRunFailed" time="0.000">
      <failure>Test run failed to complete. No test results</failure>
    </testcase>
  </testsuite>
</testsuites>
XML
        ;;
      zero)
        cat >"$dir/TEST-fake device - 16.xml" <<'XML'
<?xml version='1.0' encoding='UTF-8' ?>
<testsuites tests="0" failures="0" errors="0" skipped="0" time="0.000">
  <testsuite name="com.kino.puber.FakeTest" tests="0" failures="0" errors="0" skipped="0" time="0.000">
  </testsuite>
</testsuites>
XML
        ;;
      passing)
        cat >"$dir/TEST-fake device - 16.xml" <<'XML'
<?xml version='1.0' encoding='UTF-8' ?>
<testsuites tests="2" failures="0" errors="0" skipped="0" time="0.100">
  <testsuite name="com.kino.puber.FakeTest" tests="2" failures="0" errors="0" skipped="0" time="0.100">
    <testcase name="first" classname="com.kino.puber.FakeTest" time="0.050" />
    <testcase name="second" classname="com.kino.puber.FakeTest" time="0.050" />
  </testsuite>
</testsuites>
XML
        ;;
    esac
    exit "${FAKE_GRADLE_EXIT:-0}"
    ;;
esac
exit 0
FAKE
  chmod +x "$T/bin/adb" "$T/bin/gradle"
  export DEVICE_SERIAL
  PATH="$T/bin:$PATH"
}

run_make() {
  OUT="$(make -C "$T/repo" DEVICE="$DEVICE_SERIAL" FLAVOR=instrumentation GRADLE="$T/bin/gradle" "$@" itest 2>&1)"
  CODE=$?
}

killed_itest_server() {
  grep -q '^port=5038 args=kill-server' "$T/adb.log" || { echo "  no kill-server on 5038: $(cat "$T/adb.log")"; return 1; }
}

expect_fail_with() {
  [[ $CODE -ne 0 ]] || { echo "  make exited 0: $OUT"; return 1; }
  grep -q -- "$1" <<<"$OUT" || { echo "  output lacks '$1': $OUT"; return 1; }
}

test_incomplete_run_fails() {
  FAKE_XML=incomplete run_make
  expect_fail_with testRunFailed || return 1
  killed_itest_server
}

test_incomplete_run_attribute_order_fails() {
  FAKE_XML=incomplete_swapped run_make
  expect_fail_with testRunFailed || return 1
  killed_itest_server
}

test_no_xml_fails() {
  run_make
  expect_fail_with 'no test results' || return 1
  killed_itest_server
}

test_zero_tests_fails() {
  FAKE_XML=zero run_make
  expect_fail_with 'no tests ran' || return 1
  killed_itest_server
}

test_complete_passing_run_succeeds() {
  FAKE_XML=passing run_make
  [[ $CODE -eq 0 ]] || { echo "  make exited $CODE: $OUT"; return 1; }
  killed_itest_server
}

test_stale_xml_not_counted() {
  mkdir -p "$T/repo/$RESULTS_REL"
  cat >"$T/repo/$RESULTS_REL/$XML_NAME" <<'XML'
<testsuites tests="1" failures="0"><testsuite name="old" tests="1"><testcase name="old" classname="old" /></testsuite></testsuites>
XML
  run_make
  expect_fail_with 'no test results' || return 1
  [[ ! -e "$T/repo/$RESULTS_REL/$XML_NAME" ]] || { echo "  the stale XML was not removed"; return 1; }
  [[ -f "$T/repo/app/build/outputs/apk/sentinel.apk" ]] || { echo "  the results cleanup removed the APK directory"; return 1; }
}

test_gradle_failure_kept() {
  FAKE_XML=passing FAKE_GRADLE_EXIT=1 run_make
  [[ $CODE -ne 0 ]] || { echo "  make exited 0: $OUT"; return 1; }
  killed_itest_server
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
