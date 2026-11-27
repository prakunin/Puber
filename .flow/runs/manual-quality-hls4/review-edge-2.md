LENS: edge  ROUND: 2  BASE: 0823528b

No findings.

I reviewed the incremental diff 173b3da9..07aba956 (fixes #1-#4 from triage-1) along with the code around each change in `/home/dev/orca/workspaces/Puber/manual-quality-hls4/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` and `VideoCapParameters.kt`. I also checked each item on the `also_consider` list in `.flow/roles/reviewer.md`.

- **Pick, then Auto, then pick again:** `switchStream` builds on the player's current parameters, so a later cap clears the viewport again and Auto puts back the default from `setViewportSizeToPhysicalDisplaySize(context, true)`. That call matches what `DefaultTrackSelector(context, …)` sets in `prepare`, including the orientation flag being `true`.
- **Rebuilding the player:** `prepare` applies `applyVideoCap(stream.videoCap, context)` to the context defaults, so a capped stream starts without a viewport trim and an uncapped one keeps the display default.
- **Other code that sets the parameters:** the audio, subtitle and AC3 paths (`PlaybackController.kt:586`, `:598`, `:911`) build on the current parameters. They keep the cap and the viewport state and do not reset either.
- **Context lifetime and threading:** the code uses the `context` the controller already holds and on the same thread as before. The new display-size read is the one `DefaultTrackSelector(context)` already did. No new reference outlives a scope.
- **Cap smaller than every variant (320x180):** "exceed video constraints if necessary" still picks a variant, and the new assertion after `prepare` covers it.

### Unproven
- /home/dev/orca/workspaces/Puber/manual-quality-hls4/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:719 The warm-up player gets neither the cap nor the viewport setting, so with a manual pick it may pre-cache a different variant from the one played. This only matters if a warm-up started with a manual pick actually prefetches segments of a variant the capped player never requests. That code is outside this diff.
- /home/dev/orca/workspaces/Puber/manual-quality-hls4/app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt:138 On an emulator whose default viewport is unbounded, the check that Auto restores the default passes either way, as the plan itself records. It would only prove something on a device with a finite default display viewport, such as the TV box.
