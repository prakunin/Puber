LENS: edge  ROUND: 2  BASE: 39903d31 (incremental fc5738b1..0f20e63a)

### F1 [major] Budget eviction now deletes the archive that was just shared, while the receiving app may still need it
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:144-155 (`enforceBudget`), with `export()` at :54-77
- Problem: Zip files are now first in the eviction order. `export()` closes the current file and sets `rotatePending`, so the next event calls `openFile` -> `enforceBudget`. If logs plus the new zip exceed 18 MB, the zip just handed out through FileProvider is deleted first.
- Failure scenario: ~16 MB of `.jsonl`; Share builds a 3 MB zip; the target reads the URI later (mail, background upload). The next nav opens a new file, total 19 MB > 18 MB, zip deleted; upload fails or sends nothing.
- Fix: evict `*.tmp` and every zip except the newest first; the newest zip goes after the logs or only at the next `export`. Or keep the last exported archive name and skip it, like `currentName`.

### F2 [major] `first_content` is marked by any state-class change, including Home's error state and the root update prompt
- Where: app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:91-98
- Problem: Any first `ready` emits the startup line. `HomeVM` Loading -> Error (HomeVM.kt:159/280/402) and root `UpdatePromptVM` (App.kt:130) Hidden -> Available both count.
- Failure scenario 1: cold start with no network: `first_content` = time the error appeared; the real Content after retry is never recorded.
- Failure scenario 2: update available and the check returns before Home content: `first_content` = time the update prompt appeared. AC-9 gets a wrong value silently.
- Fix: mark only on a content-like state of the landing screen (marker on content states such as `HomeViewState.Content`, `AuthViewState.Content`, other startup tabs' content); at minimum exclude error states and root overlay VMs.

### F3 [minor] A stored `enabled_at` ahead of the clock now blocks auto-off for as long as it stays ahead
- Where: TelemetryRuntime.kt:41-47; TelemetrySink.kt:191-194 (`expired`), :227-230 (`remainingMs`)
- Problem: Any future `enabled_at` is kept and negative elapsed counts as zero; a switch time recorded under a wrong future clock (later corrected) delays expiry until real time passes that date + 7 days, while the row says "7 days left".
- Failure scenario: clock reads 2027-10-15 at enable, corrected to 2026-10-15; recording continues ~1 year (AC-4).
- Fix: also bound by monotonic time (`clock.elapsedMs() - originElapsedMs >= SEVEN_DAYS_MS`), or persist accumulated recorded uptime and expire on whichever limit comes first.

### F4 [minor] Events left in the queue at auto-off land in the next session with a negative `t`
- Where: TelemetrySink.kt:167-177 (`drain` -> `expire`), :131-151 (`startOnWriter`)
- Problem: `drain` empties the queue before `expire()` sets `isEnabled = false`; events enqueued in the gap are not cleared and, with the per-start writer, are written into the next session with the new `sid` and `t < 0`.
- Failure scenario: auto-off while scrolling; later re-enable in the same process; the new file holds old `act` lines with negative `t`.
- Fix: clear the queue in `expire()` after `isEnabled = false`, or drop events with `t < originElapsedMs` in `startOnWriter`.

Holding: row 1 (drain skipped while stopped/unavailable; FIFO on writer), rows 2 and 5 (crash never folded or capped), row 3 (uniform et/frames), row 6 (Unavailable toggle clears pref), row 8 (Throwable -> Failed), row 9 (fresh sid per later start; throwaway writer never opens a file).
