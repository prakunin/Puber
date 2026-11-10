# Research: seek-blocked-by-skip-prompt

## 1. Request

Owner, translated from Russian: "In the player, if I seek with D-pad Left/Right and land in the section 'Skip intro' / 'Go to next episode', seeking stops working; I have to dismiss the popup, start seeking again, and the same popup appears again." Expected: Left/Right seeking continues through the section; the skip prompt must not swallow seek keys nor re-appear in a way that blocks scrubbing.

## 2. Understanding

Both prompts (skip segment and next episode) are one shared composable, `PlayerTimedActionOverlay`. It takes D-pad focus as soon as it appears. Left/Right seeking is handled only by the video surface and the seek bar, through `onKeyEvent` on those two nodes. Once focus sits on the prompt's two-button row, Left/Right become focus moves inside that row and no longer seek. The VM shows the prompt on the next 500 ms position tick after a seek lands inside a segment, or inside the credits lead-in. A seek takes the prompt down, and the next tick puts it back up. For the next episode, a seek also clears the earlier "cancel", so that prompt comes back after every seek by design. When the prompt goes away, nothing gives focus back to the video surface.

## 3. Current state in code

Paths are relative to `app/src/main/java/com/kino/puber/ui/feature/player/`.

| Where | What it does |
|---|---|
| `component/PlayerTimedActionOverlay.kt:254` | `BackHandler(enabled = visible, onBack = onCancel)`: Back cancels the prompt. |
| `component/PlayerTimedActionOverlay.kt:262-270` | `LaunchedEffect(visible)` calls `requestFocus()` on the primary or the cancel button every time the prompt appears. It does not check whether controls are up or a seek is in progress. |
| `component/PlayerTimedActionOverlay.kt:339-372` | `Row` holding [cancel X, action]. It has no key handler, so Left/Right do default focus traversal between the two buttons. |
| `component/SkipSegmentOverlay.kt:28` | `focusPrimaryAction = false`: skip prompts focus the cancel X, so OK dismisses and Right reaches "Skip". |
| `component/NextEpisodeOverlay.kt:65` | `focusPrimaryAction = true`: the next-episode prompt focuses "Next episode". |
| `component/PlayerOverlayLayers.kt:105-133` | The prompts are drawn as siblings of the video surface, not its children, so key events on the prompt never reach the surface's `onKeyEvent`. The skip prompt is hidden while the next-episode countdown or the resume dialog is up. |
| `component/PlayerVideoSurface.kt:72-83, 213-227` | The focusable video surface maps `DPAD_LEFT`/`RIGHT` (and media FF/RW) to `PlayerAction.SeekBackward`/`SeekForward` through `onKeyEvent`. This works only while the surface holds focus. |
| `component/PlayerProgressBar.kt:195-208, 292-315` | The seek bar handles Left/Right the same way, only while it holds focus. |
| `component/PlayerScreenContent.kt:72-89` | `PlayerFocusEffects` puts focus back on the player only when `controlsVisible` or `activePanel` changes. Nothing reacts to a prompt hiding, so the focused button leaves the composition with no one owning focus. |
| `vm/PlayerVM.kt:706-720` | `seekForward`/`seekBackward` call `playbackController.seekTo`, using `SeekHandler` steps 10/10/20/30/60/60 s with a 1.5 s reset (`vm/SeekHandler.kt:3-15`). |
| `vm/PlaybackController.kt:538-541` | `seekTo` fires `callback.onSeekPerformed()`. |
| `vm/PlayerVM.kt:1670-1677` | `onSeekPerformed` clears `activeSkipSegment` and `nextEpisodeCountdown`, cancels both countdown jobs, and sets `countdownDismissed = false`. |
| `vm/PlayerVM.kt:1492, 1510-1515, 1958` | The tick runs every 500 ms (`POSITION_UPDATE_INTERVAL_MS`). While playing it runs `checkEarlyNextEpisode` and `checkSkipSegment`. This is where the prompt goes back up after a seek. |
| `vm/PlayerVM.kt:1679-1708` | `handleActiveSkipSegment`: the prompt opens when the playhead is inside a segment (2 s lead-in, `model/PlayerCountdowns.kt:32`), unless that type is in `dismissedSegmentType`. |
| `vm/PlayerVM.kt:1798-1809` | `cancelSkipSegment` remembers the dismissed type for `SKIP_DISMISS_TTL_MS` = 60 s (`model/PlayerCountdowns.kt:77`). A later seek inside that window should not bring the prompt back. |
| `vm/PlayerVM.kt:1543-1558, 1384-1390` | `checkEarlyNextEpisode` opens the next-episode countdown from the credits start minus the lead-in. Cancelling sets `countdownDismissed`, and any seek resets it (line 1676). |
| `vm/PlayerVM.kt:1449-1466` | The VM's Back path also cancels whichever prompt is up. |
| `component/PlayerControlsOverlay.kt:146-152` | The controls overlay's `onPreviewKeyEvent` only restarts the hide timer and never consumes keys. With controls visible, the prompt still takes focus away from the seek bar (follows from `PlayerTimedActionOverlay.kt:264`, not seen on a device). |

