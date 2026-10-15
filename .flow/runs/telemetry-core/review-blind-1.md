LENS: blind  ROUND: 1  BASE: 39903d31

### F1 [major] Error lines in release builds carry obfuscated names that cannot be retraced
- Where: app/src/main/java/com/kino/puber/core/telemetry/ErrorScrubber.kt:28-38, app/proguard-rules.pro:96-101
- Problem: Release is minified and obfuscated (`isMinifyEnabled = true`, no `-dontobfuscate`). The new `-keepnames` rules cover only VMs, UIActions, Tabs and `ui.**State`. App exception types (`com.kino.puber.core.error.ApiError.*` and others) and the classes in app frames are renamed. `type()` keeps only the simple name and `frames()` writes `SimpleName.method:line`, so the package is dropped. The mapping file then cannot resolve these names: after R8 repackaging, a short name like `a` or `b` is ambiguous. R8 also remaps line numbers, so the line is wrong too. The Developer section that holds the log is now shown in release (the `BuildConfig.DEBUG` filter was removed), so release is where most logs will come from.
- Failure scenario: A release user hits `ApiError.Unauthorized`, which reaches the log through `sessionExpiredFailure`. The line reads `"et":"c","frames":["a.b:3"]`, and nothing maps it back to the class or the call site.
- Fix: Either add `-keepnames` for `com.kino.puber.core.error.**` and other app throwables, or write the full binary class name in `et` and `frames` so the R8 mapping can retrace it. Add a test that covers an obfuscated name.

### F2 [major] `first_content` is tied to Home only, so it is missing or wrong for every other start path
- Where: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:72, app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:103
- Problem: Only `HomeViewState.Content` marks first content. The startup tab can be changed (`NavigationPreferencesRepository.getStartupTab`), and a signed-out cold start shows Auth first. In both cases Home is not the first screen. Whenever the user reaches Home inside the 60 s fallback window, that moment is recorded as `first_content`.
- Failure scenario: (a) The startup tab is Bookmarks. Bookmarks content appears at 1.2 s, but the startup line has no `first_content`, or gets the time the user later opened Home (for example 40 s). (b) A cold start on Auth: the user enters the device code in 30 s, and Home content at 31 s is reported as `first_content = 31 s`, which measures the login and not the app start.
- Fix: Mark first content from whichever screen is shown first, for example the first `Ready` of the startup tab's VM. If it stays Home-only, ignore the mark unless Home is the configured start screen and the session is already authenticated.

### F3 [minor] No `ready` event is ever written for view models whose state is a single data class
- Where: app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:97-101
- Problem: `recordReady` fires only when the runtime class of the state changes. `DeviceSettingsVM` (`DeviceSettingsViewState`), `MainVM`, `ContentListVM` and `NetworkDiagnosticsVM` start with a data-class state and keep the same class. Their real Loading-to-Success change happens inside a nested field, so the class never changes.
- Failure scenario: Opening Settings writes `nav` and `act` lines but never a `ready` line, so its time-to-content cannot be measured. A reader may take the missing line to mean the screen never loaded.
- Fix: Let a VM name its ready state class, the same way `telemetryStartupContentClass` does, or compare the nested `state` class for wrapper states. At least document which VMs are not covered.

### F4 [minor] A wrong wall clock at boot is saved and then switches the log off once time syncs
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryRuntime.kt:42-45, TelemetrySink.kt `expired()`
- Problem: If `enabledAt > now`, the code clamps `enabledAt` to `now` and saves it. Many Android TV boxes have no RTC and boot with a clock far in the past (for example 2000 or 1970) until NTP syncs.
- Failure scenario: The log is enabled on 2026-10-15 and the box reboots. The app starts before NTP sync with `now` = 2000-01-01, and `enabled_at` is saved as 2000-01-01. NTP then moves the clock to 2026, the next drain sees more than 7 days, and it writes `auto_off`. Recording stops a few seconds after boot.
- Fix: Do not save the clamped value; clamp in memory only. Or treat a jump from a pre-enable time to more than 7 days as clock skew, for example by also keeping an elapsed-time anchor.

### F5 [minor] Share hangs and stays disabled if export throws anything other than an IOException
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:101, TelemetryRuntime.kt:75-77, TelemetryExecutor.kt:29
- Problem: `writer.export()` catches only `IOException`. `ThreadTelemetryExecutor` wraps every task in `runCatching`, so any other throwable (for example a `SecurityException` or `IllegalStateException` from file APIs) is swallowed. `onResult` is then never called, and `suspendCancellableCoroutine` never resumes.
- Failure scenario: An export fails with a runtime exception. In `DeviceSettingsVM.shareSessionLog` the `finally` block never runs, so `sharing` stays `true`. The button keeps showing "preparing" and stays disabled until the VM is recreated.
- Fix: In `TelemetrySink.export`, catch `Throwable` around `writer.export()` and call `onResult(ShareResult.Failed)`.

### F6 [minor] The shared archive leaves out events still in the queue
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:100-102, TelemetryWriter.kt:64-81
- Problem: `export` flushes the folders and closes the file, but does not call `drainQueue()` first. Events that are still in `queue`, up to one drain period (5 s), are written only after the archive is built. In the same step, folded `act`/`err` lines are emitted ahead of older events that are still queued, so the order is wrong.
- Failure scenario: A user sees an error, opens Settings and presses Share within 5 s. The `err` line and the Settings nav/act lines are not in the zip.
- Fix: When `running`, call `drainQueue()` and then `writer.finishDrain(force = true)` at the start of the export task, before `writer.export()`.

### F7 [minor] `sid` and the `t` origin are not reset when recording is turned on again
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:34-38
- Problem: The writer and encoder are created lazily once per process. `sessionStartElapsedMs` and `sid` are fixed at that first touch, and `export()` also creates them even when recording was never on. `startOnWriter` writes a new `session` line but keeps the old origin and the same `sid`. This contradicts `TelemetryWriter.reset`, whose comment says "Starts a new recording session".
- Failure scenario: The user presses Share with no recording, which builds the writer. An hour later they turn the log on, and the first `session` line has `t = 3600000`. Or: on, off, on again in one process, and both sessions share one `sid`, so a summary tool merges them.
- Fix: Create the encoder (new `sid` and start time) in `startOnWriter`, or allow `reset()` to set a new origin and sid.

### F8 [minor] The last exported zip stays on disk and pushes logs out of the budget
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:147-156
- Problem: `enforceBudget` counts `.zip` files in `total` but deletes only `.jsonl` files. The archive from the previous share stays until the next export, so it keeps counting against the 20 MB budget and the oldest logs are deleted instead.
- Failure scenario: After one share of about 18 MB of logs (zip about 3 MB), the next file rotation deletes about 3 MB of the oldest `.jsonl`, although only stale archive data is over the budget.
- Fix: Delete old `.zip` files in `enforceBudget` before deleting any `.jsonl`, keeping at most the newest zip, or leave zips out of the total.
