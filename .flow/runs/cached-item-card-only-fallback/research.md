# Research: cached-item-card-only-fallback

Base 4db1b061. Paths without a prefix are under `app/src/main/java/com/kino/puber/`.

## 1. Request

the run's state.md Log (/home/dev/Puber/.flow/runs/cached-item-card-only-fallback/state.md) and its source run's files: /home/dev/Puber/.flow/runs/cached-payload-row-too-big/triage-1.md (T5, T6), /home/dev/Puber/.flow/runs/cached-payload-row-too-big/review-state-path-1.md (F1, F2), spec /home/dev/Puber/docs/superpowers/specs/2026-11-24-cached-payload-row-too-big.md (risk rows). Two problems after the row-size cap landed (merge ba679aea): (1) while an oversized item details record is in memory, every card merge for that item is refused by PersistentPayloadStore, so the card is never persisted (after restart lists naming it refetch; offline the card is missing); (2) PersistentPayloadStore.refuse deletes the key's row unconditionally, so a concurrent card write for the same item can be lost. Proposed direction: persist a card-only copy (seasons/videos cleared, detailsUpdatedAt = null) when the merged item record is over MAX_ROW_BYTES, and move the refused-key delete under CachedFeed's epoch ownership. Research the current code (CachedFeed, ContentCacheRepository mergeItems/mergeItemRecord/observeItemDetails, ItemRecord shape, PersistentPayloadStore write/refuse), where the fallback belongs (store, feed hook, repository), what other feeds could exceed the limit, and options with trade-offs.

## 2. Understanding

A card and a details record of one title share one row (`content:v2:item:<id>`). Since ce4dfbaa the store refuses any row over 1 MiB and deletes the key's old row instead. A long series' details record is over that limit, and every later list merge starts from that in-memory record, so the merged card is refused too: the title has no row at all, and after eviction or a restart every list naming it refetches (offline: the card is missing). Separately, the refusal's delete runs below CachedFeed's ownership checks and can remove a small card row that a concurrent writer just stored. The fix should persist a small, card-only form of an oversized item record (memory keeps the full one) and make the refused-key delete conditional on the feed still owning the key.

## 3. Current state in code

| What | Where |
|---|---|
| Size check and refusal: `write` refuses when `utf8Size > MAX_ROW_BYTES` (1 MiB) and returns without telling the caller | `data/repository/PersistentPayloadStore.kt:47,100-108,128` |
| `writeAll` partitions, upserts the kept rows, then refuses the oversized ones | `PersistentPayloadStore.kt:110-126` |
| `refuse` logs and calls `dao.delete(key)` with no ownership condition (problem 2) | `PersistentPayloadStore.kt:131-134`, `data/db/CachedPayloadDao.kt:35-36` |
| `write`/`writeAll` return `Unit`; the interface has no way to report a refusal or expose the limit | `PersistentPayloadStore.kt:14-38` |
| `utf8Size` is file-private | `PersistentPayloadStore.kt:173-192` |
| Read side: oversized rows filtered in SQL and deleted with a size predicate (safe against concurrent small writes) | `PersistentPayloadStore.kt:61-98`, `CachedPayloadDao.kt:14-24` |
| `CachedFeed.load`: epoch captured before the loader; `store.write` only when `settled == epoch`; after the write `store.remove` only if `supersedesWrite`; otherwise only the memory value is discarded | `data/cache/CachedFeed.kt:232-266` |
| `CachedFeed.putAll`: `advanceKey` per key, one `store.writeAll`, then per key: memory put when `settled == epoch`, `store.remove` when `supersedesWrite`, else memory drop only ("a delete would leave every list naming this key pointing at nothing") | `CachedFeed.kt:388-434` |
| Ownership model: `KeyRecord.writes`/`supersedeWrite` tickets and `Epoch.supersedesWrite` | `CachedFeed.kt:99-195,474-487` |
| Memory tier keeps whatever the feed was given, regardless of what the store kept; weight = encoded length, 16 M chars cap for content feeds | `CachedFeed.kt:66-74,406-415`, `data/cache/ContentCacheRepository.kt:389-403,549` |
| `ItemRecord(item, cardUpdatedAt, detailsUpdatedAt?)`, private to the repository; one `itemFeed` holds cards and details | `ContentCacheRepository.kt:37,491-496` |
| `mergeItems` reads existing records through `peekAll` (memory first), merges, `itemFeed.putAll` (problem 1 starts here) | `ContentCacheRepository.kt:327-349,414-418` |
| `mergeItemRecord`/`mergeSparse`: a card (seasons/videos null) keeps the existing seasons/videos and `detailsUpdatedAt` | `ContentCacheRepository.kt:420-438,480-481` |
| `observeItemDetails`: a record with `detailsUpdatedAt == null` forces a refetch and emits nothing until details arrive; offline it throws | `ContentCacheRepository.kt:191-232` |
| `markItemDetailsStale` / `invalidateItemDetails`: `put(..., supersede = true)` of the current record with `detailsUpdatedAt` 0 / null | `ContentCacheRepository.kt:293-309` |
| List-level repair: missing card rows make `queryNeedsRefetch` force the load; `readItems` drops missing cards | `ContentCacheRepository.kt:364-387` |
| Heavy part of the record: `Item.seasons -> Season.episodes -> Episode.{files, audios, subtitles}` and `Item.videos -> Video.{files, audios, subtitles}` | `data/api/models/Models.kt:83-84,274-287,542-582` |
| Card UI that reads seasons: hero "N сезонов" label (`mapHeroItems`, used by HomeVM:504 and ContentListVM:198) and `buildDuration` | `core/ui/model/VideoItemUIMapper.kt:82-86,159-163` |
| Details writers of a big record: Details screen and `DetailsPrefetcher` warm via `ItemDetailsRepository.loadItemDetails` | `data/repository/ItemDetailsRepository.kt:42-60`, `domain/interactor/prefetch/DetailsPrefetcher.kt:138-141` |

