# Player overlays: auto-hide transient, keep opened

Status: draft
Date: 2026-10-10
Slug: player-overlay-autohide
Kind: bug
Size: M
Route: standard

<!--
Each section has one owner; nobody else edits it. The conductor commits this
file in the task's worktree after every phase.
  analyst        Problem, Acceptance criteria, Out of scope, Plan (quick route)
  architect      Design
  ux-designer    Visual
  test-designer  Test design
  anyone         Open questions (append only; the user answers)
Cite code as [Source: path:line]. A claim with no source is a guess.
-->

## Problem

User request: in the player, controls and popups should hide on their own, but anything
the user opened on purpose (stream info and the like) must stay until the user closes it.
Symptom the user gave: "I pressed Down and the episode cards appeared" and they stay on screen.

**Reproduced: confirmed from code** (no device needed, the path is unambiguous):

1. Series playing, controls up with focus on the button row (or Down from the video: `ShowControls(Buttons)`).
2. Press D-pad Down on the button row. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerButtonRow.kt:93] `shouldOpenEpisodesFromButtons` is true for Down on a series, and the row calls `onEpisodesClick`.
3. That is the same action as the Episodes button: `OpenEpisodesPanel` -> `openPanel(ActivePanel.Episodes)`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:560]
4. `openPanel` hides the controls and returns `CancelHide`, and nothing schedules a hide while a panel is open; `applyControlsVisibility` refuses to hide with any panel. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:99]

Observed: the episode row stays up forever; only Up (`onUp` -> `ClosePanel`), Back or picking an episode ends it. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:274]
Expected: the row is a quick peek, so after inactivity it goes away on its own. Settings, stream info and About keep staying until closed.

So the earlier classification was wrong: the Down-revealed row is not a separate surface; it is the Episodes panel, and the code gives it the same "explicit" treatment as Settings and About. Under the user's model, Episodes is transient. Root cause: confirmed; `ControlsStateMachine` has one panel kind with no timeout.

Other D-pad-revealed surfaces checked: Up/Down/OK reveal the controls bar (already auto-hides), Left/Right show the seek indicator (1.5 s). No other surface is stuck. The Episodes row's long-press context menu (`EpisodeContextMenuDialog`) is a dialog opened on purpose; it must not be torn down by the timer. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:40]

**Pause and screensaver (added requirement: "after a long pause the player should go into some kind of screensaver").**
Today the player keeps the screen on only while `isPlaying`: `PlayerView.keepScreenOn` and the window `FLAG_KEEP_SCREEN_ON` binding both follow `content.isPlaying`, and are released on dispose. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:44] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:65] No wake lock, no in-app screensaver, no manifest hook exists (grep of `app/src/main` finds only these). So after a pause the Android TV system screensaver (Ambient/daydream) is already free to start after the system's own idle timeout. Not verified on a device; whether it ever fires depends on the TV or emulator having a screensaver configured and on its timeout (a system setting, normally minutes). `isPlaying` is also false while buffering, which lets the screen sleep during a long stall; that is a side effect to keep in mind, not part of this request.

Options:
- (a) Rely on the system screensaver: nothing to build; document and verify on a device that paused playback lets it start, and that a key press wakes it with playback still paused. Cost: the delay is the user's system setting, and on devices with no screensaver nothing happens.
- (b) In-app screensaver: after N minutes paused, dim or show a backdrop/art; any key dismisses it, playback stays paused. New UI state, a timer, a visual design (a `visual` question), and it must not fight `player-overlays-port`.

Recommendation: (a) for this task, as the verification criterion AC-15, because the keep-screen-on release is already there and the request reads as "do not burn the panel". (b) is a separate board row (new screen state plus a visual design); do not fold it in here. If the user wants (b), this task stays standard and the new row takes the `visual` route.

Classification (user model):

| Overlay | Kind | Today | Source |
|---|---|---|---|
| Controls bar | transient | 4.5 s -> 6 s | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706] `CONTROLS_HIDE_DELAY_MS` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834] |
| Debug overlay | follows controls | unchanged | [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerOverlayLayers.kt:140] |
| Seek / play-pause indicators | transient | 1.5 s | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:643] |
| Skip / next-episode chips | transient | 7 s countdown | [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerCountdowns.kt:22] |
| **Episodes panel** (Down or Episodes button) | **transient (changes)** | stays forever | above |
| Settings panel incl. stream info, About panel | explicit | stays until Back / close | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47] |
| Episode context menu dialog | explicit | stays until dismissed | [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:40] |
| Resume dialog, error overlay | explicit | stay until answered | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:357] |
| Controls at end of media | explicit | held up, Back leaves | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:87] |

Further gap (plausible): the buffering-end branch reschedules the hide whenever controls are visible, which would hide the end-of-media controls after the timeout. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:199]

