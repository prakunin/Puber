# Research: subtitle-switch-text-disable

## Request

"Disable text during switchStream as upstream 7d5addd2 does, proven by instrumented PlaybackController tests on the P6-A fixtures" (title, `state.md`). Log: Part 2 of subtitle-restore-model; scope is the switchStream text disable (then `PlaybackController.kt:501-521`, upstream 7d5addd2) and instrumented tests on P6-A fixtures; ACs AC-13..AC-15 in `../subtitle-restore-model/brief.md`. Open analyst risk: confirm PlayerVM re-sends the subtitle after switchStream, otherwise the disable leaves subtitles off after every quality switch.

## Understanding

When the player replaces its source (quality switch, next stream candidate, link refresh), upstream 7d5addd2 first clears the controller's pending subtitle pick and the text overrides and disables the text type, then lets the VM run a fresh restore against the new stream's tracks. This fork does the opposite: it carries the old track-selection parameters and the pending pick onto the new source and re-applies the pick there. The wrong-subtitle case upstream targeted (an old pick landing on a different rendition at reused coordinates) is already blocked here by the semantic filter in `SubtitleTrackSelector` and the VM's Off re-send; what remains is a short window where the old text parameters drive selection on the new source, and the absence of a test for any of it. Porting the controller change alone would break subtitles: the fork's VM does not re-send after a switch when the rows are unchanged.

## Current state in code

| Where | What it does now |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:530-552` | `switchStream` saves `trackSelectionParameters` (text enabled, old text override, preferred text language) and re-applies them after `setMediaItem`, only replacing the video cap; `pendingSubtitleTrack` is untouched. Line numbers moved from 501-521 named in the Log. |
| `PlaybackController.kt:412-415`, `:431-435` | `prepare` clears `pendingSubtitleTrack` and builds the selector with text disabled; the precedent for "text off until the VM re-sends". |
| `PlaybackController.kt:597-611` | `selectSubtitle(null/Off)` clears pending, clears text overrides, disables text; otherwise stores pending and applies. |
| `PlaybackController.kt:259-263`, `:892-913` | Every `onTracksChanged` of the current session re-applies `pendingSubtitleTrack` through `SubtitleTrackSelector`; with no match it clears overrides, enables text and sets the preferred language, leaving Media3 default/forced selection free. |
| `SubtitleTrackSelector.kt:39-66` | Semantic filter (language, forced, readable label) before identity matchers; no positional match. Ported from 7d5addd2 in f3ddd8c6 (2026-10-14). Rejects the upstream "Spanish at reused coordinates" candidate. |
| `PlayerVM.kt:450-457`, `:470-477`, `:528` | The three `switchStream` callers: quality, next candidate, refreshed links. None resets subtitle restore state. |
| `PlayerVM.kt:953-962` | `selectQuality` only calls `switchStreamUrl`; upstream's `selectQuality` at 7d5addd2 calls `resetTrackRestoreState(preserveSubtitleSelection = true)` and empties the subtitle rows first. |
| `PlayerVM.kt:564-603` | `onTracksUpdated` finds `intendedSubtitleRow` in the new rows (`AudioTrackPreferenceResolver.kt:72-89`); it calls `selectSubtitle(row)` only when `row != previousRow`. Missing row -> Off row -> re-send disables text. Identical rows -> no re-send. |
| `PlayerVM.kt:175`, `:987`, `:999`, `:1046` | `subtitleRestoredForCurrentMedia` is reset by buffer preset, Fast DNS and `resetMediaSession`, never by a switch. |

Answer to the Log's open risk: **no**. After a switch whose new rows equal the old ones (the usual case: same hls4 master with a different cap, see manual-quality-hls4), the VM sends nothing; today the subtitle survives only because the controller keeps `pendingSubtitleTrack`. Clearing it as upstream does, without a VM change, leaves subtitles Off after every such switch.

Tests around it:

| Test | Asserts today |
|---|---|
| `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt:215-270` `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` | Text still enabled and selected right after the HLS switch (`:246-247`); upstream inverted this. Side-loaded VTT only. |
| `PlaybackControllerNetworkTracksTest.kt:272-312` `progressiveSwitch_restoresSelectedSubtitleAndPausedIntent` | Subtitle comes back by itself after a progressive switch (`:306-308`); upstream requires an explicit `selectSubtitle`. |
| `PlaybackControllerRoutes.kt:34-63` `commonHlsRoutes` | Has a `qualityLowMasterBody` hook; no HLS subtitle renditions. `player-test-fixtures/.../hls/master.m3u8` has audio renditions only. Upstream's `subtitleVariantMaster`/`subtitleVariantRoutes` helpers are not ported (grep finds none). |
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt:800-811` | Only that `selectQuality` calls `switchStream`; nothing on subtitle re-send after a switch. |
| `docs/testing/player-playback.md:77-80` | Lists the two switch tests as ported from upstream `1d3ec39e`, i.e. the pre-7d5addd2 expectations. |

Run command: `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=<class>` (brief); media3 1.11.0 (`gradle/libs.versions.toml:27`).

## Related work

