# Seek is blocked by the skip / next-episode prompt

Status: approved
Date: 2026-11-10
Slug: seek-blocked-by-skip-prompt
Kind: bug
Size: M
Tracks: ui

## Problem / Why

### Request
In the player, Left/Right seeking stops once the playhead lands in a "Skip intro" or "Next episode" section. The owner has to dismiss the prompt and start seeking again, and the same prompt comes back.

### Root cause (code trace, not reproduced on a device)
| Step | Where | Status |
|---|---|---|
| 1. A seek clears both prompts (`onSeekPerformed`); the next 500 ms tick, while playing, raises the prompt again if the playhead is in a segment (2 s lead-in) or in the credits lead-in. | `vm/PlayerVM.kt:1670-1677`, `:1510-1515`, `:1679-1689`, `:1543-1558` | confirmed (code) |
| 2. Every time the prompt becomes visible it takes focus: X for skip, the action for next episode. Nothing checks for a seek in progress or for visible controls. | `component/PlayerTimedActionOverlay.kt:191-197`; `component/SkipSegmentOverlay.kt:28`; `component/NextEpisodeOverlay.kt:26` | confirmed (code) |
| 3. Left/Right seek only from the `onKeyEvent` of the video surface or the seek bar. The prompt is their sibling (`component/PlayerOverlayLayers.kt:113,127`), and its button `Row` has no key handler, so Left/Right become focus moves between X and the action. Seeking stops. | `component/PlayerVideoSurface.kt:76-82, 210-226`; `component/PlayerProgressBar.kt:200`; `component/PlayerTimedActionOverlay.kt:266-300` | confirmed (code) |
| 4. When the prompt goes away (cancel, seek, skip), the focused button leaves the composition. `PlayerFocusEffects` only re-requests focus when `controlsVisible`, `activePanel` or the resume dialog change, so no node owns focus. The first D-pad press probably only puts focus back on the surface, which matches "start seeking again". | `component/PlayerScreenContent.kt:72-89` | plausible (Compose behaviour on removing the focused node unverified) |
| 5. "Same popup again": after a cancel, the next seek resets `countdownDismissed`, so the next-episode prompt returns by design (pinned by `PlayerVMPromptDismissalTest.kt:81`). A cancelled skip type stays down for 60 s (`vm/PlayerVM.kt:1798-1809`, `model/PlayerCountdowns.kt:77`). | `vm/PlayerVM.kt:1676`, `:1384-1390` | confirmed (code); which prompt the owner saw unverified |

### Scope
In: research.md: 6, option 1.
- (a) The prompt forwards Left/Right and media FF/RW/skip keys to `SeekBackward`/`SeekForward` and consumes them. It reuses `playerActionForKeyCode` (`component/PlayerVideoSurface.kt:213`).
- (b) The skip prompt focuses its action button (Q1).
- (c) The VM raises no prompt while a seek burst is running (the `SeekHandler` reset window, `vm/SeekHandler.kt:7`) and raises it once the burst ends.
- (d) When a prompt hides, focus returns to the video surface, or to the controls target when the controls are up.
- (e) With the controls up, a prompt that appears does not take focus (Q3).

Out: the "cancel" / dismissal policy (Q2), the prompt's look, and the shared focus helper (`focus-request-helper`).

### Risks (analyst)
- Risk area: TV focus and D-pad, VM state and playback, so the size is M (`.flow/roles/analyst.md`).
- Focus timing: a same-frame `requestFocus()` can fail silently (`player-focus-after-panel-close`). Restoring focus on hide (d) may need retries across frames, as `player-overlays-port` does with `requestAcrossFrames`.
- Same files as the `player-overlays-port` branch (`92a9441d`: `PlayerTimedActionOverlay.kt`, `PlayerScreenContent.kt`). Recommended: land this first as the smaller change, and rebase that branch on it.
- `SeekHandler` reads `System.currentTimeMillis()` (`vm/SeekHandler.kt:23`). AC-3 needs a clock that the VM tests can control, or a burst marker set by the VM on the test dispatcher.
- Device data: skip segments need TMDB/IntroDB data, and a worktree build has no TMDB token (AGENTS.md). The next-episode prompt in a series is the reliable on-device case. Skip-prompt ACs may need a fixture or a main-checkout-provided session, and qa-device must say which.
- After (a), the X button can no longer be reached with Left/Right. Back stays the way to dismiss (`component/PlayerTimedActionOverlay.kt:181`).

