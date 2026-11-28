LENS: tests  ROUND: 1  BASE: 7a9fd580

No findings.

**Spanish-VTT absence assertion.** The path is right. `subtitleVariantMaster` points at `sub_es.m3u8`. `manifestSubtitleRoutes("es", …)` serves that playlist at `/media/hls/sub_es.m3u8` with a relative segment `sub_es.vtt`. That resolves to `/media/hls/sub_es.vtt`, the journal path asserted at `PlaybackControllerNetworkTracksTest.kt:353`.
- The test is not vacuous. It first waits for `currentMediaPath() == highPath`, `STATE_READY` and `"es" in textTrackLanguages()`. Selection is applied in the same track report that exposes the Spanish group, so `assertFalse(selectedTextTrack())` is checked after the selector has run.
- The journal check is weaker than the selection check. It runs immediately and a late VTT fetch could slip past it. The selection check still catches a wrongly selected track, and the implementation record's mutation run (a positional `SubtitleTrackSelector.select`) failed only this test. So I count the assertion as sufficient.
- The `harness.server.reset(...)` call carries the English routes too, so `unknownRequests == 0` holds.

**Docs section.** `docs/testing/player-playback.md` matches the pinned behaviour and the code I read in `PlaybackController`:
- `switchStream` does not disable text.
- The pending row is re-applied through `SubtitleTrackSelector` on the first track report (`PlaybackController.kt:893`, `:908-910`).
- When the intended row is gone, the VM sends the Off row.
- The four test names match the code exactly, and the descriptions match AC-1 to AC-4.

**ACs and test design.** AC-1 to AC-4 each have a test and AC-5 is covered by the evidence file and `check.full`. All four tests are guards. The test design and the implementation record say so, and the record lists a mutation run for each. No test is skipped, mocks the class under test, or uses sleeps. The instrumented tests use `awaitCondition`. I did not rerun the suite. I relied on the recorded results: the itest XML (16 tests, 0 failures, three runs), the 50 unit tests and `check.full` exit 0.

### Unproven
- `PlaybackControllerNetworkTracksTest.kt:~380` The AC-2 test cannot tell whether the English track returns through the pending row or through the old override matching an equal `TrackGroup`. Its comment claims the pending row. The mutation run cleared both together, so a regression in only one of them would go unnoticed. A mutation that clears only `pendingSubtitleTrack` and still passes would make this real.
- `PlaybackControllerNetworkTracksTest.kt:353` The VTT-absence check has no settle wait. It would matter only if Media3 fetched the VTT noticeably later than it reports the track as selected.
