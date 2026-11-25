# Gate - cached-item-card-only-fallback - round 1

**Decision:** CONCERNS
**Head:** 7aaf6222   **Base:** 4db1b061

## Done as asked

The diff (`git diff 4db1b061...HEAD`, three main files) matches the spec's Design table: `PersistentPayloadStore.write` returns `Boolean`, `writeAll` the refused keys, `refuse` and its delete are gone (`PersistentPayloadStore.kt`); `CachedFeed` takes `persistFallback` and writes it, or deletes, only while the key is owned (`persistLoaded`, `persistRefused`); `itemFeed` uses `ItemRecord::cardOnly`; `markItemDetailsStale` keeps a null `detailsUpdatedAt` null. Nothing above `ContentCacheRepository` changed; no row splitting (Out of scope respected).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PersistentPayloadStoreTest`: `writeOfAnOversizedPayloadKeepsTheKeysOlderRow`, `writeAllKeepsTheOlderRowOfAnOversizedKeyAndStoresTheSmallOne`, `writeReportsAnOversizedPayloadAsNotStored`, `writeAllReturnsOnlyTheOversizedKeys` | qa round 1-2 full suite green; check.full rerun here at 7aaf6222, exit 0 | PASS |
| AC-2 | `ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow`, `anOversizedDetailsRecordIsStillServedInFullInTheSameSession` | same | PASS |
| AC-3 | `aCardOnlyRowServesTheListAfterARestartWithoutTheLoader`, `detailsOverACardOnlyRowAfterARestartCallTheLoaderFirst` | same | PASS |
| AC-4 | `aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart` | same | PASS |
| AC-5 | `CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow`, `aRefusedLoadCrossedByABulkWriteWritesNoFallbackOverTheNewerRow` | same | PASS |
| AC-6 | `CachedFeedTest` put/load/supersede rows, `aRefusedBulkWriteWritesTheFallbackOnlyOverKeysItStillOwns`, `aRefusedBulkWriteDoesNotDeleteAKeyTakenOverDuringAnEarlierDelete`, `markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow`; D1 `markingACardOnlyRecordStaleStillMakesDetailsLoadFirst` | same | PASS |
| AC-7 | Manual check 1 on box 192.168.1.110:5555 | evidence `docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md` round 3: id 15218 row 3616 B, no `episodes`/`detailsUpdatedAt`, fallback line present, 0 `Refused cached payload` lines. Title opened from search: no oversized row on the box is a 4K title | PASS |
| AC-8 | Manual check 2 | same file, rounds 1-3: cold start, 4K grid shows cards; oversized series details from search show seasons and episodes over the card-only row. The oversized series' own card in a list after cold start was not observed on device | PASS (deviation, see Concerns) |

All named test functions exist in the worktree (grep); no `@Disabled` left under `app/src/test/.../data`.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| T1 ownership snapshot before suspending deletes | codex-1 minor | fixed | 9ac3dba7; re-check inside the loop in `persistRefused` |
| T2 no test for bulk fallback ownership | tests-1 F1 | fixed | 9ac3dba7, `aRefusedBulkWriteWritesTheFallbackOnlyOverKeysItStillOwns` |
| T3 fallback path not logged | blind-1 F1 | fixed | 9ac3dba7, reworded in b8b5e344 |
| T4 AC-7 logcat check had no positive control | tests-1 F2 | fixed (evidence) | 2b79669c, 7aaf6222 |
| T5 fallback upsert after the ownership check is not atomic | codex-1 minor, edge-1 unproven | rejected, accepted | spot-checked: base has the same check-then-write gap on the main write (`CachedFeed.kt` load); outcome is a stale card-only row repaired by the next merge or refetch |
| T6-T8 | codex-1, edge-1, blind-1 unproven | rejected | T7 spot-checked: `cardOnly` drops `videos` for a movie only when its record exceeds 1 MiB; every oversized row on the box was a series |
| U1 fallback log reused the refusal prefix | codex-2, antigravity-2 major | fixed | b8b5e344; qa round 3 confirms the new line and 0 refusal lines |
| U2, U3 | edge-2, tests-2 unproven | rejected | log-only / unreachable card-only size; accepted |
| V1 stale log quote in evidence | edge-3 unproven | fixed | 7aaf6222 marks the round 2 quote superseded |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One extra encode plus a warn line for each write of an oversized item while it is in memory (spec risk, accepted); fallback encode only on refusal. Memory weigher keeps the full length (`aRefusedValueStillWeighsItsFullPayloadInMemory`) |
| Reliability | Offline Details over a card-only row throws as before (spec risk, unit-covered by the existing test); cancellation and generation checks unchanged around the new helpers |
| Maintainability | Two private helpers in `CachedFeed`, `@Suppress("LongParameterList")` on `persistRefused`; `refuse` removed, no dead code; `Timber` in `PersistentPayloadStore.kt` still used (line 99); detekt and lint green |
| Security / privacy | Logs carry the cache key (item id) and sizes only; evidence audit `tools/mobile-evidence-audit.sh` passed here; no db copy or screenshot committed |

## Manual checks for the owner

N/A: no NOT CHECKED item.

## Must fix

none

## Concerns for the owner

1. AC-8 deviation: the box has no oversized 4K title, so the oversized series was opened from search and its card in a list after a cold start was seen only in unit tests (AC-3, AC-4), not on the device.
2. Accepted risk T5: a fallback upsert can land after a newer writer's row and leave an older card-only copy until the next merge or refetch.
3. Three install-preserve installs (`compatible_replace`, standing authorization) on the owner's box; item rows 12855, 50344, 9365 and 15218, each over 1 MiB, are now card-only rows there.