## Acceptance criteria

- AC-1. Given the skip prompt is visible and focused, When Left or Right is pressed (also held, repeated `ACTION_DOWN`), Then each press seeks 10/10/20/30/60 s like on the bare video, and focus does not move between the prompt's buttons. Checked by a Compose test and on the device.
- AC-2. Given the next-episode prompt is visible and focused, When Left or Right is pressed, Then playback seeks as in AC-1 (and the countdown is cancelled, as today, `vm/PlayerVM.kt:1671`).
- AC-3. Given a Left/Right burst whose seeks land inside a segment or the credits lead-in, When position ticks arrive less than the reset window after the last seek, Then no prompt is raised; When the window passes and playback is still inside, Then the prompt is raised once. VM unit test.
- AC-4. Given a prompt hides through a seek, a cancel (Back or X), the action, or the countdown running out, with the controls hidden and no panel open, Then the video surface holds focus, and the very next Left/Right seeks. Checked on the device and, if feasible, by a Compose test.
- AC-5. Given the skip prompt appears, Then its action button has focus; OK skips the segment and Back dismisses it. (Depends on Q1.)
- AC-6. Given the controls are up with the seek bar focused, When a prompt appears, Then focus stays on the seek bar and Left/Right keep seeking. (Depends on Q3.)
- AC-7. Given a prompt is visible, When OK is held or Left/Right auto-repeats, Then neither the prompt action nor its cancel fires from a seek key.
- AC-8. The existing player VM tests stay green, `PlayerVMPromptDismissalTest` included (the next-episode prompt still returns after a seek that follows a cancel, `:81`), and so do `PlayerVideoSurfaceTest`.
- AC-9. On the device, an owner-style sequence: seek with Left/Right through an intro and through the credits lead-in without stopping, then let the prompt appear and press Right twice. Both presses seek. (owner judges whether the prompt flashing during a burst is acceptable.)

## Out of scope

- The cancel / dismissal policy of the prompts (Q2: the next-episode prompt still returns after a seek that follows a cancel).
- The prompt's look (sizes, materials, a Back hint); `player-overlays-port` owns the overlays stand.
- The shared focus helper (`focus-request-helper`).
- Passing Up/Down and Play/Pause through the prompt (architect Q1): a follow-up idea run.

## Design

