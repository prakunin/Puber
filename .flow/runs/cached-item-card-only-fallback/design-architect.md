# Design: cached-item-card-only-fallback

Paths without a prefix are under `app/src/main/java/com/kino/puber/`; test paths under `app/src/test/kotlin/com/kino/puber/`. Lines at base 4db1b061.

## Decisions

| Question | Decision | Why |
|---|---|---|
| How does the store report a refusal? (Q3) | `write(...): Boolean` (false: over the row limit, nothing written, no row touched); `writeAll(...): Set<String>` of refused keys. The generation-moved compensating delete stays and still returns true | Problem 2: only the feed knows who owns a key; the limit stays private to the store |
| Who deletes a refused key's old row? | `CachedFeed`, only while it owns the key: `load` when `epochOf(key) == epoch` after the refused write(s); `putAll` when `epochOf(key) == epochs[key]` | Same ownership rule as the existing `settled == epoch` paths; a stale row must still not outlive the value that replaced it |
| Where does the card-only copy come from? (Q1) | `CachedFeed` constructor parameter `persistFallback: ((V) -> V)? = null`; called only for a refused key, its payload written in the same `load`/`putAll` call under the original epoch | One encode extra only for oversized values; no new write path outside the ticket model |
| When is the fallback written? | Only if the key is still owned (`epochOf(key) == epoch`) after the refusal; otherwise the other writer's row stands and nothing is written or deleted | Closes AC-5: a refused write never overwrites or deletes a newer writer's row |
| Card-only shape (Q2) | `ItemRecord.cardOnly()`: `item.copy(seasons = item.seasons?.map { Season(it.id, it.number, it.title) }, videos = null)`, `detailsUpdatedAt = null`, `cardUpdatedAt` kept | Keeps the hero "N сезонов" label; `detailsUpdatedAt = null` makes Details refetch (`ContentCacheRepository.kt:197-198`) |
| What does memory hold? | The full value with the full payload's `encodedLength`, as today | `peek` in the session returns full details (AC-2); the 16 M char weigher stays honest |
| Where is the refusal logged? | The store no longer logs; `CachedFeed` logs `Refused cached payload %s ...` (`Timber.w`) only when nothing was persisted for an owned key (no fallback, or fallback refused) | A refused full record followed by a stored card-only copy is the normal path; AC-7 expects no such line for it |
| `markItemDetailsStale` over a card-only row | Write `detailsUpdatedAt = current.detailsUpdatedAt?.let { STALE_TIMESTAMP }` (null stays null) | After memory eviction the record read is the card-only row; stamping it 0 makes `observeItemDetails` emit an episode-less item as details (`ContentCacheRepository.kt:219-222`). See Open questions |

## Changes by layer

### API / repository

| File | Change |
|---|---|
| `data/repository/PersistentPayloadStore.kt:29-32` | `write` returns `Boolean`; default `writeAll` returns the keys whose `write` returned false |
| `data/repository/PersistentPayloadStore.kt:99-109` | Over-limit: return false with no DAO call; otherwise upsert, generation check as now, return true |
| `data/repository/PersistentPayloadStore.kt:111-126` | Return the oversized keys; upsert and generation check of the kept rows unchanged |
| `data/repository/PersistentPayloadStore.kt:130-134` | `refuse` removed (no delete, no log) |
| `data/cache/CachedFeed.kt:46-56` | New constructor parameter `persistFallback: ((V) -> V)? = null` |
| `data/cache/CachedFeed.kt:238-250` (`load`) | Write via a private helper: `store.write` false -> if owned and fallback present, encode the fallback and `store.write` it; if still not stored and owned, `store.remove(key)` and log. The existing `settledAfterWrite.supersedesWrite(epoch)` remove and `discardValue` follow unchanged. `encodedLength` stays `payload.length` (:263) |
| `data/cache/CachedFeed.kt:396-400` (`putAll`) | Keep `refused = store.writeAll(...)`. For refused keys still owned (`epochOf(key) == epochs.getValue(key)`) with a fallback: one `store.writeAll` of the encoded fallbacks. Each owned key refused with no fallback stored: `store.remove(key)` and log. Then the settle loop at :401-432 unchanged (memory gets the full value, `encodedLength` from the full payload at :412; the supersede branch at :418-424 keeps its delete) |
| `data/cache/ContentCacheRepository.kt:389-403` (`newFeed`) | Optional `persistFallback` parameter passed through to `CachedFeed` |
| `data/cache/ContentCacheRepository.kt:37` | `itemFeed = newFeed(ItemRecord.serializer(), persistFallback = ItemRecord::cardOnly)`; other feeds unchanged |
| `data/cache/ContentCacheRepository.kt:491-496` (`ItemRecord`) | Add `fun cardOnly(): ItemRecord` (shape in Decisions); `Season` at `data/api/models/Models.kt:542-547` |
| `data/cache/ContentCacheRepository.kt:293-300` (`markItemDetailsStale`) | Null `detailsUpdatedAt` stays null (Decisions) |

