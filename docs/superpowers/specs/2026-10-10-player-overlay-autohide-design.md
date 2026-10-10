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

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Where does the Episodes timer live? | It reuses the controls timer: the existing `controlsHideJob` and `scheduleControlsHide()`. A new state machine call, `onHideTimeout()`, decides what the timeout means: hide the bar, close Episodes, or nothing. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:138] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706] | The user set one delay (6 s) for both (answers 3, 5), and the bar and Episodes are never up together, because `openPanel` hides the bar. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:55] With one timer there is only one timer to cancel in each place (`onCleared`, buffering, panel open and close), so no second job can race the first. |
| D2 | What does the timeout do with Episodes open? | `onHideTimeout()` sets `activePanel = None`, `controlsVisible = false`, `focusTarget = null`, and returns `[PanelClosed]` (no `ScheduleHide`). | Answer 6: the viewer lands on the clean video. `PanelClosed` still runs `onPanelClosed()`: it drops debug readings, honours `exitWhenPanelCloses`, and re-runs `checkEarlyNextEpisode()`. So the timeout is not a second dismissal path that forgets these steps. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:733] |
| D3 | How does `openPanel` treat Episodes? | `openPanel(Episodes)` returns `[ScheduleHide]`. Settings and About keep `[CancelHide]`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47] | Episodes opened by Down and by the Episodes button go through the same action, so AC-3 and AC-6 hold together. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:560] |
| D4 | How does a key in the panel restart the 6 s? | `PlayerSettingsPanels` passes `Modifier.onPreviewKeyEvent { if (ACTION_DOWN) onAction(ResetControlsTimer); false }` through the `modifier` parameter `PlayerEpisodesPanel` already has. The same action already serves the controls bar. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:137] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:148] [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:24] | It copies `ControlsScrim` (preview, ACTION_DOWN, never consumes). [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerControlsOverlay.kt:146] The parameter is applied to the `BoxWithConstraints` that is an ancestor of every card, so the hook sees each key while focus is in the row. Auto-repeat counts as activity on purpose: a held D-pad is a viewer at work. `EpisodesPanel.kt` stays untouched, which keeps it out of the `player-overlays-port` merge (see Risks R7). |
| D5 | How does the context menu hold the panel? | Add the action `PlayerAction.SetEpisodesPanelHeld(held: Boolean)`. `PlayerSettingsPanels` sends `true` where it sets `episodeContextMenuItem` and `false` in the dialog's `onDismiss`. The state machine keeps `State.episodesPanelHeld`: `holdEpisodesPanel(true)` returns `[CancelHide]` and `holdEpisodesPanel(false)` returns `[ScheduleHide]`, and both only act while Episodes is open. `onHideTimeout()` also refuses to close a held panel. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:40] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:47] | The menu is Compose-local state, so the VM has to be told. Every way out of the dialog runs `onDismiss`: an action calls `dismiss()` before `onAction`. [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/TvContextMenu.kt:114] A flag in `State`, not a private var, is reset for free by `subtitle-player-fixes`' `initialize()`, which assigns `state = State(...)`. Checking the flag in `onHideTimeout` as well as cancelling the job means a stray reschedule cannot close a held panel. |
| D6 | What stops a stale `ClosePanel` from reopening the bar? | `closePanel()` returns `emptyList()` and leaves the state alone when `activePanel == None`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:59] | The race: the timeout closes Episodes, and an Up already routed to the still-composed row sends `ClosePanel` (the row's Up handler calls `onDismiss`). [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/EpisodesPanel.kt:274] Today that would set `controlsVisible = true` with focus on the Episodes button, and AC-9 forbids anything reappearing. `selectEpisodeById` calls `closePanel()` before switching episode, and the no-op is harmless there. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:868] |
| D7 | How do the end-of-media controls survive a timeout (AC-11)? | `onHideTimeout()` does nothing while `state.playbackEnded`. The buffering-end branch in the VM is not changed. | The gap is wider than the buffering-end branch [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:199]: any key in the ended controls also fires `ResetControlsTimer`, which reschedules the hide. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:558] One guard where the timeout lands covers every scheduler. |
| D8 | What does the hide delay become? | `CONTROLS_HIDE_DELAY_MS = 6_000L`, keeping the name, with a KDoc saying it also bounds the Episodes panel. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834] | Answers 3 and 5. Renaming it would only add diff. |
| D9 | Can a timer that fires while the player is leaving start anything? | The `scheduleControlsHide` job returns early `if (closing)`. | `exitPlayer()` does not cancel `controlsHideJob`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1798] If an Episodes timeout fired during the drain, it would run `onPanelClosed()` and then `checkEarlyNextEpisode()`, which can start a countdown that `exitPlayer` already cancelled. |
| D10 | How is AC-15 (keep-screen-on) pinned down? | No behaviour change. Add `internal fun PlayerContentState.shouldKeepScreenOn(): Boolean = isPlaying` in `PlayerVideoSurface.kt` and use it at the three call sites, so a JVM test can pin the rule. [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:45] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:54] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerVideoSurface.kt:65] | `WindowKeepScreenOnBinding` is private and Android-bound. The rule is the part that can regress (for example, someone adding `|| isBuffering`). Together with VM tests that `isPlaying` goes false on pause and on end, this covers AC-15 without Robolectric. `player-overlays-port` does not touch this file. |
| D11 | Is any change needed for the system screensaver (AC-16)? | No. AC-16 is a device check only. | The keep-screen-on flags are already released when not playing (see Problem). |

