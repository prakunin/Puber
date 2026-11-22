# Review triage - round 1

Run: cache-decoded-memory-tier   Base: e9c383d0   Reviewed head: 803329d9

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind (edge unproven 3) | minor | `ContentCacheRepository.kt:36-44,355-376` | Memory cap is 16Mi encoded chars per feed (item, list, page, history, one per payload key) instead of one 16 MiB byte budget (8Mi chars) for the whole tier at base | Read base `ContentCacheRepository.kt:32-43` (`length * Char.SIZE_BYTES`, one feed) and head (`newFeed` per type, `payloadFeeds` map); holds. AC-5 as approved names 16 MB encoded per tier; qa measured no heap growth (qa-report AC-10) | defer | new run `typed-feeds-memory-budget` (design D2: a cap change goes to a follow-up) |
| 2 | tests | minor | `app/src/androidTest/.../WatchStateDaoTest.kt:84` | AC-7 P0 red row never ran red on the base DAO | Implementation record and qa-report both say not run on base; holds | fix | fixed: qa round 2 red on base (`aServerRowEqualToTheStoredOneIsNotRewritten` expected 1 was 2), green on head; evidence 6adf8e1b |
| 3 | tests | minor | `ContentCacheRepositoryTest.kt:343`; `ContentCacheRepository.kt:196,208` | No test for AC-1 Details record only in Room decoded exactly once | Read `observeItemDetails`: pre-read `readItemRecord` then loader re-read; only `peekAll` promotion keeps it at one; no test covers it | fix | fixed (c93c20c6) |
| 4 | tests | minor | `KinoPubApiClient.kt:1055,1180-1182` | `thirdPartyApiCall` and `handleApiResponse` decode-dispatcher changes untested; existing test asserts a dispatch, not the decode thread | Read both sites; `KinoPubApiClientDecodeDispatcherTest` covers `apiCall` only | fix | fixed (c93c20c6): thirdPartyApiCall, apiCall and handleApiResponse (via getDeviceCode) thread tests |

## Rejected codex claims

- [high] `ContentCacheRepository.kt:232` `flowOn` buffer lets an old-session details value reach `recordFromServer` after `clear()` - rejected by the codex-reviewer itself as unproven: the window existed at base (`WatchStateRepository.kt:256` suspends), `clear()` is a Settings action with no live details collector shown, and watch_state rows carry their own generation.

## Next

- Fix round 1: implementer adds tests for #3 and #4 (tests only, no production change unless #4 needs a seam); qa-device runs #2 (base-DAO red run) and the affected tests; then lens tests + edge + codex on the incremental diff. Unproven lines: 9 (blind 3, edge 3, tests 2, codex 1).