### Architect

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where do seek keys on the prompt go? | `onPreviewKeyEvent` on the prompt `Row` (`component/PlayerTimedActionOverlay.kt:266`). For a key that maps to `SeekForward`/`SeekBackward`, `ACTION_DOWN` (repeats included) calls `onSeek(action)`, and every phase of that key (down, repeat, up) is consumed. Other keys pass through. | Preview runs before the focused button and before focus traversal, so Left/Right never move focus between X and the action (AC-1, AC-2, AC-7). The exiting copy keeps the handler, so a held key keeps seeking during the exit animation. |
| Which keys count as seek keys? | New pure `seekActionForKeyCode(keyCode): PlayerAction?` next to `playerActionForKeyCode` (`component/PlayerVideoSurface.kt:213`): its result when it is `SeekForward` or `SeekBackward`, else null. | One mapping for the surface, the seek bar and the prompt; JVM-testable beside `PlayerVideoSurfaceTest`. |
| Who moves focus to and from a prompt? | `PlayerFocusEffects` (`component/PlayerScreenContent.kt:72`) is the only owner. Its first effect (`:77-82`) also keys on the visible prompt kind (`NextEpisode` if `nextEpisodeCountdown != null`, else `Skip` if `activeSkipSegment != null`, else none). With controls hidden, no panel and no resume dialog it focuses that prompt's action button, or the video surface when there is none. The overlay's own focus effect (`component/PlayerTimedActionOverlay.kt:189-197`) is removed. | Covers AC-4 (prompt hides: surface), AC-5 (prompt appears: action), AC-6 (controls up: no request, the seek bar keeps focus). A second, overlay-side effect would race this one on the same frame. |
| One focus requester for both prompts? | No. `PlayerFocusRequesters` (`component/PlayerFocusRequesters.kt:7`) gains `skipPrompt` and `nextEpisodePrompt`; each overlay attaches its own to the action button. | While one overlay plays its exit animation the other can already be composed; one shared requester would be attached to two nodes. |
| Which button does the skip prompt focus? (Q1) | The action. `focusPrimaryAction` (`:171`, set at `component/SkipSegmentOverlay.kt:28`, `component/NextEpisodeOverlay.kt:26`) is deleted, together with `cancelFocusRequester` (`:190`). The X stays clickable and Back still cancels (`:181`). | Q1 answer. |
| What happens when the controls auto-hide while a prompt is up? | The prompt's action button takes focus (same effect, `controlsVisible` changed). | Without this, OK on the surface would open the controls instead of acting on the prompt that is on screen. |
| How does the VM hold the prompt down during a burst? (AC-3) | `seekForward`/`seekBackward` (`vm/PlayerVM.kt:706`, `:714`) restart a `seekBurstJob = launch { delay(SEEK_BURST_WINDOW_MS) }`. The tick (`:1510-1515`) runs `checkEarlyNextEpisode()` and `checkSkipSegment()` only while `seekBurstJob?.isActive != true`; auto-mark and prefetch keep running. The window is the `SeekHandler` reset timeout, made `internal` (`vm/SeekHandler.kt:7`) and reused. | A delay on the VM scope runs on the test dispatcher, so the AC-3 test drives it with `advanceTimeBy`; no clock is injected. The first tick after the window raises the prompt once. |
| Which seeks start a burst? | Only `SeekForward`/`SeekBackward` (D-pad, seek bar, media keys). Not `onSeekPerformed` (`:1670`), which also fires for skip (`:1781`), resume (`:1403`) and start over (`:1412`). | A skip landing on the next segment must not wait 1.5 s for its prompt. `PlayerVMPromptDismissalTest` calls `onSeekPerformed()` directly, so it stays green (AC-8). |
| Next-episode prompt after a seek that follows a cancel (Q2) | Unchanged: `countdownDismissed = false` (`:1676`). | Q2 answer. |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data or API change. |
| ViewModel state and actions | `vm/SeekHandler.kt:7`: `DEFAULT_SEEK_RESET_TIMEOUT_MS` becomes `internal`. `vm/PlayerVM.kt`: field `seekBurstJob: Job?` beside the jobs at `:142-151`; restart it in `seekForward` (`:706`) and `seekBackward` (`:714`); gate the two prompt checks in the tick (`:1510-1515`). No new state field, no new action. |
| Screen and DI module | N/A: no DI or screen change. |
| Compose content | `component/PlayerFocusRequesters.kt:7-14`: add `skipPrompt`, `nextEpisodePrompt`. `component/PlayerScreenContent.kt:77-82`: the hide/prompt focus effect from the Decisions table; `:131` passes `focusRequesters` to `PlayerOverlayLayers`. `component/PlayerOverlayLayers.kt:105-133`: new `focusRequesters` parameter; passes `skipPrompt`/`nextEpisodePrompt` and `onSeek = onAction` to the two overlays. `component/SkipSegmentOverlay.kt:13-38`, `component/NextEpisodeOverlay.kt:12-33`: drop `focusPrimaryAction`, add `focusRequester: FocusRequester` and `onSeek: (PlayerAction) -> Unit`, pass both through. `component/PlayerTimedActionOverlay.kt:164-180`: same parameter change; delete `:189-197`; key handler on the `Row` at `:266`; the action button (`:279-282`) takes the passed requester. `TimedActionButton.focusRequester` (`:309`, `:381`) becomes nullable for the X. `component/PlayerVideoSurface.kt:213`: add `seekActionForKeyCode`. |
| Resources | N/A: no strings or look change. |

### Alternatives rejected

