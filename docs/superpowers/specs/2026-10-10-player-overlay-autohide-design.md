# Player overlays: auto-hide transient, keep opened

Status: approved (2026-10-10; the user delegated questions 8 and 9 and will adjust after testing the build)
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

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Where does the Episodes timer live? | It reuses the controls timer: the existing `controlsHideJob` and `scheduleControlsHide()`. A new state machine call, `onHideTimeout()`, decides what the timeout means: hide the bar, close Episodes, or nothing. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:138] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706] | The user set one delay (6 s) for both (answers 3, 5), and the bar and Episodes are never up together, because `openPanel` hides the bar. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:55] With one timer there is only one timer to cancel in each place (`onCleared`, buffering, panel open and close), so no second job can race the first. |
| D2 | What does the timeout do with Episodes open? | While playing or paused, `onHideTimeout()` sets `activePanel = None`, `controlsVisible = false`, `focusTarget = null`, and returns `[PanelClosed]` (no `ScheduleHide`). After playback has ended (`state.playbackEnded`, see D13), it closes to the end-of-media controls instead: `controlsVisible = true`, `focusTarget = lastPanelOpener` (the Episodes button), `[PanelClosed]`. Those controls then stay (D7). Before it processes the effects, the VM timer job clears `exitWhenPanelCloses` whenever the effects contain `PanelClosed`. | Answer 6: the viewer lands on the clean video. Answer 9: a timeout never makes the player exit; after the end the viewer lands on the ended controls. `PanelClosed` still runs `onPanelClosed()`: it drops debug readings and re-runs `checkEarlyNextEpisode()`. So the timeout is not a second dismissal path that forgets these steps. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:733] Clearing the flag in the job, not in `processEffects`, keeps `processEffects` untouched (R5). Back on the panel still exits as today, because `handleBack` goes through `closePanel` and leaves the flag alone. Back on the ended controls exits through `playbackEnded`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:73] |
| D3 | How does `openPanel` treat Episodes? | `openPanel(Episodes)` returns `[ScheduleHide]`. Settings and About keep `[CancelHide]`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47] | Episodes opened by Down and by the Episodes button go through the same action, so AC-3 and AC-6 hold together. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:560] |
| D4 | How does a key in the panel restart the 6 s? | `PlayerSettingsPanels` passes `Modifier.onPreviewKeyEvent { if (ACTION_DOWN) onAction(ResetControlsTimer); false }` through the `modifier` parameter `PlayerEpisodesPanel` already has. The same action already serves the controls bar. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:137] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:148] [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:24] | It copies `ControlsScrim` (preview, ACTION_DOWN, never consumes). [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerControlsOverlay.kt:146] The parameter is applied to the `BoxWithConstraints` that is an ancestor of every card, so the hook sees each key while focus is in the row. Auto-repeat counts as activity on purpose: a held D-pad is a viewer at work. `EpisodesPanel.kt` stays untouched, which keeps it out of the `player-overlays-port` merge (see Risks R7). |
| D5 | How does the context menu hold the panel? | Add the action `PlayerAction.SetEpisodesPanelHeld(held: Boolean)`. `PlayerSettingsPanels` sends `true` where it sets `episodeContextMenuItem` and `false` in the dialog's `onDismiss`. The state machine keeps `State.episodesPanelHeld`: `holdEpisodesPanel(true)` returns `[CancelHide]` and `holdEpisodesPanel(false)` returns `[ScheduleHide]`, and both only act while Episodes is open. `onHideTimeout()` also refuses to close a held panel. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:40] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:47] | The menu is Compose-local state, so the VM has to be told. Every way out of the dialog runs `onDismiss`: an action calls `dismiss()` before `onAction`. [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/TvContextMenu.kt:117] A flag in `State`, not a private var, is reset for free by `subtitle-player-fixes`' `initialize()`, which assigns `state = State(...)`. Checking the flag in `onHideTimeout` as well as cancelling the job means a stray reschedule cannot close a held panel. |
| D6 | What stops a stale `ClosePanel` from reopening the bar? | `closePanel()` returns `emptyList()` and leaves the state alone when `activePanel == None`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:59] | The race: the timeout closes Episodes, and an Up already routed to the still-composed row sends `ClosePanel` (the row's Up handler calls `onDismiss`). [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:274] Today that would set `controlsVisible = true` with focus on the Episodes button, and AC-9 forbids anything reappearing. `selectEpisodeById` calls `closePanel()` before switching episode, and the no-op is harmless there. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:868] |
| D7 | How do the end-of-media controls survive a timeout (AC-11)? | `onHideTimeout()` does nothing while `state.playbackEnded` and no panel is open. The buffering-end reschedule (D12) is harmless here because the timeout is a no-op. | The gap is wider than the buffering-end branch [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:199]: any key in the ended controls also fires `ResetControlsTimer`, which reschedules the hide. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:558] One guard where the timeout lands covers every scheduler. |
| D8 | What does the hide delay become? | `CONTROLS_HIDE_DELAY_MS = 6_000L`, keeping the name, with a KDoc saying it also bounds the Episodes panel. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834] | Answers 3 and 5. Renaming it would only add diff. |
| D9 | Can a timer that fires while the player is leaving start anything? | The `scheduleControlsHide` job returns early `if (closing)`. | `exitPlayer()` does not cancel `controlsHideJob`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1798] If an Episodes timeout fired during the drain, it would run `onPanelClosed()` and then `checkEarlyNextEpisode()`, which can start a countdown that `exitPlayer` already cancelled. |
| D10 | How is AC-15 (keep-screen-on) pinned down? | No behaviour change. Add `internal fun PlayerContentState.shouldKeepScreenOn(): Boolean = isPlaying` in `PlayerVideoSurface.kt` and use it at the three call sites, so a JVM test can pin the rule. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:45] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:54] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:65] | `WindowKeepScreenOnBinding` is private and Android-bound. The rule is the part that can regress (for example, someone adding `|| isBuffering`). Together with VM tests that `isPlaying` goes false on pause and on end, this covers AC-15 without Robolectric. `player-overlays-port` does not touch this file. |
| D11 | Is any change needed for the system screensaver (AC-16)? | No. AC-16 is a device check only. | The keep-screen-on flags are already released when not playing (see Problem). |
| D12 | How does the Episodes timer survive a buffering blip (test-designer R8)? | In the buffering-end branch, only the condition changes: `if (controlsVisible \|\| controlsStateMachine.isEpisodesPanelAwaitingTimeout())`. The new state machine query returns `state.activePanel == Episodes && !state.episodesPanelHeld`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:204] | Buffering start cancels `controlsHideJob` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:197], and buffering end reschedules only when `controlsVisible`, which is false while Episodes is open. So any stall would bring back the original stuck panel. Settings and About are never scheduled (AC-7), and a held panel is not scheduled either (R9). The content-based `controlsVisible` read stays: until `subtitle-player-fixes` adds `initialize`, the published content can show the controls while the machine says hidden, and dropping that read would leave the first controls of new media stuck after a stall. Changing one condition line in a callback that worktree does not touch keeps the merge free. |
| D13 | How does the machine know playback ended while a panel was open? | New `ControlsStateMachine.markPlaybackEnded()` sets `playbackEnded = true` and changes nothing visible. `PlayerVM.onPlaybackEnded()` calls it when `content.activePanel != ActivePanel.None`, before the existing branches. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1232] | Today an end under a panel only sets `exitWhenPanelCloses` (film or last episode) or does nothing (other episodes) [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1240], so the machine cannot tell D2's two cases apart. `onPlaybackResumed()` already clears the flag when anything plays again. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:93] Side effect: an explicit close of any panel after the end now leaves the controls up, and Back then exits, the same as the ended controls shown without a panel (AC-11). Earlier they hid after the delay and left a still frame. |

