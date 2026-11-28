# Brief: subtitle-switch-text-disable

Kind: improvement   Size: S   Tracks: none   Base: 7a9fd580
Device verification: no (instrumented and unit tests plus a testing doc; production sources unchanged).
Research: `research.md` (code state, options A-C, related runs; not repeated here).

## Recommendation
Re-scope to option B (research.md: Options): pin the fork's current switch behaviour with tests, do not port the upstream `switchStream` disable. The upstream wrong-subtitle case is already blocked here (research.md: Understanding); porting it needs a VM re-send on all three switch callers (option A, M, playback risk area) to avoid subtitles going Off after every quality switch, in exchange for closing a window that lasts until the new source's first `onTracksChanged`.

Why the residual window does not show a wrong subtitle:

| Step after `switchStream` | Code | Effect |
|---|---|---|
| Old parameters re-applied (text on, old override, preferred language) | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:535`, `:549` | The old `TrackSelectionOverride` names the old source's `TrackGroup`; it applies only to an equal group, otherwise the preferred language picks |
| First track report of the new session | `PlaybackController.kt:259-263`, `:892-913` | Pending row re-applied through the semantic selector; no positional match |
| Selector | `SubtitleTrackSelector.kt:39-66` | Rejects a candidate with another language, forced flag or label |
| VM | `PlayerVM.kt:564-603` | Intended row missing from the new rows -> Off row -> `selectSubtitle(Off)` disables text; same row -> no re-send, controller pending keeps it on |

Parent ACs AC-13..AC-15 (`../subtitle-restore-model/brief.md`) are replaced by the ACs below; AC-13's "pending is null, text disabled before `setMediaItem`" is dropped.

## Acceptance criteria
- **AC-1** Given an HLS master with a manifest-only English subtitle rendition selected, When `switchStream` loads a master whose only subtitle rendition is Spanish (DEFAULT=NO) at the same group and track coordinates, Then after the new source is READY no text track is selected and the journal has no request for the Spanish VTT. (instrumented, `PlaybackControllerNetworkTracksTest`)
- **AC-2** Given an HLS master with a manifest-only English rendition selected, When `switchStream` loads a master with the same English rendition, Then the English text track is selected again without another `selectSubtitle` call. (instrumented)
- **AC-3** Given a started `PlayerVM` with a subtitle row restored, When `SelectQuality` switches the stream and the new report lacks that row, Then the content shows the Off row and `playbackController.selectSubtitle` receives an Off row. (unit, `PlayerVMTrackSelectionTest`)
- **AC-4** Given the same VM, When `SelectQuality` switches and the new report has identical rows, Then the intended row stays selected and `selectSubtitle` is not called again. (unit; pins that the controller's pending row carries the subtitle)
- **AC-5** Given the new tests, When run through `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest` under a lease and `check.full` in the worktree, Then both pass and `docs/testing/player-playback.md` lists the fork's switch semantics and the new tests.

## Plan
1. `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerRoutes.kt:15-25`: add `subtitleVariantMaster(videoPlaylist, subtitleName, language, uri)` (one `TYPE=SUBTITLES` rendition, `SUBTITLES="subs"` on the variant, DEFAULT=NO) and inline routes for an English and a Spanish subtitle playlist plus VTT, passed through `commonHlsRoutes(extraRoutes = ...)` (`:34`); no fixture binary or `SHA256SUMS` change.
2. `.../PlaybackControllerNetworkTracksTest.kt` after `:312`: `qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates` (AC-1) and `qualitySwitch_sameManifestSubtitle_reselectsByIdentity` (AC-2), using `selectedTextTrack()`/`textTracksDisabled()` (`PlaybackControllerReads.kt:53`, `:58`) and `awaitCondition`, not immediate asserts. Leave the existing switch tests (`:215`, `:272`) as they are.
3. `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` in the Quality region (`:798-812`): AC-3 and AC-4 tests driving `callbackSlot.captured.onTracksUpdated` before and after `PlayerAction.SelectQuality(1)`.
4. `docs/testing/player-playback.md:77-80`: note that the fork keeps carry-over on switch (pre-7d5addd2 expectations) by decision of this run, and add the two new instrumented tests.

## Risks
- If the owner wants upstream parity anyway (option A), AC-1/AC-2 still hold but AC-4 inverts and the run becomes M with device verification; Q1 decides before planning.
- Media3 text selection with an unmatched preferred language picks a DEFAULT=YES or forced rendition; AC-1 uses DEFAULT=NO to test the selector, not Media3's default rule.

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Drop the upstream `switchStream` disable (parent AC-13) and ship tests that pin the current behaviour (option B)? | Yes: the upstream bug does not reproduce here and the port needs a VM change across three callers for a pre-report window only. Alternative: option A as M (research.md: Options). | yes |
| Q2 | Should the HLS rendition with DEFAULT=YES (Media3 auto-selects it when the preferred language is absent) be covered too? | No: that is Media3 default selection, the same after `prepare`; not switch-specific. | no |
