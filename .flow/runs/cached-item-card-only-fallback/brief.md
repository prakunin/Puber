# Brief: cached-item-card-only-fallback

| Field | Value |
|---|---|
| kind | bug |
| size | M |
| tracks | none |
| base | 4db1b061 |
| source | `cached-payload-row-too-big` triage-1 T5, T6 (state-path F1, F2) |
| device verification | yes: persistence and caches; box `192.168.1.110:5555` (`com.kino.puber.stage`), install-preserve only, online only (see AC-7) |

Paths without a prefix are under `app/src/main/java/com/kino/puber/`. Findings and options: research.md (cited by section).

## Request

Two defects left by the row-size cap (merge ba679aea), state.md Log 2026-11-24: (1) an item whose details record is over the limit never gets its card persisted; (2) a refused write deletes the key's row with no ownership check, so a concurrent small card write can be lost.

## Root cause

Both **confirmed by code trace** at 4db1b061; neither reproduced on a device. Problem 2 is a timing race that only a unit test with `FakePayloadStore` hooks can force (research.md: 7).

| # | Step | Where |
|---|---|---|
| 1 | A card merge reads the existing record memory first, so it starts from the full details record | `data/cache/ContentCacheRepository.kt:414-418` (`readItemRecords` via `peekAll`) |
| 1 | `mergeItemRecord` keeps the existing seasons/videos tree, so the merged card is as large as the details record | `ContentCacheRepository.kt:420-438` |
| 1 | `writeAll` refuses it over `MAX_ROW_BYTES` and deletes the key: the title has no row; after eviction or restart lists naming it refetch (`ContentCacheRepository.kt:364-387`), offline the card is dropped | `data/repository/PersistentPayloadStore.kt:47,110-134` |
| 2 | `refuse` calls `dao.delete(key)` unconditionally, below `CachedFeed`'s epoch checks | `PersistentPayloadStore.kt:131-134` |
| 2 | `CachedFeed.load` writes after its epoch check; a `putAll` of the same key can upsert a small card in between, which the refusal's delete then removes. `putAll` itself never deletes a row another writer owns | `data/cache/CachedFeed.kt:237-249`, `:388-434` |
| 2 | The store cannot report a refusal: `write`/`writeAll` return `Unit` | `PersistentPayloadStore.kt:28-31` |

## Chosen approach

research.md: 6, option A, with the recommended answers to Q1-Q3 below.

- The store stops deleting on refusal and reports it (`write` returns whether it stored, `writeAll` the refused keys).
- `CachedFeed` takes an optional fallback `(V) -> V`. When the store refuses the full payload, it writes the fallback's payload under the same epoch ticket; when that is refused too, it deletes the row only while it still owns the key (`load`: `epochOf(key) == epoch` after the write; `putAll`: `settled == epoch`). Supersede writes keep their existing delete. Memory keeps the full value and its full `encodedLength`.
- `ContentCacheRepository` gives `itemFeed` a card-only fallback: `seasons` mapped to skeletons with `episodes = null`, `videos = null`, `detailsUpdatedAt = null`. Other feeds pass none (research.md: 3, "Other feeds and the limit").

Size M: persistence and caches is a risk area (`.flow/roles/analyst.md`); touches `PersistentPayloadStore.kt`, `CachedFeed.kt`, `ContentCacheRepository.kt` and every `PersistentPayloadStore` implementation in tests (`app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt`, `ItemDetailsRepositoryTest.kt:265`, `HomeInteractorCacheTest.kt:217`).

## Acceptance criteria