### Changes by layer

**API / repository:** none.

**State machine** (`ControlsStateMachine.kt`):
- `State` gets `val episodesPanelHeld: Boolean = false`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:8]
- `openPanel(panel)`: copies `episodesPanelHeld = false` and returns `if (panel == ActivePanel.Episodes) listOf(ScheduleHide) else listOf(CancelHide)`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47]
- `closePanel()`: returns early with `emptyList()` when `activePanel == None`, and otherwise copies `episodesPanelHeld = false`. Effects stay the same. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:59]
- New `fun onHideTimeout(): List<Effect>`. This replaces `applyControlsVisibility(visible: Boolean)`, whose only caller is `scheduleControlsHide`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:99] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:710] The cases, in order:
  - Episodes open, not held, and `playbackEnded`: `activePanel = None`, `controlsVisible = true`, `focusTarget = lastPanelOpener`, `episodesPanelHeld = false`, and return `[PanelClosed]`.
  - Episodes open and not held: close the panel to clean video (`controlsVisible = false`, `focusTarget = null`) and return `[PanelClosed]`.
  - Any other panel open: `[]`.
  - `playbackEnded`: `[]`.
  - Otherwise: `controlsVisible = false`, `focusTarget = null`, and return `[]`.
- New `fun isEpisodesPanelAwaitingTimeout(): Boolean = state.activePanel == ActivePanel.Episodes && !state.episodesPanelHeld` (D12).
- New `fun markPlaybackEnded() { state = state.copy(playbackEnded = true) }` (D13).
- New `fun holdEpisodesPanel(held: Boolean): List<Effect>`. When `activePanel != Episodes` it returns `[]` and leaves the state alone. Otherwise it sets the flag and returns `[CancelHide]` when held, `[ScheduleHide]` when released.
- Put the new functions at the end of the class, and leave line 36 (`lastPanelOpener`) and the lines around it alone. `subtitle-player-fixes` inserts `initialize` right there.

