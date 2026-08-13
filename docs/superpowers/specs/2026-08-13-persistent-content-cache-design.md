# Persistent content cache with background revalidation

## Problem

Opening a title that is already being watched costs a network round trip, and so does
returning to the home screen. Nothing the user sees in those two places is volatile enough
to justify that.

Three concrete causes, none of which is "there is no cache":

1. `PlayerInteractor.saveWatchingTime()` calls `itemDetailsRepository.invalidate(id)` on
   every position write. The existing five-minute memory cache in `ItemDetailsRepository`
   therefore almost never hits for exactly the titles the user watches most.
2. `DetailsVM.loadData()` holds the screen in `Loading` until `isInWatchLaterFolder()`
   resolves. For a movie whose payload carries no inline bookmarks, that is always a call
   to `/items/{id}/bookmarks`, so a cached item still waits on the network before drawing.
3. `HomeVM.loadContentSections()` awaits all ten section requests before calling
   `publishSections()` once. The home screen is as slow as its slowest section, and a cold
   start shows a spinner until every one of them lands.

On top of that, all caching is in-memory, so a process restart pays full price.

## Goals

- The "Continue watching" row and every other home section render from local storage
  immediately, including after a cold start, then refresh in the background.
- A title's details screen renders from local storage immediately, then refreshes in the
  background.
- A failed background refresh never replaces content that is already on screen with an
  error.
- Cached content never outlives the session it belongs to.

## Non-goals

- No user-facing freshness indicator, no pull-to-refresh affordance, no cache settings.
- No caching of search results, paginated catalogue lists, or player streams.
- No offline mode. With an empty cache and no network the app fails exactly as it does now.

## Design

### Storage

One new Room entity, alongside the existing watch-state tables:

```kotlin
@Entity(tableName = "cached_payload")
data class CachedPayloadEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updatedAt: Long,
)
```

`payload` is JSON of the existing `@Serializable` API models — `Item`, `List<Item>`,
`List<KCollection>`. No parallel model layer is introduced.

The database goes from version 1 to 2 and keeps
`fallbackToDestructiveMigration(dropAllTables = true)`. The stance already recorded in
`PuberDatabase.kt` holds: the index rebuilds from a sync, the cache rebuilds from the
network, so an unmigratable schema costs freshness and nothing else. `exportSchema` stays
on, so a new schema JSON is committed with the change.

`PersistentPayloadStore` wraps the DAO with `read`, `write`, `remove`, `removeByPrefix`,
`clear`. It knows about strings and timestamps; serialization lives one level up.

### The `CachedFeed` primitive

`CachedFeed<V>` composes three tiers for a single key: the existing `TypedTtlCacheImpl` in
memory, `PersistentPayloadStore` on disk, and a suspend network loader.

```kotlin
sealed interface Cached<out V> {
    data class Value<V>(val value: V, val isStale: Boolean) : Cached<V>
    data class RefreshFailed(val error: Throwable) : Cached<Nothing>
}

fun load(force: Boolean = false): Flow<Cached<V>>

/** Keeps the stored value readable but guarantees the next `load()` revalidates it. */
fun markStale()

/** Drops the stored value outright, so the next `load()` has nothing to emit first. */
fun invalidate()
```

The flow emits at most twice, under these rules:

- A stored value (memory first, then disk) is emitted immediately, with `isStale` derived
  from `updatedAt` against the key's TTL.
- With nothing stored, the loader runs and the result is emitted once. The caller renders
  its own loading state meanwhile.
- When the emitted value was stale, or `force` is set, a background revalidation runs and
  emits the fresh value as the second emission.
- A loader failure *after* a value was emitted produces `RefreshFailed`; the emitted value
  stays on screen. A loader failure with nothing emitted makes the flow throw, so the
  existing `ErrorHandler` path is unchanged.
- Concurrent `load()` calls for one key share a single in-flight request. This reuses the
  flight de-duplication already in `TypedTtlCacheImpl` rather than adding a second
  mechanism.

Time is injected, mirroring the existing `nowNanos` parameter of `TypedTtlCacheImpl`, so
freshness is testable without waiting.

### Freshness policy

| Key | TTL |
| --- | --- |
| `home:continue_watching` | 2 minutes |
| all other `home:*` sections | 30 minutes |
| `item:<id>` | 10 minutes |
| `similar:<id>` | 30 minutes |

A hard ceiling of 7 days applies to every key: an entry older than that is treated as
absent, so a long absence produces a spinner rather than a week-old row. All values live in
one place in the cache module.

### Home screen

