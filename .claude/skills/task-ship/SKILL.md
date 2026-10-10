---
name: task-ship
description: Use when a verified Puber task should land on master — review, merge, board update, worktree cleanup. Run from the main checkout. Never tags a release.
---

# Ship a Task

Precondition: the board row is at `review` with evidence (or a recorded reason
why no device was needed). Otherwise run `/task-verify` first.

1. **Review the branch** against master. If `task-run` already wrote
   `docs/superpowers/reviews/<date>-<slug>.md` with a gate of `PASS` or
   `CONCERNS` at the branch head, that is the review: show it and skip to
   step 2. Otherwise `/code-review` on `master...<branch>`.
   Visual changes are also judged on looks (sizes, spacing, materials,
   consistency), not only behaviour. Fixes go back into the worktree; re-verify
   what they touch.
2. **Show the user** the commit list, the review outcome, and the evidence, and
   get a go-ahead to merge.
3. **Merge** into master from the main checkout (fast-forward or a merge commit
   matching recent history; English commit messages, no Co-Authored-By lines).
   Push to `origin` only when the user asks. `gh` is not installed here, so a PR,
   if wanted, is opened by the user.
4. **Board**: move the row to Done with the merge commit and date. Tick or strike
   the plan's remaining checkboxes so the plan reflects reality.
5. **Clean up** with the user's OK: `orca worktree rm <slug>`.

A release is a separate step: `docs/release.md`. Creating or pushing a tag needs
its own explicit approval; the guard hook asks before `git tag` and tag pushes.