**ViewModel** (`PlayerVM.kt`):
- `scheduleControlsHide()` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706]: the job body becomes `delay(CONTROLS_HIDE_DELAY_MS); if (closing) return@launch; val effects = controlsStateMachine.onHideTimeout(); if (ControlsStateMachine.Effect.PanelClosed in effects) exitWhenPanelCloses = false; applyControlsState(); processEffects(effects)`. Running `processEffects` while the job is still active is safe: the `PanelClosed` path does not schedule a hide, so the job never cancels itself mid-body.
- `onAction` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:543]: one new branch, `is PlayerAction.SetEpisodesPanelHeld -> holdEpisodesPanel(action.held)`, and a private `holdEpisodesPanel` that follows the `closePanel()` pattern (machine call, `applyControlsState()`, `processEffects`). [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:727]
- `CONTROLS_HIDE_DELAY_MS` changes from `4_500L` to `6_000L`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834]
- Buffering-end branch: only the condition line changes (D12). [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:204]
- `onPlaybackEnded()`: one new line, `if (content.activePanel != ActivePanel.None) controlsStateMachine.markPlaybackEnded()`, placed right after `val content = state.content` (D13). [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1235]
- Not touched: `applyControlsState`, `processEffects`, `openPanel` and `preparePlayback`. These are the lines `subtitle-player-fixes` rewrites or sits next to.

**Model** (`PlayerAction.kt`): add `data class SetEpisodesPanelHeld(val held: Boolean) : PlayerAction` under `// Panels`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:26]

**Screen / DI:** none. The Koin module, the screen `key` and `puberViewModel` all stay as they are.

**Compose** (`PlayerSettingsPanels.kt`, the only Compose file whose behaviour changes) [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:35]:
- `PlayerEpisodesPanel(modifier = Modifier.onPreviewKeyEvent { ... ResetControlsTimer ...; false }, ...)`. Remember the modifier against `onAction`. The hook never consumes the event, so focus, the Up-to-close handler and long-press keep working.
- `onEpisodeContextMenu = { episodeContextMenuItem = it; onAction(PlayerAction.SetEpisodesPanelHeld(true)) }`, and the dialog gets `onDismiss = { episodeContextMenuItem = null; onAction(PlayerAction.SetEpisodesPanelHeld(false)) }`.
- `PlayerVideoSurface.kt`: the D10 extraction only.
- `TrailerOverlay.kt`: the comment on its `CONTROLS_HIDE_DELAY_MS` changes and the value does not (answer 8). [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt:47]
- Not touched: `EpisodesPanel.kt`, `PlayerScreenContent.kt`, `PlayerButtonRow.kt`, `PlayerOverlayLayers.kt`. Focus after the timeout comes from the existing `PlayerFocusEffects`: it requests player focus when `!controlsVisible && activePanel == None`, and its key includes `activePanel`, so the change from Episodes to None re-runs it. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreenContent.kt:77]

**Resources:** none. No new strings.

**Existing tests that change meaning** (for the test-designer):
- `openPanel_episodes_keepsPlaybackRunning` expects `[CancelHide]` and becomes `[ScheduleHide]`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt:72]
- The two `applyControlsVisibility_*` tests move to `onHideTimeout`. [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt:190]
- Append new tests at the end of the file. `subtitle-player-fixes` adds its tests after `setUp` (line 19), so the two edits do not collide.

### Alternatives rejected

