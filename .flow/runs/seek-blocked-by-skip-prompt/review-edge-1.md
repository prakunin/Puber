LENS: edge  ROUND: 1  BASE: 6b087697

No findings.

Boundaries checked against `git diff 6b087697..HEAD -- app` and surrounding code; each one is handled:

- Episode switch during a seek burst: `resetMediaSession` does not cancel `seekBurstJob`. A prompt in the new episode can wait up to 1.5 s. The spec already accepts this as Risk 4.
- Skip prompt swapped for the next-episode prompt: the focus effect is keyed on the prompt kind, and the request gets one retry on the next frame. Going the other way (cancel the next-episode prompt while a skip segment is still set) changes the key to Skip and focuses `skipPrompt`. Swapping one skip segment for another keeps the same content (`contentKey = { it != null }`), so focus is not left without an owner.
- Panels and dialogs: the effect does nothing while a panel or the resume dialog is open. `checkSkipSegment`, `checkEarlyNextEpisode`, `onPlaybackEnded` and the late credits load all require `activePanel == None` before raising a prompt. So a prompt cannot appear under a panel, and when the panel closes the prompt is focused.
- Controls toggling: a prompt that appears while the controls are up takes no focus; when the controls hide, the prompt's action button gets focus; when the controls show, the second effect focuses the controls target as before.
- Key repeat: the `Row` preview handler seeks on every `ACTION_DOWN`, repeats included, and consumes the key-up. The first seek clears the prompt synchronously, because `PlaybackController.seekTo` calls `onSeekPerformed` inline. Focus then goes to the video surface, which handles the remaining repeats.
- Holding Right to the end of the episode: `onPlaybackEnded` is not held back by the burst, so the next-episode prompt appears and takes focus. Each further repeat seeks to the duration, clears the countdown, and the player ends again, so the prompt comes back. The surface did the same before this change, so nothing new breaks.
- Lifecycle: `seekBurstJob` runs in the VM scope and is cancelled with it. The tick checks prompts only while playing, so a burst that times out during buffering raises the prompt once playback resumes.

### Unproven
- `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreenContent.kt:85` OK pressed on the prompt as it hides on its own (skip countdown at zero, or the segment ends between the OK key-down and key-up). Focus now moves to the surface, and the OK key-up becomes `OkReleased`, which opens the controls on the buttons (before this change the key-up was lost). It would be real if a device trace showed OK presses landing at the moment the countdown expires.
- `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerTimedActionOverlay.kt:263` With the controls up, Up from the seek bar may land on the prompt (qa did not check where Up goes). Keys pressed there skip `ControlsScrim`'s auto-hide timer reset, and Left/Right seek instead of moving focus. It would be real if the device showed Up from the seek bar landing on the prompt.
