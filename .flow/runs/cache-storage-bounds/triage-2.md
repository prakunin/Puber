# Review triage - round 2

Run: cache-storage-bounds   Base: a22c03bc   Reviewed head: a8fdd8d1 (incremental dfad8b7c..a8fdd8d1)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial; clean), qa-report.md round 2 (check.full red).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | `data/cache/MediaCacheTrim.kt:27-30`, `data/di/modules.kt:111-118` | `trimMediaCache` (removeSpan loop) and the DI wiring of the trim lambda have no test that can fail | Read: MediaCacheTrimTest covers spansToEvict only; sweeper tests inject their own lambda. Device: qa round 2 saw the trim work with a temporary 128 MiB cap build (130348 KB after 70 s); the shipped 256 MB build left an at-cap cache unchanged, as expected | fix (unit test of trimMediaCache with a mocked Cache: evicts exactly the LRU spans above the cap, nothing when it fits; make the DI wiring testable or assert it) | fixed (7c6868fd) |
| 2 | qa round 2 defect 1 | blocker | `MediaCacheTrim.kt:10-29`, `CacheWriteSink.kt:8-9` | lintDevDebug red: 17 UnsafeOptInUsageError, missing @OptIn(UnstableApi::class) | qa reproduced with :app:lintDevDebug | fix | fixed (7c6868fd) |
| 3 | qa round 2 defect 2 | blocker | `PlaybackController.kt:643` | detekt CollapsibleIfStatements | qa reproduced with :app:detektDevDebug | fix | fixed (7c6868fd) |
| 4 | tests F2 | minor | `CacheWriteSinkTest.kt:13-21` | Test checks the helper, not which flag each call site passes | Read; AC-1/AC-2 covered on device in round 1 | accept | accepted: round 2 minor; AC-1 and AC-2 have device evidence; listed for the owner |
| 5 | tests F3 | minor | `CachedPayloadSweeperTest.kt:81-93` | Back-to-back foreground/background calls collapse in the StateFlow; sweep count not asserted | Read the test | accept | accepted: round 2 minor, the trim-once property is still asserted; listed for the owner |

## Rejected codex claims

- none (codex note: device evidence predated round 2 - addressed by qa round 2).

## Next

- Fix round 2 (rows 1-3); then check.full, lens edge + tests + codex on the incremental diff. No runtime behaviour change expected (annotations, if-collapse, tests), so qa reruns check.full only. Unproven lines: 3 (edge 2, tests 1).
