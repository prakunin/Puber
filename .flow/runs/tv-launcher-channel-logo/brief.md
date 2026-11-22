# Brief: tv-launcher-channel-logo

Base 64a580b1. Analyst, 2026-11-22. Builds on `research.md`. Lines re-read at base and holding: `core/tvhome/AndroidTvHomePublisher.kt:24-35,42-55,90-110,112-135`, `TvHomeSyncCoordinator.kt:29-41`, `TvHomePublisherFactory.kt:19-25`, `PuberApp.kt:57-58`; `res/mipmap-*/ic_launcher.webp` present, no `mipmap-anydpi*`. No Robolectric in `app/build.gradle.kts` (only mockk), so the publisher has no JVM test seam today. The tvprovider bytecode trace is taken from research.md: Current state in code, not re-run.

kind: bug. size: S. tracks: none. Device verification: **yes** (writes to the system TV provider; the result only shows at runtime).

Size reason: one class (`AndroidTvHomePublisher`), no contract or DI change; the only stored-format touch is a sentinel for a missing preview ID in its own private prefs, read back compatibly.

## Root cause (confirmed)
`AndroidTvHomePublisher.kt:48-51` builds the `PreviewChannel` without `setLogo`. tvprovider 1.1.0 `PreviewChannelHelper.publishChannel` inserts the channel, `addChannelLogo` returns false because the logo never changed, the helper deletes the row and throws `IOException("Failed to add logo, so channel (ID=…) was not created")` (research.md: Current state in code). The throw at `:52` escapes `reconcile` at `:25` before any program is written, so Watch Next entries are lost too; `TvHomeSyncCoordinator.kt:38-39` only logs it. Confirmed by the bytecode path and the identical log on the box (2026-11-22) and in `docs/superpowers/evidence/2026-10-15-telemetry-core-device.md:106-107`. Present since 46c9d9a1.

## Scope
Option B of research.md: Options (logo plus Watch Next decoupled), assuming Q1 and Q2 as recommended.

| Item | Change | Where |
|---|---|---|
| Logo | `PreviewChannel.Builder.setLogo(Uri)` with `android.resource://<packageName>/mipmap/ic_launcher` (built from `context.packageName`, so dev/stage suffixes resolve) | `AndroidTvHomePublisher.kt:48-51` |
| Decouple | `reconcile` catches a channel failure (rethrows `CancellationException`), logs it with `core.logger.log`, and still publishes/updates Watch Next; preview programs are skipped while there is no channel | `AndroidTvHomePublisher.kt:24-35,62-105` |
| Stored IDs | `StoredProgram.previewId` may be `NO_ID` when no preview was written; `delete` and the reuse check skip `NO_ID`; the prefs string format is unchanged | `AndroidTvHomePublisher.kt:90-96,107-110,132-135` |

## Acceptance criteria
Device ACs run on the box `192.168.1.110:5555` (package `com.kino.puber.stage`) with a worktree build installed by install-preserve; provider rows are read with `adb -s <serial> shell content query` on the TV provider URIs, filtered to the app's rows.

- AC-1. Given the fixed build on a device with at least one Continue watching item, When the app starts and the first TV home reconcile runs, Then logcat has no `Failed to add logo` and no `Failed to publish TV home programs`, and `content://android.media.tv/channel` has exactly one preview channel of the package with the display name of `tv_home_continue_watching`.
- AC-2. Given AC-1, When the reconcile has finished, Then `preview_program` holds one row per published item linked to that channel ID, and `watch_next_program` holds one `WATCH_NEXT_TYPE_CONTINUE` row per published item.
- AC-3. Given the channel exists, When the app is restarted (a second reconcile), Then the channel ID is unchanged and no second channel row of the package appears (stored ID reused, `AndroidTvHomePublisher.kt:43-44`).
- AC-4. Given channel creation fails (forced by a temporary debug change or a unit test with a mocked helper if a seam is added), When `reconcile` runs, Then Watch Next rows are still written, no preview program is written, the failure is logged once, and a later successful reconcile writes the preview programs without duplicating Watch Next rows.
- AC-5. Given the box launcher shows app channels, When the home screen is opened, Then a Puber row with the app icon is visible; recorded in evidence with the owner's other apps cropped out. If the launcher hides app channels, evidence records the launcher type and AC-1/AC-2 stand as the gate. (owner judges the logo's look)
- AC-6. `check.full` passes; `TvHomeSyncCoordinatorTest` and `ContinueWatchingSourceTest` stay green unchanged.

## Risks

| Risk | Guard |
|---|---|
| Google TV home may ignore app channels and Watch Next for apps outside Engage (community reports, research.md: External findings) | AC-1/AC-2 gate on provider rows, AC-5 records the launcher type |
| `ic_launcher` may have transparent corners; the docs ask for an opaque 80dp logo under a circular mask | Cosmetic; AC-5 owner judges; dedicated asset is a follow-up (Q1) |
| A preview program persisted in prefs with a `NO_ID` sentinel could be passed to `deletePreviewProgram(-1)` | Scope row "Stored IDs"; AC-4 |
| Release app `com.kino.puber` on the box also publishes channels; queries must not mix its rows into evidence | Filter by package `com.kino.puber.stage` |

## Open questions
1. Channel logo: `ic_launcher` or a new dedicated opaque 80dp asset? Recommended: `ic_launcher` now, a dedicated asset only if AC-5 shows it badly. Blocks: no.
2. Decouple Watch Next from channel creation (B) or fix the logo only (A)? Recommended: B. Blocks: yes (AC-4 and Scope row "Decouple").
3. Device gate: provider rows or a visible launcher row? Recommended: provider rows (AC-1/AC-2) as the gate, the visible row recorded when the launcher shows app channels (AC-5). Blocks: no.

## Plan
1. `AndroidTvHomePublisher.kt:48-51` `ensureChannel`: add `.setLogo(logoUri)`, where `logoUri` = `Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).authority(context.packageName).appendPath("mipmap").appendPath("ic_launcher").build()` (or `resources.getResourceTypeName/EntryName` of `R.mipmap.ic_launcher`).
2. `AndroidTvHomePublisher.kt:24-35` `reconcile`: `val channelId = try { ensureChannel() } catch (CancellationException) { throw } catch (Throwable) { log(...); null }`; pass the nullable ID to `publish`.
3. `AndroidTvHomePublisher.kt:62-105` `publish`: build and publish the preview only when `channelId != null`, otherwise keep `stored?.previewId` if still present or `NO_ID`; Watch Next path unchanged. `delete` at `:107-110` skips `deletePreviewProgram` for `NO_ID`; the reuse check at `:90` ignores `NO_ID`.
4. Build `:app:compileDevDebugKotlin` and `check.full` via `./tools/agentw`; qa-device verifies AC-1..AC-5 on the box.
