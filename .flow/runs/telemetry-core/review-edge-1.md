LENS: edge  ROUND: 1  BASE: 39903d31

### F1 [major] A crash is folded into an earlier logged error and loses `src=uncaught`
- Where: app/src/main/java/com/kino/puber/core/telemetry/EventFolder.kt:62-67, 92-95
- Problem: The ErrFolder key is only `(type, first app frame)`. It ignores `src`. When an `UNCAUGHT` Err arrives within 60 s of an earlier Err with the same key, the folder adds it to `repeats` and returns. On the crash flush (`flushBlocking` → `finishDrain(force = true)`), `followUp` writes the entry's original `src` and `error`, so the crash comes out as `src=log`, with the first error's message and an `n`.
- Failure scenario: code catches an `IllegalStateException`, logs it with `log(e)` (Timber.w → `src=log`), and within 60 s the same exception from the same first app frame goes uncaught. The file gets one `err src=log` line and later an `err src=log n=1` follow-up. No `src=uncaught` line is written, so the crash cannot be seen in the log (AC-10).
- Fix: put `src` in the fold key, or let `ErrFolder.accepts` return false for `ErrSource.UNCAUGHT` so a crash is always written straight away.

### F2 [major] Share does not drain the queue, so the last ~5 s of events miss the archive
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:419-421 (`export`)
- Problem: Spec D8 says "Drain runs every 5 s, on onActivityPaused, on export, and on a crash". `export` calls `writer.export()` with no `drainQueue()` first. `writer.export()` only force-flushes the folders. Anything still in the `ConcurrentLinkedQueue`, plus pending overflow counts, goes to the next file after the rotation, not into the zip. The pause flush comes too late: the chooser pauses the activity only after the archive is built.
- Failure scenario: the user reproduces a problem and presses "Share log" within 5 s of the last drain. The nav/act/err lines of that problem, including the `ShareSessionLog` act, are not in the shared zip. `shareRotatesAndZipsOnlyClosedFiles` does not catch this because it calls `executor.runRepeating()` before sharing.
- Fix: in `export`, run `if (running) { drainQueue(); writer.finishDrain(force = true) }` before `writer.export()`. Add a test that records an event and shares without a drain in between.

### F3 [minor] After an IO failure the user cannot switch the log off; recording silently resumes on a later cold start
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryRuntime.kt:84 (`onStopped = ::detachProbes`); app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt (`ToggleSessionLog -> setEnabled(!latestSessionLog.enabled)`)
- Problem: `onWriterUnavailable` keeps `enabled_at_wall_ms`. The mapper shows `enabled = false` for `Unavailable`, so pressing the toggle always calls `setEnabled(true)`, never `setEnabled(false)`. While storage keeps failing, the preference can never be cleared.
- Failure scenario: the disk fills up, `append` throws, and the status becomes "unavailable". The user presses the toggle to turn it off; that only retries the enable. Later space is freed and the app cold-starts: `TelemetryRuntime.start` finds the key and recording resumes, up to 7 days after the first enable, although the user last saw it not recording.
- Fix: clear `KEY_ENABLED_AT` in the runtime's `onStopped` callback, or make the toggle call `setEnabled(false)` when the state is `Unavailable`.

### F4 [minor] A crash during an err storm is dropped by the per-minute cap
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:206-219 (`accept`)
- Problem: `UNCAUGHT` errors use the shared `err` cap of 20 per wall minute. Once 20 distinct err lines have been written in a minute, the crash only increments `dropped`.
- Failure scenario: a network outage logs more than 20 distinct `log(e)` errors (different first frames) within a minute, then the app crashes. The file shows `meta dropped counts={err:1}` and no crash line.
- Fix: exempt `ErrSource.UNCAUGHT` from the cap, since there is at most one per process.

### F5 [minor] `et` for the same exception differs between `src=log` and `src=auth`/`uncaught`
- Where: app/src/main/java/com/kino/puber/core/telemetry/ErrorScrubber.kt:230-233 together with app/src/main/java/com/kino/puber/core/logger/SensitiveRequestLogPolicy.kt:118
- Problem: `sourceType` comes from `throwable::class.qualifiedName` (dots, e.g. `...ApiError.Unauthorized`). The raw-throwable path uses `javaClass.name` (`...ApiError$Unauthorized`). After `substringAfterLast('.')` the same class gives `Unauthorized` on one path and `ApiError$Unauthorized` on the other. The ErrFolder keys also differ.
- Failure scenario: a refresh failure records `src=auth et=ApiError$Unauthorized`. The same exception logged through `log(e)` records `et=Unauthorized`. A summary that groups by `et` counts them as two error types.
- Fix: normalise in `ErrorScrubber.type`: use `javaClass.name` for both paths, or apply `substringAfterLast('$')` to both.

### F6 [minor] An orphaned `.zip.tmp` is never cleaned up and sits outside the 20 MB budget
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryStore.kt:43-49, 85-103
- Problem: `list()` returns only `*.jsonl`/`*.zip`. A `.tmp` left behind when the process dies during `zip` is invisible to `enforceBudget` and to the zip cleanup in `export`. It is overwritten only if a later archive gets the exact same minute-stamped name.
- Failure scenario: the process is killed (low-memory killer, or the user force-stops) while a large archive is being zipped. Up to ~20 MB of `.tmp` stays in `files/telemetry/` permanently on top of the budget.
- Fix: in `export`, before zipping, delete every `*.tmp` in the directory (or have `list()` report them so the cleanup sees them).

Checked and handled: null external dir, mkdirs failure and IO errors (writer `guard`/`fail`, status `Unavailable`); the 2 MiB rotation and the budget never deleting the open file; queue bound and overflow counts; wall clock going backwards for the 7-day check (in memory and persisted); the uncaught handler always chaining in `finally`, skipping the flush on the writer thread, and going inert when it is no longer the default; Timber tree and handler planted only once (null-guarded under `@Synchronized`); scrubber handling of null, empty and huge messages; FileProvider path matching the store directory; session-log UI state cached before device settings load; R8 keepnames for VMs, actions, tabs and `*State` classes.
