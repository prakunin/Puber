CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1427-1455 (`onPlaybackEnded`) with `requestSeek` at about :758 - A queued backward target can be lost when playback ends. I read the code, and nothing in `onPlaybackEnded` commits or honours `pendingSeekTarget`.
  - Scenario: a film is playing and a forward seek lands within 250 ms of the end. The throttle window is open. The viewer presses Left, so the target is only stored in `pendingSeekTarget`, and the indicator shows the rewind. Media3 reaches ENDED before the trailing seek runs.
  - Result: `onPlaybackEnded` overwrites `currentPosition` with the player position. With no panel open and the title a film or last episode, it calls `exitPlayer()`, which drops the pending target. The viewer is thrown out of the player despite having pressed rewind.
  - Before the branch, `seekTo` ran on every press, so ENDED then Left worked.
  - Narrow timing window, so minor. A fix would call `commitPendingSeek()` at the start of `onPlaybackEnded`, or skip end handling while a target is pending.
REJECTED:
- Codex (adversarial, high): a next-episode prompt can start during a seek burst because `onPlaybackEnded` bypasses the tick's burst guard - Not a regression. A seek that clamps to the duration ends playback immediately, as it did at the base, so the prompt would appear then too. The only new behaviour is the lost-rewind case confirmed above.
- Codex (both passes): AC-8 device evidence used eight discrete keyevents about 140 ms apart, not a 5 s hold, and did not test the seek bar's key repeat. This is an evidence-coverage gap and does not show a defect. The throttle logic is covered by unit tests (`aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget`, `aLongHoldKeepsAtLeastOneWindowBetweenSeeks`). The evidence file already lists the fine-grained per-window seek count as not observable from the UI. Rejected as `unproven`. The conductor may still decide the AC wording needs a real held-key run.
VERDICT: changes needed
