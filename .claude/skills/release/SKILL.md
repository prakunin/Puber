---
name: release
description: Use when the owner asks for a Puber release ("сделай релиз", "давай релиз", "выпусти версию") — bumps the version through a PR, writes Russian release notes, and after the owner's merge and explicit go-ahead tags and publishes.
---

# Release

`tools/release.sh` does the mechanics of `docs/release.md`; this skill adds what a
script cannot: the release notes and the two waits on the owner. Talk to the owner in
Russian.

## Steps

1. **Prepare.** `tools/release.sh prepare` (patch bump by default; pass `minor` or
   `major` only when the owner names it). It needs the main checkout on `master`
   with `origin/master` already contained in it; when it says local master lacks
   commits of origin, fast-forward with `git merge --ff-only origin/master` and rerun.
   Unrelated uncommitted files (e.g. `.flow/runs/`) can stay; the script commits
   only `app/build.gradle.kts` and switches back to `master` at the end.
2. **Checks.** `tools/release.sh checks <version>` until terminal (give it a
   20-minute timeout). A failed check blocks the release: report the failing job.
3. **Notes.** `tools/release.sh range` lists the commit subjects since the last tag
   (bookkeeping commits filtered out). Write `.todo/release-<version>/release-notes-ru.md`:
   a `## Что нового в <version>` heading and 3–7 bullets of what a viewer of the TV
   app notices. Rewrite technical titles into user-visible effects; drop tests, docs,
   debug-only and tooling changes; merge related commits into one bullet. Read the
   run briefs in `.flow/runs/<slug>/` when a title does not say what changed for
   the user.
4. **Hand over.** Give the owner the PR link, the check results and the notes path.
   Never merge the PR yourself. Stop here.
5. **After "смержил".** Run `tools/release.sh status <version>` and report the merge
   commit. The merge is not approval to publish: ask explicitly whether to tag
   `v<version>` on that commit, and wait for a yes.
6. **Publish.** `tools/release.sh publish <version> <notes-file>` (give it a
   25-minute timeout; the guard hook asks before it runs). It refuses an unmerged PR,
   a wrong version on the merge commit, or an existing tag; if it stops on a tag that
   already exists, report where it points and stop. On success report the release URL
   and assets; mention workflow warnings only if they are new.
7. Suggest `git merge --ff-only origin/master` so local master picks up the merge.

## Gotchas this encodes

- `gh` defaults to the `upstream` remote (rovkinmax/Puber); the script always targets
  `origin`. Pass `--repo` to any manual `gh` call.
- The gh token lacks `read:org`, so `gh pr edit` fails; edit a PR body with
  `gh api -X PATCH repos/<owner>/Puber/pulls/<n> -f body=...`.
- The release branch is cut from local `master`, which usually carries shipped runs
  not yet on `origin`; the version PR is what lands them.