Other feeds and the limit:

| Feed | Payload | Can exceed 1 MiB |
|---|---|---|
| `itemFeed` | `ItemRecord` with the seasons/videos tree | yes; box row was about 2.4 MB (`docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md:10`) |
| `listFeed`, `pageFeed` | item ids (+ pagination) | no |
| `historyFeed` | up to 50 entries (`data/api/history/HistoryRequest.kt:17`), each with an optional `Video` incl. files | unlikely; `unverified` (no size measured) |
| `payloadFeeds` | genres, collections page, bookmark folders, home collections (`GenreInteractor.kt:18`, `CollectionInteractor.kt:18`, `BookmarkFoldersInteractor.kt:30`, `HomeInteractor.kt:101`) | no |

A card-only `Item` (no seasons/videos) keeps `cast`, `plot`, `tracklist`, `bookmarks`; under 1 MiB in practice, but not bounded by type, so a refusal path is still needed.

Tests around it: `app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt:38-60` (refused value served from memory), `ContentCacheRepositoryTest.kt` (merge, details over a card-only record at :247, invalidation crossing a details write at :292-326, 16 MB tier at :465), `PersistentPayloadStoreTest.kt`, `app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt:12,44,65` (`refusedKeys` deletes the row like the Room store). Other `PersistentPayloadStore` implementations an interface change touches: `ItemDetailsRepositoryTest.kt:265`, `HomeInteractorCacheTest.kt:217`.

## 4. Related work

| Item | Relevance |
|---|---|
| `cached-payload-row-too-big` (done; ce4dfbaa, merge ba679aea) | Source run. Q2 "memory only this run; split/trim as follow-up"; triage-1 T5/T6/T7 deferred here; state-path round 2 confirms the T5/T6 race unchanged |
| `cache-targeted-invalidation` (done), `cache-decoded-memory-tier` (done), `cache-storage-bounds` (done) | Built the epoch/ticket model, typed memory tier and weigher this change relies on |
| `cache-layer-rework` (idea, umbrella, split) | No open item covers oversized rows |
| `catalogue-card-audio-subtitle-badges` (idea) | May want audio/subtitle data on cards; a card-only copy that drops videos/seasons removes one possible source for it after eviction |
| `cached-feed-key-maps`, `content-cache-legacy-cleanup` (dropped) | Not related |

Possible duplicate: none.

## 5. External findings

N/A: internal cache logic; the CursorWindow and SQLite facts are in `.flow/runs/cached-payload-row-too-big/research.md` section 5.

## 6. Options

Problem 2 is solved the same way in every option: the store stops deleting by key on a refusal and reports the refused keys (`write` returns a Boolean, `writeAll` the refused set, or a separate `fits(payload)`); `CachedFeed` removes the row only while it owns the key (`load`: `epochOf(key) == epoch` after the write; `putAll`: `settled == epoch`; supersede writes keep the delete). The check-then-delete window stays, as it does for the existing `store.remove` calls (`CachedFeed.kt:249,422`).

