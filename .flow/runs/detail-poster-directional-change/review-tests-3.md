LENS: tests  ROUND: 3  BASE: 01d1d305

Suite state (round 3, only the touched tests). I ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.details.*'` in the worktree at b27800f6 and it passed. Result files dated 2026-11-20T07:07-07:08Z: PosterTransitionTest 5/5, PanelDirectionTest 18/18, VideoDetailsPosterStateTest 25/25, TrailerSurfaceRedrawTest 5/5. Nothing skipped, nothing failed.

The new test `run_bringsASeededOutgoingLayerToRest_whileWaitingForThePicture` targets a regression from fix round 1, so it has no meaning on base. The plan's "Fix round 2" records it red against a mutant that skips the rest animation (alpha 0.5 left), and that matches what the test asserts. No test was weakened: the two assertion edits change `outgoingAlpha` to `outgoingAlpha.value` and add the frame clock.

### F1 [minor] Nothing tests a picture that settles while the rest animation is still running, so the cancel branch is uncovered and its result is unchecked
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:920-926 (`resting.cancel()`), :929-944
- Problem: The only new test advances past the full rest before checking, and it never calls `settle()`. The case where `ready` flips while `restOutgoing` is running has no test. In that case `resting.cancel()` leaves `outgoingAlpha` wherever the fade stopped, and the settled branch never animates it again. Two things could break with every test still green:
  - Someone drops `resting.cancel()`. `coroutineScope` then waits up to 250 ms for the rest before the still starts moving.
  - The layer stays see-through for the whole motion. That is the round 1 ghost band this fix was meant to remove.
- Failure scenario: The predecessor is ready and mid-fade, so the new record is seeded with alpha 0.5 and offset 30. The new poster is a Coil memory-cache hit and settles about one frame later, while the 75 ms fade is at about 0.6. The outgoing layer then slides out at alpha about 0.6 under a still that is fading in, and the panel shows through. No test asserts the outgoing alpha during the settled motion.
- Fix: Add a test that seeds `transition(offsetFrom = 30F, alphaFrom = 0.5F)`, launches `run`, advances one frame, then calls `settle()` and `Snapshot.sendApplyNotifications()`. Assert that the run ends within `DURATION_MS` of the settle, so a missing cancel fails. If the intent is "opaque until the motion ends", also assert `outgoingAlpha.value == 1F` at mid-motion. That assertion fails on the current code, so the edge lens should confirm whether the settled branch should snap or animate `outgoingAlpha` to 1.

### F2 [minor] Triage asked for a handover test from a ready mid-motion predecessor, but the new test only covers the constructor values
- Where: VideoItemGridDetails.kt:1022-1027 (seeding `when` in `rememberPosterTransition`); PosterTransitionTest.kt:63-74
- Problem: Triage-2 "Next" asks for "a unit test of the handover from a ready mid-motion predecessor". The new test passes offset 30 and alpha 0.5 straight into the constructor. Which values of the predecessor get chosen (`previous.ready` gives the still's values, otherwise the outgoing values, now `.value`) is still untested. Breaking that `when`, for example by swapping the branches, keeps all tests green. This is the remainder of round 2 tests F2/F3, now partly covered.
- Fix: Extract the `when` into an internal `seedFrom(previous: PosterTransition?): Pair<Float, Float>`. Test three cases: null, a ready predecessor mid-motion (offset and alpha from the still), and a waiting predecessor (from the outgoing layer).

### F3 [nit] The mid-motion alpha assertion still only echoes the default
- Where: PosterTransitionTest.kt:57
- Problem: `assertEquals(1F, record.outgoingAlpha.value)` still checks the default `alphaFrom = 1F`. With alpha already at 1 the rest fade has zero duration, and nothing in the settled branch changes alpha, so the assertion cannot fail with this seed. The plan says the self-comparing assertion was "replaced", but it still compares the default with itself.
- Fix: Drop it, or move it into the F1 test, where the seed is below 1 and the assertion can fail.

### Unproven
- PosterTransitionTest.kt:73 `job.cancel()` without `join`: `runTest` waits for the cancelled child. This only becomes a problem if `restOutgoing` gains a non-cancellable section.
