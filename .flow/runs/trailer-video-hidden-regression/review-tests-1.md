LENS: tests  ROUND: 1  BASE: 3ec85287
### F1 [minor] AC-4 still names the favourites panel, but nothing can check it there
- Where: /home/user1/orca/workspaces/Puber/trailer-video-hidden-regression/docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md:42 (AC-4), and the D3 and T2 rows of the Test design
- Problem: AC-4 and the D3/T2 checks require trailers to play on the favourites panel. qa found that `FavoriteScreenContent.kt` passes no `trailerUrl`, so favourites never plays a trailer and that half of AC-4 cannot be tested. The spec and the test design were not changed to match. The qa report only marks favourites as NOT CHECKED and tells the owner to skip it in T2.
- Failure scenario: the owner runs T2 as the spec writes it, finds no trailer on favourites, and gate 2 is left with an AC that is neither tested nor documented as out of scope.
- Fix: in the spec, remove favourites from AC-4, D3 and T2, or record it as N/A with the `FavoriteScreenContent.kt` reason.

### F2 [minor] The red run is recorded for only one of the four P0 red unit tests
- Where: /home/user1/orca/workspaces/Puber/trailer-video-hidden-regression/docs/superpowers/plans/2026-11-19-trailer-video-hidden-regression.md:84-86
- Problem: the test design marks four AC-6 rows as P0 red. The Implementation record shows a red run only for `surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero`. On base all four tests fail the same way, because `surfaceNeedsRedraw` does not exist and the file does not compile. So the other three are red on base as well, but the record does not say so. None is recorded green on base, which is why this is not major.
  - I reasoned through mutations instead of using the record, and the five tests pin the predicate completely. Each of these changes fails at least one test:
    - dropping the inequality fails `false_whenSizeUnchanged`;
    - firing only for 0 to non-zero fails `true_whenSizeChangesBetweenNonZeroSizes`;
    - reading "non-zero" as `||` fails `false_whenOnlyOneDimensionIsZero`;
    - firing on a collapse fails `false_whenSizeBecomesZero`.
- Failure scenario: an audit of the record sees three P0 red tests with no red evidence.
- Fix: add one line to the Red run block saying that all four P0 red tests share the same compile failure on base.

Suite state: I ran check.full (`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) in the worktree and it passed: BUILD SUCCESSFUL. Unit tests and detekt were up to date and lint ran. Totals are 2097 tests, 0 failures, 0 errors, 0 skipped. `TrailerSurfaceRedrawTest` ran 5 tests, all green. The AC-5 guard holds: the diff adds only the new test file under `app/src/test`, and no existing assertion was removed.

The listener wiring at `TrailerPreviewPlayer.kt:136-158` has no unit test: the attach and remove calls, the old and new argument order, and `right - left`. Swapping old and new would turn the 0x0 to 1350x759 case into no redraw, and D1 on the device would catch that (it passed on emulator-5554). AC-3, the TV half of AC-4 and AC-5 (T1-T3), plus M5, still wait for the owner's TV check, and the qa report lists them as such.

### Unproven
- `TrailerPreviewPlayer.kt:155-158` the `post { invalidate() }` could start a redraw loop that never stops. It would become real if a device trace showed repeated layout callbacks with no size change. The test design accepts this risk with a reason.
