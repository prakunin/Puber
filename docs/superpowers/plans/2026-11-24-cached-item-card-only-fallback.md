# Persist a card-only copy of an oversized item record, and let only the owning writer delete a refused key - plan

**Spec:** `docs/superpowers/specs/2026-11-24-cached-item-card-only-fallback.md`
**Goal:** The store reports a refused write instead of deleting the key; `CachedFeed` writes a fallback payload or deletes the row only while it owns the key; `itemFeed` falls back to a card-only `ItemRecord`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Never `./gradlew` in the worktree.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'` (one `--tests` per class).
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths without a prefix: production under `app/src/main/java/com/kino/puber/`, tests under `app/src/test/kotlin/com/kino/puber/`. Lines at base 4db1b061 (worktree HEAD 8e1321e6 changes only the spec).
- Out of scope (spec): splitting a details record across rows; offline cold start on the box. No change above `ContentCacheRepository` (no VM, screen, DI, Compose or resource edit).
- Detekt: `CachedFeed` has 22 functions of its own; `TooManyFunctions` allows 25 (`config/detekt/detekt.yml:157-161`), so this plan adds exactly two private functions to it. `ReturnCount` max 3 (`detekt.yml:756-759`).
- Refusal log line, written only by `CachedFeed`: `Timber.w("Refused cached payload %s: %d chars, nothing persisted", key, payload.length)`. The prefix `Refused cached payload <key>` is what AC-7 greps for.

## Red rows and `@Disabled`

The test design requires every `red` row to fail on base, after the fixture and before any production edit. Task 1 writes those rows, runs them on base, records the failures in the Implementation record, and commits them with `@org.junit.jupiter.api.Disabled("Red until Task N")`. The task that fixes a row removes its `@Disabled` before its production edit. `red (compile)` rows are written first in their own task; the compile failure is their red run.

---

## Tasks

### Task 1: Extend FakePayloadStore and record the red run on base

**Covers:** fixture for AC-1..AC-6; guards for AC-2, AC-3, AC-6
**Files:**
- Modify: `util/FakePayloadStore.kt` - add `var maxPayloadLength: Int = Int.MAX_VALUE` next to `refusedKeys` (:12); a key is refused when `key in refusedKeys || payload.length > maxPayloadLength`, in `write` (:38-50) and `writeAll` (:62-67). Base semantics kept: a refusal removes the row. Add `var onBeforeWriteAll: (suspend (Set<String>) -> Unit)?`, one-shot like `onBeforeWrite` (:36, :39-41), fired at the start of `writeAll` before any row lands.
- Test: `data/repository/PersistentPayloadStoreTest.kt`, `data/cache/CachedFeedTest.kt`, `data/cache/ContentCacheRepositoryTest.kt`

**Consumes:** `CachedFeedTest.feed()` (:31-37, `String.serializer()`, so a value `"card"` is stored as the payload `"\"card\""`); `ContentCacheRepositoryTest` `store`/`subject`/`now` (:45-47), `item(id)` (:644); `ContentCacheRepository.itemKey` (`data/cache/ContentCacheRepository.kt:551`); public `mergeItems` (`ContentCacheRepository.kt:274-285`), `markItemDetailsStale` (:293-300), `observeItemDetails` (:191-232), `observeItems` (:49-78); `Season`/`Episode`/`Video`/`VideoFile` (`data/api/models/Models.kt:542-547`, :550, :274, :579).
**Produces:** `FakePayloadStore.maxPayloadLength`, `FakePayloadStore.onBeforeWriteAll`; a private `series(7)` fixture in `ContentCacheRepositoryTest`: `ItemType.SERIAL`, 3 seasons x 20 episodes, each episode with one `VideoFile(quality = "1080p", codec = "h264")`, plus one `Video` with files. The tests set `store.maxPayloadLength` (for example 4_000) after the details load and first assert that the stored full row is longer than it; AC-2 asserts the card-only row fits.

Red rows, written, run on base (each must fail), then `@Disabled`:

| Test | Enabled in | Base failure expected |
|---|---|---|
| `PersistentPayloadStoreTest.writeOfAnOversizedPayloadKeepsTheKeysOlderRow` | Task 2 | row `k` deleted |
| `PersistentPayloadStoreTest.writeAllKeepsTheOlderRowOfAnOversizedKeyAndStoresTheSmallOne` | Task 2 | row `old` deleted |
| `CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow` | Task 2 | no row: the refusal deletes the crossing `"card"` (uses `onBeforeWrite`, not `onWrite`) |
| `ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow` | Task 4 | no row for `itemKey(7)` |
| `ContentCacheRepositoryTest.aCardOnlyRowServesTheListAfterARestartWithoutTheLoader` | Task 4 | card missing from the list |
| `ContentCacheRepositoryTest.aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart` | Task 4 | card dropped from the stale list |
| `ContentCacheRepositoryTest.markingACardOnlyRecordStaleStillMakesDetailsLoadFirst` | Task 4 | card stamped 0 emitted before the loaded details |

Guard rows, enabled, must pass on base:

| Test | AC |
|---|---|
| `CachedFeedTest.anOwnedPutRefusalWithNoFallbackLeavesNoRow` | AC-6 |
| `CachedFeedTest.anOwnedLoadRefusalWithNoFallbackLeavesNoRow` | AC-6 |
| `CachedFeedTest.aRefusedBulkWriteCrossedByANewerWriteKeepsTheNewerRow` (`onWriteAll` runs a newer small `putAll`) | AC-6 |
| `ContentCacheRepositoryTest.anOversizedDetailsRecordIsStillServedInFullInTheSameSession` (`observeItemDetails(7) { error("memory") }` after the merge) | AC-2 |
| `ContentCacheRepositoryTest.detailsOverACardOnlyRowAfterARestartCallTheLoaderFirst` (emissions are exactly the loaded details) | AC-3 |

Scenarios follow the Proves column of `test-design.md` for each name. AC-2/AC-3/AC-4 sequence: `observeItemDetails(7) { series(7) }`, set `store.maxPayloadLength`, then the list merge (`mergeItems(listOf(item(7).copy(title = "Merged")))` for AC-2; `observeItems("home", CacheTtl.HomeSection) { listOf(item(7).copy(title = "Merged")) }` for AC-3/AC-4), then `ContentCacheRepository(store = store, clock = { now })` as the restart (pattern at `ContentCacheRepositoryTest.kt:399-403`; AC-4 advances `now` past `CacheTtl.HomeSection` and uses a throwing loader). AC-2 decodes the row with `kotlinx.serialization.json.Json` (`jsonObject` imports at :26-29): `item.title == "Merged"`, every season has `id`/`number`/`title` and no `episodes`, no `item.videos`, no `detailsUpdatedAt`.

- [x] Extend the fixture
- [x] Write the red and guard rows above; run the three test classes on base; red rows fail as listed, guards pass, existing tests pass; record each failing line under **Red run**
- [x] Add `@Disabled("Red until Task N")` to the red rows; run the fast gate and the three classes; commit

**Done when:** the three classes are green with the seven red rows skipped, and the Implementation record lists seven base failures.

---

### Task 2: Make the store report refusals and let only the owning writer delete