- **A separate `episodesHideJob` beside `controlsHideJob`:** a second timer to cancel at every site, and the only difference would be which flag it clears.
- **The timeout returns to the controls bar (`closePanel()` + `ScheduleHide`):** answer 6 rules it out, and it would cost the viewer 6 more seconds of controls.
- **Moving the context-menu item into VM state:** a bigger change to the content state and `PlayerSettingsPanels`. The hold only needs one boolean.
- **A `LaunchedEffect(menuOpen)` that sends the hold:** one frame late, and it fires `false` on first composition. Explicit calls at the two transition points are exact.
- **A key hook inside `EpisodesPanel.kt` (new `onInteraction` param):** behaves the same, but touches a file `player-overlays-port` edits.
- **Guarding the buffering-end branch in the VM:** fixes one of the two schedulers. The guard in `onHideTimeout` covers both.
- **Pausing the timer while paused:** the user decided controls keep hiding (answer 2).

### Risks (highest first)

- **R1 Focus after the panel auto-closes.** The row's focus group cancels any exit unless `allowFocusExit` is true. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:184] The timeout asks for player focus while the row is still composed during its 180 ms exit animation. Back and Up already leave the row by programmatic focus requests (to the Episodes button), so the same mechanism is in use. But the target is new (the `AndroidView` surface), and a single `requestFocus` attempt may fail. `player-overlays-port` replaces it with `requestAcrossFrames()`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreenContent.kt:80] Mitigation: device check AC-14/AC-9. After the timeout, press Left: a seek indicator must appear. If focus is stuck, the fix is to let the exit through when `!visible` in `PlayerEpisodesPanel`. That is a one-line change in a port-owned file, so it would be done after the port merges.
- **R2 The timer races a key event.** Both run on the main thread, so they are serialized. A key that arrives after the timeout but is routed to the still-composed row either fires `ResetControlsTimer` or `ClosePanel`. `ResetControlsTimer` only schedules another timeout, which then lands on a bare video and is a no-op hide. `ClosePanel` is a no-op under D6. OK on a card at that moment can still select an episode. That is the viewer's choice and acceptable.
- **R3 Timer lifecycle.** The job runs in `viewModelScope` and is cancelled in `onCleared`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1824] D9 covers the closing window. Koin: no new scoped object, so the VM lifetime does not change.
- **R4 An episode that ends with Episodes open (answered: question 9).** The timeout never exits. It closes the panel to the end-of-media controls, which stay until Back (D2, D13). For the last episode, `exitWhenPanelCloses` is set at the end [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1240], and the timer job clears it, so only Back leaves. For a non-last episode, `onPanelClosed` re-runs `checkEarlyNextEpisode()`, so the countdown may start over the ended controls, the same as an explicit close today. Test consequence: the test designer's `lastEpisode_episodesTimeout_exitsThePlayer` is replaced by `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`. That test asserts, after the end and 6 s idle: `activePanel == None`, `controlsVisible == true`, no `router.back`. After 60 s more the controls are still up, and one Back then calls `router.back`. The plan carries it.
- **R8 Buffering blip with Episodes open (test-designer).** Fixed by D12. The paired tests `episodesPanel_stillClosesAfterBufferingBlip` and `settingsPanel_staysOpenAcrossBufferingStartAndEnd` pin both sides.
- **R5 Merge with `subtitle-player-fixes`.** That worktree is uncommitted. It touches `ControlsStateMachine` (inserts after line 36), `PlayerVM.applyControlsState` and `preparePlayback`, and the setUp area of `ControlsStateMachineTest`. This design leaves those hunks alone and puts new code at the end of the class. The new `State` field is reset by `initialize` because `initialize` builds a fresh `State`. Whichever task merges second rebases. The expected conflict is none, or the `State` KDoc context at most.
- **R6 Context menu hold never released.** If the dialog left composition without `onDismiss`, the panel would stay up for good, which is today's behaviour, so no regression. `openPanel`, `closePanel` and `initialize` all clear the flag.
- **R7 Merge with `player-overlays-port` (92a9441d, not merged).** Correction to the Problem section: it does edit `EpisodesPanel.kt` and `PlayerScreenContent.kt`. This design touches neither, so there is no textual conflict, and its `requestAcrossFrames` focus change only helps R1.
- **Recomposition:** the remembered modifier and the hold lambdas are created inside `PlayerSettingsPanels`, which already recomposes with every content change. No new state is read in composition. **Main thread / memory:** one `launch` per key press, cancelled before relaunch (same as the controls bar today). **Baseline profile:** unaffected (no new screen or start path).

### Open questions (architect)

None open. Question 8 is answered: the trailer stays at 4.5 s and only its comment changes, so it no longer claims to match `PlayerVM.CONTROLS_HIDE_DELAY_MS`. The new comment says the delay is the trailer's own, shorter than the player's 6 s. [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt:47] Question 9 is answered and folded into D2, D13 and R4.

