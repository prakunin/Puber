---
name: task-start
description: Use when starting a new piece of Puber work (feature, fix, refactor, port) — registers it on the board and opens an Orca worktree for it. Run from the main checkout.
---

# Start a Task

The main checkout is the conductor: it owns `docs/superpowers/BOARD.md` and never
carries task code. Each task gets its own Orca worktree and session.

1. **Check the board.** Read `docs/superpowers/BOARD.md`. If a row already covers
   this work, continue it instead of opening a second one.
2. **Pick a slug**: short kebab-case, e.g. `subtitle-upstream-port`. Branch and
   worktree carry the same name.
3. **Size it.**
   - *Small* (one screen or class, no design question): no spec; the plan is a
     few lines in the board row's Notes.
   - *Otherwise*: the worker writes `docs/superpowers/specs/<date>-<slug>-design.md`
     and then `docs/superpowers/plans/<date>-<slug>.md`, and the user agrees to
     the spec before code starts.
   - A visual rework goes through the `designing-a-screen` skill first.
4. **Add the row** to the Active table with stage `spec` (or `impl` for small
   tasks), the branch, today's date, and a one-line goal.
5. **Create the worktree** through Orca, never by hand:

   ```bash
   orca worktree create --name <slug> --base-branch master --setup run \
     --comment "<one-line goal>" --json
   ```

   Add `--agent claude --prompt "<brief>"` to launch the worker directly. The
   brief names the board row, the spec/plan to follow, that builds go through
   `./tools/agentw` (with `AGENT_GRADLE_SERIAL=1` when other worktrees are
   building), and that the worker finishes with `/task-verify`.
6. **Commit the board change** on master on its own (`Track <slug> on the board`),
   so the worktree and every other session see it.

Report the slug, worktree path, and the stage you set.
