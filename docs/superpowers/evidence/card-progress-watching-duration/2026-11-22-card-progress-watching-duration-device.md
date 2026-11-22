# Device evidence: card-progress-watching-duration

Date: 2026-11-22. Head: db292ad0. Device: 192.168.1.110:5555 (owner's TV box), package com.kino.puber.stage.

## Build and install

- Built `:app:assembleDevDebug` in the worktree; APK sha256 94ca5259ff6753e80b5680d9e22dc5634f317e497d643f0a1d7edd75d7066c8b.
- `tools/android-apk-install-preserve`: outcome `installed`, classification `compatible_replace`, destructive_action false.
- On-device base.apk sha256 equals the built APK sha256. PASS.
- App launched; Home rendered; lease released and app force-stopped afterwards.

## AC-7 (card bar over Details visit and restart)

Status: NOT CHECKED.

- The Home row "continue watching" holds only series (episode-count badge on every card, 16 cards scanned by D-pad). No movie card with a progress bar exists on this account, so the movie path cannot be observed without playing one.
- Starting and playing a movie past 5 min writes playback progress to the owner's account. The standing authorization in AGENTS.md covers lease, install, launch, remote input and screenshots, not playback or account mutation, so it was not done.
- Field names of /v1/items/<id>: not recorded, no capture was made.
- Side observation: a cold start, a Details open and BACK left the Home row and its series bars intact; no crash. This is not evidence for AC-7.

Screenshots were deleted: they showed the account's watching list.