How the failure plays out, read from the code and not yet reproduced on a device:

1. Left/Right on the video surface seeks, the seek lands inside a segment, and the next tick shows the prompt, which takes focus.
2. Further Left/Right presses move focus between X and the action button, so seeking stops.
3. Back cancels the prompt and the focused node leaves the composition. Where Compose puts focus then is `unverified`. The first D-pad press probably only restores focus to the video surface, which matches "start seeking again".
4. For next episode, the next seek clears the dismissal and the prompt comes back (`PlayerVM.kt:1676`). For skip segments, a cancelled type stays down for 60 s, so a skip prompt coming back right after a cancel would need a second segment type or the TTL running out. Which prompt the owner saw is `unverified`.

Existing tests:

| Test | Covers | Relevance |
|---|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPromptDismissalTest.kt` | rewind-after-cancel (34), TTL expiry (53), `seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (81), seek takes prompt down (122) | Line 81 pins the "next-episode prompt returns after a seek" behaviour. |
| `PlayerVMCreditsPromptTest.kt`, `PlayerVMSkipCountdownFitTest.kt`, `PlayerVMCountdownClockTest.kt` | VM prompt logic | none |
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/component/PlayerVideoSurfaceTest.kt` | `playerActionForKeyCode` mapping | none |
| (none) | Focus or key-routing tests for `PlayerTimedActionOverlay` | The only Compose UI test in the player is `app/src/androidTest/.../PlayerAboutPanelTest.kt`. |

## 4. Related work

| Item | Relation |
|---|---|
| `player-overlays-port` (stage impl, branch `player-overlays-port`, `92a9441d`) | Edits `PlayerTimedActionOverlay.kt` (+44/-44) and rewrites the focus effects in `PlayerScreenContent.kt` (`requestAcrossFrames`). Same files, so merge conflicts are likely. |
| `player-focus-after-panel-close` (idea) | Same family: `requestControlsFocus` silently loses a focus request (`PlayerScreenContent.kt:84-88`). |
| `focus-request-helper` (idea) | "One `tryRequestFocus()`", a shared focus-request helper. |
| `player-vm-split` (idea, L) | Plans to move the skip-segment logic out of `PlayerVM` (audit SOLID-1). |
| `1a2c6cde` (2026-09-08) "Rework the player timed-action countdowns and their tests" | Introduced `onSeekPerformed` as the seek signal. `9ab05955` added the test that keeps the next-episode prompt coming back after a seek. |
| `c29f405b` Release 1.7.4 | Introduced `focusPrimaryAction`, i.e. the prompt auto-focus. |
| Upstream `rovkinmax/Puber` (head `fb6a22b6`, 2026-11-03) | No fix found. Its `SkipSegmentOverlay.kt:55-59` and `NextEpisodeOverlay.kt:55-59` also call `requestFocus()` on appear (last touched in `6aa310f2`, 2026-06-22). Log searched for skip/seek/intro/countdown: `4a6829de` and `e6126d91` deal with countdown timing, not key routing. |
| `docs/design/player-controls/README.md` | Does not cover the timed-action prompt. No design stand governs the prompt. |

Duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| Android TV playback guidance: Left/Right single press seeks ±N s, hold scrubs, while playing or paused. Up/Down peeks at the controls. The guidance has no exception for overlays. | https://developer.android.com/training/tv/playback/controls (read 2026-11-10) |
| Compose focus: `FocusRequester.requestFocus()` from a `LaunchedEffect`; `focusRestorer()` for returning focus to a container. The page does not say where focus goes when the focused node is removed (`unverified` for Compose BOM `2026.08.00`). | https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior (read 2026-11-10) |
| Other TV players (Plex, Emby) have had the same class of problem: the skip-intro button gaining or stealing focus from other controls. | https://forums.plex.tv/t/android-tv-shield-skip-intro-opens-overlay-instead/803736 ; https://emby.media/community/topic/126842-continue-playing-niggle-when-skip-into-is-showing |
| tv-samples issue: a visible control should intercept D-pad events so that a focus move does not trigger a seek. This is the mirror case. Resolution `unverified`. | https://www.github.com/android/tv-samples/issues/157 |

Project versions: Compose BOM `2026.08.00`, tv-material `1.1.0`, media3 `1.11.0` (`gradle/libs.versions.toml:7,8,27`).

## 6. Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| 1 (recommended) | **The prompt passes seek keys through and gives focus back.** (a) `onPreviewKeyEvent` on the prompt row sends `DPAD_LEFT`/`RIGHT` (and media FF/RW) to `SeekBackward`/`SeekForward` and consumes them. (b) The skip prompt focuses its action button, so OK = skip and Back = dismiss, matching the next-episode prompt. (c) The VM keeps the prompt down during a seek burst (the `SeekHandler` reset window, 1.5 s) and raises it once the burst ends, so it does not flash on every 500 ms tick. (d) `PlayerFocusEffects` gives focus back to the player, or to the controls target, when a prompt hides. | Fixes all three symptoms. The X button can no longer be reached with the D-pad and becomes a visual hint for Back. Touches overlay, layers, focus effects and VM, all in a TV-focus risk area. Conflicts with `player-overlays-port`. | M |
| 2 | **Focus guard only, in the VM and the focus effects.** Never raise a prompt during a seek burst, and give focus back when it hides. The overlay key handling stays as it is. | Smallest change. Once the prompt is up (the viewer stopped inside the segment), the next Left/Right still moves focus inside the row instead of seeking, so the owner's "start seeking again" case stays broken. | S |
| 3 | **The prompt never takes focus.** Make the chips non-focusable. While a prompt is up, the video surface maps OK to the prompt action and Back to dismiss, and Left/Right keep seeking unchanged. | Seeking is fully natural and there is a single focus owner. It changes the meaning of OK (normally show controls or play/pause) while a prompt is up, the focus ring disappears so a different visual cue is needed, and screen-reader focus on the prompt is lost. Needs a UX decision. | M |

## 7. Risks

- **The next-episode prompt is still in the way after every seek.** `seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (`PlayerVMPromptDismissalTest.kt:81`) is intended behaviour. With option 1 it no longer blocks seeking, but it still comes back after every seek; whether that stays is Q2.
- **A seek during the next-episode countdown cancels it** (`PlayerVM.kt:1671`). A Right press meant to "seek" on the prompt therefore removes the prompt; this is already the case today and keeps working with option 1.
- **Focus timing.** A same-frame `requestFocus()` failing silently is a known defect in this area (`player-focus-after-panel-close`). Restoring focus on hide can hit it too. `player-overlays-port` introduces `requestAcrossFrames` for this.
- **Merge conflict** with the `player-overlays-port` branch in `PlayerTimedActionOverlay.kt` and `PlayerScreenContent.kt`.
- **Device verification is required** (TV focus, D-pad, overlays). A segment needs real skip data (TMDB/IntroDB) or a fixture, and a worktree build has no TMDB token (AGENTS.md), so the device may not get skip segments without a fixture.
- **Held-key scrubbing.** Repeated `ACTION_DOWN` events while the prompt is up must keep going to seek, and none of them may land on a button as OK.

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Which prompt button should OK hit by default: skip (action) or dismiss (X)? Today the skip prompt focuses X, the next-episode prompt focuses the action. | Focus the action for both. Back already dismisses, and Left/Right are needed for seeking. |
| Q2 | Should the next-episode prompt still come back after a seek that follows "cancel"? | Yes. Keep it (pinned by the test added in `9ab05955`); once it no longer blocks seeking, it is harmless. |
| Q3 | When the controls bar is up and the seek bar has focus, should a prompt that appears take focus? | No. Leave focus on the seek bar; the prompt shows above the bar and the bar's Left/Right keep seeking. |
| Q4 | Should this wait for `player-overlays-port` to merge, or go first? | Go first as a small focused fix. `player-overlays-port` rebases on top of it. |

