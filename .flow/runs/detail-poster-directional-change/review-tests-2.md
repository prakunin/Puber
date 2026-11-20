LENS: tests  ROUND: 2  BASE: 01d1d305

Suite state (round 2, only the tests the incremental diff touches): I ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.details.*'` in the worktree. All passed: PosterTransitionTest 4/4, PanelDirectionTest 18/18, VideoDetailsPosterStateTest 25/25, TrailerSurfaceRedrawTest 5/5. Nothing was skipped. Triage item 7 is done as triage asked: the 17 green-on-stub rows are recorded as guards, with four targeted mutant red runs. The four new PosterTransitionTest tests are new tests for the fix round. Each has a recorded red run against a mutant (no outgoing clear, empty `settle()`), so none is a P0 row recorded green on base.

### F1 [minor] Triage item 2 (failed last poster URL ends the hold) has no test that fails if the fix is removed
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:653-661; plan "Fix round 1" red-run line 2
- Problem: The plan says the empty-`settle()` mutant proves that "a failed last URL would not end the hold". It does not. `run_endsTheHold_whenSettledBeforeTheCap` calls `settle()` directly, so that mutant only checks what `settle()` does. The real fix is the new `else` branch in `onError`, which calls `record?.settle()` and sets `transition.shownImage = null`. That branch sits inside the composable lambda and no test reaches it.
- Failure scenario: someone deletes the `else` branch, or drops `transition.shownImage = null`. Every test stays green, and the triage-item-2 bug comes back: the old poster stays at alpha 1 for 3 s under the new card's text, and a stale image slides out on the next change.
- Fix: move the decision into a small internal pure function, for example `onStillError(urlIndex, lastIndex): StillErrorOutcome` (advance, or settle and forget), and unit-test both branches in VideoDetailsPosterStateTest. If that is not done, correct the plan record so it no longer claims the fix is covered and list it as device or owner-only.

### F2 [minor] The interruption logic for triage item 4 has no test
- Where: VideoItemGridDetails.kt:964-968 (`layers`), :1014-1032 (`LaunchedEffect(record)` with `holder.active`)
- Problem: The filter `!it.ready || record == null`, the cancel and remove of waiting records, and the oldest-first draw order of `layers` are all new branching logic. No test would fail if any of them broke. The plan says so itself ("For the reviewers: ..."). Only a held-key retarget on the device covers it (M3 re-run, which is still pending).
- Failure scenario: the filter is inverted (`it.ready`), so ready layers get cancelled and waiting ones stay in `active`. The older outgoing layer disappears in one frame again (the item-4 bug), or a waiting record keeps its job alive alongside the new one. Every unit test stays green.
- Fix: `PosterTransitionState.layers` is pure over `holder.active` and `record`. Make the holder internal and add a test for the order and filtering (ready non-current layers first, then the current one, and waiting non-current layers excluded). Also add one test of the prune rule, pulled out as a function over the list.

### F3 [nit] An assertion in `run_keepsOutgoingImageUnderTheStill_untilMotionEnds` cannot fail
- Where: app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/PosterTransitionTest.kt:56
- Problem: `assertEquals(1F, record.outgoingAlpha)` checks a constructor `val` against the value the test passed into the constructor. It says nothing about item 1's "opaque until the motion ends". In production that alpha is seeded in `rememberPosterTransition` (`1F`, `previous.stillAlpha.value` or `previous.outgoingAlpha`), and no test covers that seeding. The `assertNotNull` / `assertNull` pair in the same test does real work.
- Fix: drop the assertion. If the opacity rule should be guarded, test the seeding (for example, an extracted `seedFrom(previous)`).

### Unproven
- PosterTransitionTest.kt:64-72 `run_endsTheHold_whenSettledBeforeTheCap` relies on `Snapshot.sendApplyNotifications()` reaching `snapshotFlow` without a global apply observer. It passed here; it would only be a real problem if it flaked under the parallel full suite (the unit-suite-flaky run).
