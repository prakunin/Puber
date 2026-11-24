# Readiness 1: cached-payload-row-too-big

Verdict: **READY**

Inputs: spec and plan in `/home/dev/orca/workspaces/Puber/cached-payload-row-too-big/docs/superpowers/` (worktree HEAD `f1ff4a13`, plan uncommitted), `test-design.md`. Base `8e005395`.

## AC coverage

| AC | Task | Test design row |
|---|---|---|
| AC-1 | 1 (red), 3 (green) | `PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow` (P0 red) |
| AC-2 | 1, 3 | `PersistentPayloadStoreDbTest.readReturnsNullAndDeletesARowTooBigForTheCursorWindow` (P0 red) |
| AC-3 | 2 | `writeOfAnOversizedPayloadDeletesTheKeysOlderRow`, `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` (P0 red) |
| AC-4 | 2 | `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore`, `writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes` (P0 red) |
| AC-5 | 4 | `CachedFeedTest.aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession` (P0 guard), load variant (P1) |
| AC-6 | Device work 3 | Manual check 1 |

## Citations sampled (at worktree HEAD, base code unchanged)

| Citation | Resolves to |
|---|---|
| `PersistentPayloadStore.kt:40-42`, `:56-65`, `:67-73`, `:75-86`, `:83-85` | store class, `read`/`readAll`, `write`, `writeAll`, generation check |
| `CachedPayloadDao.kt:10-11`, `:13-14`, `:16-26` | `read`, `readAll` (`SELECT *`), upsert/upsertAll/touch/delete |
| `PuberDatabase.kt:13`, `:37-45` | `version = 5`; builder with `AndroidSQLiteDriver` |
| `PersistentPayloadStoreTest.kt:70-89`, `:111-125`, `:120`, `:127-172`, `:128-130`, `:133-135`, `:142-144` | clear-crossing test, sweep test, its `calls` assert, fake DAO, `rows`/`calls`/`onUpsert`, fake reads, fake `upsertAll` |
| `FakePayloadStore.kt:9`, `:35-43`, `:55-60` | `rows`, `write`, `writeAll` |
| `CachedFeed.kt:197-202`, `:239-243`, `:332-343`, `:346-373`, `:360`, `:388-434`, `:619-634` | `load`, its `store.write`, `peek`, `peekAll`, `store.readAll`, `putAll` (memory put after `writeAll`), `decodeUsable` |
| `ContentCacheRepository.kt:380-387` | `queryNeedsRefetch` |
| `CachedPayloadSweeper.kt:17`, `data/di/modules.kt:115`, `PuberApp.kt:105`, `CachedPayloadDaoTest.kt:25-37` | Timber import, store DI line, `LinkingDebugTree` plant, in-memory db setup |

All resolve. Every `dao.read`/`dao.readAll` caller (`rg`) is in the plan's Task 3 list; the only `SELECT *` on `cached_payload` are the two queries Task 3 rewrites.

## Order and build

| Task | Depends on | Build after commit |
|---|---|---|
| 1 | - | compiles; its two instrumented tests red by design (not part of `check.full`) |
| 2 | - | green; unit tests of `PersistentPayloadStoreTest` |
| 3 | 1, 2 (`MAX_ROW_BYTES`) | green; DAO signature, fake and both androidTest classes change in the same commit |
| 4 | - | full gate |

## Scope and questions

Plan stays inside Out of scope (no schema, driver or details-split change). Q1-Q3 answered; none blocking.

## Notes (not blocking)

1. The P0 guard row `writeStartedBeforeAClearDoesNotSurviveIt` names its defect only through its R6 ID; the refused branch of `write` must keep returning before the upsert so this guard stays meaningful.
2. AC-1/AC-2 green and AC-6 depend on `qa-device` runs on `192.168.1.110:5555` (Device work rows 1-3); Task 3 is not done until row 2 is reported.
