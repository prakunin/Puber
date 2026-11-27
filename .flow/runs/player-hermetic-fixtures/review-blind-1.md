LENS: blind  ROUND: 1  BASE: f53f2144

### F1 [minor] Sequence occurrence is read outside the admission lock, so concurrent requests can get the same step
- Where: player-test-fixtures/src/main/kotlin/com/kino/puber/playertestfixtures/server/HermeticTestServer.kt:184 (dispatch, `val occurrence = journal.matchedCount(route.id)`)
- Problem: `journal.begin(...)` raises the route's match count inside `synchronized(lock)`. `matchedCount` is then read after the lock is released. Two requests that both pass admission before either reads the count both see N+1. Both then get `responses[N]`, and step N-1 of the `Sequence` is never served.
- Failure scenario: Media3 sends two loads for one `sequence(...)` route at the same time, for example a retry that overlaps a cancelled load. The tests that depend on "first answer fails, second succeeds" (`assertVariantHttpFailureFallsBack`, the recovering segment) then get two healthy answers or two faulty ones. This contradicts the class's own claim that concurrent requests are dispatched deterministically.
- Fix: have `begin` return the occurrence, or read `matchedCount` inside the same `synchronized(lock)` block that calls `begin`.

### F2 [minor] `Sequence` (and any wrapper) skips the selected plan's own `responseFor`, so a nested `Range` loses range handling
- Where: HermeticTestServer.kt `ResponsePlan.Sequence.responseFor` and dispatch line 185 (`route.response.responseFor(...).toMockResponse(...)`)
- Problem: `Sequence.responseFor` returns the chosen element as it is, and the dispatcher calls `toMockResponse` on it directly. The element's own `responseFor` never runs. `Range.toMockResponse` ignores the `Range` header and returns the whole body with 200.
- Failure scenario: `sequence(truncated(...), Range(segment))` answers the ranged recovery request with a full 200 instead of a 206. The recovery test in `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` already gets a full 200 (it uses `bytes(...)`) for a request whose `range != null` it asserts, so the "ranged recovery" path it claims to cover is never served as a range.
- Fix: in `Sequence.responseFor`, return `responses[i].responseFor(request, occurrence)`.

### F3 [minor] A truncated response is recorded as `Completed(200)`, so the journal cannot show that truncation happened
- Where: HermeticTestServer.kt `Truncated.toMockResponse` with `completedBody` (`onComplete(status)` after the partial write)
- Problem: the partial write succeeds, so `onComplete(200)` fires before the socket is closed. A truncated answer and a complete answer leave the same journal outcome.
- Failure scenario: in PlaybackControllerNetworkTracksTest.kt (`hlsDelayedAndInterruptedSegment...`), `assertEquals(200, recoveryEntries[0].outcome ...status)` also passes when the first answer was not truncated at all. The assertion cannot fail on the behaviour it names.
- Fix: give truncated and disconnected responses their own outcome, such as `ResponseOutcome.Truncated(bytesWritten)`, and assert on that.

### F4 [minor] The new device tests and fixture module are never compiled in CI
- Where: .github/workflows/pr-checks.yml:95 and :129
- Problem: CI runs `testProdDebugUnitTest`, `:player-test-fixtures:testDebugUnitTest` and `assembleProdDebug`. No job compiles `androidTestInstrumentation`, so a change that breaks the four new device test classes, the harness, or the module's API as they use it stays green in CI.
- Failure scenario: a later rename in `PlaybackControl.Callback` or `HermeticTestServer` breaks `PlaybackControllerHarness`. CI passes, and the breakage only shows up at the next manual `make itest`.
- Fix: add `:app:compileInstrumentationDebugAndroidTestKotlin` (or the variant's androidTest assemble task) to a CI job.

### F5 [minor] The harness leaks its cache, server and player when `init` fails, so later tests fail too
- Where: app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerHarness.kt:149-170 (fields and `init`)
- Problem: `SimpleCache`, `PlaybackController` and the fixed-port server are built in field initialisers and `init`. If `server.start(...)` or `setCallback` throws, `@Before` fails, `harness` is never assigned, and `@After` cannot call `close()`. Nothing releases the cache or the bound port.
- Failure scenario: a previous test leaves port 18768 bound, for example because its `server.close()` step failed. Every later test in the run then fails in `setUp` with a bind error, and the original cause is hidden.
- Fix: wrap the construction in try/catch and release whatever was already built before rethrowing. Or build lazily and make `close()` tolerate a partly built harness.

### F6 [nit] The stale-callback test's `StreamReady` URL assertion cannot fail
- Where: app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:80-83
- Problem: `onStreamReady` reports `currentStreamUrl`, which `switchStream` sets to B before any callback (PlaybackController.kt:507). So `assertEquals(url(B), ...StreamReady.url)` holds whichever stream actually reached READY. The test also relies on a fixed `Thread.sleep(3_000)` to settle.
- Fix: also check `currentMediaPath() == STREAM_B_PATH`, and wait for A's journal entry to get an outcome (599) instead of sleeping.

### F7 [nit] `awaitQuiescence` busy-spins with `Thread.yield()`
- Where: HermeticTestServer.kt `awaitQuiescence`
- Problem: it can spin a CPU core for up to 10 s on the TV box while ExoPlayer threads are still tearing down.
- Fix: sleep a few milliseconds per iteration, or signal through a condition from `complete`.

### Unproven
- HermeticRequestJournal.kt `add` (256-entry cap): evicting an entry that is still active means `complete` never finds it, so `active` stays above 0 for good, `awaitQuiescence` fails and `reset` throws. Evicted unknown-route entries also escape `assertNoUnknownRequests`. This becomes real if a single generation records more than 256 requests while an early request is held, which would need evidence of that request volume.
- HermeticTestServer.kt `parseRange`: an unsatisfiable range or a suffix range (`bytes=-N`) gets a full 200 instead of a 416 or a suffix slice. This only matters if Media3 sends such ranges to these routes.
