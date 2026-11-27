LENS: tests  ROUND: 2  BASE: 13432572 (incremental 59da16aa..c1de902f)

No findings. I read the diff and the harness code; I did not run the device test.

On your focus question, the retry cannot hide a real regression, and it cannot pass without running the scenario:

- **Retry condition:** `probe.error.get() != null` is the only thing that decides whether an attempt counts. A fresh `PlayerProbe` is created for each attempt inside `harness.prepare`, so an error from one attempt cannot be credited to another.
- **Judging a counted attempt:** once an attempt counts, `assertNoFailureAfter(mark)` runs with no retry and no catch. A `StreamFailure` or `Error` after the switch fails the test immediately, and so does a wrong `currentMediaItem`. A controller that reports A's failure for B therefore fails on the first attempt where A's error reached the player.
- **Error delivery:** the main looper is blocked for the whole `onMain` turn, so A's error cannot reach the probe before `switchStream(B)` runs. A non-null `probe.error` therefore means the error was delivered after the switch. That is exactly the queued-failure window.
- **Never exercised:** if no attempt delivers the error, the test throws an `AssertionError` with per-attempt diagnostics. It does not pass silently.
- **Fresh state per attempt:** each attempt has its own path (`/a1` to `/a5`), route and gate. The `ClosedResponseSignals` change resets that path's latch when its request starts, so an earlier close cannot satisfy a later wait.
- **Latch ordering:** the latch is replaced before `chain.proceed`. The journal entry that `requested(path)` waits on is written by the server, which only sees the request after `chain.proceed`. The replacement therefore cannot race the wait.

### Unproven
- `PlaybackControllerStaleCallbackTest.kt` (retry loop): A1's error might arrive late and land after attempt 2's `mark`, which would give a false failure. That would need A1's error to be delivered after A1's player source was released and more than `SETTLE_MS` after the switch. Device runs showing a flake that names an earlier path in its events would make it real.
- `PlayerTestControl.kt` (`awaitClosed`): the latch counts any close of that path's response. If the loader re-requested the same path, the second request would replace the latch while the first response was still open. The code comment now says this is a bound, not proof. Evidence of a second request to the same A path in the diagnostics would make it real.
- Nothing in the diff measures how often attempt 1 misses the window, so 5 attempts is not backed by data. Real device runs would show whether the retries are needed.
