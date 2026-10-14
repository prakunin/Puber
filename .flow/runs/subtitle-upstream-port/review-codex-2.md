CODEX: ran: review (adversarial pass not run)

I ran only the plain `review` with `--model gpt-6-astra`. I did not run `adversarial-review`, although the brief called for it on size M.

CONFIRMED:
- [minor] /home/user1/orca/workspaces/Puber/subtitle-player-fixes/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:372-374 - `prepare()` now clears `pendingSubtitleTrack`. The VM re-sends the picker row only from `restoreTrackPreferences()`. That runs only from `onTracksUpdated`, and `notifyTracksUpdated()` (line 852-855) returns early when there are no audio groups.
  - Scenario: media has no audio track and the viewer picks a subtitle. Changing the buffer preset or toggling Fast DNS calls `initializePlayer()` for the same media and sets `tracksRestoredForCurrentMedia = false`. Subtitles then go off while the picker still shows the row selected.
  - Before the fix, `onTracksChanged` re-applied the pending pick.
  - It is a regression, but it only affects audio-less streams, which are rare. The fix could be to also call the restore outside the audio-group guard, or to clear the pending pick only on a media change.

REJECTED:
- None. Codex raised only the one finding above.

I also read the rest of the round 2 diff (the label mapping, the `lastPanelOpener` removal, the tests and the plan notes). I found no other defects.

VERDICT: changes needed (one minor item; the conductor can accept it as a known limitation)
