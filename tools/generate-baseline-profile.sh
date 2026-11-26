#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
    echo "Set DEVICE to the adb serial of the emulator to generate on, for example DEVICE=host.docker.internal:5555." >&2
    exit 2
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GRADLE="${GRADLE:-$ROOT_DIR/tools/agentw}"

# Nothing is installed or started before the device is known to be a reachable emulator on API 34+;
# the make target repeats the refusals on its private adb server.
preflight_device() {
    case "$DEVICE" in
        *:*) adb connect "$DEVICE" >/dev/null 2>&1 || true ;;
    esac

    local state
    state="$(adb -s "$DEVICE" get-state 2>/dev/null | tr -d '\r' || true)"
    if [[ "$state" != "device" ]]; then
        echo "refused: $DEVICE is not reachable (adb get-state: '${state:-none}'). Start the emulator and try again." >&2
        exit 2
    fi

    local sdk qemu kqemu hw
    sdk="$(adb -s "$DEVICE" shell getprop ro.build.version.sdk | tr -d '\r')"
    qemu="$(adb -s "$DEVICE" shell getprop ro.boot.qemu | tr -d '\r')"
    kqemu="$(adb -s "$DEVICE" shell getprop ro.kernel.qemu | tr -d '\r')"
    hw="$(adb -s "$DEVICE" shell getprop ro.hardware | tr -d '\r')"
    if [[ "$qemu" != 1 && "$kqemu" != 1 && "$hw" != ranchu ]]; then
        echo "refused: $DEVICE is not an emulator (ro.boot.qemu=$qemu ro.kernel.qemu=$kqemu ro.hardware=$hw)." >&2
        exit 2
    fi
    if ! [[ "${sdk:-0}" =~ ^[0-9]+$ ]] || (( sdk < 34 )); then
        echo "refused: $DEVICE is API '${sdk}'; generation needs API 34 or newer, where the benchmark reset keeps app data." >&2
        exit 2
    fi
}

logcat_pid=""

# Runs on exit, Ctrl-C and TERM: the logcat process and the private adb server of the make target
# (ITEST_ADB_PORT, 5038) must not outlive the run.
cleanup() {
    if [[ -n "$logcat_pid" ]]; then
        kill "$logcat_pid" 2>/dev/null || true
        wait "$logcat_pid" 2>/dev/null || true
        logcat_pid=""
    fi
    ANDROID_ADB_SERVER_PORT="${ITEST_ADB_PORT:-5038}" adb kill-server >/dev/null 2>&1 || true
}

run_generation() {
    # The generator logs one PuberGen line per iteration; logcat goes outside git.
    local log_dir="${PUBER_BASELINE_LOG_DIR:-${TMPDIR:-/tmp}/puber-baseline-profile}"
    local log_file="$log_dir/$(date +%Y%m%d-%H%M%S)-logcat.txt"
    mkdir -p "$log_dir"
    chmod 700 "$log_dir"
    adb -s "$DEVICE" logcat -c >/dev/null 2>&1 || true
    adb -s "$DEVICE" logcat -v threadtime -s PuberGen:V Benchmark:V AndroidRuntime:E >"$log_file" 2>&1 &
    logcat_pid=$!
    echo "Generator events go to $log_file"

    local status=0
    make -C "$ROOT_DIR" DEVICE="$DEVICE" GRADLE="$GRADLE" baseline-profile || status=$?
    cleanup

    return "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

preflight_device
run_generation
