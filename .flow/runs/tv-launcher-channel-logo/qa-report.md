# QA report, Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; most tasks UP-TO-DATE from the implementer's identical-input run; AndroidTvHomePublisherTest 13/13, TvHomeSyncCoordinatorTest and ContinueWatchingSourceTest green)
DEVICE: 192.168.1.110:5555 (owner's box, Android 10, stock Android TV launcher), package com.kino.puber.stage, HEAD APK on device (sha256 matches local, 143987bcd2dcdbf7...)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (partly indirect) | D0 base: IOException "Failed to add logo" reproduced, no channel. HEAD: neither failure line; channel created (stored channel_id=271; launcher row "Puber(Dev): Продолжить просмотр"). Provider channel table and exact-one-row count NOT readable (shell and run-as queries return nothing/refused). |
| AC-2 | PASS (indirect) | Stored state: 5 items each with real preview and Watch Next IDs; launcher row shows items. provider rows not dumped. |
| AC-3 | PASS (indirect) | channel_id 271 and all IDs byte-identical across two restarts. |
| AC-4 | PASS (JVM) | AndroidTvHomePublisherTest: channelFailureStillPublishesWatchNext, channelFailureIsLoggedOnce, cancellationFromChannelCreationPropagates, recoveryAfterChannelFailure..., programRemovedWhileChannelMissingSkipsPreviewDelete, noIdPreviewIsNotTreatedAsExistingProgram, clearAccountPrograms...; no on-device forced failure |
| AC-5 | PASS for row presence; logo look NOT CHECKED | Launcher shows the Puber channel row (header crop attached); this launcher draws no logo in row header, owner cannot judge the icon from it |
| AC-6 | PASS | check.full green, the two named tests unchanged and green |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/tv-launcher-channel-logo/docs/superpowers/evidence/2026-11-22-tv-launcher-channel-logo-verify-device.md (+ ...-launcher-row.png), audit passed, committed
DEFECTS: none. Observation: first channel creation logs one E-level tvprovider line "Logo for preview channel (ID:-1) not found" (FileNotFoundException) but the channel is created and nothing propagates; not seen on restarts.
UNAVAILABLE: reading content://android.media.tv tables as shell (empty) or run-as (permission refused), so the per-row provider gate of the brief was replaced by app-stored IDs plus the launcher row.
MANUAL: owner may confirm in Settings > channels (or the launcher "customize" screen) that the Puber(Dev) channel shows the app icon.
