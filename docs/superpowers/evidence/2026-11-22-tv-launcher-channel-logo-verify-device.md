# Device evidence: tv-launcher-channel-logo (verify, round 1)

- Build: worktree HEAD d7fbb00d (base e11d1b66), dev debug APK, sha256 143987bcd2dcdbf7379b231d191e676b07750fc8bd31665bfd4502ed83d242a0. On-device base.apk sha256 equals it after install-preserve (outcome installed, compatible_replace, version 1.12.5-dev).
- Device: the owner's TV box 192.168.1.110:5555 (model KM6, Android 10, launcher com.google.android.tvlauncher, the stock Android TV home that shows app channels). Package com.kino.puber.stage only. Leased; auth-save taken before the first install.
- Final state of the box: clean HEAD APK installed.

## Reading the provider

`adb shell content query` on android.media.tv returns no rows for the shell user, even for the app's own channel (the TV provider scopes rows by package permission), and `run-as ... content query` is refused (ACCESS_CONTENT_PROVIDERS_EXTERNALLY). So the provider tables could not be dumped as the brief planned. Substitutes used: the publisher's own stored IDs (`shared_prefs/tv_home_android.xml`, IDs only) and the launcher row. A per-row listing of channel / preview_program / watch_next_program, and the channel display-name count, are NOT CHECKED here.

## D0: base build (e11d1b66 versions of the tvhome main files, restored afterwards)

Steps: install base APK, force-stop, launch, wait 25 s, read logcat filtered to the two messages.
Observed: the coordinator logged "Failed to publish TV home programs" with java.io.IOException "Failed to add logo, so channel (ID=270) was not created". No channel stored by the app. Red baseline reproduced.

## D1-D3: HEAD build

| Step | Expected | Observed | Result |
|---|---|---|---|
| Install HEAD APK, force-stop, launch, wait 30 s | no "Failed to add logo", no "Failed to publish TV home programs" | neither line in logcat (filtered grep). The tvprovider helper logged one E-level "Logo for preview channel (ID:-1) not found" with a FileNotFoundException on the first creation only; the channel was created anyway and no exception reached the publisher (see Notes) | PASS |
| Read the publisher's stored state | one channel ID and one preview and one Watch Next ID per item | `channel_id=271`; programs string holds 5 items, each `key,previewId,watchNextId` with real IDs (30139645..30139649 and 40..44), no -1 | PASS (indirect) |
| Launcher: HOME, scroll to the last row | a Puber channel row | row titled "Puber(Dev): Продолжить просмотр" present, with Puber's items; crop in `2026-11-22-tv-launcher-channel-logo-launcher-row.png` (header only, other apps cropped out) | PASS |
| Restart twice (force-stop, launch, 30 s), re-read stored state | same channel ID, same program IDs, no new channel | stored state byte-identical across the restarts (channel_id=271, same 5 triples); no failure line in logcat | PASS (indirect) |

## AC mapping

- AC-1: no failure lines; channel created (launcher row, stored ID 271). The channel row listing and exact-one-row count were not readable (see above).
- AC-2: stored state has a preview ID and a Watch Next ID for each of the 5 items; the launcher row shows the items. Not read from provider tables.
- AC-3: channel ID 271 unchanged over two restarts; base attempt used ID 270 and was deleted, so 271 is the first and only creation.
- AC-4: JVM tests only, AndroidTvHomePublisherTest (13 green): channelFailureStillPublishesWatchNext, channelFailureIsLoggedOnce, cancellationFromChannelCreationPropagates, recoveryAfterChannelFailureAddsPreviewsWithoutDuplicatingWatchNext, programRemovedWhileChannelMissingSkipsPreviewDelete, noIdPreviewIsNotTreatedAsExistingProgram, clearAccountProgramsDeletesAllStoredIncludingNoIdPreview. No forced failure on the device.
- AC-5: the stock Android TV launcher shows app channels. Row header is text only ("Puber(Dev): ..."); this launcher does not draw the channel logo in the row header, so the icon itself is not visible and the owner cannot judge its look from this row. Logo look: NOT CHECKED.

## Notes

- The E-level helper line mentions channel ID -1 and a FileNotFoundException of content://android.media.tv/channel/-1/logo, emitted inside tvprovider before the channel is stored; it did not recur on the two later launches.
