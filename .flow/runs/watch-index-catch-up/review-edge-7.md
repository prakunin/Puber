LENS: edge  ROUND: 7  BASE: e4c3b5aa

No findings.

The round-6 fix in commit 210f055e works.

**Index collector.** It no longer reads `windowClock()` unless a deadline is set (`WatchStateRepository.kt:191`). `NO_DEADLINE` is `Long.MIN_VALUE`. A deadline set at line 253 is `elapsedRealtime() + window`, which is never negative, so it can never be mistaken for `NO_DEADLINE`. The deadline is still cleared in the same places as before (lines 182, 192 and 244). On a device the outcome is the same as before; only the JVM stub path changes.

**`readHistoryNewerThan` restructure.** It behaves the same as before in all four cases:
- A failed page still returns `null`.
- A server that answers an earlier page than asked still returns what was read, marked incomplete.
- Catching up to the mark or reaching the last page still marks the read complete.
- Running out of `maxCatchUpPages` still marks the read incomplete.

**Module test.** It would fail on the old code: the collector died on its first emission, so `before` came back `null` and the assertion failed.

### Unproven
- `/home/dev/orca/workspaces/Puber/watch-index-catch-up-2/app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:253` A repository built through DI in a JVM test that closes a catch-up window (`endSyncWindow(CATCH_UP)`) still reads the `SystemClock` stub there and would throw. No such test or caller exists today. It would become real if a JVM test closed a catch-up window on a repository built from `repositoryModule`.