## Visual

<!-- ux-designer. Visual and perf routes only when a screen changes. -->

## Test design

### Risks

Scored from the Problem and the architect's R1-R7, plus one gap found while reading the buffering callback (R8).

| Risk | P | I | Score | Mitigation |
|---|---|---|---|---|
| R1 Focus stuck in the Episodes row after auto-close (Left would not seek) | 2 | 3 | 6 | Not unit-testable (real Compose focus plus exit animation). Device check D2; `PlayerFocusEffects` input is pinned by a VM test (`activePanel == None`, `controlsVisible == false` after the timeout). |
| R2 Timer races a key (stale `ClosePanel` reopens the bar) | 2 | 2 | 4 | State machine test for the `closePanel` no-op (D6) plus a VM test with a stale `ClosePanel` after the timeout. |
| R3 Timer outlives the player (`closing`, `onCleared`) | 1 | 2 | 2 | One VM test for `closing` (D9); accepted otherwise. |
| R4 Last episode ends with Episodes open and 6 s later the player exits | 2 | 2 | 4 | VM test pins the chosen behaviour (open question 9); the device check does not cover it. |
| R5 Merge with `subtitle-player-fixes` | 2 | 2 | 4 | New tests appended at the end of the file; `initialize` reset covered by a test that is added after that merge (P2). |
| R6 Hold never released | 1 | 2 | 2 | Accepted; `openPanel`/`closePanel` clearing the flag is covered in the state machine tests. |
| R7 Merge with `player-overlays-port` | 1 | 2 | 2 | Accepted; no shared file. |
| R8 Buffering start cancels `controlsHideJob`; buffering end reschedules only when `controlsVisible`. With Episodes open `controlsVisible` is false, so after any buffering blip the Episodes panel never times out (AC-3/AC-4 broken in the field, and a stuck panel is the original bug) | 3 | 3 | 9 | P0 VM test `episodesPanel_stillClosesAfterBufferingBlip`. The test is expected to FAIL on the design as written; the architect/implementer must make the buffering-end branch reschedule when Episodes is open and not held (for example `controlsVisible \|\| activePanel == Episodes`). Settings stays unscheduled (AC-7): covered by a paired test. |
| R9 Hold lost across buffering: buffering end reschedules while the context menu is open and closes the panel under the menu | 2 | 3 | 6 | State machine guards it (`onHideTimeout` refuses a held panel); P0 VM test with hold plus buffering plus 30 s. |
| R10 `shouldKeepScreenOn` regresses to include buffering and blocks the screensaver | 1 | 2 | 2 | P1 pure test; accepted at that level. |

Highest score 9 (R8).

### Existing tests that change meaning

In `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt`:
- `openPanel_episodes_keepsPlaybackRunning` currently asserts `[CancelHide]`. It is edited to expect `[ScheduleHide]` (D3), and the name should stay the same so history is readable. Add an assertion that `controlsVisible` is still false.
- `applyControlsVisibility_false_withOpenPanel_doesNotHide` and `applyControlsVisibility_false_noPanel_hides` call a function that is removed. Rewrite them against `onHideTimeout()`: keep the Settings case (state untouched, `[]`) and the no-panel case (`controlsVisible == false`, `focusTarget == null`, `[]`). Rename them `onHideTimeout_withSettingsOpen_doesNotHide` and `onHideTimeout_noPanel_hidesControls`.
- No other existing test is touched. `PlayerVMTest`-family tests that rely on 4.5 s (grep `4_500`, `4500`, `CONTROLS_HIDE_DELAY_MS` in `app/src/test`) must be re-checked by the implementer; none was found at design time.

New tests in `ControlsStateMachineTest` are appended at the very end of the class (after the last test), never near `setUp`, so the merge with `subtitle-player-fixes` (which edits the area after line 19) stays clean. VM-level tests go in a new file `PlayerVMOverlayAutoHideTest.kt` (same package, extends `PlayerVMTestFixture`, `@RegisterExtension MainDispatcherExtension`, `runTest(mainDispatcher.dispatcher.scheduler)` with `advanceTimeBy`, ending with `vm.testCancelScope()`, the pattern of `PlayerVMPromptDismissalTest`). A new file cannot collide with anyone. Pure rule tests go into `PlayerVideoSurfaceTest`.

Virtual time: boundaries are asserted at 5_900 ms (still up) and 6_000 ms+1 (gone). Use `advanceTimeBy(5_900)` then `advanceTimeBy(200)`.

