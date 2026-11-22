# Research: tv-launcher-channel-logo

## 1. Request

Android TV launcher channel is never created: on the owner TV box 192.168.1.110:5555 (com.kino.puber.stage, clean HEAD of cache-targeted-invalidation) the first reconcile logs "IOException: Failed to add logo, so channel (ID=269) was not created" from AndroidTvHomePublisher.ensureChannel, so no Continue watching / Puber row ever appears on the launcher. Pre-existing (core/tvhome unchanged between 40762767 and ef2d1c20). Source: cache-targeted-invalidation qa round 3, evidence docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md (in worktree /home/user1/orca/workspaces/Puber/cache-targeted-invalidation, commit 7415b586).

## 2. Understanding

The Android TV publisher builds its preview channel without a logo. androidx.tvprovider 1.1.0 treats a channel without a logo as a failed insert: it deletes the row it just inserted and throws. Because `reconcile` creates the channel before anything else, neither the app's preview row nor its Watch Next ("Play Next" / Continue watching) entries are ever written. The failure is deterministic on every Android TV device and has existed since the feature landed (46c9d9a1, 2026-08-18); it is not specific to the box.

## 3. Current state in code

- `app/src/main/java/com/kino/puber/core/tvhome/AndroidTvHomePublisher.kt:42-55` `ensureChannel`: `PreviewChannel.Builder` sets only display name and app-link intent; no `setLogo`. Line 52 calls `helper.publishDefaultChannel(channel)`.
- `AndroidTvHomePublisher.kt:24-35` `reconcile`: `ensureChannel()` runs first, so its exception skips every `publishPreviewProgram` and `publishWatchNextProgram` call (lines 90-103). Watch Next does not need a channel, yet is gated on it.
- `AndroidTvHomePublisher.kt:43-44`: stored channel ID is reused only if `getPreviewChannel` still returns it; nothing is stored on failure, so every reconcile retries, inserting and deleting one channel row each time (ID 269 on the box suggests many attempts; unverified).
- tvprovider 1.1.0 bytecode (`javap` on the cached `tvprovider-1.1.0-runtime.jar`):
  - `PreviewChannelHelper.publishChannel`: insert channel, then `addChannelLogo`; on `false` it calls `deletePreviewChannel(id)` and throws `IOException("Failed to add logo, so channel (ID=…) was not created")`.
  - `addChannelLogo` returns `false` immediately when `PreviewChannel.isLogoChanged()` is false, i.e. when neither `Builder.setLogo(Bitmap)` nor `Builder.setLogo(Uri)` was called. This is the exact path hit.
  - `getLogoFromUri` reads `android.resource`, `file` and `content` URIs through `ContentResolver.openInputStream` + `BitmapFactory.decodeStream`; other schemes are downloaded.
  - `publishDefaultChannel` = `publishChannel` + `TvContractCompat.requestChannelBrowsable`.
  - `PreviewChannel.Builder.build()` validates only display name and app-link URI, so the missing logo is not caught at build time.
- `core/tvhome/TvHomeSyncCoordinator.kt:29-41`: catches the exception and logs `Failed to publish TV home programs`; nothing surfaces to the user.
- `core/tvhome/TvHomePublisherFactory.kt:19-25`: Android TV publisher for API 26+ with `FEATURE_LEANBACK`; Fire TV uses `FireTvHomePublisher` (notifications, `setSmallIcon(R.mipmap.ic_launcher)` at `FireTvHomePublisher.kt:61`) and is unaffected.
- `PuberApp.kt:57-58`: publisher and coordinator are Koin singletons on `Dispatchers.IO` (satisfies `@WorkerThread`).
- Logo candidates: `res/mipmap-*/ic_launcher.webp` (bitmap WebP, no `mipmap-anydpi` adaptive XML, so `BitmapFactory` can decode it); `res/mipmap-*/ic_banner.webp`; `res/drawable-xxxhdpi/ic_kinopub.webp`. Opacity of these images: unverified.
- Strings: `tv_home_continue_watching` and `tv_home_recommendation_channel` in `res/values/strings.xml:3-4`, `res/values-en/strings.xml:3-4`.
- Manifest: `WRITE_EPG_DATA` declared (`AndroidManifest.xml:4`).
- Tests: `app/src/test/kotlin/com/kino/puber/core/tvhome/TvHomeSyncCoordinatorTest.kt` and `ContinueWatchingSourceTest.kt` mock the publisher; `AndroidTvHomePublisher` has no unit or instrumented test.

## 4. Related work

| Item | Relation |
|---|---|
| Commit 46c9d9a1 "feat: integrate TV home and content links" | Introduced `AndroidTvHomePublisher` without a logo; later commits 9930b952, 85bfd1d2, 779b2a42 only touched lint suppressions. |
| Run `telemetry-core` (qa-report.md:19,49,68,88; evidence `docs/superpowers/evidence/2026-10-15-telemetry-core-device.md:106-107,205,239-245`) | Same IOException seen at every cold start on 2026-10-15, retraced to `ensureChannel:52`; used only as sample `err` data, never filed. |
| Run `telemetry-frames-keys-memory` (qa-report.md:36; evidence 2026-10-16 line 125) | Same error noted as "unrelated". |
| Run `cache-targeted-invalidation` (state.md log, evidence at 7415b586) | Source of this run; launcher row AC left NOT CHECKED because of this bug. |
| Runs `cache-layer-rework`, `startup-settled-signal` | Touch when TV home refresh is triggered, not how the channel is built; no overlap. |

