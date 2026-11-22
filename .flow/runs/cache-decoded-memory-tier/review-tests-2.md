LENS: tests  ROUND: 2  BASE: e9c383d0

### F1 [nit] The two new tests never close the Ktor client they create
- Where: /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientDecodeDispatcherTest.kt (`probeHttpClient()`, called in `aThirdPartyResponseBodyIsDecodedOnTheInjectedDispatcher` and `theApiCallBodyIsDecodedOnTheDecodeThread`)
- Problem: Each of these tests builds an `HttpClient(OkHttp)` and never closes it. Its engine dispatcher and connection pool stay alive in the test JVM after the test ends. No test result depends on this.
- Failure scenario: Each run of these two tests leaves an OkHttp engine's threads and pool open. The suite stays green, but resources build up in a long Gradle test worker.
- Fix: Wrap the client in `probeHttpClient().use { probeClient -> ... }`, or close it in the existing `finally` block.

Checked and found sound:
- **Run state:** I ran both test classes the diff touches with `./tools/agentw :app:testDevDebugUnitTest`. All tests passed. `KinoPubApiClientDecodeDispatcherTest` ran 4 tests and `ContentCacheRepositoryTest` ran 37, none skipped. `detailsOnlyInRoomReadTheItemRowExactlyOnce` is among them.
- **Triage #3, `detailsOnlyInRoomReadTheItemRowExactlyOnce`:** this test can fail. The record is inside its TTL, so the loader is not called. That leaves two paths that read row 7: the early `readItemRecord` and `itemFeed.load`. Only the `peekAll` promotion in `readItemRecords` keeps the store reads at exactly one. With `peek`, the count would be 2. This matches the red run in the plan's fix record.
- **Triage #4, `thirdPartyApiCall` and `apiCall` tests:** the probe serializer records the thread that actually decodes the body. Without `withContext(decodeDispatcher)`, decoding runs on the test thread, so the thread-name check fails. These tests now check the decode thread, not just that a dispatch happened, which was the round-1 gap.
- **Triage #4, `handleApiResponse` test (reached through `getDeviceCode`):** this one only shows that a block ran on the decode thread, not that `decodeFromString` ran inside it. It still kills the mutation that matters: if `withContext` is removed, `ranOn` is empty and the test fails. It needs no network, because the injected interceptor answers the OAuth request.
- **Weakened tests:** the diff removes no assertions. The `RecordingDispatcher` change wraps the block but still calls `block.run()`, so the older `responseBodyIsDecodedOnTheInjectedDispatcher` test keeps its meaning.
- **Triage #2 (the AC-7 red run on the base DAO):** this belongs to qa-device and is not in this diff. I did not check it.