### Traceability

SM = `ControlsStateMachineTest`; VM = `PlayerVMOverlayAutoHideTest`; VS = `PlayerVideoSurfaceTest`.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | VM `controlsBar_staysAt5900ms_hidesAfter6000ms` | Delay is 6 s, not 4.5 s (fails on master: bar gone at 5.9 s). |
| AC-1 | unit | P0 | SM `onHideTimeout_noPanel_hidesControls` (rewritten) | Timeout hides the bar and clears focus target. |
| AC-2 | unit | P0 | VM `resetControlsTimer_at4s_restartsTheSixSeconds` | A key at 4 s keeps the bar up at 9.9 s, gone at 10.1 s. |
| AC-3 | unit | P0 | VM `openEpisodesPanel_closesItself_after6s` (**failing first**) | Panel open, `activePanel == None`, `controlsVisible == false`, `controlsFocusTarget == null` after 6 s; open at 5.9 s. |
| AC-3 | unit | P0 | SM `onHideTimeout_episodesOpen_closesToCleanVideoAndReportsPanelClosed` | State: panel None, controls hidden, focus null, effects `[PanelClosed]` only (no `ScheduleHide`, no controls bar). |
| AC-3 | unit | P0 | SM `openPanel_episodes_keepsPlaybackRunning` (edited) | Episodes arms the timer (`[ScheduleHide]`). |
| AC-3 | unit | P1 | VM `downFromButtons_viaOpenEpisodesPanel_isTheSamePathAsTheButton` | Skipped: `shouldOpenEpisodesFromButtons` is already covered in `PlayerButtonRowTest`; the VM only sees `OpenEpisodesPanel`. Not written (note only). |
| AC-4 | unit | P0 | VM `resetControlsTimer_withEpisodesOpen_restartsTheSixSeconds` | `ResetControlsTimer` at 5 s keeps the panel at 10.9 s and closes it at 11.1 s. |
| AC-4 | device | P1 | Device check D3 | The key hook on the row fires for real D-pad moves and auto-repeat (the Compose hook itself has no JVM test). |
| AC-5 | unit | P0 | SM `holdEpisodesPanel_true_cancelsHideAndTimeoutKeepsPanel` | Held: `[CancelHide]`; `onHideTimeout` returns `[]`, panel stays. |
| AC-5 | unit | P0 | SM `holdEpisodesPanel_false_schedulesHide` | Release: `[ScheduleHide]`, flag cleared. |
| AC-5 | unit | P0 | VM `episodesPanelHeldByContextMenu_staysFor30s_thenClosesSixSecondsAfterRelease` | `SetEpisodesPanelHeld(true)`, 30 s later still open; `false`, open at 5.9 s, closed at 6.1 s. |
| AC-5 | unit | P0 | VM `heldEpisodesPanel_staysThroughBufferingBlip` | R9: buffering start and end with the hold on do not close the panel. |
| AC-5 | unit | P1 | SM `holdEpisodesPanel_withOtherPanelOrNone_isNoOp` | Hold with Settings open or no panel: `[]`, state unchanged (no stray flag). |
| AC-5 | unit | P1 | SM `openPanel_and_closePanel_clearTheHeldFlag` | R6: flag does not survive a panel change. |
| AC-6 | unit | P0 | VM `openEpisodesPanel_closesItself_after6s` | Same test: `OpenEpisodesPanel` is the one action used by Down and the button. |
| AC-7 | unit | P0 | VM `settingsPanel_staysOpenFor60s` | `OpenSettingsPanel` (or the existing settings action; use the info page variant too): still open at 60 s. |
| AC-7 | unit | P0 | VM `settingsPanel_staysOpenAcrossBufferingStartAndEnd` | Buffering start (advance 1 s past the 800 ms debounce) and end, then 60 s: still open. Guards the R8 fix from widening to Settings. |
| AC-7 | unit | P0 | VM `aboutPanel_staysOpenFor60s` | Same for About. |
| AC-7 | unit | P0 | SM `onHideTimeout_withSettingsOpen_doesNotHide` and `onHideTimeout_withAboutOpen_returnsNoEffects` | State untouched, `[]`. |
| AC-8 | unit | P0 | VM `closingSettingsPanel_bringsBackControlsThatHideAfter6s` | `ClosePanel` -> bar visible; 5.9 s up; 6.1 s hidden. |
| AC-8 | unit | P1 | SM existing `closePanel_afterEpisodes_schedulesControlsHideAndReportsThePanelClosed` | Unchanged and must stay green. |
| AC-9 | unit | P0 | SM `closePanel_withNoPanel_returnsNoEffectsAndLeavesStateAlone` | D6: stale Up cannot reopen the bar. |
| AC-9 | unit | P0 | VM `staleClosePanelAfterEpisodesTimeout_doesNotReshowControls` | After the timeout, `ClosePanel` leaves `controlsVisible == false`, panel None. |
| AC-9 | unit | P0 | VM `backAfterEpisodesTimeout_exitsOnTheFirstPress` | After the timeout, `BackPressed` runs save-and-exit once (router back), not a swallow. Assert through the router mock as the existing Back tests do. |
| AC-9 | unit | P1 | VM `staleResetControlsTimerAfterEpisodesTimeout_doesNotReopenAnything` | R2: a late `ResetControlsTimer` plus 6 s leaves bare video. |
| AC-9 | device | P0 | Device check D2 | Focus lands on the video: Left seeks right after the auto-close (R1). |
| AC-10 | unit | P0 | VM `resumeDialog_noHideTimerRunsWhileItIsUp` | With the resume dialog up, 60 s later it is still up; answering it starts the 6 s (reuse the existing resume fixture/factory in `PlayerVMPlaybackFlowTest`/`PlayerStartModeTest`). If the fixture cannot build the dialog cheaply, downgrade to P1 and cover on the device (D6). |
| AC-11 | unit | P0 | SM `onHideTimeout_afterPlaybackEnded_keepsControlsUp` | D7: state untouched, `[]`. |
| AC-11 | unit | P0 | VM `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime` | Drive end-of-media, buffering start/end, `ResetControlsTimer`, 60 s: controls still visible; `BackPressed` leaves. |
| AC-12 | unit | P0 | VM `controlsHideAfter6s_whilePaused` | `isPlaying = false` through the callback, show controls, 6.1 s: hidden (unchanged behaviour pinned). |
| AC-13 | unit | P1 | VM `debugReadings_dropWhenEpisodesPanelTimesOut` | With `debugOverlayEnabled = false`, the readings are cleared on timeout (shows `PanelClosed` runs `onPanelClosed`). |
| AC-13 | unit | P2 | VM `debugOverlay_followsControlsWhenEnabled` | With the preference on, the overlay follows `controlsVisible` after the 6 s hide. Only if the fixture exposes the field cheaply. |
| AC-14 | device | P0 | D1 | The end-to-end of AC-3 and AC-7 on the TV. |
| AC-15 | unit | P0 | VS `shouldKeepScreenOn_isFalse_whenPausedOrEnded_andTrueWhenPlaying` | The rule on `PlayerContentState.isPlaying`; build content with `isPlaying=false` and `isBuffering=true` and assert false (R10), then `isPlaying=true` and assert true. Use the existing `testContentState` builder style from the fixture. |
| AC-15 | unit | P1 | VM `pausing_setsIsPlayingFalse_andResuming_setsItTrue` | Callback with `isPlaying=false` and `OkPressed` pause make the content state `isPlaying == false`; end of media too. |
| AC-16 | device | P0 | D5 | System screensaver starts after a long pause and a key returns to the paused player (user judges). |
| (design) | unit | P1 | VM `episodesTimeoutDuringExit_doesNotStartANextEpisodeCountdown` | D9: begin exit, advance 10 s: no countdown, no `checkEarlyNextEpisode` side effects. |
| (design) | unit | P1 | VM `lastEpisode_episodesTimeout_exitsThePlayer` | R4/open question 9. Write it only when the user answers yes to question 9; otherwise replace by the opposite assertion. |
| (design) | unit | P2 | VM `afterInitialize_episodesPanelHeldIsReset` | R5: add after `subtitle-player-fixes` merges (`initialize` exists only there). |

