# Readiness 2 - subtitle-restore-model

Judge: final-judge, mode readiness, round 2. Worktree HEAD `055d22be`, code base `f351d988`.
`git diff --stat f351d988 HEAD` touches only the spec and the plan (no `app/` change), so the round 1 citation
sample still holds against the base code. Round 2 reviews the `62d24119..055d22be` delta.

## Verdict: READY

## Round 1 must-fix

1. **AC-11 quality-switch clause has no device design: fixed.**
   - Spec section 5 step 3 is now clause (a): a quality that keeps the row means the row must stay on. An Off or
     wrong row "FAILS AC-11; it is a failure of this run, not an observation". The old escape is gone.
   - Step 4 is clause (b): the stream lacks the row (manual quality `url.hls`, Outlander S1E5 720p, checked through
     stream info). The picker must show Off, then (4b) Auto brings the row back, then (4c) re-entry shows the row,
     which proves Off was not saved.
   - If no such stream is found, (b) is marked `NOT CHECKED` with the titles tried. The plan says it cannot pass by
     observation or by (a).
   - The Test design AC-11 row now names steps 3 and 4 and the `NOT CHECKED` rule.
   - The plan's "After Task 4" section has the same (a)/(b) steps and says "(a) alone does not pass AC-11".
   - The plan relies on `VM:545-550` for (b): when the row is missing, `onTracksUpdated` shows Off and leaves
     `intendedSubtitleRow` alone. Checked in the code: it does.

## Round 1 advisories

- Task 3 rule 4 now says "Skipped when the normalised preferred label is null (same kind of guard as rule 3)".
  It also gives the reason (T-7a, T-10c). Addressed.
- T-8b and T-8c now call `clearMocks(interactor, answers = false)` after the pick, before the step under test. They
  assert zero saves with `verify(exactly = 0)` and `any()` in all four slots. Addressed.

## Coverage, ordering, scope (unchanged since round 1)

- Every AC (AC-1..AC-12, AC-16) has a plan task and a Test design row. AC-11 is now fully designed.
- T1 -> T2 -> T3 -> T4 is in dependency order, and no task leaves the build red.
- The plan stays inside Out of scope. Q1-Q4 are answered.

## Advisory (not blocking)

1. Spec section 5 step 4 says to check the save in "the player's subtitle-restore log line ... filtered by tag".
   No such log exists at base: `PlayerVM.kt` has no `Log.*` call, and `saveTrackPreferences` (`:1849-1870`) does
   not log. The plan adds no log. So qa-device cannot use that line. Step 4c (re-entry shows the row) is the
   evidence that Off was not saved, and it does not need the log. qa should not report the log check as done.
2. Naming drift: the plan's pointer says "the reworded M3". The spec splits the quality switch into steps 3 (a)
   and 4 (b, 4b, 4c). The Test design row names both steps. qa should follow steps 3 and 4 of section 5.
3. Carried from round 1: the spec cites `PlaybackController.kt:822` for the early return, which is at `:823`.

## Must fix

None.