Overlap with in-flight work:
- `player-overlays-port` (92a9441d) changes only Compose components, including `PlayerScreenContent`; it does not touch `PlayerVM` or `ControlsStateMachine`. A key-event hook added in `EpisodesPanel.kt` is not in its change list, so no conflict expected.
- `subtitle-player-fixes` (uncommitted) adds `ControlsStateMachine.initialize` and `PlayerVM.publishPreparedContent` / `withControlsState`, and rewrites the `applyControlsState` body. This task edits the same state machine and `applyControlsState` area; rebase after it merges, and make `initialize` reset any new field.

## Acceptance criteria

- **AC-1** Given the controls are shown and nothing else is open, when 6 s pass without a key press in the bar, then the controls hide (was 4.5 s).
- **AC-2** Given a key press in the bar, when it happens before the 6 s are up, then the 6 s start over.
- **AC-3** Given a series and the controls on the button row, when the viewer presses Down, then the Episodes panel opens, and when 6 s pass with no key press then it closes by itself and the screen shows the video with no controls bar.
- **AC-4** Given the Episodes panel is open, when the viewer presses any key in it (move between cards, scroll), then the 6 s start over.
- **AC-5** Given the Episodes panel is open and the viewer opened an episode's context menu, when time passes, then the panel and the menu both stay; the timer starts again once the menu is dismissed.
- **AC-6** Given the Episodes panel opened by the Episodes button (not Down), when 6 s pass with no key press, then it closes the same way (one panel, one rule).
- **AC-7** Given the Settings panel (or the stream info page inside it) or the About panel is open, when any time passes (well beyond 6 s) with no key press, also across a buffering start and end, then it stays open until Back or close.
- **AC-8** Given an explicit panel is closed by the viewer, when it closes, then the controls come back and hide after 6 s (existing behaviour, new delay).
- **AC-9** Given the Episodes panel timed out, when the viewer presses Back, then the player is not left on the first press with a panel already gone (Back on a bare video exits as today) and no stale timer reopens anything; focus is on the video so D-pad works at once.
- **AC-10** Given the resume dialog is up, when time passes, then it stays until answered, and the controls timer starts only after the answer.
- **AC-11** Given the media ended and the end-of-media controls are up, when buffering ends or time passes, then the controls stay up until Back leaves the player.
- **AC-12** Given playback is paused, when 6 s pass with no key press, then the controls still hide (unchanged).
- **AC-13** Given the debug overlay preference is on, when the controls hide or show, then the overlay follows them (unchanged); when it is off, a closed panel leaves no readings on screen (unchanged).
- **AC-15** Given playback is paused or ended, when the player screen is up, then neither `PlayerView.keepScreenOn` nor the window keep-screen-on flag is set (so the system screensaver can start); given it plays again, then both are set. (Unit-checkable on the existing binding.)
- **AC-16** On the TV, paused for longer than the device's screensaver timeout: the system screensaver starts, and one key press returns to the paused player with playback still paused. (user judges; needs a device with a screensaver configured)
- **AC-14** On the TV (paired emulator-5554): on a series press Down, wait 7 s, the row is gone and the video is clean; open Settings > stream info, wait 30 s, it is still there.

## Out of scope

- Visual changes to any overlay (owned by `player-overlays-port`).
- Changing the 1.5 s indicator and 7 s chip timings, and the 60 s skip-dismiss memory.
- Pausing keeps no controls: controls hide on pause as today (user decision).
- Making the debug overlay permanent (user decision: it keeps following the controls).
- Subtitle work and `ControlsStateMachine.initialize` (owned by `subtitle-player-fixes`).
- A user-visible setting for the delays.
- An in-app screensaver (option (b)) unless the user chooses it; then it moves to its own board row.

## Design

<!-- architect. Decisions table, changes by layer, alternatives rejected,
risks. Not on the quick route. -->

## Visual

<!-- ux-designer. Visual and perf routes only when a screen changes. -->

## Test design

<!-- test-designer.
### Risks
| Risk | P | I | Score | Mitigation |
|---|---|---|---|---|

### Traceability
| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|

### Device checks
1. …

### Baseline (perf route)
-->

## Plan

Standard route: the architect writes the design and plan; see docs/superpowers/plans/. Points the design must settle: where the Episodes timer lives (VM job beside `controlsHideJob`, reset by a new panel-interaction action fired from `EpisodesPanel` key events); how the context menu pauses it (an action pair or a flag); the state-machine transition for "panel timed out" (close panel to a clean video, not back to the controls bar, still emitting `PanelClosed`); and the buffering-end guard for the ended-state.

## Open questions

Answered by the user:
1. Symptom: pressing Down shows the episode cards, which then do not hide. The row is transient in the user's model; explicit means opened by a deliberate action (stream info, settings, About).
2. Paused: keep hiding as today.
3. Controls timeout: 6 s instead of 4.5 s.
4. Debug overlay: keeps following the controls.

5. Episodes panel timeout: 6 s, the same as the controls, reset on every key.
6. Episodes panel timeout lands on the clean video, not the controls bar.
7. Pause screensaver: option (a), the system screensaver. No in-app screensaver; this task only verifies on a device that a long pause lets the system screensaver start.
