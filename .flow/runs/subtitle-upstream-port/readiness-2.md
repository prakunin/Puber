# Readiness 2: subtitle-upstream-port

Judge: final-judge, mode readiness, round 2. Date: 2026-10-14.
Read: plan `docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md` (wt, untracked, 571
lines), `readiness-1.md`, master be8395a4 via `git show`, and the worktree at 3eb98f96 (unchanged
since round 1: same uncommitted file set, `replay_pid1700735.log` still untracked).

**Verdict: READY**

## 1. Round 1 must-fix

Must-fix 1 (move the D8 rewrite directly after the rebase; name the rebased port commit as the
only commit with the two known red tests): **resolved**.

- Task order is now 1 commit in place, 2 rebase, **3 D8** (rewrite of
  `resumeDialog_noHideTimerRunsWhileItIsUp` and `controlsTimerDuringExit_changesNothing`),
  4 D3, 5 D7, 6 T-P1/T-P2, 7 labels, 8 preview, 9 full gate and record.
- Task 2's run step says "Task 3 rewrites both, directly after this task"; its Done-when names the
  rebased port commit as the only commit whose unit suite carries the two known red tests, as the
  accepted consequence of owner Q1, and says every later task ends on a green suite.
- Task 3's Done-when requires the whole player test package green.
- Task 9 requires the Implementation record to state the red-commit fact and that Task 3 turned
  them green, and to carry T-M1's known limit under For the reviewers (round 1 note, now also
  written into Task 3).

## 2. Regression check after the renumbering

- Every `Task N` cross-reference in the plan (lines 22, 27, 31, 33, 115, 134, 138, 145-146, 332,
  432, 536, 540-545) points at the right task under the new numbers. `test-design.md` and
  `state.md` hold no task-number references.
- Dependencies hold in the new order: Task 3 consumes `publishPreparedContent` (present after
  Task 2); Task 4 (D3) and Task 5 (D7) do not depend on Task 3; Task 6 adds T-P2 to
  `PlayerVMOverlayAutoHideTest` after Task 3 rewrote that file, so no edit collision.
- AC coverage is unchanged from round 1 with the new numbers: AC-1 Tasks 1, 4; AC-3 Tasks 1, 4;
  AC-5/6/7 Tasks 1, 7; AC-8 Tasks 1, 3, 5, 6; AC-9 Tasks 2, 3, 9; AC-2, AC-4 Task 1 (owner Q2/Q3).
- Scope unchanged: no task touches side-loading, the merge model or restore keys.

## 3. Citation re-sample (master be8395a4)

| Citation | Claimed | Found |
|----------|---------|-------|
| `PlayerVM.kt:543-549` | `onAction` drops all but Back while `closing` | yes |
| `PlayerVM.kt:707-719`, `:712` | `scheduleControlsHide`, `if (closing) return@launch` | yes |
| `PlayerVM.kt:1244-1255` | `onPlaybackEnded`, `!hasNextEpisode` + no panel -> `exitPlayer()` | yes |
| `PlayerVM.kt:365-368`, `:926`, `:1327-1332` | `buildResumeDialog`, `StartFromBeginning` in `switchEpisode`, `retryPlayback` without hide-job cancel | yes |
| `PlayerVMOverlayAutoHideTest.kt:41-49`, `:171`, `:256`, `:272-281` | `vmTest`, the two tests to rewrite, last-episode pattern with `CompletableDeferred` save | yes |

## Must fix

None.

## Notes carried from round 1 (non-blocking)

- AC-2, AC-4, AC-6 (`SELECTION_FLAG_FORCED`) and AC-1's DEFAULT/FORCED manifest case have no
  automated proof by owner decision Q2/Q3; the gate will carry them as device-only or
  NOT VERIFIED unless the smoke shows them.
- T-L1 covers en through the mapper; ru stays at the pure-function level (SubtitleLabelsTest, T-L2).
