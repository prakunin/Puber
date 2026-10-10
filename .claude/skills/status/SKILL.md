---
name: status
description: Use when asked where the Puber project stands — what is in flight, blocked, waiting for a device or review, and what to do next.
---

# Project Status

Gather, then summarize; change nothing.

```bash
cat docs/superpowers/BOARD.md
orca worktree ps 2>/dev/null || git worktree list
git for-each-ref --sort=-committerdate refs/heads --format='%(refname:short) %(committerdate:short) %(subject)'
for b in $(git for-each-ref --format='%(refname:short)' refs/heads | grep -v '^master$'); do
  echo "$b: $(git rev-list --count master..$b) ahead, $(git rev-list --count $b..master) behind"
done
git status --short
tools/emulator-resource-lock.sh status
adb devices -l
```

Report, in this order:

1. **Blocked / needs the user**: rows at `needs-device`, open questions, the
   environment itself (no SDK, no device).
2. **In flight**: each active row with its worktree, commits ahead, and the next step.
3. **Drift**: branches or worktrees with no board row, rows whose worktree is
   gone, uncommitted work in the main checkout, divergence from `upstream`.
4. **Next**: one recommendation.

Mismatches between the board and git are reported, not silently fixed.
