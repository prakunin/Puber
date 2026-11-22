# Test design: tv-launcher-channel-logo

Base e11d1b66. Kind bug, size S, no tracks. Device: TV box 192.168.1.110:5555 only, package `com.kino.puber.stage`.

## Seam decision

`AndroidTvHomePublisher` takes a `Context` and builds `PreviewChannelHelper` and `SharedPreferences` inside; the tvprovider builders and `Uri` are android stubs. No Robolectric (mockk only), so no AC of the publisher runs on the JVM today. Minimal seam, no behaviour change for production:

| Seam | Shape | Enables |
|---|---|---|
| `TvProviderGateway` (internal interface, plain-data args) | `getChannel(id)`, `publishChannel(displayName, logoUri: String, appLink: String): Long`, `getPreview(id)`, `publishPreview(channelId, program)`, `updatePreview(id, ...)`, `deletePreview(id)`, same four for Watch Next. Production impl wraps `PreviewChannelHelper` and the builders. | AC-1 (logo passed), AC-3, AC-4, stored-ID risk |
| `TvHomeStore` (internal interface) over prefs: `channelId`, `programs` string | In-memory fake in tests; production impl wraps `SharedPreferences`. | AC-3, AC-4, stored-ID risk |
| `channelLogoUri(packageName): String` (pure, internal) | `android.resource://<pkg>/mipmap/ic_launcher` as a string; the gateway turns it into a `Uri`. | AC-1 logo value |

The implementer may pick other names; the test names below bind to behaviour, and the `Test` column names are the targets. Without the seam only the device rows remain, and AC-4 and the `NO_ID` risk are device-only with a forced failure (weaker). Recommendation: add the seam; the publisher is the sole untested class of the feature. `gateway`/`store` fakes are hand-written (not mockk of the class under test); mockk is not needed.

Unit-testable with the seam: AC-1 (logo argument only), AC-3, AC-4, stored-ID guards, AC-6 compile/regression. Device-only: AC-1 (no log lines, real channel row), AC-2, AC-5, and the real `addChannelLogo` acceptance of the URI (only the system provider proves the bitmap decodes).

## Risk profile

| Risk | P | I | Score | Test level |
|---|---|---|---|---|
| Logo URI wrong or not decodable, channel still deleted by tvprovider | 2 | 3 | 6 | unit (URI value) + device (provider row) |
| Channel failure still blocks Watch Next (decouple missing) | 2 | 3 | 6 | unit |
| `NO_ID` preview passed to `deletePreview(-1)` or reused as a real ID | 2 | 2 | 4 | unit |
| Failure swallowed incl. `CancellationException` (breaks coroutine cancel) | 2 | 3 | 6 | unit |
| Reconcile after recovery duplicates Watch Next rows | 2 | 2 | 4 | unit + device |
| Duplicate channel on second reconcile | 1 | 2 | 2 | unit; device AC-3 (accepted extra) |
| Launcher ignores app channels (Google TV) | 2 | 1 | 2 | accepted; AC-5 records launcher type |
| `ic_launcher` transparent corners | 2 | 1 | 2 | accepted; owner judges AC-5 |
| Release app `com.kino.puber` rows mixed into evidence | 2 | 1 | 2 | accepted; filter by package in queries |

## Traceability

