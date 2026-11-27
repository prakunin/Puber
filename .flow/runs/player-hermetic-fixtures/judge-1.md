# Gate - player-hermetic-fixtures - round 1

**Decision:** CONCERNS
**Head:** 7570c24b   **Base:** f53f2144

## Done as asked

The diff adds `:player-test-fixtures` (upstream 1d3ec39e fixture pack, `HermeticTestServer` with optional TLS, JUnit 5 tests), adds the fixture origin to the one `BaselineNetworkBlocker` (`BaselineInstrumentationEnvironment.kt` `playerFixtureOrigin()`), consumes the module only through `androidTestInstrumentationImplementation` (`app/build.gradle.kts:446`), adds four controller device test classes, `docs/testing/player-playback.md` and CI steps (`pr-checks.yml:95`). Nothing excluded is in it: `git diff f53f2144...HEAD -- app/src/main` and `-- .flow` are empty; no Part B, no `PlaybackCallbackGate`, no Kaspresso.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PlayerTestFixturesTest`, `HermeticTestServerTest` | qa-report.md round 3; `check.full` re-run by the judge at 7570c24b, exit 0; `sha256sum -c SHA256SUMS`: 0 non-OK lines (judge) | PASS |
| AC-2 | `:app:dependencies` grep (T9) | qa-report.md: devDebug 0, prodRelease 0, instrumentationDebugRuntime 0, androidTest 2; dependency line read in the diff | PASS |
| AC-3 | `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsPlayerFixture`, `BaselineInstrumentationEnvironmentTest` | judge `check.full` exit 0; `rg ': ProxySelector()'` lists only `BaselineNetworkBlocker.kt` (judge) | PASS |
| AC-4 | `make FLAVOR=instrumentation ... itest`, four classes | `itest-r6-1..3.xml` (judge): 4 suites, 20 cases, 0 failures, 1 skipped, no `testRunFailed` each; evidence `docs/superpowers/evidence/2026-11-27-player-hermetic-fixtures-device.md` (audit passed, judge) | PASS |
| AC-5 | `PlaybackControllerStaleCallbackTest` | `itest-r5-1.xml`: 2 failures incl. the queued-failure case; `@Ignore("player-callback-gate: ...")` at `PlaybackControllerStaleCallbackTest.kt:84`; run `.flow/runs/player-callback-gate/state.md` exists (judge) | PASS (reproduced branch) |
| AC-6 | doc table, Decisions wording "not ported: <reason>" | `docs/testing/player-playback.md` "Upstream cases": 20 rows, each ported or "not ported: <reason>" (judge read) | PASS |
| AC-7 | `git diff --stat -- app/src/main` | empty against base and master (judge) | PASS |
| AC-8 | doc read | command, device rule, `sha256sum -c SHA256SUMS`, generator as maintainer tool all present (judge read) | PASS |
| AC-9 | `pr-checks.yml:95`; main `.flow/config.yaml` `check.full` | CI part in the diff; config part is the conductor's ship step (spec Decisions, A2), not done yet | PASS (branch part); config part open, Concerns 1 |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Probe never reaches the queued-error window (major) | edge-1 F1, tests-1 F1, codex-1 | fixed | 7e86a573 (new queued-failure case), device run 5 reproduced it, 73f3b5a4 `@Ignore` |
| `StreamReady.url` assertion cannot fail (nit) | blind-1 F6 | fixed | 7e86a573 |
| Sequence occurrence outside the lock | blind-1 F1, codex-1 | fixed | e63d16e6 |
| Nested `Range` in `Sequence` answers 200 | blind-1 F2 | fixed | e63d16e6 |
| Truncation journaled as `Completed(200)` | blind-1 F3 | fixed | e63d16e6, 7e86a573 |
| No CI compile of androidTest sources | blind-1 F4 | fixed | 3c437d95 |
| Harness init leak | blind-1 F5 | fixed | 7e86a573 |
| `hlsVod` hard-codes 360p | codex-1 | fixed | 7e86a573 |
| Corrupt-cache step accepts any outcome | tests-1 F2 | fixed, then softened after device run 5 | 7e86a573, 73f3b5a4; run `player-corrupt-cache-recovery` |
| Close-stall test depends on a sleep; busy spin | tests-1 F4, blind-1 F7 | fixed | e63d16e6 |
| Main `check.full` lacks the module task | tests-1 F3 | rejected | accepted: spec Decisions A2, ship step (Concerns 1) |
| Journal eviction after 256 entries | codex-1, codex-2 | rejected | accepted: `maxEntries = 256` (`HermeticRequestJournal.kt:11`), largest scenario `STRESS_ITERATIONS = 3` (`PlaybackControllerResourcesTest.kt:170`) |
| 720p fixture at Level 3.0 | codex-1, codex-2 | rejected | accepted: plays to ENDED in `itest-r6-*` |
| Delayed-segment gate opens early; chunkless proof weak; lost `getCachedSpans` assertion | codex-2 1-2, tests-2 F1 | deferred | run `player-device-tests-hardening` (log names all three) |
| Test name claims recovery (nit) | tests-2 F2 | accepted | doc row says "outcome not asserted" |
| Doc "Layers" row says the controller "reports no failure for a stream that was switched away", which device run 5 disproved | judge | open | Concerns 3 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No production code; device suites run 10-15 s each (`itest-r6-*`). |
| Reliability | Harness `close()` runs every step and rethrows the first failure; teardown flake root-caused (da1c1f89) and absent in 8 later runs. `make itest` can exit 0 on an incomplete run (run `itest-incomplete-run-green`); doc warns to check the XML count. |
| Maintainability | Module and dependency follow `libs.versions.toml` and `Versions`; one `ProxySelector`; `*ForTest` hooks are `internal` to the fixture module. |
| Security / privacy | Loopback only, per-run certificate, network security config untouched; `tools/mobile-evidence-audit.sh docs/superpowers/evidence` passed (402 files). |

## Manual checks for the owner

N/A: no manual-by-nature item is NOT CHECKED.

## Must fix

none

## Concerns for the owner

1. Ship steps still open: add `:player-test-fixtures:testDebugUnitTest` to `check.full` in `/home/dev/Puber/.flow/config.yaml` (AC-9, A2), and create the idea run `player-compose-e2e-tests` blocked by `player-overlays-port` (spec Q2, Out of scope); no such run exists in `/home/dev/Puber/.flow/runs/`.
2. The stale-callback defect is real and its regression test is `@Ignore`d until `player-callback-gate` lands; production playback keeps the bug.
3. `docs/testing/player-playback.md` "Layers" row claims "reports no failure for a stream that was switched away"; correct it at ship or in `player-callback-gate`.
4. AC-4/AC-5 evidence is from the emulator, run by the conductor (owner's Q1 answer), not from the TV box or the tester.
