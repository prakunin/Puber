# Review edge, round 2

LENS: edge  ROUND: 2  BASE: 5cd3e37e

### F1 [minor] The late check never resets the mock, so one failed journey also fails every test after it
- Where: BaselineProfileGenerator.kt:27-33 (resetMock), BaselineMockBackend.kt:63-64
- Problem: resetMock() calls checkLate before backend.resetTest() and resetJournal(); unknownRequests that failed test N are still recorded, so checkLate throws in N+1's @Before and the resets never run; N+2 and @AfterClass fail the same way.
- Failure scenario: one unknown route in startupProfile fails it correctly, then browseAndDetails, rail, playback and @AfterClass all fail with "Late requests after Startup", collecting no profile; one failure shows as five.
- Fix: try { checkLate } finally { backend.resetTest(); resetJournal(...) }, or run checkLate only after a passed test (TestWatcher).

No other findings. Unpinned prod unchanged: DNS_RESOLVER = DohResolver.GOOGLE (8.8.4.4, 8.8.8.8); HttpStack.create defaults to GOOGLE; getLatestGitHubRelease defaults to https://api.github.com (trimEnd('/') no-op); buildAutoResolveCandidates new path only when pinned; Koin binding now single<IAppUpdateRepository> (no get<AppUpdateRepository>() callers).
Handled: configureRuntime before startKoin; per-iteration process kill so /dns-query and items/fresh hit every home iteration; seed re-done on null/empty token; failed pinned probe -> NotFound -> missing routes fail the run; NSC base-config covers .test hosts; 11 guard substrings present; no profile/ or mockwebserver rules in profiles.

### Unproven
- BaselineMockBackend.kt:176-186 dnsResponse throws on a truncated question with no unknown request recorded.
- history.json leaves a "continue watching" preview program (artwork 127.0.0.1) in the generation device's launcher.
- BaselineAuthSeed.ensure could overwrite a real paired token if the instrumentation build shared an applicationId with a paired install.
