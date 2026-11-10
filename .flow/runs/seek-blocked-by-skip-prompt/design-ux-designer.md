# Design delta: seek-blocked-by-skip-prompt

ux-designer, 2026-11-10, base 6b087697. Paths are relative to `app/src/main/java/com/kino/puber/ui/feature/player/component/`.

## Governing stand

**No stand at base governs the timed-action prompt.** `docs/design/player-controls/README.md` covers the title, seek bar and button row only. The chips' scene lives in `docs/design/player-overlays/`, which exists only on the unmerged `player-overlays-port` branch (`92a9441d`). Its chip sizes were agreed on 2026-09-26 but never compiled or measured on a television, and the stand says nothing about focus order.

The conductor does not need to run `designing-a-screen` before planning. This run changes which chip holds focus and when, not how a chip looks (look is out of scope, brief.md "Out"). Nothing here needs a slider.

## Agreed numbers that apply

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

## What the viewer sees change

| Case | Before | After |
|---|---|---|
| Skip prompt appears, controls hidden | The X has focus: its tint goes from 82 % to 100 % and tv-material's default 1.1 focus scale applies (no `scale =` passed to `Surface`). The action chip stays at its idle 45 % hairline. | The action chip has focus: its hairline brightens to 85 % (`TIMED_ACTION_BORDER_FOCUS_ALPHA`) and it scales to 1.1. The X stays idle at 82 %. This is the same picture the next-episode prompt already shows. |
| Next-episode prompt appears, controls hidden | Action focused | Unchanged |
| A prompt appears while the controls are up and the seek bar has focus (Q3) | The prompt takes focus and the seek bar loses it | Both chips stay idle and the seek bar keeps focus. This idle-only state of the prompt is new on screen. |
| Left/Right while the prompt has focus | The focus ring jumps between X and the action | The ring stays on the action and the seek indicator moves |
| The prompt hides | Nothing on screen holds focus | The video surface holds focus, or the controls target if the controls are up (no visible ring) |

Focus visibility is the hairline going from 45 % to 85 % plus the 1.1 scale. That is the existing, deliberate treatment (`PlayerTimedActionOverlay.kt:99-106`), and it needs no change for the action to be the default. The X's weak cue no longer matters, because the X is never the default focus.

**Back hint: add none.** The X glyph already reads as "dismiss". Adding a "Back" label or icon would change the prompt's look, which is out of scope. The X loses its D-pad role but keeps its click target for pointer input. Whether to drop it is a question for the overlays stand.

## Focus order and visibility

The run adds no new focusable element. With a prompt up, the keys work as follows:

| Key | With the prompt focused |
|---|---|
| OK | Runs the action (skip, or next episode) |
| Back | Cancels (`BackHandler`, `:181`) |
| Left/Right, FF/RW, media skip | Seek and are consumed; the ring does not move |
| Up/Down, Play/Pause | Open question A |

Invariant to hold, so the prompt never sits on screen without an obvious OK target: **while the controls are hidden and no panel or dialog is open, a visible prompt's action chip is focused.**

## Open for the architect

- **A. Which keys the prompt forwards.** `playerActionForKeyCode` (`PlayerVideoSurface.kt:213-227`) also maps Up/Down to `ShowControls` and Play/Pause to `TogglePlayPause`. Recommended: forward the whole map. The prompt then answers to everything the bare video does except OK and Back, and Up/Down peek at the controls, as the Android TV playback guidance describes. If only seek keys are forwarded, Up/Down on the prompt fall to default focus search, which on this layout has no target.
- **B. Focus when the controls hide while a prompt is up.** Scope item (d) and `PlayerFocusEffects` (`PlayerScreenContent.kt:72-79`) send focus to the video surface. That leaves a prompt that appeared under the controls (Q3) on screen with no ring, and OK on the surface opens the controls instead of skipping. Recommended: when the controls hide and a prompt is visible, focus its action, which is the invariant above. Send focus to the surface only when no prompt is visible.
- **C. Reaching the prompt while the controls are up.** The prompt sits above the bar on the right (`timedActionInset`, `PlayerOverlayLayers.kt:111`). Whether Up from the seek bar lands on it is untested; geometric search may pick it. Recommended: no explicit route. B covers the case once the controls time out, and qa should note on the device where Up from the seek bar goes.

## Shared constants: take a parameter, do not retune in place

| Constant / parameter | Rule |
|---|---|
| `focusPrimaryAction` (`SkipSegmentOverlay.kt:28` -> `true`) | Flip the caller's argument, not the default inside the overlay. If both callers end up `true`, removing the parameter is acceptable. |
| Taking focus on appear (`LaunchedEffect(visible)`, `:191-197`) | Gate it on a parameter passed from `PlayerOverlayLayers` (for example `takeFocus = !controlsVisible && no panel/dialog`), not on a check read inside the overlay |
| `TimedActionBottomPadding`, the `Row` 12 dp gap, 15 sp, 14 × 10 dp padding, shadow, all `TIMED_ACTION_*_ALPHA` | Do not touch. `player-overlays-port` replaces them; edits here are rebase conflicts and give no visual gain |
| `playerActionForKeyCode` | Reuse it; do not copy the key map into the overlay |

## For visual review

On the emulator at 1080p (1 dp = 2 px), check:

1. The skip prompt appears with the action chip focused: 85 % hairline and 1.1 scale on the action, X at 82 % tint.
2. With the controls up, the prompt appears with both chips idle and the seek bar keeps focus.
3. Chip geometry is unchanged from base: 48 dp edge offset, 12 dp gap, 15 sp label.
