# Player overlays: auto-hide transient, keep opened

Status: draft
Date: 2026-10-10
Slug: player-overlay-autohide
Kind: bug
Size: S
Route: quick

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

**Reproduced: no.** Read from code only; the emulator is unpaired (board, subtitle-upstream-port),
so the player cannot be reached. The rule the user asks for is already mostly in place, so the
exact thing they saw is unknown (see Open questions).

What exists today (the code wins over the request):

| Overlay | Kind | Today | Source |
|---|---|---|---|
| Controls bar (seek bar, buttons, title) | transient | hides 4.5 s after the last show or key press in the bar; timer paused while buffering, restarted when buffering ends | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:706] `CONTROLS_HIDE_DELAY_MS` [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1834]; [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerControlsOverlay.kt:146] |
| Debug overlay (pref `debugOverlayEnabled`) | follows controls | drawn only while controls are visible | [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerOverlayLayers.kt:140] |
| Seek indicator | transient | 1.5 s | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:643] |
| Play/pause indicator | transient | 1.5 s | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:609] |
| Skip-segment chip | transient | 7 s countdown then auto-skip; dismissed ones stay down 60 s | [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerCountdowns.kt:25] |
| Next-episode chip | transient | 7 s countdown then next episode | [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerCountdowns.kt:22] |
| Side panel: Settings (incl. stream info page), Episodes, About | explicit | opening cancels the hide timer, hides the bar; only Back / close dismisses; `applyControlsVisibility` refuses to hide with a panel open | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:47]; [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:99] |
| Resume dialog | explicit | no timer; controls hide timer only starts once it is answered | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:357]; [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1293] |
| Error overlay | explicit | Retry / Back | [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreenContent.kt:100] |
| Controls at end of media | explicit | held up, no timer, Back leaves the player | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:87] |

Gaps found (root cause plausible, not confirmed on a device):

1. When buffering ends the VM reschedules the hide whenever the controls are visible, without
   asking whether they are held up for the ended-playback state or are otherwise pinned.
   That would hide the end-of-media controls after 4.5 s. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:199]
2. Nothing tests the timing: no test drives `CONTROLS_HIDE_DELAY_MS` and a panel together, so the
   "stays until closed" rule is protected only by two state-machine unit tests
   ([Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/ControlsStateMachineTest.kt:191]).
3. The controls hide while playback is paused (the timer ignores `isPlaying`). Whether that is wanted is a product question.

Overlap with in-flight work:
- `player-overlays-port` (commit 92a9441d): touches only Compose components (`PlayerScreenContent`, `ResumeDialog`, `PlayerTimedActionOverlay`, ...); no change to `PlayerVM` or `ControlsStateMachine`. No conflict expected for a VM-side fix; any new on-screen element I add would conflict, so this task adds none.
- `subtitle-player-fixes` (uncommitted): adds `ControlsStateMachine.initialize(resumeDialogVisible)` and `PlayerVM.publishPreparedContent` / `withControlsState`, replacing `applyControlsState` body. This task edits the buffering-end branch (`PlayerVM.kt:199-206`) and `ControlsStateMachine` state; merge it after, or rebase, and keep the new `initialize` resetting any flag added here.

## Acceptance criteria

- **AC-1** Given the controls are shown and nothing else is open, when 4.5 s pass without a key press in the bar, then the controls hide.
- **AC-2** Given the viewer opened the Settings panel, the stream info page inside it, Episodes or About, when any time passes (well beyond 4.5 s) with no key press, then the panel stays open and the controls bar does not reappear.
- **AC-3** Given a panel is open, when buffering starts and ends, then the panel stays open.
- **AC-4** Given a panel is open, when the viewer closes it (Back or close), then the controls come back and hide again after 4.5 s.
- **AC-5** Given the resume dialog is up, when time passes, then it stays until answered, and the controls hide timer starts only after the answer.
- **AC-6** Given the media has ended and the end-of-media controls are up, when buffering ends or any time passes, then the controls stay up until Back leaves the player.
- **AC-7** Given a seek or play/pause indicator is shown, when 1.5 s pass, then it disappears; the skip and next-episode chips go away after their countdown or when dismissed.
- **AC-8** Given the debug overlay preference is off and a panel was open, when the panel closes, then no stream readings are left on screen (existing behaviour kept).
- **AC-9** On the TV: open Settings > stream info, wait 30 s, and it is still there; close it, wait 5 s, and the controls are gone.

## Out of scope

- Visual changes to any overlay (owned by `player-overlays-port`).
- Changing the 4.5 s / 1.5 s / 7 s / 60 s timings, unless the user asks.
- Subtitle work and `ControlsStateMachine.initialize` (owned by `subtitle-player-fixes`).
- A user-visible setting for the hide delay.

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

1. Add a state-machine guard so the buffering-end reschedule and `scheduleControlsHide` do nothing while `playbackEnded` is set; add `ControlsStateMachineTest` cases (`applyControlsVisibility` with `playbackEnded`). [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/ControlsStateMachine.kt:99]
2. In the buffering-end branch, skip `scheduleControlsHide()` unless the machine says the controls may time out. [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:199]
3. Add `PlayerVM` tests with a test scheduler: controls hide at 4.5 s (AC-1); panel stays after 60 s and across a buffering cycle (AC-2, AC-3); close restarts the timer (AC-4); ended-state controls stay (AC-6). Follow the style of [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMCountdownClockTest.kt].
4. Device check for AC-9 once a paired device is available (needs-device).


## Open questions


1. What exactly did you see? Today the Settings, stream info, Episodes and About panels already never time out and the controls do hide after 4.5 s. Which overlay stayed on screen too long, or closed too early, and in which situation (paused, buffering, after switching episodes)?
2. Should the controls also hide while playback is paused? Today they do. Or should they stay up on pause?
3. Is 4.5 s right for the controls, or do you want another value?
4. Should the debug overlay (settings toggle) stay on screen permanently instead of riding the controls? Today it appears and disappears with them.