| Run / commit | Relation |
|---|---|
| subtitle-restore-model (done, 7becc4d0) | Parent; Part 1 shipped AC-1..AC-12; AC-13..AC-15 belong here. AC-14 requires the VM restore after a switch, which the code above lacks. |
| player-hermetic-fixtures (done) | P6-A on master: module `player-test-fixtures`, harness, `make itest`; prerequisite AC-15 met. |
| player-callback-gate (done) | `switchStream` now opens a callback session (`bindSession`, `PlaybackController.kt:292-297`) before `setMediaItem`; the disable must sit before it or at least before `setMediaItem`, inside the same main-thread turn. |
| manual-quality-hls4 (done) | Manual quality now plays hls4 with a video cap, so a quality switch reloads the same master and yields identical subtitle rows: the no-re-send case is the common one. |
| player-audio-und-label, subtitle-und-preferred-fallback (done) | Changed `preferredTextLanguage` for undetermined rows (`PlaybackController.kt:910`); relevant to what Media3 picks in the window after a switch. |
| playback-controller-split (idea) | Deferred: text-off default and FORCED flag lack regression tests; overlaps T-tests here. |
| player-device-tests-hardening | Collects deferred minors from the fixture runs; no subtitle-switch item. |
| upstream 7d5addd2 (2026-09-29) | Source: `disableSubtitlesForSourceReplacement` in `switchStream`, VM `resetTrackRestoreState(preserveSubtitleSelection = true)` in `selectQuality`, three instrumented test changes incl. `qualitySwitch_missingPreferredLanguage_staysOffAtReusedCoordinates`. Not an ancestor of HEAD (unrelated histories). |

Possible duplicate: none.

## External findings

External findings: off (local research only).

## Options

| Option | Change | Size | Trade-offs |
|---|---|---|---|
| A. Full upstream semantics | Controller: clear pending, clear text overrides, disable text before `setMediaItem` in `switchStream`. VM: on every `switchStream` caller, mark the subtitle restore pending (`subtitleRestoredForCurrentMedia = false`, keep `intendedSubtitleRow`) so the next `onTracksUpdated` re-sends the intended row or Off. Invert the two switch tests, port the reused-coordinates test with text-only HLS subtitle routes, add VM unit tests, update `player-playback.md`. | M | Matches AC-13..AC-15 as written; no old parameter can drive selection on the new source. Subtitles blink off for the reload; restore depends on a track report (side-loaded HLS waits for text groups, `subtitleReportKind`). Touches playback, a risk area. |
| B. Tests only, keep carry-over | Pin current behaviour: port the reused-coordinates test (expect Off, no Spanish VTT request) and a missing-row VM test; leave `switchStream` as is. | S | Proves the upstream bug does not reproduce here at low risk. Leaves the window where the old override/preferred language selects on the new source; contradicts AC-13. |
| C. Narrow controller fix | Keep `pendingSubtitleTrack`, but in `switchStream` clear text overrides and disable text; the existing `onTracksChanged` re-apply turns it back on through the semantic selector. Plus tests of A. | S-M | No VM change; closes the stale-override window. Re-enable path is controller-only, so the VM's view and the player can diverge when no rows change; AC-13 wording (pending null) must change. |

Recommended: **A**, with the VM re-send as the first task and a failing-first VM unit test (identical rows after `SelectQuality` must call `selectSubtitle(row)`), so the controller disable never lands without it.

## Risks

- Subtitles Off after every quality switch if the controller part lands without the VM re-send (`PlayerVM.kt:599-601` condition). Highest risk; must be an AC with a unit test and a device step.
- Next-candidate and link-refresh switches (`PlayerVM.kt:476`, `:528`) need the same re-send; upstream only reset on `selectQuality`.
- Side-loaded HLS reports nothing until text groups appear (`subtitleReportKind` NOT_YET); text stays off until then. Acceptable, but tests must await, not assert, the restore.
- A switch racing an incoming track report of the old session is dropped by the callback gate; the restore must come from the new session's report.
- Instrumented HLS subtitle renditions need new text routes (playlist + VTT) in `PlaybackControllerRoutes.kt`; no binary fixture change, but `player-test-fixtures/.../SHA256SUMS` stays untouched only if routes are inline.
- Device check: the box shows the blink and restore only at human pace on a real title; no API probing.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Adopt full upstream semantics (option A) including the VM re-send on all three switch callers? | Yes, A on all three callers. |
| Q2 | Is a brief subtitle blink during a quality switch acceptable? | Yes; it is what upstream ships and what `prepare` already does. |
| Q3 | Reword AC-14 to name the VM re-send after a switch with unchanged rows explicitly? | Yes, add "including when the new stream reports the same rows". |

## Sources

- Files: `PlaybackController.kt`, `PlayerVM.kt`, `SubtitleTrackSelector.kt`, `AudioTrackPreferenceResolver.kt` (all under `app/src/main/java/com/kino/puber/ui/feature/player/vm/`); `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt`, `PlaybackControllerRoutes.kt`; `app/src/test/.../PlayerVMTrackSelectionTest.kt`; `player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8`; `docs/testing/player-playback.md`; `gradle/libs.versions.toml`; `Makefile`.
- Commits: 7d5addd2 (upstream), f3ddd8c6, f4b311e0, 49afcadf, 992665a9, b314cd0f, 7becc4d0.
- Runs: subtitle-restore-model (state.md, brief.md AC-13..AC-15), player-hermetic-fixtures, player-callback-gate, manual-quality-hls4, player-audio-und-label, subtitle-und-preferred-fallback, playback-controller-split, upstream-sync-triage.