Priority counts: P0 and P1 are listed per row; the Compose key hook and the dialog hold lambdas (`PlayerSettingsPanels`) have no JVM test (the project has no Compose UI unit harness for this screen), so they are covered only by D3 and D4 and are the main residual risk.

### Bug route

Failing first (fails on master today, passes after the fix): VM `openEpisodesPanel_closesItself_after6s`. It is runnable on master unchanged (uses `PlayerAction.OpenEpisodesPanel` and the state fields only), `advanceTimeBy(6_100)` then asserts `activePanel == None`; on master the panel stays forever. Second reproduction: VM `controlsBar_staysAt5900ms_hidesAfter6000ms` fails on master (hidden at 4.5 s). Write both before any production change and watch them fail. Also write `episodesPanel_stillClosesAfterBufferingBlip` (R8) first; it fails on the design as written, not only on master.

### Device checks

Device: emulator-5554 (lease first via `tools/emulator-resource-lock.sh`; every adb call with `-s emulator-5554`; install the worktree dev APK with `tools/android-apk-install-preserve`, never uninstall or clear data). Use a series with at least 2 episodes. Observe: the video area, the focus ring on the bottom button row, and the seek indicator (R1).

D1 (AC-14, AC-3, AC-6, AC-7). Start a series. Press Down on the video (controls bar appears with focus on the buttons). Press Down again; the episode cards row appears. Do not touch the remote for 7 s. Expected: row gone, no bar, video clean. Must not: the row or the bar still showing, the bar flashing back, a crash. Repeat with OK on the Episodes button instead of Down. Then open Settings > stream info, wait 30 s: still there. Press Back: closes, bar returns and hides after 6 s. Repeat for About.

