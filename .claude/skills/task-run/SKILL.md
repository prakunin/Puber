---
name: task-run
description: Use when a Puber idea, bug or feature should be carried through the whole flow by role agents — triage, design, plan, implementation, verification, review, final gate — up to the merge go-ahead. Run from the main checkout with a slug or a one-line description; re-run with the slug to resume.
---

# Run a Task

You are the conductor. You hold no opinions about the code: role agents in
`.claude/agents/` do the work, files carry the state, the user holds two
gates. Run from the main checkout; it owns `docs/superpowers/BOARD.md`.

## State lives in files, so you can resume

| What | Where |
|---|---|
| Stage | the row in `docs/superpowers/BOARD.md` |
| Problem, ACs, design, test design | `<wt>/docs/superpowers/specs/<date>-<slug>-design.md` |
| Steps, implementation record, readiness | `<wt>/docs/superpowers/plans/<date>-<slug>.md` |
| What was seen on the TV | `<wt>/docs/superpowers/evidence/<date>-<slug>-device.md` |
| Review rounds and the gate | `<wt>/docs/superpowers/reviews/<date>-<slug>.md` |

Templates: `docs/superpowers/templates/`. On `/task-run <slug>` for an existing
row, read the stage and these files and continue from the first phase whose
output is missing. Never redo a finished phase unless its inputs changed.

## Routes

The analyst picks one; the user may override it at gate 1.

| Route | When | Phases |
|---|---|---|
| quick | S bug or S refactor | 1 → 2q → 4 → 5 → 6 (code, test and codex reviewers) → 7 |
| standard | feature, M/L bug | 1 → 2 → 3 → 4 → 5 → 6 → 7 |
| visual | how a screen looks | 1 → 2v → 3 → 4 → 5 → 6 (+ ux-designer) → 7 |
| perf | a measured number is the goal | 1 → 2 + baseline → 3 → 4 → 5 (re-measure) → 6 → 7 |

## Calling an agent

`Agent(subagent_type: "<role>", description: "<role> <slug>", prompt: …)`.
Every brief names: slug, absolute worktree path `<wt>`, the absolute paths of
spec / plan / evidence / review, the mode if the role has one, and whether
other worktrees may build (then `AGENT_GRADLE_SERIAL=1`). Launch independent
agents in one message. Wait for their notifications; never assume a result.

Each role returns a fixed block. Read it, check the file it claims to have
written, and only then move on. After every phase that wrote documents, commit
them in the worktree: `git -C <wt> add docs/superpowers && git -C <wt> commit -m "<Write|Update> the <slug> <spec|plan|review>"`.

The device is shared: at most one agent that leases it (analyst repro, qa,
ux-designer review) runs at a time, across all tasks.

## Phases

### 0. Set up

No row → follow `task-start` to add it and create the Orca worktree (without
`--agent`; the role agents are the workers). Row without a worktree → create
it. Copy the spec template into the worktree. Stage → `triage`.

### 1. Triage — `analyst`

Returns kind, size, route, ACs, open questions. Stage → `spec`.

### 2. Design

- **standard / perf**: `architect` (mode `design`), then `test-designer`
  (it needs the design's risks). Perf: then `qa` measures the baseline on the
  unchanged worktree build and writes it under Test design → Baseline.
- **visual**: `ux-designer` (mode `design`) and `architect` (mode `design`)
  in parallel, then `test-designer`. If the ux-designer says no stand governs
  the screen, run the `designing-a-screen` skill with the user yourself first;
  sliders need the user, not a subagent.
- **2q (quick)**: `test-designer` only; the analyst already wrote the Plan.

**Gate 1 — the user approves the spec.** Show in one screen: route, ACs,
key decisions, top risks, P0 tests, open questions. Ask with
`AskUserQuestion`: approve / change / stop. Answers go into the spec (Open
questions → answered, decisions table). Spec `Status: approved`.
Stage → `plan` (quick: → `impl`).

### 3. Plan — `architect` (mode `plan`), then `final-judge` (mode `readiness`)

`NOT READY` → send the must-fix list back to the architect once. Still not
ready → escalate to the user. Stage → `impl`.

### 4. Implement — `implementer`

`STATUS: blocked` is a **correct course**, not a failure to push through:
send the blocker to the architect (mode `design`). If ACs or decisions change,
gate 1 again with only the delta; otherwise update the plan and resume.
Stage → `verify`.

### 5. Verify — `qa`

- `DEVICE: unavailable` → stage `needs-device`, tell the user, ask. Stop.
- Gate failing, an AC `FAIL`, or defects → implementer fixes those only →
  qa again. Two failed verify rounds → escalate.
- `NOT CHECKED` ACs are carried to the gate as they are; never round them up.

Stage → `review`.

### 6. Review — in parallel

`code-reviewer`, `test-reviewer` and `codex-reviewer` (and `ux-designer`
mode `review` on the visual route, and when the diff touches Compose layout).
Tell `codex-reviewer` the route: it adds Codex's adversarial pass except on
`quick`. Create the review file from the template and record each finding as
a row; Codex's rejected claims go in their own list, so the user can see what
was turned down. `CODEX: unavailable` is recorded as skipped with the reason
and does not block the task.

`codex-reviewer` sends the branch diff and surrounding code to OpenAI through
the `codex@openai-codex` plugin (user scope); the user approved that on
2026-10-10.

- Every blocker and major: implementer fixes it, or you reject it with a
  reason the user could check, or defer it to a new board row.
- Minors: fix if cheap, otherwise defer; never drop.
- Fixes touched runtime code → qa re-checks the affected ACs.
- Round 2 re-runs only the reviewers that had findings. After round 2,
  remaining blockers go to the user.

### 7. Gate — `final-judge` (mode `gate`)

- `FAIL` → must-fix list to the implementer, then 5 → 6 → 7 again. Counts as
  a review round.
- `PASS` / `CONCERNS` → hand over.

### 8. Hand over

Report to the user: commits, the gate decision with concerns, ACs with
their status, evidence path, deferred items. Then **gate 2**: the user decides
whether to run `task-ship`. Only the user may set `WAIVED`.

## Board

Change the row's stage on every transition and say in Notes what the next
step is. Commit the board on master when the stage changes
(`Move <slug> to <stage>`). If `BOARD.md` already has someone else's
uncommitted edits, do not sweep them into your commit: ask.

## Escalate instead of looping

Stop and ask the user when: an agent fails or returns nothing twice; the
same finding comes back after a fix; a role wants to change an approved AC;
the work needs a physical TV, an uninstall, a data clear or a downgrade; or a
phase would exceed its two rounds.

## What the conductor never does

Write production code, review code itself in place of a reviewer, edit a
section another role owns, merge, push, tag, or move a stage forward without
the file that proves it.
