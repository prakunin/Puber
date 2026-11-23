#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
    echo "Set DEVICE to the adb serial of the emulator to generate on, for example DEVICE=host.docker.internal:5555." >&2
    exit 2
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The build reads CLIENT_SECRET from the environment, .env or local.properties (envOrNull in app/build.gradle.kts).
if [[ -z "${PUBER_CLIENT_SECRET:-}" ]] \
    && ! grep -qs '^PUBER_CLIENT_SECRET=.' "$ROOT_DIR/.env" "$ROOT_DIR/local.properties"; then
    echo "PUBER_CLIENT_SECRET is not set. Put it, with PUBER_API_DOMAIN and TMDB_READ_ACCESS_TOKEN, from the main checkout's .env into this command's environment." >&2
    exit 2
fi

GRADLE="${GRADLE:-$ROOT_DIR/tools/agentw}"
TARGET_PACKAGE="${PUBER_BASELINE_TARGET_PACKAGE:-com.kino.puber.stage}"
MAIN_ACTIVITY="${TARGET_PACKAGE}/com.kino.puber.MainActivity"
AUTH_PROVIDER_URI="content://${TARGET_PACKAGE}.profile.auth/tokens"

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

# Status 0 only when the app holds both tokens. Prints nothing.
is_paired() {
    local output
    output="$(adb -s "$DEVICE" shell content query --uri "$AUTH_PROVIDER_URI" 2>/dev/null || true)"
    grep -q 'name=access_token, value_base64=.' <<<"$output" \
        && grep -q 'name=refresh_token, value_base64=.' <<<"$output"
}

ensure_paired() {
    if is_paired; then
        return
    fi

    echo "$TARGET_PACKAGE is not paired on $DEVICE; installing devNonMinifiedRelease, keeping app data."
    (cd "$ROOT_DIR" && "$GRADLE" :app:assembleDevNonMinifiedRelease)
    "$ROOT_DIR/tools/android-apk-install-preserve" install-preserve \
        --serial "$DEVICE" \
        --package "$TARGET_PACKAGE" \
        --apk "$ROOT_DIR/app/build/outputs/apk/dev/nonMinifiedRelease/app-dev-nonMinifiedRelease.apk"
    adb -s "$DEVICE" shell am force-stop "$TARGET_PACKAGE" >/dev/null 2>&1 || true
    adb -s "$DEVICE" shell am start -n "$MAIN_ACTIVITY" >/dev/null

    if [[ -t 0 ]]; then
        echo "Pair the app on $DEVICE (code at kino.watch/device), then press Enter."
        read -r
        if ! is_paired; then
            echo "$TARGET_PACKAGE on $DEVICE is still not paired." >&2
            exit 1
        fi
    else
        echo "Pair the app on $DEVICE (code at kino.watch/device), then run this script again."
        exit 3
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
    # The app is not debuggable and logs nothing in this build type except the auth events of
    # AuthEventLog and the generator; logcat goes outside git.
    local log_dir="${PUBER_BASELINE_LOG_DIR:-${TMPDIR:-/tmp}/puber-baseline-profile}"
    local log_file="$log_dir/$(date +%Y%m%d-%H%M%S)-logcat.txt"
    mkdir -p "$log_dir"
    chmod 700 "$log_dir"
    adb -s "$DEVICE" logcat -c >/dev/null 2>&1 || true
    adb -s "$DEVICE" logcat -v threadtime -s PuberAuth:V PuberGen:V Benchmark:V keystore2:I AndroidRuntime:E >"$log_file" 2>&1 &
    logcat_pid=$!
    echo "Auth and generator events go to $log_file"

    local status=0
    make -C "$ROOT_DIR" DEVICE="$DEVICE" GRADLE="$GRADLE" baseline-profile || status=$?
    cleanup

    if is_paired; then
        echo "Pairing on $DEVICE is intact."
    else
        echo "WARNING: $TARGET_PACKAGE on $DEVICE is not paired after the run; pair it again before the next run." >&2
    fi
    return "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

preflight_device
ensure_paired
run_generation