D2 (R1, AC-9). After the Episodes row auto-closes in D1, immediately press Left once. Expected: a seek indicator appears and the position moves back by the seek step. Press Down: the bar comes back with focus on the buttons. Must not: Left doing nothing, focus stuck on an invisible card (the row's exit animation), Back needing two presses. Then repeat once, press Back right after the close: the player leaves on the first press (it is a bare video). If Left fails, the architect's fix is to let focus exit when `!visible` in `PlayerEpisodesPanel`, done after `player-overlays-port` merges.

D3 (AC-4, hold). Open the row; every 4 s press Right (move between cards) for 20 s. Expected: the row stays throughout; it closes 6 s after the last press. Hold Right (auto-repeat) for 8 s: stays. Must not: closing mid-scroll. Long-press OK on a card to open the context menu, wait 20 s: panel and menu both stay. Press Back (menu closes): the row stays and closes about 6 s later. Must not: the row vanishing while the menu is open, or staying forever after the menu is dismissed.

D4 (AC-9 stale key). Open the row, wait until about 5.5 s, press Up exactly around the close. Expected: either the row closes through the timeout and the video is clean, or Up closes the panel and the bar appears and hides after 6 s. Must not: bar and row both on screen, or a bar that never hides.

D5 (AC-15, AC-16). Needs a screensaver. Check first: `adb -s emulator-5554 shell settings get secure screensaver_enabled` and `... screensaver_components`. To set it without touching app data, either D-pad through Settings > Device Preferences > Screen saver (pick a saver, set "When to start" to the shortest), or via adb: `settings put secure screensaver_enabled 1`, `settings put secure screensaver_components <installed dream component>` (list with `adb -s emulator-5554 shell cmd package query-services -a android.service.dreams.DreamService`), and shorten the idle time (`settings put system screen_off_timeout 60000`; on TV builds the "When to start" option uses the same sleep/dream timeout, so confirm by reading the value back). Record the original values and restore them afterwards. If the emulator image has no dream component installed, record "not testable on this device" and ask the user for a real TV; do not install anything or clear data. Steps: play, press OK to pause (the bar hides after 6 s), do not touch the remote for longer than the timeout. Expected: the screensaver starts (`adb -s emulator-5554 shell dumpsys dreams` shows it is dreaming, or the screen shows it). Press one D-pad key: back on the paused player, still paused, video frame visible. Also confirm that while playing, the saver does not start across the same wait. Must not: playback resuming by itself, the app crashing or restarting on wake, the saver starting during playback.

D6 (AC-10, only if the unit test is downgraded). Re-open a half-watched title; the resume dialog stays up 30 s; answer it; the bar hides 6 s later.

### Baseline (perf route)

Not a perf task; none.

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
8. Trailer overlay: delegated by the user to the conductor ("look the answers up, I will test the result and we will adjust"). Decision: leave the trailer's 4.5 s as it is (it is a passive preview, not the player; common defaults sit between 3 s, Leanback, and 5 s, Media3 `PlayerControlView`) and only correct its comment so it no longer claims to match `PlayerVM.CONTROLS_HIDE_DELAY_MS`. Revisit after the user's test.
9. Episodes panel open across the end of the last episode: delegated the same way. Decision: the player never exits on its own because of a timeout. When the Episodes timer fires after playback has ended, the panel closes to the end-of-media controls, which stay until Back (AC-11), the way post-play screens wait for the viewer. This replaces the design's "close and exit" answer to R4. Revisit after the user's test.
