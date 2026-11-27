# Review triage - round 1

Run: player-hermetic-fixtures   Base: f53f2144   Reviewed head: ce54fc41

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (ran: clean).
state-path-reviewer and ux-designer not run: the diff changes no ViewModel, focus, overlay or Compose layout (app/src/main untouched).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, tests F1, codex 3 | major | `PlaybackControllerStaleCallbackTest.kt:64-65` | The probe opens A's gate after `switchStream(B)`, so A's load is normally cancelled before any error exists; "not reproduced" cannot tell absent from never exercised; the evidence calls the probe non-vacuous | Read lines 55-83: `switchStream` in `onMain`, then `gate.countDown()`; nothing makes A's failure exist before the switch | fix | Add a case where A's failure is produced (journal shows A completed, short parse wait) and `switchStream(B)` runs in the same main-thread turn; keep the current case; record in evidence and doc which ordering each case covers; device re-run. If the new case fails, AC-5 "reproduced" branch applies (`@Ignore("player-callback-gate")` and a new run) |
| 2 | blind F6 | nit | `PlaybackControllerStaleCallbackTest.kt:80-83` | `StreamReady.url` assertion cannot fail; fixed 3 s sleep | `onStreamReady` reports `currentStreamUrl` set by `switchStream` | fix (with #1) | also assert the current media path is B; wait on A's journal outcome where possible |
| 3 | blind F1, codex 1, edge unproven | minor | `HermeticTestServer.kt:152-168` | Sequence occurrence read via `matchedCount` outside the lock that called `begin`; concurrent same-route requests share a step | Read 152-168: `begin` under lock, `matchedCount` at 167 outside | fix (cheap) | `begin` returns the occurrence |
| 4 | blind F2, edge unproven | minor | `HermeticTestServer.kt:344` | `Sequence.responseFor` returns the inner plan without calling its `responseFor`, so a nested `Range` answers 200 | Read 337-351 | fix (cheap) | delegate to the element's `responseFor` |
| 5 | blind F3 | minor | `HermeticTestServer.kt` Truncated | Truncated answer journaled as `Completed(200)`; the recovery test's status assertion cannot see truncation | Read Truncated.toMockResponse / completedBody | fix (cheap) | own outcome for truncated/disconnected, asserted in the recovery test |
| 6 | blind F4 | minor | `.github/workflows/pr-checks.yml` | No CI job compiles androidTestInstrumentation sources | Read pr-checks.yml jobs | fix (cheap) | add `:app:compileInstrumentationDebugAndroidTestKotlin` to a CI job; keep `test_ci_workflows.py` green |
| 7 | blind F5, codex rejected 4, edge unproven | minor | `PlaybackControllerHarness.kt:135-170` | A failed harness init leaks cache, port and client and `@After` adds a second error | Read init order | fix (cheap) | release what was built before rethrowing; `@After` tolerates an unbuilt harness |
| 8 | codex 2 | minor | `PlaybackControllerDeviceTest.kt:62-65` | `hlsVod` requires `video_360.m3u8`; a device picking 720p fails | Master lists both variants | fix (cheap) | accept either `video_*.m3u8` |
| 9 | tests F2 | minor | `PlaybackControllerResourcesTest.kt:49-66` | Corrupt-cache step accepts either outcome and the test removes the resource itself, so recovery is never asserted | Read 45-70 | fix (cheap) | assert recovery (READY, no error, path re-requested) without removing the resource before the clean run; if READY on a corrupt entry is not reachable without a production change, mark the doc row "ported, outcome not asserted" |
| 10 | tests F4 | nit | `HermeticTestServerTest.kt` close-stall test | Red state depends on a 300 ms sleep | Read the test | fix (cheap) | latch right before `server.close()` |
| 11 | blind F7 | nit | `HermeticTestServer.kt` awaitQuiescence | busy spin with `Thread.yield()` | Read | fix (cheap) | short sleep per iteration |
| 12 | tests F3 | minor | `/home/dev/Puber/.flow/config.yaml:13` | main `check.full` lacks `:player-test-fixtures:testDebugUnitTest` | Read config | reject | Decided A2 (spec Decisions): the conductor adds it in the main checkout at ship; adding it before the module lands on master would break `check.full` in every other worktree. Carried to the gate as a ship step |

## Rejected codex claims

- Journal eviction after 256 entries leaves `active` stuck - unproven: largest scenario makes a few dozen requests per reset.
- Eviction hides unknown requests - unproven, same reason.
- 720p fixtures `-level 3.0` - unproven: plays to ENDED in every run; fixtures are upstream bytes (Q3).
- (Harness init leak was rejected by codex as unproven but is fixed as #7 because it is cheap.)

## Next

- Fix round 1 for #1-#11 (implementer), then check.fast, device re-run by the conductor, qa on AC-4/AC-5, incremental review: edge, codex, antigravity, tests (tests change). Unproven lines: 9.
