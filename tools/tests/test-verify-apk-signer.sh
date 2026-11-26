#!/usr/bin/env bash
# Tests for tools/verify-apk-signer.sh.
# Usage: bash tools/tests/test-verify-apk-signer.sh <path-to-published-puber-v1.12.7.apk>
set -uo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "usage: $0 <published-release.apk>" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/tools/verify-apk-signer.sh"
PUBLISHED="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
PIN=7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)"
if [[ -z "$APKSIGNER" ]]; then
  echo "apksigner not found under $ANDROID_HOME/build-tools (set ANDROID_HOME)" >&2
  exit 2
fi
KEYTOOL="${KEYTOOL:-$(command -v keytool || true)}"
if [[ -z "$KEYTOOL" && -x "${JAVA_HOME:-}/bin/keytool" ]]; then
  KEYTOOL="$JAVA_HOME/bin/keytool"
fi
if [[ -z "$KEYTOOL" ]]; then
  echo "keytool not found (put it on PATH, set JAVA_HOME or KEYTOOL)" >&2
  exit 2
fi
ZEROS="$(printf '0%.0s' $(seq 64))"
ONES="$(printf '1%.0s' $(seq 64))"

OUT=""
CODE=0
run() {
  OUT="$("$SCRIPT" "$@" 2>&1)"
  CODE=$?
}

# Fake SDK: fake_sdk <dir> <version> <exit> <text...>; the stub prints each text as a line and exits.
fake_sdk() {
  local dir="$1" ver="$2" code="$3"
  shift 3
  mkdir -p "$dir/build-tools/$ver"
  printf '#!/usr/bin/env bash\nprintf "%%s\\n" %s\nexit %s\n' "$(printf '%q ' "$@")" "$code" > "$dir/build-tools/$ver/apksigner"
  chmod +x "$dir/build-tools/$ver/apksigner"
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

test_published_release_apk_passes() {
  mkdir "$T/d" && cp "$PUBLISHED" "$T/d/"
  run "$T/d" "$PIN"
  expect 0 "signer OK: $PIN"
}

test_debug_signed_apk_fails_with_both_digests() {
  "$APKSIGNER" sign --ks "$ROOT/app/debug.jks" --ks-pass pass:android --ks-key-alias androiddebugkey \
    --out "$T/debug.apk" "$PUBLISHED" || return 1
  local want
  want="$("$KEYTOOL" -list -v -keystore "$ROOT/app/debug.jks" -storepass android -alias androiddebugkey 2>/dev/null \
    | grep -i 'SHA256:' | head -n 1 | sed 's/.*SHA256:[[:space:]]*//' | tr -d ':' | tr 'A-F' 'a-f')"
  [[ ${#want} -eq 64 ]] || { echo "  could not read debug digest"; return 1; }
  mkdir "$T/d" && cp "$T/debug.apk" "$T/d/"
  run "$T/d" "$PIN"
  expect 1 "$PIN" "$want"
}

test_empty_dir_fails() {
  mkdir "$T/d"
  run "$T/d" "$PIN"
  expect 2 'no APK'
}

test_two_apks_fail() {
  mkdir "$T/d" && cp "$PUBLISHED" "$T/d/a.apk" && cp "$PUBLISHED" "$T/d/b.apk"
  run "$T/d" "$PIN"
  expect 2 'more than one APK'
}

test_unsigned_apk_fails() {
  python3 -I -c '
import sys, zipfile
src, dst = sys.argv[1:3]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zo:
    for item in zi.infolist():
        if not item.filename.startswith("META-INF/"):
            zo.writestr(item, zi.read(item.filename))
' "$PUBLISHED" "$T/unsigned.apk" || return 1
  mkdir "$T/d" && cp "$T/unsigned.apk" "$T/d/"
  run "$T/d" "$PIN"
  expect 1 'verify failed'
}

test_script_syntax() {
  bash -n "$SCRIPT" && [[ -x "$SCRIPT" ]]
}

test_apk_file_argument_accepted() {
  run "$PUBLISHED" "$PIN"
  expect 0
}

test_pin_is_normalised() {
  local pin
  pin="$(tr 'a-f' 'A-F' <<<"$PIN" | sed 's/../&:/g; s/:$//')"
  run "$PUBLISHED" "$pin"
  expect 0
}

test_unparsed_output_fails_closed() {
  fake_sdk "$T/sdk" 36.0.0 0 Verifies
  ANDROID_SDK_ROOT= ANDROID_HOME="$T/sdk" run "$PUBLISHED" "$PIN"
  expect 1 'no signer digest'
}

test_missing_path_fails() {
  run "$T/does-not-exist" "$PIN"
  expect 2 'not a file or directory'
}

test_rotated_signer_line_counts() {
  fake_sdk "$T/sdk" 36.0.0 0 "Signer #1 certificate SHA-256 digest: $PIN" "Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: $ONES"
  ANDROID_SDK_ROOT= ANDROID_HOME="$T/sdk" run "$PUBLISHED" "$PIN"
  expect 1 "expected: $PIN" "$ONES"
}

test_bad_pin_rejected() {
  run "$PUBLISHED" "${PIN:1}"
  expect 2 || return 1
  run "$PUBLISHED"
  expect 2
}

test_missing_apksigner_fails() {
  mkdir "$T/empty-sdk"
  ANDROID_SDK_ROOT= ANDROID_HOME="$T/empty-sdk" run "$PUBLISHED" "$PIN"
  expect 2 'apksigner'
}

test_newest_build_tools_chosen() {
  fake_sdk "$T/sdk" 9.0.0 0 "Signer #1 certificate SHA-256 digest: $ZEROS"
  fake_sdk "$T/sdk" 10.0.0 0 "Signer #1 certificate SHA-256 digest: $PIN"
  ANDROID_SDK_ROOT= ANDROID_HOME="$T/sdk" run "$PUBLISHED" "$PIN"
  expect 0
}

test_mixed_signers_fail() {
  fake_sdk "$T/sdk" 36.0.0 0 "Signer #1 certificate SHA-256 digest: $PIN" "Signer #2 certificate SHA-256 digest: $ONES"
  ANDROID_SDK_ROOT= ANDROID_HOME="$T/sdk" run "$PUBLISHED" "$PIN"
  expect 1 "expected: $PIN" "$ONES"
}

failed=0
for name in $(declare -F | awk '{print $3}' | grep '^test_'); do
  T="$(mktemp -d)"
  if result="$("$name")"; then
    echo "PASS $name"
  else
    failed=1
    echo "FAIL $name"
    [[ -n "$result" ]] && echo "$result"
  fi
  rm -rf "$T"
done
exit "$failed"
