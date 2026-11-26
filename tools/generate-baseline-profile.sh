#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
    echo "Set DEVICE to the adb serial of the emulator to generate on, for example DEVICE=host.docker.internal:5555." >&2
    exit 2
fi

# The same refusal as the make target, before anything runs: the cleanup below kills the server on this
# port, so a port that belongs to itest or to the default adb server must never reach it.
case "${BASELINE_ADB_PORT:-5039}" in
    "${ITEST_ADB_PORT:-5038}"|5037)
        echo "refused: BASELINE_ADB_PORT=${BASELINE_ADB_PORT:-5039} is the itest port (${ITEST_ADB_PORT:-5038}) or the default adb server (5037)" >&2
        exit 2
        ;;
esac

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GRADLE="${GRADLE:-$ROOT_DIR/tools/agentw}"

# Nothing is installed or started before the device is known to be a reachable emulator on API 34+;
# the make target repeats the refusals on its private adb server. A just-started emulator reports
# 'offline' for a few seconds, so the state is polled for up to 30 s (15 polls, 2 s apart).
preflight_device() {
    case "$DEVICE" in
        *:*) adb connect "$DEVICE" >/dev/null 2>&1 || true ;;
    esac

    local state="" poll
    for poll in $(seq 1 15); do
        state="$(adb devices | tr -d '\r' | awk -v d="$DEVICE" '$1 == d {print $2}')"
        [[ "$state" == "device" ]] && break
        if [[ -z "$state" ]]; then
            case "$DEVICE" in
                *:*) adb connect "$DEVICE" >/dev/null 2>&1 || true ;;
            esac
        fi
        sleep 2
    done
    if [[ "$state" != "device" ]]; then
        echo "refused: $DEVICE did not reach 'device' within 30 s (last state: '${state:-absent}'); start the emulator with 'ssh mac-emu start' and try again." >&2
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
        echo "refused: $DEVICE is API '${sdk}'; generation needs API 34 or newer, where the benchmark library resets compilation in place instead of reinstalling the app." >&2
        exit 2
    fi
}

logcat_pid=""
generation_started=""

# Runs on exit, Ctrl-C and TERM: the logcat process and the private adb server of the make target
# (the generation port, 5039) must not outlive the run. A preflight refusal started neither, so it
# kills no server.
cleanup() {
    if [[ -n "$logcat_pid" ]]; then
        kill "$logcat_pid" 2>/dev/null || true
        wait "$logcat_pid" 2>/dev/null || true
        logcat_pid=""
    fi
    if [[ -n "$generation_started" ]]; then
        ANDROID_ADB_SERVER_PORT="${BASELINE_ADB_PORT:-5039}" adb kill-server >/dev/null 2>&1 || true
    fi
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
    generation_started=1
    make -C "$ROOT_DIR" DEVICE="$DEVICE" GRADLE="$GRADLE" BASELINE_ADB_PORT="${BASELINE_ADB_PORT:-5039}" baseline-profile || status=$?
    cleanup

    return "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

preflight_device
run_generation