**Covers:** AC-1, AC-5 (no-fallback half), AC-6 (owned delete)
**Files:**
- Modify: `data/repository/PersistentPayloadStore.kt` - interface `write` (:29) returns `Boolean` (KDoc: false when over the row limit; nothing written, the key's row untouched); default `writeAll` (:30-32) returns `Set<String>` of the keys whose `write` returned false. `RoomPersistentPayloadStore.write` (:99-109): oversized returns false with no DAO call; otherwise upsert, generation check and compensating delete as now, return true. `writeAll` (:111-126): return the oversized keys (empty map returns `emptySet()`); kept rows unchanged. Delete `refuse` (:130-134) and its KDoc.
- Modify: `data/cache/CachedFeed.kt` - add `import timber.log.Timber`. New private `suspend fun persistLoaded(key: String, value: V, payload: String, updatedAt: Long, epoch: Epoch)` replacing the `store.write(...)` call at :239-243: if `store.write` returned true or `epochOf(key) != epoch`, return; otherwise `store.remove(key)` and log. Lines :244-250 (supersede check, `discardValue`) and `encodedLength = payload.length` (:263) unchanged. New private `suspend fun persistRefused(refused: Set<String>, values: Map<String, V>, payloads: Map<String, String>, epochs: Map<String, Epoch>, updatedAt: Long)` called with the result of `store.writeAll` at :398-400, before the settle loop: for each refused key with `epochOf(key) == epochs.getValue(key)`, `store.remove(key)` and log. The settle loop :401-432 unchanged.
- Modify: `util/FakePayloadStore.kt` - `write` returns false on refusal and leaves the row; `writeAll` returns the refused set and leaves their rows; hooks fire as now.
- Modify: `domain/interactor/home/HomeInteractorCacheTest.kt:225-227`, `data/repository/ItemDetailsRepositoryTest.kt:273-275` - `write` stores and returns true.
- Test: `data/repository/PersistentPayloadStoreTest.kt` - delete `writeOfAnOversizedPayloadDeletesTheKeysOlderRow` (:95-102) and `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` (:104-116); `data/cache/CachedFeedTest.kt`.

**Consumes:** `epochOf` (`CachedFeed.kt:448-458`), `Epoch` (:172-195), `store.remove`; `advanceKey` captures epochs at :396 after its own advance, so the putAll owner check compares against the post-advance epoch.
**Produces:** `PersistentPayloadStore.write(...): Boolean`, `writeAll(...): Set<String>`; `CachedFeed.persistLoaded` and `CachedFeed.persistRefused`, which Task 3 extends.

| Test | Base | AC |
|---|---|---|
| `PersistentPayloadStoreTest.writeReportsAnOversizedPayloadAsNotStored` (false over, true at exactly `MAX_ROW_BYTES`) | red (compile) | AC-1 |
| `PersistentPayloadStoreTest.writeAllReturnsOnlyTheOversizedKeys` | red (compile) | AC-1 |
| `PersistentPayloadStoreTest.writeOfAnOversizedPayloadKeepsTheKeysOlderRow` | remove `@Disabled` | AC-1 |
| `PersistentPayloadStoreTest.writeAllKeepsTheOlderRowOfAnOversizedKeyAndStoresTheSmallOne` | remove `@Disabled` | AC-1 |
| `CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow` | remove `@Disabled` | AC-5 |

- [x] Write the two `red (compile)` rows and remove the three `@Disabled`; confirm the compile failure; delete the two replaced tests
- [x] Change the store, the fake and the two in-memory stores; add `persistLoaded` and `persistRefused`
- [x] Run the fast gate and `PersistentPayloadStoreTest`, `CachedFeedTest`, `CachedFeedMemoryWeightTest`, `ContentCacheRepositoryTest`, `HomeInteractorCacheTest`, `HomeInteractorTest`, `ItemDetailsRepositoryTest`; the Task 1 guards stay green; commit

**Done when:** those classes are green and `grep -n "refuse\|Refused" app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt` prints nothing.

---

### Task 3: Write a fallback payload for a refused value under the caller's epoch

**Covers:** AC-5, AC-6, R5
**Files:**
- Modify: `data/cache/CachedFeed.kt` - constructor (:46-56) gains last parameter `private val persistFallback: ((V) -> V)? = null`. `persistLoaded`: after a refused write and an owner check, if `persistFallback` is set, encode `persistFallback(value)` with `json.encodeToString(serializer, ...)` and `store.write` it; when that is stored, return; otherwise, if still owned, `store.remove(key)` and log. At most 3 `return`s. `persistRefused`: for the owned refused keys, when `persistFallback` is set, one `store.writeAll` of their encoded fallbacks stamped `updatedAt`; each owned key with no fallback, or whose fallback is refused, and still owned after that write: `store.remove(key)` and log. Memory keeps the full value and the full payload's length (:263, :412).
- Test: `data/cache/CachedFeedTest.kt`, `data/cache/CachedFeedMemoryWeightTest.kt`

**Consumes:** Task 2's `persistLoaded`, `persistRefused`, `write: Boolean`, `writeAll: Set<String>`; `FakePayloadStore.maxPayloadLength`, `onBeforeWrite`, `onWriteAll`.
**Produces:** `CachedFeed(..., persistFallback: ((V) -> V)? = null)`.

| Test | Base | AC |
|---|---|---|
| `CachedFeedTest.aRefusedLoadCrossedByABulkWriteWritesNoFallbackOverTheNewerRow` | red (compile) | AC-5 |
| `CachedFeedTest.aRefusedPutPersistsItsFallbackAndServesTheFullValueFromMemory` | red (compile) | AC-6 |
| `CachedFeedTest.aRefusedLoadPersistsItsFallback` | red (compile) | AC-6 |
| `CachedFeedTest.aPutWhoseFallbackIsAlsoRefusedLeavesNoRow` | red (compile) | AC-6 |
| `CachedFeedTest.aSupersedingPutPersistsItsFallbackNeverTheSupersededPayload` (`put(..., supersede = true)`, fitting and over-limit fallback) | red (compile) | AC-6 |
| `CachedFeedMemoryWeightTest.aRefusedValueStillWeighsItsFullPayloadInMemory` (a feed with `maximumMemoryWeight = 20L`, `memoryWeigher = { it }`, a 1-char fallback, `store.maxPayloadLength = 5`; two 10-char values: the older is evicted, its `load` reads the store once) | red (compile) | R5 |

- [x] Write the six rows; confirm the compile failure
- [x] Add `persistFallback` and the fallback paths
- [x] Run the fast gate, `CachedFeedTest`, `CachedFeedMemoryWeightTest`, `ContentCacheRepositoryTest`; commit

**Done when:** both `CachedFeed*` classes are green, and `ContentCacheRepositoryTest` is green with its four Task 4 rows still skipped.

---

### Task 4: Give itemFeed a card-only fallback and keep a null detailsUpdatedAt null when marked stale

**Covers:** AC-2, AC-3, AC-4, AC-6, D1
**Files:**
- Modify: `data/cache/ContentCacheRepository.kt` - `ItemRecord` (:491-496) gains `fun cardOnly(): ItemRecord = copy(item = item.copy(seasons = item.seasons?.map { Season(id = it.id, number = it.number, title = it.title) }, videos = null), detailsUpdatedAt = null)` (`cardUpdatedAt` kept; import `com.kino.puber.data.api.models.Season`). `newFeed` (:389-403) gains `persistFallback: ((V) -> V)? = null` passed to `CachedFeed`. `itemFeed` (:37) becomes `newFeed(ItemRecord.serializer(), persistFallback = ItemRecord::cardOnly)`; the other feeds and `payloadFeed` (:407-408) unchanged. `markItemDetailsStale` (:293-300) writes `detailsUpdatedAt = current.detailsUpdatedAt?.let { STALE_TIMESTAMP }`.
- Test: `data/cache/ContentCacheRepositoryTest.kt`

**Consumes:** Task 3's `persistFallback`; `CachedFeed.DefaultJson` does not encode defaults (`CachedFeed.kt:654-657`), so null `episodes`, `videos` and `detailsUpdatedAt` are absent from the row.
**Produces:** nothing consumed later.

| Test | Base | AC |
|---|---|---|
| `ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow` | remove `@Disabled` | AC-2 |
| `ContentCacheRepositoryTest.aCardOnlyRowServesTheListAfterARestartWithoutTheLoader` | remove `@Disabled` | AC-3 |
| `ContentCacheRepositoryTest.aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart` | remove `@Disabled` | AC-4 |
| `ContentCacheRepositoryTest.markingACardOnlyRecordStaleStillMakesDetailsLoadFirst` | remove `@Disabled` | D1 |
| `ContentCacheRepositoryTest.markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow` (AC-2 state, `markItemDetailsStale(7)`: row card-only without `detailsUpdatedAt`) | red: fails before the edit (no row) | AC-6 |

- [x] Write the new row and remove the four `@Disabled`; run the class and record the five failures
- [x] Make the repository change
- [x] Run the fast gate and `ContentCacheRepositoryTest` (including the existing `detailsOverACardOnlyRecordReportTheFailureInsteadOfEmittingNothing`, :247), `ItemDetailsRepositoryTest`, `HomeInteractorCacheTest`, `HomeInteractorTest`; then the full gate; commit

**Done when:** the full gate is green and `grep -rn "@Disabled" app/src/test` prints nothing.

---

## Device work

Owner: `qa-device`, on the box `192.168.1.110:5555` (`com.kino.puber.stage` only), after Task 4, with the worktree's dev APK installed by `tools/android-apk-install-preserve`. Steps, expected results and must-not-happen are the Manual checks of `test-design.md`.

| Check | AC | Evidence |
|---|---|---|
| Manual check 1: card-only row for an over-1 MiB series, no refusal line | AC-7 | query results only (key, length, `instr` values), ids tried; no db copy, no raw rows; `tools/mobile-evidence-audit.sh` over the evidence dir |
| Manual check 2: cold start, grid card, details with seasons and episodes | AC-8 | screenshots of the grid and the details screen |

## Coverage

| AC | Task | Tests |
|---|---|---|
| AC-1 | 2 | four `PersistentPayloadStoreTest` rows; existing `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore` (:120) |
| AC-2 | 1, 4 | `aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow`, `anOversizedDetailsRecordIsStillServedInFullInTheSameSession` |
| AC-3 | 1, 4 | `aCardOnlyRowServesTheListAfterARestartWithoutTheLoader`, `detailsOverACardOnlyRowAfterARestartCallTheLoaderFirst` |
| AC-4 | 4 | `aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart` |
| AC-5 | 2, 3 | `aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow`, `aRefusedLoadCrossedByABulkWriteWritesNoFallbackOverTheNewerRow` |
| AC-6 | 1, 3, 4 | three Task 1 guards, four Task 3 `CachedFeedTest` rows, `markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow` |
| AC-7, AC-8 | Device work | Manual checks 1, 2 |
| D1 | 4 | `markingACardOnlyRecordStaleStillMakesDetailsLoadFirst` |
| R5 | 3 | `aRefusedValueStillWeighsItsFullPayloadInMemory` |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| sha | subject |
|---|---|
| bca6c9a1 | Add the oversized-payload fixture and the red and guard rows for the card-only fallback |
| 2e69f402 | Make the payload store report refused writes and let only the owning writer delete the key |
| 58bc2aa0 | Write a fallback payload for a refused value only while the caller still owns the key |
| (this commit) | Persist a card-only copy of an oversized item record and keep a null detailsUpdatedAt null when marked stale |

**Red run** (P0 `red` tests on the unchanged code):

- red: PersistentPayloadStoreTest.writeOfAnOversizedPayloadKeepsTheKeysOlderRow - AssertionFailedError at PersistentPayloadStoreTest.kt:120 (row `k` deleted)
- red: PersistentPayloadStoreTest.writeAllKeepsTheOlderRowOfAnOversizedKeyAndStoresTheSmallOne - AssertionFailedError at :129 (row `old` deleted)
- red: CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow - AssertionFailedError at CachedFeedTest.kt:67 (crossing `"card"` row deleted)
- red: ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow - AssertionFailedError at :265 (no row for itemKey(7))
- red: ContentCacheRepositoryTest.aCardOnlyRowServesTheListAfterARestartWithoutTheLoader - AssertionFailedError at :304 (loader called, card row missing)
- red: ContentCacheRepositoryTest.aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart - AssertionFailedError at :335 (card missing from the stale list)
- red: ContentCacheRepositoryTest.markingACardOnlyRecordStaleStillMakesDetailsLoadFirst - AssertionFailedError at :352 (card stamped 0 emitted before the loaded details)
- red (compile): PersistentPayloadStoreTest.writeReportsAnOversizedPayloadAsNotStored, writeAllReturnsOnlyTheOversizedKeys - `None of the following candidates is applicable` (PersistentPayloadStoreTest.kt:99-100)
- red (compile): CachedFeedTest (5 rows) and CachedFeedMemoryWeightTest.aRefusedValueStillWeighsItsFullPayloadInMemory - `No parameter with name 'persistFallback' found`
- red: ContentCacheRepositoryTest.markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow - AssertionFailedError at :361 (no row), run in Task 4 before the edit
- guards green on base as listed in Task 1: anOwnedPutRefusalWithNoFallbackLeavesNoRow, anOwnedLoadRefusalWithNoFallbackLeavesNoRow, aRefusedBulkWriteCrossedByANewerWriteKeepsTheNewerRow, anOversizedDetailsRecordIsStillServedInFullInTheSameSession, detailsOverACardOnlyRowAfterARestartCallTheLoaderFirst

**Deviations from the plan:**

- `FakePayloadStore.onBeforeWriteAll` was dropped: no row uses it (readiness note 2).
- `markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow` was written in Task 4, not Task 1, as the plan's Task 4 says; Task 1 therefore listed seven base failures.
- `persistLoaded`/`persistRefused` got their final signatures in Task 3; in Task 2 they omitted the unused `value`/`values`/`updatedAt`.
- `persistRefused` carries `@Suppress("LongParameterList")` (five parameters, the plan's signature).

**Files touched outside the plan:** none.

**For the reviewers:**

- `CachedFeed.persistLoaded`: the fallback write keeps the base check-then-write window (readiness note 3); a `putAll` landing while it is suspended is overwritten by the card-only copy.
- `ItemRecord.cardOnly` drops `videos` and episodes only; every other `Item` field stays, so a record with a huge plot would still be refused and its row removed.
- Full gate `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL (detekt prints "7 compiler errors found during analysis" for the unit-test source set).

**Fix round 1 (T1-T3):**

- red: CachedFeedTest.aRefusedBulkWriteDoesNotDeleteAKeyTakenOverDuringAnEarlierDelete - AssertionFailedError at CachedFeedTest.kt:181 (key `b` deleted after the crossing write)
- green on base: CachedFeedTest.aRefusedBulkWriteWritesTheFallbackOnlyOverKeysItStillOwns - the owned filter already exists; the row pins it (guard, T2)
- T1: `persistRefused` re-checks ownership immediately before each `store.remove`. T3: warn log for the stored fallback (key and sizes, no payload; wording changed in fix round 2, U1) in `persistLoaded` and `persistRefused`; the "nothing persisted" log is kept.

**Fix round 2 (U1):**

- U1: the fallback-persisted warn in `persistLoaded` and `persistRefused` no longer contains "Refused cached payload" (now `Cached payload %s over the row limit (%d chars); stored the fallback (%d chars)`, key and sizes only), so the AC-7 grep matches only the "nothing persisted" line. No test change.

**Blocked:** none.

## Readiness

# Readiness 1: cached-item-card-only-fallback

Verdict: **READY**

Spec and plan at worktree HEAD 8e1321e6 (plan untracked); citations checked against `git show 4db1b061:<path>`.

## Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | yes: AC-1..AC-6 and D1 in Tasks 1-4 (plan Coverage), AC-7/AC-8 in Device work, Manual checks 1-2 |
| Citations resolve | yes, all sampled (table below) |
| Build stays green per task | yes: Task 1 red rows compile on base and are `@Disabled`; Task 2 changes the interface together with every implementer (`RoomPersistentPayloadStore`, `FakePayloadStore`, the in-memory stores at `HomeInteractorCacheTest.kt:217`, `ItemDetailsRepositoryTest.kt:265`); the relaxed mocks at `HomeInteractorTest.kt:50,275` need no edit; `androidTest/.../PersistentPayloadStoreDbTest.kt` calls `write` as a statement only |
| Tasks ordered by dependency | yes: fixture -> store/feed ownership -> fallback -> `itemFeed` |
| P0 rows name defect, red/guard, test name | yes; manual P0 rows are AC-7/AC-8 (Manual checks 1-2) |
| Inside Out of scope | yes: no change above `ContentCacheRepository`, no row splitting, no offline device check |
| Blocking open questions | none; Q1-Q3, D1 answered |

## Sampled citations

| Citation | Resolves to |
|---|---|
| `PersistentPayloadStore.kt:29-32` | `write` returning Unit, default `writeAll` |
| `PersistentPayloadStore.kt:99-109,111-126,130-134` | Room `write`, `writeAll`, `refuse` with `dao.delete(key)` at :133 |
| `CachedFeed.kt:46-56` | constructor |
| `CachedFeed.kt:238-250,263` | `load` write, `supersedesWrite` remove, `encodedLength = payload.length` |
| `CachedFeed.kt:396-400,401-432,412,418-424` | `putAll` epochs, `writeAll`, settle loop, supersede delete |
| `CachedFeed.kt:448-458`, `:172-195`, `:654-657` | `epochOf`, `Epoch`, `DefaultJson` (no `encodeDefaults`) |
| `ContentCacheRepository.kt:37,191-232,274-285,293-300,389-403,414-438,491-496,551` | `itemFeed`, `observeItemDetails`, `mergeItems`, `markItemDetailsStale`, `newFeed`, `readItemRecords`/`mergeItemRecord`, `ItemRecord`, `itemKey` |
| `Models.kt:542-547` | `Season(id, number, title, episodes)` |
| `FakePayloadStore.kt:12,36,38-50,62-67` | `refusedKeys`, `onBeforeWrite`, `write`, `writeAll` |
| `PersistentPayloadStoreTest.kt:95-116,120` | the two delete tests to replace; byte-boundary test |
| `detekt.yml:157-161,756-759` | `TooManyFunctions` 25; `ReturnCount` 3; `CachedFeed` has 22 own functions at base |
| `PlayerVM.kt:1986` | `markItemDetailsStale` call |

## Notes for the implementer (not blocking)

| # | Note |
|---|---|
| 1 | `markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow` is `red (compile)` in test-design.md but uses only public API; follow plan Task 4 (plain red before the edit). |
| 2 | `FakePayloadStore.onBeforeWriteAll` (Task 1) is used by no test row; use it or drop it. |
| 3 | The fallback write in `persistLoaded` keeps the base check-then-write window (`CachedFeed.kt:238-243`): a `putAll` landing while the fallback write is suspended is overwritten by the card-only copy. No test covers it; reviewers should confirm it is no wider than the base window. |
| 4 | test-design.md R11 points to "Manual step 2"; the retry is in Manual check 1, step 5. |