## 9. Sources

- Files: `component/PlayerTimedActionOverlay.kt`, `component/SkipSegmentOverlay.kt`, `component/NextEpisodeOverlay.kt`, `component/PlayerOverlayLayers.kt`, `component/PlayerVideoSurface.kt`, `component/PlayerProgressBar.kt`, `component/PlayerScreenContent.kt`, `component/PlayerControlsOverlay.kt`, `vm/PlayerVM.kt`, `vm/PlaybackController.kt`, `vm/SeekHandler.kt`, `model/PlayerCountdowns.kt`, `app/src/test/.../PlayerVMPromptDismissalTest.kt`, `gradle/libs.versions.toml`, `docs/design/player-controls/README.md`, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`.
- Commits: `1a2c6cde`, `9ab05955`, `c29f405b`, `92a9441d` (branch `player-overlays-port`); upstream `fb6a22b6`, `6aa310f2`, `4a6829de`, `e6126d91`, `803f85ef`.
- Runs: `player-overlays-port`, `player-focus-after-panel-close`, `focus-request-helper`, `player-vm-split`, `upstream-sync-triage`.
- URLs: https://developer.android.com/training/tv/playback/controls ; https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior ; https://forums.plex.tv/t/android-tv-shield-skip-intro-opens-overlay-instead/803736 ; https://emby.media/community/topic/126842-continue-playing-niggle-when-skip-into-is-showing ; https://www.github.com/android/tv-samples/issues/157