Test names are `PublisherTest` = `AndroidTvHomePublisherTest` in `app/src/test/kotlin/com/kino/puber/core/tvhome/` (JUnit 5, `runTest`).

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 (logo) | unit | P0 red | `AndroidTvHomePublisherTest.channelIsCreatedWithAppIconLogo` | Fake gateway records `logoUri == "android.resource://<pkg>/mipmap/ic_launcher"` for pkg `com.kino.puber.stage`; fails if `setLogo` is dropped or the package is hardcoded |
| AC-1 (logo) | unit | P1 guard | `AndroidTvHomePublisherTest.channelLogoUriUsesGivenPackage` | Pure fn with `com.kino.puber` and `.stage` yields distinct authorities; fails on a hardcoded package |
| AC-1, AC-2, AC-3 | device | P0 red | Device checks D1-D3 | Real provider accepts the logo; rows exist; fails on the base build (no channel, IOException) |
| AC-2 | unit | P1 guard | `AndroidTvHomePublisherTest.publishesPreviewAndWatchNextPerProgramOnChannel` | Two programs give two previews with the returned channel ID and two Watch Next rows; fails if either path is skipped |
| AC-3 | unit | P0 guard | `AndroidTvHomePublisherTest.secondReconcileReusesStoredChannel` | Gateway `getChannel(stored)` non-null: `publishChannel` called once over two reconciles; fails if the stored ID is ignored |
| AC-3 | unit | P1 guard | `AndroidTvHomePublisherTest.missingStoredChannelIsRecreated` | Stored ID gone from provider gives a new channel and new stored ID |
| AC-4 | unit | P0 red | `AndroidTvHomePublisherTest.channelFailureStillPublishesWatchNext` | `publishChannel` throws `IOException`: Watch Next published per program, zero `publishPreview`, no exception out of `reconcile`; fails on base (throws before any program) |
| AC-4 | unit | P0 red | `AndroidTvHomePublisherTest.channelFailureIsLoggedOnce` | One log entry per failed reconcile (injected logger/sink); fails if swallowed silently or logged twice |
| AC-4 | unit | P0 red | `AndroidTvHomePublisherTest.cancellationFromChannelCreationPropagates` | `CancellationException` from the gateway escapes `reconcile`; fails if the catch is `Throwable` only |
| AC-4 | unit | P0 red | `AndroidTvHomePublisherTest.recoveryAfterChannelFailureAddsPreviewsWithoutDuplicatingWatchNext` | Reconcile 1 fails, reconcile 2 succeeds: previews now published, Watch Next published once per program (second call is update, not publish); fails if the stored Watch Next ID is lost or reused wrongly |
| risk NO_ID | unit | P0 red | `AndroidTvHomePublisherTest.programRemovedWhileChannelMissingSkipsPreviewDelete` | Program stored with `NO_ID` preview then dropped from the list: Watch Next deleted, `deletePreview` never called with -1; fails if the guard is removed |
| risk NO_ID | unit | P1 red | `AndroidTvHomePublisherTest.noIdPreviewIsNotTreatedAsExistingProgram` | `NO_ID` stored: `getPreview` not queried with -1 and a preview is published once the channel exists |
| risk NO_ID | unit | P1 guard | `AndroidTvHomePublisherTest.storedProgramsFormatIsUnchanged` | Store string for a normal program stays `key,preview,watch` joined by `;` and old strings are read back; fails if the format drifts |
| AC-4 | unit | P1 guard | `AndroidTvHomePublisherTest.clearAccountProgramsDeletesAllStoredIncludingNoIdPreview` | Logout path survives a `NO_ID` entry |
| AC-5 | device | P2 | Device check D4 | Row visible with app icon, or launcher type recorded |
| AC-6 | unit | P0 guard | `TvHomeSyncCoordinatorTest` (all), `ContinueWatchingSourceTest` (all) | Unchanged files stay green under `check.full` |

Failing-first: `AndroidTvHomePublisherTest.channelFailureStillPublishesWatchNext`. It must fail on e11d1b66: there `reconcile` rethrows from `ensureChannel` before any Watch Next call (`git -C <wt> show e11d1b66:app/src/main/java/com/kino/puber/core/tvhome/AndroidTvHomePublisher.kt`, lines 24-35). The tests need the seam, so the first commit adds the seam with the old behaviour; the red run is then on that commit. If the seam is declined, the failing-first proof is device D1 on the base build (log shows `Failed to add logo`, no channel row).

## Manual checks (device, box 192.168.1.110:5555)

Lease the device, `adb -s 192.168.1.110:5555`, install the worktree dev APK with install-preserve. Queries: `adb -s <serial> shell content query --uri content://android.media.tv/<table>`, filter output to `com.kino.puber.stage` (never `com.kino.puber`). Do not capture other apps' rows or launcher screens with the owner's apps (crop).

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| D0 (base) | Before the fix: force-stop, launch the base build with a Continue watching item, read logcat filtered to the app | `Failed to add logo`; no channel row of the package (red baseline) | none; record only |
| D1 | Fixed build: force-stop, launch, wait for the reconcile; query `channel` | Exactly one row, `package_name=com.kino.puber.stage`, display name = `tv_home_continue_watching` string; logcat has no `Failed to add logo` and no `Failed to publish TV home programs` (AC-1) | Zero rows; two rows; either log line |
| D2 | Query `preview_program` and `watch_next_program` | Preview rows = published items, `channel_id` = D1 ID; Watch Next rows = items, each `watch_next_type=0` (CONTINUE) (AC-2) | Missing rows; Watch Next without items; orphan `channel_id` |
| D3 | Force-stop, relaunch, wait; re-query `channel`, programs | Same channel ID, one row; program counts unchanged, no duplicate Watch Next (AC-3) | New channel ID; doubled rows |
| D4 | Press HOME on the remote (`input keyevent KEYCODE_HOME`), look for the Puber row | Row with app icon, or the launcher type recorded and AC-1/AC-2 stand as gate (AC-5) | Evidence including other apps' rows |
| D5 (optional) | Debug-only forced channel failure (temporary, not committed) if the seam was declined | Watch Next rows written, no preview rows, one log line (AC-4) | Committed debug change |

## UNTESTABLE

AC-5 look of the logo: owner judgement, not an assertion; D4 records it. No other AC is untestable.
