# Design (architect): seek-blocked-by-skip-prompt

Base 6b087697. Paths are relative to `app/src/main/java/com/kino/puber/ui/feature/player/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

## Decisions

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

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data or API change. |
| ViewModel state and actions | `vm/SeekHandler.kt:7`: `DEFAULT_SEEK_RESET_TIMEOUT_MS` becomes `internal`. `vm/PlayerVM.kt`: field `seekBurstJob: Job?` beside the jobs at `:142-151`; restart it in `seekForward` (`:706`) and `seekBackward` (`:714`); gate the two prompt checks in the tick (`:1510-1515`). No new state field, no new action. |
| Screen and DI module | N/A: no DI or screen change. |
| Compose content | `component/PlayerFocusRequesters.kt:7-14`: add `skipPrompt`, `nextEpisodePrompt`. `component/PlayerScreenContent.kt:77-82`: the hide/prompt focus effect from the Decisions table; `:131` passes `focusRequesters` to `PlayerOverlayLayers`. `component/PlayerOverlayLayers.kt:105-133`: new `focusRequesters` parameter; passes `skipPrompt`/`nextEpisodePrompt` and `onSeek = onAction` to the two overlays. `component/SkipSegmentOverlay.kt:13-38`, `component/NextEpisodeOverlay.kt:12-33`: drop `focusPrimaryAction`, add `focusRequester: FocusRequester` and `onSeek: (PlayerAction) -> Unit`, pass both through. `component/PlayerTimedActionOverlay.kt:164-180`: same parameter change; delete `:189-197`; key handler on the `Row` at `:266`; the action button (`:279-282`) takes the passed requester. `TimedActionButton.focusRequester` (`:309`, `:381`) becomes nullable for the X. `component/PlayerVideoSurface.kt:213`: add `seekActionForKeyCode`. |
| Resources | N/A: no strings or look change. |

## Alternatives rejected

- Overlay keeps its focus effect, gated by a `takeFocus` flag: two effects request focus in one frame when the controls hide.
- Burst marker in `onSeekPerformed`: delays prompts after skip, resume and start over, and changes what `PlayerVMPromptDismissalTest` exercises.
- Clock injected into `SeekHandler`: larger change for the same AC-3 coverage.
- Forward the whole `playerActionForKeyCode` map from the prompt (Up/Down, play/pause): not asked; Open question 1.
- Option 3 from research (non-focusable prompt, OK remapped on the surface): changes what OK means; the owner took option 1.

## Risks

| # | Risk | Lives in | Mitigation |
|---|---|---|---|
| 1 | Focus: a `requestFocus()` in the effect fails silently (prompt node not yet attached on the frame its state arrives, or the surface request lost while the focused button exits), so no node owns focus and the first D-pad press is swallowed (AC-4). | `component/PlayerScreenContent.kt:77-82` | Device check of AC-4 and AC-9 for each hide path; if a request fails, one retry after `withFrameNanos` in this effect only (the shared helper is `focus-request-helper`, out of scope). |
| 2 | Focus: the skip overlay leaves composition instantly when the next-episode countdown starts (`component/PlayerOverlayLayers.kt:112`); if the effect key misses this, focus is orphaned. | `component/PlayerScreenContent.kt:77` | The effect keys on the prompt kind, not a visible boolean. |
| 3 | D-pad: a stray OK `ACTION_UP` (OK pressed on the surface, prompt focused before release) lands on the action button. tv-material3 `Surface` clicking on an up without its down is unverified (AC-7). | `component/PlayerTimedActionOverlay.kt:350` | Compose test: OK up without down on the focused action does not call `onAction`; if it does, the `Row` handler drops OK events whose down it did not see. |
| 4 | Lifecycle: `seekBurstJob` survives an episode switch; a prompt in the new episode can wait up to 1.5 s. | `vm/PlayerVM.kt:1510` | Accepted; bounded by the window. |
| 5 | Recomposition: `PlayerOverlayLayers` takes `PlayerFocusRequesters`; the effect reads two more content fields. | `component/PlayerOverlayLayers.kt:105` | `PlayerFocusRequesters` holds only `val FocusRequester` fields, so it is inferred stable; the new keys change only when a prompt appears or hides. |
| 6 | Rollout: `player-overlays-port` rewrites `PlayerFocusEffects` and edits `PlayerTimedActionOverlay.kt`. | `component/PlayerScreenContent.kt`, `component/PlayerTimedActionOverlay.kt` | This lands first; that branch rebases and keeps the prompt-kind key. |
| 7 | Verification data: skip segments need TMDB/IntroDB data, which a worktree build lacks. | device run | qa-device names the source for AC-1, AC-5; AC-2, AC-4, AC-9 use the next-episode prompt in a series. |

## Open questions

1. Should the prompt also forward Up/Down (show controls) and play/pause like the bare video? Recommended: yes, as a follow-up idea; no AC asks for it. Blocks: no.
