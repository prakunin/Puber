#!/usr/bin/env bash
# release.sh — the mechanical steps of docs/release.md. Judgment (release notes,
# the owner's tag approval) stays with the caller; see .claude/skills/release.
#
#   tools/release.sh prepare [patch|minor|major]  bump, branch, compile, push, open the PR
#   tools/release.sh checks  <version>            watch the version PR's checks
#   tools/release.sh range   [<from-tag>]         commit subjects for the release notes
#   tools/release.sh status  <version>            is the PR merged, does the tag exist
#   tools/release.sh publish <version> <notes.md> tag the merge, push, watch, apply notes
#
# Runs only in the main checkout. Never merges, never force-pushes.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

die() { echo "release: $*" >&2; exit 1; }

git_dir="$(git rev-parse --absolute-git-dir)"
common="$(cd "$(git rev-parse --git-common-dir)" && pwd)"
[[ "$git_dir" == "$common" ]] || die "run from the main checkout, not a worktree"

# gh picks the 'upstream' remote by default; the release repo is origin.
repo="$(git remote get-url origin | sed -E 's#^(git@github\.com:|https://github\.com/)##; s#\.git$##')"
gradle_file="app/build.gradle.kts"

version_at() { git show "$1:$gradle_file" | sed -nE 's/^val currentVersion = "([0-9]+\.[0-9]+\.[0-9]+)"$/\1/p'; }

bump() {
  local v="$1" kind="$2" a b c
  IFS=. read -r a b c <<<"$v"
  case "$kind" in
    patch) echo "$a.$b.$((c + 1))" ;;
    minor) echo "$a.$((b + 1)).0" ;;
    major) echo "$((a + 1)).0.0" ;;
    *) die "bump kind must be patch, minor or major" ;;
  esac
}

pr_number() {
  gh api "repos/$repo/pulls?head=${repo%%/*}:release/$1&state=all" --jq '.[0].number // empty'
}