Duplicate: none. No other run or spec covers the channel logo.

## 5. External findings

| Finding | Source |
|---|---|
| "You must add a logo for your channel. Use a Uri or Bitmap. The logo icon should be 80dp x 80dp, and it must be opaque. It is displayed under a circular mask." | https://developer.android.com/training/tv/discovery/recommendations-channel (read 2026-11-22) |
| The first channel can be the default channel and appears without user approval; Watch Next ("Play Next") is a system row the app adds programs to, independent of app channels. | same page; https://developer.android.com/training/tv/discovery/watch-next-add-programs (read 2026-11-22) |
| `publishChannel` adds the channel to the provider; `publishDefaultChannel` also makes it browsable. The reference page does not mention the logo requirement. | https://developer.android.com/reference/androidx/tvprovider/media/tv/PreviewChannelHelper (1.1.0) |
| tvprovider 1.1.0 (2025-05-07) is the latest stable; 1.1.0 changelog has no logo-related change. | https://developer.android.com/jetpack/androidx/releases/tvprovider |
| Community reports (not official): the Google TV home ignores app preview channels and builds its Continue watching row from the Engage SDK for onboarded partners; classic Android TV home still shows channels and Play Next. Unverified against Google documentation. | https://github.com/brunoccst/iptv-player/issues/320 ; https://github.com/Silo-Server/silo-android/issues/714 ; https://forums.plex.tv/t/android-tv-google-tv-watch-next-integration-lost-since-at-least-2026-17-0/943380 |

## 6. Options

| Option | Change | Trade-offs | Size |
|---|---|---|---|
| A. Set a logo (recommended) | In `ensureChannel` call `PreviewChannel.Builder.setLogo(Uri)` with `android.resource://<package>/mipmap/ic_launcher` (or `setLogo(Bitmap)` decoded from that resource). | One-line root-cause fix that follows the official requirement; `getLogoFromUri` handles `android.resource`. Image opacity and 80dp shape not checked; may need a dedicated opaque logo asset later. | S |
| B. A plus decouple Watch Next from the channel | A, and in `reconcile` publish Watch Next entries even when `ensureChannel` fails (catch per step, log). Optionally add a publisher test seam. | Continue watching survives any future channel failure, and on launchers that ignore app channels. Slightly more code in one file; behaviour on channel failure must be specified (skip preview programs, keep Watch Next). | S |
| C. Drop the preview channel, publish Watch Next only | Remove `ensureChannel` and preview programs; keep `WatchNextProgram`. | Simplest, matches launchers that only show Play Next/Continue watching; loses the dedicated Puber row on classic Android TV; changes what the feature promises. | S |

Recommended: B. A alone fixes the reported error; the decoupling costs little and removes the single point of failure that hid this bug for three months.

## 7. Risks

- Launcher type on the box is unknown. If it is a Google TV home, neither the channel nor Watch Next may be shown even after the fix (community reports, unverified), so the on-device acceptance must check provider rows (`content query` on `preview_program`/`watch_next_program`/`channel`) and not only the launcher screen.
- `ic_launcher` may have transparent corners; the docs ask for an opaque logo. Cosmetic only.
- Existing installs have `channel_id` unset (never stored), so the fix needs no migration; orphan channels were deleted by the library each time.
- Device verification is required (persistence/system provider). Screens of the launcher show the owner's other apps; keep them out of evidence (as the source run did).

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Which image is the channel logo: `ic_launcher` or a new dedicated opaque 80dp asset? | `ic_launcher` now; a dedicated asset only if the device shows it badly. |
| Q2 | Decouple Watch Next from channel creation (option B) or fix the logo only (A)? | B. |
| Q3 | Acceptance on the box: provider rows present, or a visible launcher row? | Provider rows via `content query` as the gate; a visible row recorded if the box launcher shows app channels. |

## 9. Sources

- Files: `app/src/main/java/com/kino/puber/core/tvhome/{AndroidTvHomePublisher,TvHomeSyncCoordinator,TvHomePublisherFactory,TvHomePublisher,FireTvHomePublisher}.kt`, `PuberApp.kt`, `AndroidManifest.xml`, `res/values*/strings.xml`, `res/mipmap-*`, `gradle/libs.versions.toml:9,68`, `app/build.gradle.kts:360`, `app/src/test/kotlin/com/kino/puber/core/tvhome/*`.
- Library bytecode: `~/.gradle/caches/9.7.0/transforms/9e000a6ad3a46643985780d6dede340f/transformed/tvprovider-1.1.0-runtime.jar` (`javap -c -p` on `PreviewChannelHelper`, `PreviewChannel`, `PreviewChannel$Builder`).
- Commits: 46c9d9a1, 9930b952, 85bfd1d2, 779b2a42; 7415b586 (evidence, cache-targeted-invalidation worktree).
- Runs: cache-targeted-invalidation, telemetry-core, telemetry-frames-keys-memory, cache-layer-rework, startup-settled-signal.
- Docs: `docs/superpowers/evidence/2026-10-15-telemetry-core-device.md`, `docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md`.
- URLs: listed in section 5.