### Changes by layer

**API / repository:** none.

**State machine** (`ControlsStateMachine.kt`):
- `State` gets `val episodesPanelHeld: Boolean = false`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:8]
- `openPanel(panel)`: copies `episodesPanelHeld = false` and returns `if (panel == ActivePanel.Episodes) listOf(ScheduleHide) else listOf(CancelHide)`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47]
- `closePanel()`: returns early with `emptyList()` when `activePanel == None`, and otherwise copies `episodesPanelHeld = false`. Effects stay the same. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:59]
- New `fun onHideTimeout(): List<Effect>`. This replaces `applyControlsVisibility(visible: Boolean)`, whose only caller is `scheduleControlsHide`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:99] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:710] The cases, in order:
  - Episodes open and not held: close the panel to clean video and return `[PanelClosed]`.
  - Any other panel open: `[]`.
  - `playbackEnded`: `[]`.
  - Otherwise: `controlsVisible = false`, `focusTarget = null`, and return `[]`.
- New `fun holdEpisodesPanel(held: Boolean): List<Effect>`. When `activePanel != Episodes` it returns `[]` and leaves the state alone. Otherwise it sets the flag and returns `[CancelHide]` when held, `[ScheduleHide]` when released.
- Put the new functions at the end of the class, and leave line 36 (`lastPanelOpener`) and the lines around it alone. `subtitle-player-fixes` inserts `initialize` right there.

**ViewModel** (`PlayerVM.kt`):
- `scheduleControlsHide()` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706]: the job body becomes `delay(CONTROLS_HIDE_DELAY_MS); if (closing) return@launch; val effects = controlsStateMachine.onHideTimeout(); applyControlsState(); processEffects(effects)`. Running `processEffects` while the job is still active is safe: the `PanelClosed` path does not schedule a hide, so the job never cancels itself mid-body.
- `onAction` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:543]: one new branch, `is PlayerAction.SetEpisodesPanelHeld -> holdEpisodesPanel(action.held)`, and a private `holdEpisodesPanel` that follows the `closePanel()` pattern (machine call, `applyControlsState()`, `processEffects`). [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:727]
- `CONTROLS_HIDE_DELAY_MS` changes from `4_500L` to `6_000L`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834]
- Not touched: `applyControlsState`, `processEffects`, `openPanel`, `preparePlayback` and the buffering callback. These are the lines `subtitle-player-fixes` rewrites or sits next to.

**Model** (`PlayerAction.kt`): add `data class SetEpisodesPanelHeld(val held: Boolean) : PlayerAction` under `// Panels`. [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerAction.kt:26]

**Screen / DI:** none. The Koin module, the screen `key` and `puberViewModel` all stay as they are.

**Compose** (`PlayerSettingsPanels.kt`, the only Compose file whose behaviour changes) [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanels.kt:35]:
- `PlayerEpisodesPanel(modifier = Modifier.onPreviewKeyEvent { ... ResetControlsTimer ...; false }, ...)`. Remember the modifier against `onAction`. The hook never consumes the event, so focus, the Up-to-close handler and long-press keep working.
- `onEpisodeContextMenu = { episodeContextMenuItem = it; onAction(PlayerAction.SetEpisodesPanelHeld(true)) }`, and the dialog gets `onDismiss = { episodeContextMenuItem = null; onAction(PlayerAction.SetEpisodesPanelHeld(false)) }`.
- `PlayerVideoSurface.kt`: the D10 extraction only.
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
- **R4 An episode that ends with Episodes open.** For the last episode, `exitWhenPanelCloses` is set [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1240], so 6 s idle after the end now leaves the player. That is consistent with the films and last episodes that already exit at the end. For a non-last episode with the countdown dismissed, the timeout leaves a still frame with no controls, and Back exits. Both are flagged in Open questions.
- **R5 Merge with `subtitle-player-fixes`.** That worktree is uncommitted. It touches `ControlsStateMachine` (inserts after line 36), `PlayerVM.applyControlsState` and `preparePlayback`, and the setUp area of `ControlsStateMachineTest`. This design leaves those hunks alone and puts new code at the end of the class. The new `State` field is reset by `initialize` because `initialize` builds a fresh `State`. Whichever task merges second rebases. The expected conflict is none, or the `State` KDoc context at most.
- **R6 Context menu hold never released.** If the dialog left composition without `onDismiss`, the panel would stay up for good, which is today's behaviour, so no regression. `openPanel`, `closePanel` and `initialize` all clear the flag.
- **R7 Merge with `player-overlays-port` (92a9441d, not merged).** Correction to the Problem section: it does edit `EpisodesPanel.kt` and `PlayerScreenContent.kt`. This design touches neither, so there is no textual conflict, and its `requestAcrossFrames` focus change only helps R1.
- **Recomposition:** the remembered modifier and the hold lambdas are created inside `PlayerSettingsPanels`, which already recomposes with every content change. No new state is read in composition. **Main thread / memory:** one `launch` per key press, cancelled before relaunch (same as the controls bar today). **Baseline profile:** unaffected (no new screen or start path).

### Open questions (architect)

8. The trailer overlay in details copies the player delay, and its comment says "Matches `PlayerVM.CONTROLS_HIDE_DELAY_MS`". [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt:47] Should it move to 6 s too, or should only the comment be corrected? The design leaves the trailer alone (out of scope).
9. R4: should an Episodes panel left open across the end of the last episode close and exit after 6 s idle? The design says yes, because it falls out of `PanelClosed`.

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