need_version() { [[ "${1:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "give the version as X.Y.Z"; }

cmd_prepare() {
  local kind="${1:-patch}" current next branch base
  git fetch origin --tags --quiet
  [[ "$(git branch --show-current)" == master ]] || die "switch the main checkout to master first"
  git merge-base --is-ancestor origin/master master \
    || die "local master lacks commits of origin/master: run 'git merge --ff-only origin/master' (or resolve the divergence) first"
  git diff --quiet -- "$gradle_file" && git diff --cached --quiet -- "$gradle_file" \
    || die "$gradle_file has uncommitted changes"

  current="$(version_at master)"
  [[ -n "$current" ]] || die "cannot read currentVersion from $gradle_file"
  next="$(bump "$current" "$kind")"
  branch="release/$next"
  if git rev-parse -q --verify "refs/tags/v$next" >/dev/null; then die "tag v$next already exists"; fi
  if git ls-remote --exit-code --tags origin "v$next" >/dev/null 2>&1; then die "tag v$next already exists on origin"; fi
  if git rev-parse -q --verify "refs/heads/$branch" >/dev/null; then die "branch $branch already exists locally"; fi

  base="$(git rev-parse --short master)"
  echo "release: $current -> $next ($kind), base master $base, $(git rev-list --count origin/master..master) commit(s) not yet on origin"

  git switch -q -c "$branch" master
  trap 'git switch -q master || true' EXIT
  sed -i -E "s/^val currentVersion = \"$current\"$/val currentVersion = \"$next\"/" "$gradle_file"
  [[ "$(git diff --numstat -- "$gradle_file" | awk '{print $1}')" == 1 ]] || die "version bump did not change exactly one line"
  git commit -q -m "Bump version to $next" -- "$gradle_file"

  ./gradlew -q :app:compileDevDebugKotlin :app:compileProdReleaseKotlin

  git push -q -u origin "$branch"
  local body url
  body="$(printf 'Bump version to %s.\n\nLocal checks: `:app:compileDevDebugKotlin`, `:app:compileProdReleaseKotlin`.\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)\n' "$next")"
  url="$(gh api -X POST "repos/$repo/pulls" -f title="Bump version to $next" -f head="$branch" -f base=master -f body="$body" --jq .html_url)"
  echo "release: version=$next"
  echo "release: pr=$url"
}

cmd_checks() {
  need_version "${1:-}"
  local n; n="$(pr_number "$1")"; [[ -n "$n" ]] || die "no PR for release/$1"
  gh pr checks "$n" --repo "$repo" --watch --interval 30
}

cmd_range() {
  git fetch origin --tags --quiet
  local from="${1:-$(git describe --tags --abbrev=0 --match 'v*' origin/master)}"
  local to="master"
  git merge-base --is-ancestor origin/master master || to="origin/master"
  echo "release: range $from..$to" >&2
  git log --no-merges --format='%s' "$from..$to" | grep -vE '^(Bump version to |Record the |Add the spec, plan)' || true
}

cmd_status() {
  need_version "${1:-}"
  git fetch origin --tags --quiet
  local n; n="$(pr_number "$1")"
  if [[ -z "$n" ]]; then echo "pr=none"; else
    gh api "repos/$repo/pulls/$n" --jq '"pr=\(.html_url) state=\(.state) merged=\(.merged) merge_sha=\(.merge_commit_sha)"'
  fi
  echo "origin_master=$(git rev-parse --short origin/master) version_on_origin_master=$(version_at origin/master)"
  if git ls-remote --exit-code --tags origin "v$1" >/dev/null 2>&1; then echo "tag=exists"; else echo "tag=absent"; fi
}

cmd_publish() {
  local v="${1:-}" notes="${2:-}"
  need_version "$v"
  [[ -f "$notes" ]] || die "notes file '$notes' not found"
  git fetch origin --tags --quiet
  local n merged sha
  n="$(pr_number "$v")"; [[ -n "$n" ]] || die "no PR for release/$v"
  read -r merged sha < <(gh api "repos/$repo/pulls/$n" --jq '"\(.merged) \(.merge_commit_sha)"')
  [[ "$merged" == true ]] || die "PR #$n is not merged"
  git merge-base --is-ancestor "$sha" origin/master || die "merge commit $sha is not on origin/master"
  [[ "$(version_at "$sha")" == "$v" ]] || die "currentVersion at $sha is not $v"
  if git ls-remote --exit-code --tags origin "v$v" >/dev/null 2>&1; then
    die "tag v$v already exists on origin; stop and check where it points"
  fi
  if git rev-parse -q --verify "refs/tags/v$v" >/dev/null; then
    [[ "$(git rev-parse "v$v^{commit}")" == "$sha" ]] || die "local tag v$v points elsewhere than $sha"
  else
    git tag -a "v$v" "$sha" -m "Release $v"
  fi
  git push -q origin "v$v"
  echo "release: pushed v$v at $sha"

  local run="" i
  for i in $(seq 1 20); do
    run="$(gh run list --repo "$repo" --workflow release.yml --branch "v$v" --limit 1 --json databaseId --jq '.[0].databaseId // empty')"
    [[ -n "$run" ]] && break
    sleep 6
  done
  [[ -n "$run" ]] || die "no release workflow run appeared for v$v"
  echo "release: watching run $run"
  gh run watch "$run" --repo "$repo" --exit-status --interval 30 >/dev/null \
    || die "release run $run failed: gh run view $run --repo $repo --log-failed"

  gh release edit "v$v" --repo "$repo" --notes-file "$notes" >/dev/null
  local body want
  body="$(gh release view "v$v" --repo "$repo" --json body --jq .body)"
  want="$(cat "$notes")"
  [[ "$body" == "$want" ]] || die "published notes differ from $notes"
  gh release view "v$v" --repo "$repo" --json url,assets --jq '"release: \(.url)\nrelease: assets \([.assets[].name] | join(", "))"'
}

case "${1:-}" in
  prepare) shift; cmd_prepare "$@" ;;
  checks)  shift; cmd_checks "$@" ;;
  range)   shift; cmd_range "$@" ;;
  status)  shift; cmd_status "$@" ;;
  publish) shift; cmd_publish "$@" ;;
  *) sed -n '2,10p' "$0"; exit 2 ;;
esac