| | A. Feed hook (recommended) | B. Repository trims before the write | C. Store trims the JSON |
|---|---|---|---|
| How | `CachedFeed` gets an optional `persistable: (V) -> V` (or `fallback`). When the encoded payload does not fit, the feed encodes the fallback and writes that; memory keeps the full value. `itemFeed` passes "card-only": `item.copy(seasons = null, videos = null)`, `detailsUpdatedAt = null` | `mergeItems` / `observeItemDetails` loader encode the record, measure it and pass a trimmed record to the feed | `RoomPersistentPayloadStore` parses an oversized payload as `JsonObject` and drops `item.seasons` / `item.videos` |
| Memory keeps full details | yes | no: the feed only sees the trimmed value, so details are refetched on every open | yes |
| Ownership / epochs | unchanged: same writer, same ticket | unchanged | unchanged |
| Cost | one extra encode only for oversized values | an extra encode of every record (megabytes per details load) | parse of a 2+ MB JSON on the write path |
| Coupling | feed generic; item shape stays in the repository | none new | store learns `ItemRecord`'s JSON shape |
| Size | M (persistence risk area; `CachedFeed`, `ContentCacheRepository`, `PersistentPayloadStore`, fakes, tests) | M | M |

Variant inside A: keep season skeletons (`Season(id, number, title, episodes = null)`) instead of `seasons = null`, so the hero "N сезонов" label survives a cold start. Item fields that list endpoints never send are lost either way until details load again.

## 7. Risks

| Risk | Where |
|---|---|
| A card-only row has `detailsUpdatedAt = null`: after eviction or restart, Details refetches before showing anything, and offline it fails instead of showing stale details (same as today, where nothing is persisted) | `ContentCacheRepository.kt:197-200,229` |
| Memory and Room hold different shapes for one key; a `peek` after eviction promotes the card-only row, and code reading `seasons` from a card loses it (hero label) | `CachedFeed.kt:581-602`, `VideoItemUIMapper.kt:82-86` |
| `markItemDetailsStale` writes `detailsUpdatedAt = 0`; the fallback turns it into `null` in Room. Both force a refetch, but a supersede write must still persist the fallback and keep its ticket | `ContentCacheRepository.kt:293-309`, `CachedFeed.kt:474-487` |
| The fallback itself can exceed the limit (`cast`, `plot`, `tracklist`); the refusal path with owned delete must remain | `Models.kt:46-97` |
| `encodedLength` for the memory weigher must stay the full payload's length, or the 16 M char cap under-counts | `CachedFeed.kt:263,412` |
| An interface change ripples to three test stores | `FakePayloadStore.kt`, `ItemDetailsRepositoryTest.kt:265`, `HomeInteractorCacheTest.kt:217` |
| The race of problem 2 needs an oversized details load and a card merge of the same item at once; only a unit test with `FakePayloadStore` hooks (`onWrite`, `onWriteAll`) can reproduce it | `FakePayloadStore.kt:17-48` |

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Where does the card-only fallback live? | Option A: a `CachedFeed` hook supplied by `ContentCacheRepository` for `itemFeed` |
| Q2 | Card-only shape: `seasons = null` or season skeletons without episodes? | Season skeletons (`episodes = null`), `videos = null`, `detailsUpdatedAt = null`; keeps the hero label |
| Q3 | How does the store report a refusal? | `write` returns whether it stored; `writeAll` returns the refused keys; `refuse` no longer deletes |
| Q4 | Device verification? | Yes (persistence, offline card behaviour): on the box, open a long 4K series' details, browse the 4K «Все» grid, cold start offline, the card is still there |

## 9. Sources

- Code at 4db1b061: `data/repository/PersistentPayloadStore.kt`, `data/db/CachedPayloadDao.kt`, `data/cache/CachedFeed.kt`, `data/cache/ContentCacheRepository.kt`, `data/api/models/Models.kt`, `data/repository/ItemDetailsRepository.kt`, `domain/interactor/prefetch/DetailsPrefetcher.kt`, `core/ui/model/VideoItemUIMapper.kt`, `data/api/history/HistoryRequest.kt`, the four `getPayload`/`observePayload` interactors.
- Tests: `app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt`, `app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt`, `app/src/test/kotlin/com/kino/puber/data/cache/ContentCacheRepositoryTest.kt`.
- Commits: ce4dfbaa, ba679aea, ce32f8a2, 5b627683, 264748e7.
- Runs: `cached-payload-row-too-big` (state, triage-1, triage-2, review-state-path-1/2, qa-report), `cache-layer-rework`, `catalogue-card-audio-subtitle-badges`, `cached-feed-key-maps`, `content-cache-legacy-cleanup`.
- Docs: `docs/superpowers/specs/2026-11-24-cached-payload-row-too-big.md`, `docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md`, `docs/architecture/api.md`.
- URLs: none.
