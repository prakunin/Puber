I ran `check.full` in the worktree and it exited 0. Gradle had cached most tasks from the QA run, and the 2 tests in `TrailerPreviewLoadControlTest` passed. One major finding survived: the AC-2 test has no red run.

```
LENS: tests  ROUND: 1  BASE: 8c3117ea
### F1 [major] P0 test for AC-2 recorded green on base and not marked guard
- Where: app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt:22; docs/superpowers/plans/2026-11-28-trailer-preview-load-control.md (Implementation record, Red run)
- Problem: The test design lists `buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` as "P0 red". The Implementation record says it was "green on base": the zero-valued stub returned a default `DefaultLoadControl`. So no run ever showed this test fail, and the record does not mark it `guard` or note it as a deviation (Deviations says "None"). The test can fail in principle, because `setBufferDurationsMs` throws on mis-ordered durations, but no red run proves it.
- Failure scenario: Someone changes `TRAILER_PREVIEW_BUFFER` so that `bufferForPlaybackMs` is above `minBufferMs`. The test should catch it, but because it was never seen red, nothing shows that it would. AC-2 now stands on an unproven test.
- Fix: Do a red run with a stub that passes mis-ordered durations (for example min 0, playback 1_000) to the builder and record the IllegalArgumentException failure. Or mark the row `guard` in the test design and the Implementation record and list the change under Deviations.

### F2 [nit] assertNotNull on a non-nullable result asserts nothing
- Where: TrailerPreviewLoadControlTest.kt:22-23
- Problem: `assertDoesNotThrow<Any>` widens the type to `Any`, and `buildTrailerPreviewLoadControl()` returns a non-null Kotlin type, so `assertNotNull` can never fail. The design asked for a non-null `DefaultLoadControl`. All of the test's value comes from `assertDoesNotThrow`.
- Failure scenario: none; the assertion is dead weight.
- Fix: Drop the `assertNotNull`, or use `assertDoesNotThrow<DefaultLoadControl>` and remove the extra assertion.

### F3 [nit] Implementation record names a commit that is not in the reviewed range
- Where: docs/superpowers/plans/2026-11-28-trailer-preview-load-control.md (Implementation record, Commits)
- Problem: The record lists `e7a54763`, which looks like a pre-rebase hash. The change actually landed as `94339eaf` in 8c3117ea..ffd9d243, so the record points away from the commit that was reviewed.
- Fix: Replace the hash with `94339eaf`.
```

Coverage by acceptance criterion:
- **AC-1:** pinned by `trailerPreviewBuffer_matchesAcceptedValues`. It checks all six fields against literal values and was red on the stub.
- **AC-3:** the diff matches review checks A and B. `.setLoadControl(buildTrailerPreviewLoadControl())` sits inside `remember` between `ExoPlayer.Builder(context)` and `.build()`. The helper creates its own allocator. The diff does not touch `TrailerOverlay.kt` or `PlaybackController.kt`.
- **AC-4 and AC-5:** these are checked on the device, and the QA report records them as PASS in `/home/dev/orca/workspaces/Puber/trailer-preview-load-control/docs/superpowers/evidence/2026-11-28-trailer-preview-load-control-device.md`.

Every criterion has either a test or a recorded manual check.
