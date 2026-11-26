#!/usr/bin/env bash
# Fail unless the release APK is signed by exactly one certificate whose SHA-256 equals the pin.
# Usage: verify-apk-signer.sh <apk-or-dir-with-one-apk> <sha256-pin>
# Exit: 0 match, 1 verification or signer mismatch, 2 usage or environment error.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <apk-or-dir> <sha256-pin>" >&2
  exit 2
fi

target="$1"
pin="$(tr -d ':' <<<"$2" | tr 'A-F' 'a-f')"
if [[ ! "$pin" =~ ^[0-9a-f]{64}$ ]]; then
  echo "invalid pin: expected 64 hex characters, got '$2'" >&2
  exit 2
fi

if [[ -f "$target" ]]; then
  apk="$target"
elif [[ -d "$target" ]]; then
  mapfile -t apks < <(find "$target" -maxdepth 1 -type f -name '*.apk' | sort)
  if (( ${#apks[@]} == 0 )); then
    echo "no APK in $target" >&2
    exit 2
  fi
  if (( ${#apks[@]} > 1 )); then
    echo "more than one APK in $target:" >&2
    printf '  %s\n' "${apks[@]}" >&2
    exit 2
  fi
  apk="${apks[0]}"
else
  echo "not a file or directory: $target" >&2
  exit 2
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
apksigner=""
if [[ -n "$sdk" ]]; then
  apksigner="$(find "$sdk"/build-tools -mindepth 2 -maxdepth 2 -name apksigner -type f -perm -u+x 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$apksigner" ]]; then
  echo "apksigner not found under '$sdk/build-tools' (set ANDROID_HOME)" >&2
  exit 2
fi

if ! output="$("$apksigner" verify --print-certs "$apk" 2>&1)"; then
  echo "verify failed for $apk:" >&2
  echo "$output" >&2
  exit 1
fi

digests="$(grep -oiE 'certificate SHA-256 digest:[[:space:]]*[0-9a-fA-F:]+' <<<"$output" \
  | sed -E 's/.*digest:[[:space:]]*//' | tr -d ':' | tr 'A-F' 'a-f' | sort -u || true)"
if [[ -z "$digests" ]]; then
  echo "no signer digest in apksigner output for $apk:" >&2
  echo "$output" >&2
  exit 1
fi

if [[ "$digests" != "$pin" ]]; then
  echo "signer mismatch for $apk" >&2
  echo "expected: $pin" >&2
  while IFS= read -r digest; do
    echo "actual: $digest" >&2
  done <<<"$digests"
  exit 1
fi

echo "signer OK: $pin $apk"