### Test doubles (interface ripple)

| File | Change |
|---|---|
| `util/FakePayloadStore.kt:38-50,62-67` | Return values per the new interface; a refusal no longer removes the row; add `maxPayloadLength: Int = Int.MAX_VALUE` refusing `payload.length > maxPayloadLength` next to `refusedKeys` (:12), so a fallback can fit where the full payload does not; hooks fire as now |
| `domain/interactor/home/HomeInteractorCacheTest.kt:225-227` | `write` returns true |
| `data/repository/ItemDetailsRepositoryTest.kt:273-275` | `write` returns true |
| `data/repository/PersistentPayloadStoreTest.kt:96-117` | The two tests asserting the delete are replaced by AC-1 tests (row kept, key reported) |

### ViewModel state and actions; screen and DI module; Compose content; resources

N/A: the change ends at `ContentCacheRepository`; no public signature above it moves.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Repository trims before every write (research.md: 6, B) | Extra multi-megabyte encode on every details load, and memory would lose full details |
| Store trims the JSON (research.md: 6, C) | Store learns `ItemRecord`'s shape and parses 2+ MB on the write path |
| Store exposes `fits(payload)` / `MAX_ROW_BYTES` to the feed | Leaks the limit and the byte counting out of the store; a check-then-write pair instead of one call |
| `seasons = null` in the card-only copy | Hero "N сезонов" label lost after a cold start (`core/ui/model/VideoItemUIMapper.kt:82-86`) |
| Fallback write as a separate `put` from the repository | New write path outside the ticket and generation checks; reopens problem 2 |

## Risks

| Risk | Kind | Where |
|---|---|---|
| The fallback write must reuse the caller's epoch and re-check ownership before writing and before deleting; a write after another writer's `advanceKey` overwrites its newer row | concurrency | `CachedFeed.kt:232-250`, `:392-432`, `:474-487` |
| Memory (full) and Room (card-only) differ for one key; after eviction `peekAll` promotes the card-only row, so readers of `seasons`' episodes or `videos` see none until details reload | data shape | `CachedFeed.kt:581-602`, `ContentCacheRepository.kt:414-418` |
| A card-only row stamped `detailsUpdatedAt = 0` by `markItemDetailsStale` (called after playback, `ui/feature/player/vm/PlayerVM.kt:1986`) would be emitted as details without episodes; covered by the null-stays-null decision | correctness | `ContentCacheRepository.kt:219-222,293-300` |
| Check-then-delete window for an owned refusal, as for the existing `store.remove` calls | concurrency | `CachedFeed.kt:249,422` |
| `HomeInteractorTest` uses a relaxed `mockk<PersistentPayloadStore>`: `write` returns false, so every load takes the refusal path (fallback encode, `remove` on the mock); behaviour unchanged since the mock stores nothing | test | `domain/interactor/home/HomeInteractorTest.kt:50,275` |
| Detekt complexity/`ReturnCount` in `load`'s loader lambda and `putAll`; the fallback logic belongs in private helpers | rollout | `CachedFeed.kt:228-265,388-434` |
| Two encodes per write of an oversized value while it is in memory (each list merge of that item) | performance | `CachedFeed.kt:235,397` |
| After restart the card-only row forces a Details refetch; offline Details throws, as today | lifecycle | `ContentCacheRepository.kt:197-200,229` |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| D1 | No AC covers the `markItemDetailsStale` null-stays-null change; add a unit row (card-only record from Room, `markItemDetailsStale`, `observeItemDetails` calls its loader and emits no episode-less value first)? | Yes: test-designer adds it under AC-3's fixture; no AC change | no |
