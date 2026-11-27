LENS: state-path  ROUND: 1  BASE: 0823528b

No findings.

I followed each path the brief named through the code (worktree `/home/dev/orca/workspaces/Puber/manual-quality-hls4`, diff `0823528b..173b3da9`). None of them has a later write that undoes an earlier one.

- **Quality picks, Auto -> manual -> manual -> Auto:**
  - `selectQuality` calls `switchStreamUrl(index)`. That calls `interactor.selectStreamCandidates`, which for a manual pick returns hls4, then hls, then http. The two HLS candidates carry the picked file's size limit (`videoCap`); http and Auto carry none.
  - `replaceStreamCandidates` resets the candidate index to 0. `PlaybackController.switchStream` then builds on the saved track settings with `.applyVideoCap(stream.videoCap)`. That replaces the earlier limit, or removes it on Auto, without touching the audio or subtitle choice, and the selected quality index is written only after the switch is accepted.
  - When playback falls back to the next candidate (`switchToNextStreamCandidate`), the limit stays on hls and is removed on http.
  - The link-refresh path (`refreshStreamLinksOnce`) maps the quality to its new index and switches with that file's limit.
  - A full player rebuild (`initializePlayer` -> `prepare`) builds a fresh track selector with the limit from the kept quality index.
  - The AC-3 audio fallback (`disableAc3AndRetry`) builds on the current settings, so the limit is kept.
  - A stale failure from the previous stream with the same shared hls4 URL is already blocked by the per-session listener (`bindSession`) from the previous run.
- **Audio choice across a quality switch:** the selected row comes from the player's actual selection (`isSelected`), so the screen follows what plays. On the box, "Auto, pick UKR, then 720p" kept UKR selected; whether UKR was actually heard was not checked (no audio capture). The case where the audio choice is lost between two different hls4 manifests is design risk 3 in the spec; it was not seen on the box.
- **Audio/subtitle pick while the audio row is a fallback "Track N":** `saveTrackPreferences` writes the stored audio label and language back unchanged, and the subtitle part is not affected. A restore on that stream finds no match (empty language) and leaves the default track, so no fallback label gets saved.
- **Restore on the next start:** the next start resets the restore flags. `restoreAudioPreference` then matches the stored real label against the hls4 rows with `persist = false`, so nothing is saved during the restore. On the box, UKR came back after leaving and restarting.
- **Next-episode prefetch:** it always uses Auto (index 0), which matches the next episode starting at Auto.
