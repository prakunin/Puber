LENS: tests  ROUND: 2  BASE: 88fa7303

(No blockers or majors in 780aa9ca..e3a8cdb3. Touched tests green: com.kino.puber.data.api.network.*, ApiModuleHttpStackTest, *ApiDomainInteractorTest* via ./tools/agentw :app:testDevDebugUnitTest. Row 1 red run matches; row 2 assertNotSame is a real red; row 4 builds from HttpStack.create().client; row 5 guard labels carry reasons and the evict-failure test has a real red run.)

### F1 [minor] Coil's own fetcher wiring in PuberApp is still not under test
- Where: app/src/main/java/com/kino/puber/PuberApp.kt:135; app/src/test/kotlin/com/kino/puber/data/di/ApiModuleHttpStackTest.kt:85
- Problem: `apiClients_shareOneConnectionPoolAndOneDns` now checks the Koin single `ImageHttpClientQualifier`. That proves the single shares the pool and the DNS. Nothing checks that the `KtorNetworkFetcherFactory` lambda in `PuberApp` uses that single. Triage row 3 asked for the Coil fetcher factory itself to be tested; it was only half done.
- Failure scenario: someone changes the lambda back to `createImageHttpClient(OkHttpClient(), ...)` or any client built inline. Coil then gets its own pool and system DNS, and the whole suite stays green.
- Fix: move the fetcher factory into a function in the DI layer that takes a `Koin`/`Scope` and returns the factory. Call it from `PuberApp`. Assert in `ApiModuleHttpStackTest` that the client it makes is the qualified single. Or accept the one-line `get<HttpClient>(ImageHttpClientQualifier)` as covered by the device evidence for AC-7 and say so in the record.

### Unproven
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:636`: the debug-only `player` event-listener label has no test. It would matter if any AC or evidence relied on the `player` counter being separate from `app`.