`HomeSectionsRepository` owns the `home:<section>` key space — one entry per section rather
than one blob, so a failing section cannot poison its neighbours and TTLs can differ.

A key corresponds to a *rendered* section, not to an API request. Two sections are built
from a pair of requests each: hot items merge `movie` and `serial` sorted by rating, and
fresh items merge them sorted by update time. That merge happens before the write, so
`home:hot` and `home:fresh` each store one already-merged, already-sorted list.

`HomeVM.loadContentSections()` changes from "await ten, publish once" to publishing as
sections land. `loadedSections` becomes a `HomeSectionType -> List<Item>` map; each section
is collected by its own coroutine inside the existing `supervisorScope`, and every emission
updates the map and calls `publishSections()`. Ordering is unchanged — the existing
`sortedBy { it.type.ordinal }` already handles out-of-order arrival. The state leaves
`Loading` on the first section to arrive, which with a warm disk cache is the first frame.

Hero items derive from the hot-items section and follow the same path.

`silentRefresh()` on resume passes `force = true` for the continue-watching section only;
the rest revalidate on their TTL.

TV focus is a real risk here: a row that appears while focus sits below it can steal or
displace focus. Rows are only ever replaced in place at their ordinal position — nothing is
inserted mid-list — but stable Compose keys per section must be confirmed by test and by a
manual pass on the device.

### Details screen

`ItemDetailsRepository` gains the disk tier under key `item:<id>`. Its existing one-shot
`getItemDetails` stays for the player, bookmarks and history call sites; `DetailsVM` moves
to a new streaming `observeItemDetails(id)`.

`PlayerInteractor.saveWatchingTime()` stops calling `invalidate(id)` and calls
`markStale(id)` instead: the entry stays readable and is merely guaranteed to revalidate on
the next read. Watch position is tracked separately by `WatchStateRepository`, so nothing on
screen regresses. True invalidation remains where the server's next answer is genuinely
unpredictable — the watched toggle and bookmark add/remove.

The bookmark-folder lookup leaves the critical path. `loadData()` renders content
immediately with `isInWatchlist` seeded from the item itself (`inWatchlist` for series, a
non-empty `bookmarks` list for movies), and a second coroutine resolves the authoritative
answer and patches the flag. If the user toggles the control before that patch arrives, the
patch is dropped; the existing `pendingMutations` bookkeeping is where that guard hangs.

`getSimilarItems` gains a cache entry under `similar:<id>`. It already loads off the
critical path, so this only removes the repeated request.

### Invalidation and privacy

The table holds one account's viewing history as cleartext JSON on the device — the same
class of data as `WatchStateEntity`, under the same rule: it must not outlive the session.

- `SessionExpiredHandler` in `App.kt` wipes the cache next to the existing
  `watchStateSyncInteractor.invalidate()` call on `SessionEvent.Unauthorized`.
- `ApiDomainInteractor.clearDomainSensitiveCaches()` clears the disk tier as well as the
  memory tier. Without this, switching domains shows the previous domain's catalogue.
- A revalidation already in flight during a wipe would otherwise write a row belonging to
  the previous session. The existing remedy is reused: a generation counter bumped before
  the wipe, with writes from a superseded generation discarded. The counter lives on the
  component that owns the wipe — the store — rather than on each `CachedFeed`, so a single
  bump covers every key at once.
- Only parsed models are serialized. No tokens and no credential-bearing URLs enter a
  payload.

## Testing

`CachedFeed`, with injected time: a fresh hit emits once; a stale hit emits cached then
fresh; an empty key emits once from the network; a loader failure after a cached emission
yields `RefreshFailed` and preserves the value; a loader failure with nothing cached
throws; concurrent loads share one request; an entry past the 7-day ceiling counts as
absent.

`HomeVM`: sections publish incrementally; one failing section leaves the others intact;
resume forces only the continue-watching section; ordinal ordering holds when sections
arrive out of order.

`DetailsVM`: content renders before the folder lookup resolves; a late patch is dropped
after the user has toggled; `RefreshFailed` produces a snackbar rather than the error
screen.

`PlayerInteractor`: `saveWatchingTime` marks stale without removing; the watched toggle
still removes.

Wipe behaviour: logout clears the table; a revalidation from a superseded generation does
not resurrect a row.

Suites that must stay green because they mock `ItemDetailsRepository`:
`DetailsInteractorTest`, `PlayerInteractorTest`, and the `HistoryVM*` family.

Manual verification on the device: after a force-stop, the continue-watching row is visible
before the old spinner would have cleared; after playing roughly thirty seconds and backing
out, reopening the card shows no spinner.