- Overlay keeps its focus effect, gated by a `takeFocus` flag: two effects request focus in one frame when the controls hide.
- Burst marker in `onSeekPerformed`: delays prompts after skip, resume and start over, and changes what `PlayerVMPromptDismissalTest` exercises.
- Clock injected into `SeekHandler`: larger change for the same AC-3 coverage.
- Forward the whole `playerActionForKeyCode` map from the prompt (Up/Down, play/pause): not asked; Open question 1.
- Option 3 from research (non-focusable prompt, OK remapped on the surface): changes what OK means; the owner took option 1.

### Risks

| # | Risk | Lives in | Mitigation |
|---|---|---|---|
| 1 | Focus: a `requestFocus()` in the effect fails silently (prompt node not yet attached on the frame its state arrives, or the surface request lost while the focused button exits), so no node owns focus and the first D-pad press is swallowed (AC-4). | `component/PlayerScreenContent.kt:77-82` | Device check of AC-4 and AC-9 for each hide path; if a request fails, one retry after `withFrameNanos` in this effect only (the shared helper is `focus-request-helper`, out of scope). |
| 2 | Focus: the skip overlay leaves composition instantly when the next-episode countdown starts (`component/PlayerOverlayLayers.kt:112`); if the effect key misses this, focus is orphaned. | `component/PlayerScreenContent.kt:77` | The effect keys on the prompt kind, not a visible boolean. |
| 3 | D-pad: a stray OK `ACTION_UP` (OK pressed on the surface, prompt focused before release) lands on the action button. tv-material3 `Surface` clicking on an up without its down is unverified (AC-7). | `component/PlayerTimedActionOverlay.kt:350` | Compose test: OK up without down on the focused action does not call `onAction`; if it does, the `Row` handler drops OK events whose down it did not see. |
| 4 | Lifecycle: `seekBurstJob` survives an episode switch; a prompt in the new episode can wait up to 1.5 s. | `vm/PlayerVM.kt:1510` | Accepted; bounded by the window. |
| 5 | Recomposition: `PlayerOverlayLayers` takes `PlayerFocusRequesters`; the effect reads two more content fields. | `component/PlayerOverlayLayers.kt:105` | `PlayerFocusRequesters` holds only `val FocusRequester` fields, so it is inferred stable; the new keys change only when a prompt appears or hides. |
| 6 | Rollout: `player-overlays-port` rewrites `PlayerFocusEffects` and edits `PlayerTimedActionOverlay.kt`. | `component/PlayerScreenContent.kt`, `component/PlayerTimedActionOverlay.kt` | This lands first; that branch rebases and keeps the prompt-kind key. |
| 7 | Verification data: skip segments need TMDB/IntroDB data, which a worktree build lacks. | device run | qa-device names the source for AC-1, AC-5; AC-2, AC-4, AC-9 use the next-episode prompt in a series. |

### Open questions

1. Should the prompt also forward Up/Down (show controls) and play/pause like the bare video? Recommended: yes, as a follow-up idea; no AC asks for it. Blocks: no.

### UX designer

### Governing stand

**No stand at base governs the timed-action prompt.** `docs/design/player-controls/README.md` covers the title, seek bar and button row only. The chips' scene lives in `docs/design/player-overlays/`, which exists only on the unmerged `player-overlays-port` branch (`92a9441d`). Its chip sizes were agreed on 2026-09-26 but never compiled or measured on a television, and the stand says nothing about focus order.

The conductor does not need to run `designing-a-screen` before planning. This run changes which chip holds focus and when, not how a chip looks (look is out of scope, brief.md "Out"). Nothing here needs a slider.

### Agreed numbers that apply

None from a stand at base. The run must leave the chip's current values as they are. `player-overlays-port` retunes them on rebase.

| Value | At base (`PlayerTimedActionOverlay.kt`) | Agreed on the unmerged stand |
|---|---|---|
| Edge offset, right and bottom | `TimedActionBottomPadding` 48 dp | 10 / 10 dp |
| Cancel-to-action gap | 12 dp (`Row` literal) | 2 dp |
| Label size | 15 sp | 10 sp |
| Label padding | 14 × 10 dp | 12 × 8 dp |
| Shadow on the action chip | 14 dp | none |
| Corner, bed, idle hairline | 12 dp, 86 %, 1 dp at 45 % | unchanged |