| ID | Given | When | Then | Checked by |
|---|---|---|---|---|
| AC-1 | a store holding a small row for a key | `write` or `writeAll` is given an over-limit payload for it | the call reports the key as refused and the old row is still there (the store no longer deletes) | unit, `PersistentPayloadStoreTest.kt` |
| AC-2 | `itemFeed` holding a details record over the limit in memory (seasons with episodes and files) | a list merge for that item runs (`mergeItems`) | the store holds a row for the key within the limit, whose decoded record has the card fields of the merge, season skeletons with `episodes == null`, `videos == null`, `detailsUpdatedAt == null`; `peek` in the same session still returns the full record | unit, `ContentCacheRepositoryTest.kt` over a store with a small limit |
| AC-3 | the state of AC-2 | the memory tier is dropped (new repository over the same store) and the list page naming the item is read | the card is returned from the store with no loader call; `observeItemDetails` for it calls its loader (details refetched) | unit, `ContentCacheRepositoryTest.kt` |
| AC-4 | the state of AC-3 with a loader that fails (offline) | the list page is read | the card is in the result (today it is dropped) | unit |
| AC-5 | a details `load` of an over-limit record and a `putAll` of a small card for the same key, the card upsert landing between the load's epoch check and its refused write (`FakePayloadStore.onWrite`) | both finish | the key has a row within the limit; no write deletes a row another writer owns | unit, `CachedFeedTest.kt` |
| AC-6 | a fallback that is itself over the limit | `put`, then a supersede `put` (as `markItemDetailsStale`) | an owned refusal leaves no row; a non-owning writer leaves the other writer's row; the supersede write persists its fallback or removes the row, never leaves the superseded payload | unit, `CachedFeedTest.kt` |
| AC-7 | the box with the fixed dev APK installed by install-preserve, online | the details of a series whose record encodes over 1 MiB are opened (one from the 4K «Все» grid, research.md: 3, evidence `docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md:10`), then the 4K «Все» grid is browsed, then `am force-stop` | a copy of `databases/puber.db` (with `-wal`/`-shm`) taken through `run-as com.kino.puber.stage` holds `content:v2:item:<id>` with `length(CAST(payload AS BLOB)) <= 1048576` and `"detailsUpdatedAt"` absent or null; logcat filtered to the app has no `Refused cached payload content:v2:item:<id>` line | observation on the box; only query results go into evidence, not the db copy |
| AC-8 | AC-7 after a cold start, online | the 4K «Все» grid is opened, then the series' details | the grid shows the card; the details screen loads and shows seasons and episodes | observation on the box |

The offline cold start is unit-only (AC-4): cutting the box's network drops adb and is not authorised.

## Risks

| Risk | Where |
|---|---|
| After eviction or restart the card-only row has `detailsUpdatedAt = null`: Details refetches before showing anything and throws offline, as today | `ContentCacheRepository.kt:197-200,229` |
| Memory and Room hold different shapes for one key; a reader of `seasons` beyond the count (hero label, `buildDuration`) sees skeletons after eviction | `core/ui/model/VideoItemUIMapper.kt:82-86,159-163` |
| The fallback write must reuse the original epoch ticket and generation check; a second write path outside them reopens the race of problem 2 | `CachedFeed.kt:99-195,474-487` |
| The check-then-delete window for an owned refusal stays, as for the existing `store.remove` calls | `CachedFeed.kt:249,422` |
| Two encodes for an oversized value on every list merge of that item while it is in memory | `CachedFeed.kt:235,397` |
| AC-7 needs a title whose record is over 1 MiB on the box; the 2.4 MB row was healed on read in the source run and its id was not recorded | evidence `2026-11-24-cached-payload-row-too-big-device.md:10` |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Where does the card-only fallback live? | A `CachedFeed` fallback hook supplied by `ContentCacheRepository` for `itemFeed` (research.md: 6, option A) | no |
| Q2 | Card-only shape: `seasons = null` or season skeletons? | Skeletons (`Season(id, number, title, episodes = null)`), `videos = null`, `detailsUpdatedAt = null`: keeps the hero "N сезонов" label after a cold start | yes (AC-2) |
| Q3 | How does the store report a refusal? | `write` returns a Boolean, `writeAll` the refused keys; the feed calls the fallback on a refusal, so the limit stays private to the store | no |
