#!/usr/bin/env bash
# PreToolUse guard for Bash: turns the hard rules in AGENTS.md into refusals.
# Reads the hook JSON on stdin; prints a decision only when a rule matches.
set -euo pipefail

input="$(cat)"
cmd="$(jq -r '.tool_input.command // ""' <<<"$input")"
cwd="$(jq -r '.cwd // ""' <<<"$input")"
[[ -n "$cmd" ]] || exit 0

# Ignore heredoc bodies and quoted text (file contents, commit messages, grep
# patterns) so that mentioning a command is not the same as running it.
bare="$(awk '
  delim != "" { if ($0 ~ "^[[:space:]]*" delim "[[:space:]]*$") delim = ""; next }
  match($0, /<<-?[[:space:]]*["'\'']?[A-Za-z_][A-Za-z0-9_]*/) {
    d = substr($0, RSTART, RLENGTH); sub(/^<<-?[[:space:]]*["'\'']?/, "", d); delim = d
  }
  { print }
' <<<"$cmd" | perl -0pe 's/\x27[^\x27]*\x27//g; s/"(?:[^"\\]|\\.)*"//g')"

decide() {
  jq -n --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  exit 0
}
deny() { decide deny "$1"; }
ask() { decide ask "$1"; }

in_worktree() {
  local dir="${cwd:-$PWD}" git_dir common
  git_dir="$(git -C "$dir" rev-parse --absolute-git-dir 2>/dev/null)" || return 1
  common="$(cd "$dir" && cd "$(git rev-parse --git-common-dir)" && pwd)" || return 1
  [[ "$git_dir" != "$common" ]]
}

# Subagents start in the main checkout and reach a worktree with `cd <wt> &&`
# or call `<wt>/gradlew` directly: judge the directory gradlew would run in.
gradlew_dir="$(grep -oE '(^|[[:space:];&|])[^[:space:];&|]*/gradlew([[:space:]]|$)' <<<"$bare" | head -n1 | sed -E 's/^[[:space:];&|]*//; s#/gradlew[[:space:]]*$##' || true)"
cd_dir="$(grep -oE '(^|[[:space:];&|])cd[[:space:]]+[^[:space:];&|]+' <<<"$bare" | tail -n1 | awk '{print $NF}' || true)"
for d in "$cd_dir" "$gradlew_dir"; do
  [[ -n "$d" && "$d" != "." ]] || continue
  d="${d/#\~/$HOME}"
  [[ "$d" = /* ]] || d="${cwd:-$PWD}/$d"
  if [[ -d "$d" ]]; then cwd="$d"; fi
done

if grep -Eq '(^|[[:space:];&|/])gradlew([[:space:]]|$)' <<<"$bare" && in_worktree; then
  deny "This is a worktree: build with ./tools/agentw <task>, never ./gradlew (AGENTS.md, Build And Worktrees)."
fi

if grep -Eq 'git[[:space:]]+worktree[[:space:]]+add' <<<"$bare"; then
  deny "Worktrees are Orca-managed: use 'orca worktree create --name <slug>' (`/task:run` creates the worktree for a run), not git worktree add."
fi

if grep -Eq 'connected[A-Za-z]*AndroidTest' <<<"$bare"; then
  deny "connected*AndroidTest hits every attached device and uninstalls the app. Use 'make DEVICE=<serial> itest [TESTS=<class>]'."
fi

if grep -Eq '(^|[[:space:];&|/])adb[[:space:]](.*[[:space:]])?(uninstall|disconnect)\b|pm[[:space:]]+(uninstall|clear)\b|make[[:space:]].*\buninstall\b' <<<"$bare"; then
  deny "Uninstalling, clearing app data or detaching a device needs explicit user authorization for that exact action: it destroys the KinoPub pairing (AGENTS.md, Runtime And External Systems)."
fi

if grep -Eq '(^|[[:space:];&|/])adb[[:space:]].*install[^[:space:]]*[[:space:]].*(^|[[:space:]])-[a-z]*d\b' <<<"$bare"; then
  deny "Allowing a downgrade needs explicit user authorization. Install with tools/android-apk-install-preserve instead."
fi

if grep -Eq '(^|[[:space:];&|/])adb[[:space:]]+(shell|install|install-multiple|logcat|push|pull|reboot|root|emu)\b' <<<"$bare"; then
  deny "Name the device: every adb call takes -s <serial> of a device whose lease you hold (tools/emulator-resource-lock.sh)."
fi

if grep -Eq 'git[[:space:]]+tag[[:space:]]+[^-]|git[[:space:]]+push\b.*(--tags|[[:space:]]v[0-9])' <<<"$bare"; then
  ask "Creating or pushing a release tag publishes the release and needs its own approval; the merge is not it (docs/release.md)."
fi

if grep -Eq 'git[[:space:]]+push\b.*(--force|-f\b)' <<<"$bare"; then
  ask "Force push rewrites shared history."
fi

exit 0