Still open, and not for this run: whether the X stays drawn now that the D-pad cannot reach it (see below), and the scenes the overlays stand has not agreed.

### What the viewer sees change

| Case | Before | After |
|---|---|---|
| Skip prompt appears, controls hidden | The X has focus: its tint goes from 82 % to 100 % and tv-material's default 1.1 focus scale applies (no `scale =` passed to `Surface`). The action chip stays at its idle 45 % hairline. | The action chip has focus: its hairline brightens to 85 % (`TIMED_ACTION_BORDER_FOCUS_ALPHA`) and it scales to 1.1. The X stays idle at 82 %. This is the same picture the next-episode prompt already shows. |
| Next-episode prompt appears, controls hidden | Action focused | Unchanged |
| A prompt appears while the controls are up and the seek bar has focus (Q3) | The prompt takes focus and the seek bar loses it | Both chips stay idle and the seek bar keeps focus. This idle-only state of the prompt is new on screen. |
| Left/Right while the prompt has focus | The focus ring jumps between X and the action | The ring stays on the action and the seek indicator moves |
| The prompt hides | Nothing on screen holds focus | The video surface holds focus, or the controls target if the controls are up (no visible ring) |

Focus visibility is the hairline going from 45 % to 85 % plus the 1.1 scale. That is the existing, deliberate treatment (`PlayerTimedActionOverlay.kt:99-106`), and it needs no change for the action to be the default. The X's weak cue no longer matters, because the X is never the default focus.

**Back hint: add none.** The X glyph already reads as "dismiss". Adding a "Back" label or icon would change the prompt's look, which is out of scope. The X loses its D-pad role but keeps its click target for pointer input. Whether to drop it is a question for the overlays stand.

### Focus order and visibility

The run adds no new focusable element. With a prompt up, the keys work as follows:

| Key | With the prompt focused |
|---|---|
| OK | Runs the action (skip, or next episode) |
| Back | Cancels (`BackHandler`, `:181`) |
| Left/Right, FF/RW, media skip | Seek and are consumed; the ring does not move |
| Up/Down, Play/Pause | Open question A |

Invariant to hold, so the prompt never sits on screen without an obvious OK target: **while the controls are hidden and no panel or dialog is open, a visible prompt's action chip is focused.**

### Open for the architect

- **A. Which keys the prompt forwards.** `playerActionForKeyCode` (`PlayerVideoSurface.kt:213-227`) also maps Up/Down to `ShowControls` and Play/Pause to `TogglePlayPause`. Recommended: forward the whole map. The prompt then answers to everything the bare video does except OK and Back, and Up/Down peek at the controls, as the Android TV playback guidance describes. If only seek keys are forwarded, Up/Down on the prompt fall to default focus search, which on this layout has no target.
- **B. Focus when the controls hide while a prompt is up.** Scope item (d) and `PlayerFocusEffects` (`PlayerScreenContent.kt:72-79`) send focus to the video surface. That leaves a prompt that appeared under the controls (Q3) on screen with no ring, and OK on the surface opens the controls instead of skipping. Recommended: when the controls hide and a prompt is visible, focus its action, which is the invariant above. Send focus to the surface only when no prompt is visible.
- **C. Reaching the prompt while the controls are up.** The prompt sits above the bar on the right (`timedActionInset`, `PlayerOverlayLayers.kt:111`). Whether Up from the seek bar lands on it is untested; geometric search may pick it. Recommended: no explicit route. B covers the case once the controls time out, and qa should note on the device where Up from the seek bar goes.

### Shared constants: take a parameter, do not retune in place

| Constant / parameter | Rule |
|---|---|
| `focusPrimaryAction` (`SkipSegmentOverlay.kt:28` -> `true`) | Flip the caller's argument, not the default inside the overlay. If both callers end up `true`, removing the parameter is acceptable. |
| Taking focus on appear (`LaunchedEffect(visible)`, `:191-197`) | Gate it on a parameter passed from `PlayerOverlayLayers` (for example `takeFocus = !controlsVisible && no panel/dialog`), not on a check read inside the overlay |
| `TimedActionBottomPadding`, the `Row` 12 dp gap, 15 sp, 14 × 10 dp padding, shadow, all `TIMED_ACTION_*_ALPHA` | Do not touch. `player-overlays-port` replaces them; edits here are rebase conflicts and give no visual gain |
| `playerActionForKeyCode` | Reuse it; do not copy the key map into the overlay |

### For visual review

On the emulator at 1080p (1 dp = 2 px), check:

1. The skip prompt appears with the action chip focused: 85 % hairline and 1.1 scale on the action, X at 82 % tint.
2. With the controls up, the prompt appears with both chips idle and the seek bar keeps focus.
3. Chip geometry is unchanged from base: 48 dp edge offset, 12 dp gap, 15 sp label.

## Test design

### Where the tests live

| File | Level | Notes |
|---|---|---|
| `test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMSeekBurstTest.kt` (new) | unit | Extends `PlayerVMTestFixture`; `MainDispatcherExtension`, `runTest(mainDispatcher.dispatcher.scheduler)`, `advanceTimeBy`/`runCurrent`. Mutable-position stub copied from `PlayerVMSkipCountdownFitTest.playingInto`. A seek is `vm.onAction(PlayerAction.SeekForward)` followed by `callbackSlot.captured.onSeekPerformed()`, which the real player fires. |
| `test/kotlin/com/kino/puber/ui/feature/player/component/PlayerVideoSurfaceTest.kt` (extend) | unit | `seekActionForKeyCode` cases. |
| `androidTest/kotlin/com/kino/puber/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt` (new) | instrumented | `createAndroidComposeRule<ComponentActivity>()`, `PuberTheme { PlayerScreenContent(state, onAction = actions::add, exoPlayer = { null }) }` with the state in a `mutableStateOf`. Goes through `PlayerScreenContent` because its signature does not change, so every `red` row compiles and runs on the base. A file-local `contentState(...)` builder fills `PlayerContentState` (the unit fixture is not visible to `androidTest`). Keys through `onRoot().performKeyInput { keyDown/keyUp(Key.DirectionRight) }`; nodes found by the action label. Run: `make DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest`. |

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Seek keys on a focused prompt move focus instead of seeking (the bug) | 3 | 3 | 9 | AC-1, AC-2 instrumented `red` |
| R1: focus request on hide fails silently, first D-pad press swallowed | 2 | 3 | 6 | AC-4 instrumented `red`, M2 |
| Burst gate never releases, or delays prompts after skip/resume/start over | 2 | 3 | 6 | AC-3 unit, `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` |
| An appearing prompt takes focus from the seek bar | 3 | 2 | 6 | AC-6 instrumented `red`, M4 |
| R7: no skip-segment data on a worktree build | 3 | 2 | 6 | Skip prompt covered by instrumented tests; M3 source named by qa-device |
| R2: skip prompt replaced by next-episode prompt orphans focus | 2 | 2 | 4 | `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` |
| R3: stray OK `ACTION_UP` fires the prompt action | 2 | 2 | 4 | `skipPrompt_okUpWithoutDownDoesNotSkip` |
| Prompt forwards the wrong key set (Up/Down, play/pause, missing media keys) | 1 | 2 | 2 | `seekActionForKeyCode_*` |
| R5: recomposition cost of the new parameters | 1 | 2 | 2 | Accepted: requesters hold only `val FocusRequester`. |
| R6: rebase conflict with `player-overlays-port` | 2 | 1 | 2 | Accepted: process, not behaviour. |
| R4: burst job survives an episode switch (prompt up to 1.5 s late) | 1 | 1 | 1 | Accepted, bounded by the window. |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 `red` | `skipPrompt_leftAndRightSeekAndFocusStaysOnAction` | `onAction` receives `SeekBackward`, `SeekForward` and the action stays focused; fails if the `Row` preview handler is removed (base: Right moves focus X to action, no seek). |
| AC-1 | instrumented | P0 `red` | `skipPrompt_heldRightSeeksOnEveryRepeat` | `keyDown`, `advanceEventTime(1_000)`, `keyUp` yields more than one `SeekForward`; fails if the handler ignores repeats (`repeatCount > 0`) or acts on up. |
| AC-1 | unit | P1 `guard` | `SeekHandlerTest.rapidConsecutiveCalls_incrementsStep` (existing) | 10/10/20/30/60 s escalation unchanged. |
| AC-2 | instrumented | P0 `red` | `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` | Same as AC-1 for `nextEpisodeCountdown`; fails if only the skip overlay gets `onSeek`. |
| AC-2 | unit | P1 `guard` | `PlayerVMPromptDismissalTest.seekingWhileTheNextEpisodePromptIsUpTakesItDown` (existing) | A seek still cancels the countdown. |
| AC-3 | unit | P0 `red` | `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` | Start outside the intro, seek in; at +1 000 ms `activeSkipSegment` is null (base: raised by the 500 ms tick); after the window plus one tick it is set and stays equal over two more ticks. Fails if the tick gate is removed. |
| AC-3 | unit | P0 `red` | `aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds` | Same for `nextEpisodeCountdown` (seek from 100 000 to 2 380 000 ms); fails if only `checkSkipSegment` is gated. |
| AC-3 | unit | P1 | `everySeekInTheBurstRestartsTheWindow` | Seeks at 0, 1 000, 2 000 ms: prompt still null at 3 000 ms, set after 3 500 ms plus a tick; fails if the job is not restarted per seek. |
| AC-3 | unit | P1 `guard` | `aSkipLandingOnTheNextSegmentOffersItWithoutWaiting` | After `SkipSegmentClicked` onto a second segment, its prompt is up within one tick; fails if the burst starts in `onSeekPerformed`. |
| AC-4 | instrumented | P0 `red` | `skipPromptHidden_nextRightSeeks` | Prompt visible and focused, state flips to `activeSkipSegment = null`, after idle one Right yields `SeekForward`; fails if the hide does not refocus the surface. If it passes on the base, root-cause step 4 is refuted: record it and keep the test as `guard`. |
| AC-4 | instrumented | P0 `red` | `nextEpisodePromptHidden_nextRightSeeks` | Same for `nextEpisodeCountdown = null`. |
| AC-4 | instrumented | P1 | `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` | R2: the effect keys on the prompt kind; fails if it keys on a visible boolean. |
| AC-5 | instrumented | P0 `red` | `skipPrompt_appearsWithActionFocused_okSkips` | Action node `assertIsFocused()`, OK yields `SkipSegmentClicked`; fails if the X keeps default focus. |
| AC-5 | instrumented | P1 `guard` | `skipPrompt_backCancels` | `activity.onBackPressedDispatcher.onBackPressed()` yields `CancelSkipSegment`. |
| AC-6 | instrumented | P0 `red` | `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` | Controls up, `controlsFocusTarget = SeekBar`, then a skip prompt: action not focused, Right yields `SeekForward`; fails if the prompt requests focus while controls are visible. |
| AC-6 | instrumented | P1 `red` | `controlsHidingWhilePromptIsUp_focusesThePromptAction` | `controlsVisible` flips to false with the prompt up: action focused; fails if the effect sends focus to the surface. |
| AC-7 | instrumented | P0 `guard` | `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` | Recorded actions during held Left and Right contain no `SkipSegmentClicked` or `CancelSkipSegment`; fails if the handler lets the key reach the focused button. |
| AC-7 | instrumented | P1 | `skipPrompt_okUpWithoutDownDoesNotSkip` | R3: a native `ACTION_UP` `KEYCODE_DPAD_CENTER` sent through `activity.dispatchKeyEvent` with the action focused records no `SkipSegmentClicked`. |
| AC-8 | unit | P0 `guard` | `PlayerVMPromptDismissalTest.seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (existing) | Next-episode prompt still returns after seek-after-cancel (Q2). |
| AC-8 | unit | P0 `guard` | `PlayerVideoSurfaceTest.playerActionForKeyCode_opensButtons_whenDownPressed`, `playerActionForKeyCode_opensSeekControls_whenUpPressed` (existing) | Surface key map unchanged; whole suite via `./tools/agentw testDevDebugUnitTest`. |
| AC-8 | unit | P1 | `seekActionForKeyCode_returnsSeekActions_forLeftRightAndMediaSeekKeys` | Left, Right, FF, RW, skip forward/backward map to the seek actions. |
| AC-8 | unit | P1 | `seekActionForKeyCode_returnsNull_forUpDownCenterAndPlayPause` | Fails if the prompt forwards the whole `playerActionForKeyCode` map. |
| AC-9 | manual | P0 | M1, M5 | Owner sequence on the device. |

### Failing-first test

`skipPrompt_leftAndRightSeekAndFocusStaysOnAction`. On the base the X holds focus (`component/SkipSegmentOverlay.kt:28`, `focusPrimaryAction = false`) and the `Row` (`component/PlayerTimedActionOverlay.kt:266`) has no key handler, so Right only moves focus and `onAction` records no `SeekForward`. VM side: `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` (base tick at `vm/PlayerVM.kt:1510-1515` raises the prompt at 500 ms).

### Manual checks

Emulator, worktree dev APK, lease and `adb -s <serial>`. Keys: Right 22, Left 21, Up 19, OK 23, Back 4. Series episode for the next-episode prompt; qa-device names the skip-segment source for M3.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | Play an episode; press Right repeatedly (under 1.5 s apart) until the playhead passes into the credits lead-in; stop; wait for the prompt; press Right twice. | No prompt during the burst; prompt appears about 1.5 s after the last press with the action focused; both Right presses seek (seek indicator moves). | Seeking stops; focus ring moves to X. |
| M2 | With the next-episode prompt up, hide it in turn by: Left, Back, waiting out the countdown, OK (then in the new episode). After each, press Right once. | The first Right after every hide seeks. | First press swallowed; no focused node; crash. |
| M3 | Reach an intro with the skip prompt; check focus; press Right, then let it reappear and press OK; repeat and press Back, then Right. | Action chip focused (85 % hairline, 1.1 scale); Right seeks; OK skips; Back dismisses and the next Right seeks. | X focused by default; seek key fires skip or cancel. |
| M4 | Press Up to open the seek bar; press Right until the playhead enters the lead-in; wait for the prompt; press Right; then wait for the controls to auto-hide. | Seek bar keeps focus while the prompt shows idle; Right seeks; after auto-hide the prompt action is focused. | Prompt steals focus from the seek bar; no focus after auto-hide. |
| M5 | `input keyevent --longpress 22` through an intro or the credits lead-in. | Seeking continues through the section. | Seeking halts; prompt action or cancel fires. Owner judges any prompt flash. |

Visual checks: `design-ux-designer.md#for-visual-review`.

### Perf

N/A: perf track is off.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Skip prompt default focus: X or action? | yes | answered | Action button: OK skips, Back dismisses |
| Q2 | Keep the next-episode prompt returning after a seek that follows a cancel? | no | answered | Yes, keep the tested behaviour |
| Q3 | With the controls up and the seek bar focused, should an appearing prompt take focus? | yes | answered | No, focus stays on the seek bar |
| Q4 | Pass Up/Down and Play/Pause through the prompt (architect Q1)? | no | answered | Not in this run; follow-up idea |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-10 | Q1-Q3 take the analyst's recommended answers | owner (standing instruction: recommended) |
| 2026-11-10 | Q4: Up/Down and Play/Pause pass-through deferred to a follow-up idea (architect's recommendation) | owner (standing instruction: recommended) |
| 2026-11-10 | Land before player-overlays-port; that branch rebases onto this | owner (standing instruction: recommended) |
| 2026-11-10 | Gate 1: spec approved (size M, track ui, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-10 | Gate 2: ship (judge CONCERNS; Concern 1 deferred as run prompt-hide-focus-under-controls; AC-9 manual check for the owner) | owner (standing instruction: recommended) |
