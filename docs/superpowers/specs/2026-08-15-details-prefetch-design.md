# Focus-driven details prefetch

## Problem

Opening a card costs a round trip. `DetailsVM` starts `observeItemDetails` when the screen is
created, so the user watches a spinner for as long as the API takes — every time, on every card,
even though the app knew which card was focused seconds earlier.

The information needed to avoid that is already on screen. To press OK the user must first move
focus onto a card, and focus usually rests there for a moment before the press. That pause is
enough to fetch the details nobody has asked for yet.

Focus also telegraphs the *next* card: from any card the D-pad can only reach four others — left,
right, up, down. A card the focus is about to reach is a card whose details are about to be worth
having.

## Goals

- Pressing OK on a card the user has been sitting on opens the details screen without a network
  wait.
- Moving to an adjacent card and pressing OK quickly is also covered, as far as it can be.
- Fast travel through a row costs nothing. Holding the D-pad must not issue a request per card.
- No user-visible trace: prefetching never reports an error, never shows a spinner, never changes
  what is on screen.

## Non-goals

- **Images.** Coil already holds the posters of cards that are on screen, and the four neighbours
  almost always are. Only row edges would benefit, which does not pay for the extra machinery.
- **Pagination.** Fetching the next page as focus nears the end of a row is a separate concern with
  its own trigger and its own budget.
- **Rows that are not composed.** `LazyColumn` does not compose rows far off screen, so a downward
  neighbour in a row that does not exist yet has no candidate. The user cannot reach that row in one
  press either.
- **The hero carousel.** It is not a row of cards and does not register, so moving up out of the
  first row yields no candidate. It can be added later without changing anything here.

## Design

### The prefetcher

A singleton, `DetailsPrefetcher`, in `domain/interactor/prefetch/`, with its own coroutine scope.
Not screen-scoped: focus survives moves between tabs, and the work it schedules should not be tied
to one screen's lifetime.

```kotlin
class DetailsPrefetcher(
    private val details: ItemDetailsRepository,
    private val foreground: AppForegroundState,
    private val focusedDwell: Duration = 250.milliseconds,
    private val neighbourDwell: Duration = 750.milliseconds,
    private val recentlyWarmedWindow: Duration = 60.seconds,
    private val maxConcurrent: Int = 2,
) {
    /** Opaque identity of one composed list surface. */
    class SurfaceId internal constructor()

    /** Focus has landed on [itemId]; [neighbours] is where it may go next, likeliest first. */
    fun onFocused(surfaceId: SurfaceId, itemId: Int, neighbours: List<Int>)

    /** The list surface went away; its scheduled work is dropped if it is still active. */
    fun onSurfaceGone(surfaceId: SurfaceId)
}
```

The prefetcher knows nothing about layout. It receives an ordered list of candidates and decides
only *when* and *how many*. That keeps the whole policy in one testable place, in the same shape as
`WatchStateSyncInteractor`.

Warming goes through the existing `ItemDetailsRepository`, which is a Koin `single` holding one
`CachedFeed` for details. This is what makes the feature work at all: what the prefetcher writes is
exactly what `DetailsVM` later reads.

The repository gets an explicit cache-only entry point:

```kotlin
suspend fun warmItemDetails(id: Int)
```

Both `warmItemDetails` and `observeItemDetails` collect the same private `CachedFeed.load` path, so
they share fresh values and in-flight requests. The difference is the consumer-side effect:
`observeItemDetails` records a non-stale `Cached.Value` in `WatchStateRepository`, while
`warmItemDetails` only fills or revalidates the details cache. This moves the existing
`recordFromServer` call out of the network loader itself. It is important that recording happens on
the observing path rather than only when its loader runs: if Details joins a prefetch already in
flight, it must still record the value it receives.

To keep that record correctly ordered against watch-state sync and user mutations, `Cached.Value`
also carries the value's original `updatedAt`. `CachedFeed` retains that timestamp in both its
memory tier and persistent tier, and a fresh network result is stamped once when accepted.
`observeItemDetails` passes this timestamp as `observedAt`; it must not stamp an older cached item
with the time at which Details happened to open. Existing `Cached.Value` consumers otherwise keep
the same value/stale semantics.

Consequently a prefetch cannot tick `WatchStateRepository.settledChanges`, redraw watched badges,
or re-page a list with `hideWatched` enabled. Reading the warmed value on Details preserves the
existing watch-state behaviour.

The content-list focus preview also reads this path through
`getItemDetailsCacheOnly(id): Item`. Its previous private `detailedItemsCache` is removed: otherwise
the preview and prefetch issue the same details request into two unrelated caches, and the value
already drawn beside the focused card is invisible to `DetailsVM`. Preview, prefetch, and Details
now share one cached value and one in-flight request. Cache-only preview and prefetch reads have no
watch-state side effect; normal reads by Details, Favorites, player, or another domain consumer
preserve the existing recording behaviour.

#### One logical cache, two storage tiers

`CachedFeed` is the sole coordinator of both storage tiers. Memory and Room are not independent
caches with separate policies:

1. A normal read checks the in-memory value first.
2. A miss reads Room and promotes a fresh payload into memory.
3. A stale Room payload is emitted for stale-while-revalidate, then the API is consulted through the
   same per-key single-flight coordinator.
4. A network result is stamped once, persisted to Room, and retained in memory.

The original `updatedAt` is authoritative in both tiers. Promotion from Room gives memory only the
*remaining* portion of the TTL; it never restarts the TTL at promotion time. Consequently an item
cannot be fresh in memory after the same item has become stale in Room. `markStale`, per-key
invalidation, namespace invalidation, and store-generation changes evict or supersede memory and
Room as one operation. Results from requests that began before an invalidation are rejected rather
than allowed to repopulate either tier. The coordinator checks the key, namespace, and store
generation around every suspending persistent read. Invalidation places barriers on both sides of
its database mutation, so a read that began during that mutation is rejected too. A forced refresh
atomically reserves its single-flight slot and bypasses any completed memory value; promotion from
Room cannot satisfy it while the Room read is suspended.

All full-details consumers — prefetch, content-list preview, Favorites, Details, and player/domain
callers — enter through `ItemDetailsRepository`. They choose only whether reading may publish
watch-state; they do not choose a cache tier or freshness policy. Catalogue card summaries, Coil
images, similar-items payloads, and pagination remain separate data domains and are intentionally
not folded into the full-details namespace.

`AppForegroundState`, added for the watch-state sync, is reused unchanged. No new warm starts while
the app is off screen. A request that was already in flight is allowed to finish under the same rule
as a request whose card has since lost focus.

### Policy

**Two dwell thresholds, both measured from the moment focus landed.** At `focusedDwell` the focused
card is warmed; at `neighbourDwell` the neighbours are, in the order given. A focus change cancels
all not-yet-started work for the previous position, including its focused-card warm. Travelling
through a row therefore issues nothing, because the user never stops long enough to cross the first
threshold.

**At most `maxConcurrent` warms in flight**, counting the focused card as one of them. OkHttp allows
five connections per host; the rest is left to the requests the screen actually needs.

**Two levels of deduplication:** ids currently in flight, and ids warmed within the last
`recentlyWarmedWindow` (60 seconds). Without the second, rocking focus left and right re-warms the
same two cards indefinitely. The window is well under `CacheTtl.ItemDetails`, so it suppresses
repeats without ever standing in for the cache's own freshness rule.

An id enters the recently-warmed set only after `warmItemDetails` completes with a usable value. A
failed initial load does not suppress a later attempt. A failed revalidation with a stored value does,
because that value remains usable and the cache has already applied its own freshness policy.

**Stale entries are refreshed.** The shared details load does not touch the network when the cached
entry is fresh, and revalidates when it is not, which is the behaviour wanted here.

### Neighbour resolution

The eligible card surfaces — title rows on the home screen, content-list rows, and the favorites
`VideoGrid` — are the same shape: a vertical list of horizontal rows. The grid is not a
`LazyVerticalGrid`; it already keeps an ordered list of rows and looks up a row's position by key.

Registration is explicit about navigation semantics. Only a row whose cards open `DetailsScreen`
participates. The Home Collections row is not registered, and `VideoGrid` defaults prefetch off so
the player episode grid, whose ids are episode ids, cannot accidentally call the item-details
endpoint. Favorites opts its `VideoGrid` in. Content-list title rows are always eligible.

A row knows its own items and nothing else, so vertical neighbours have to come from somewhere else.
A small screen-scoped registry holds them:

```kotlin
// core/ui/uikit/component/moviesList/
internal class FocusNeighbourhood {
    fun register(rowOrder: Int, rowKey: String, itemIds: List<Int>)
    fun unregister(rowKey: String)
    /** Null when the row/item is not registered; an empty list is a registered item with no neighbour. */
    fun neighboursOf(rowKey: String, itemId: Int): List<Int>?
}

internal class DetailsPrefetchSurfaceState(
    val id: DetailsPrefetcher.SurfaceId,
    val neighbourhood: FocusNeighbourhood,
    private val prefetcher: DetailsPrefetcher,
) {
    fun onItemFocused(rowKey: String, itemId: Int) {
        val neighbours = neighbourhood.neighboursOf(rowKey, itemId) ?: return
        prefetcher.onFocused(id, itemId, neighbours)
    }
}

internal val LocalDetailsPrefetchSurface =
    compositionLocalOf<DetailsPrefetchSurfaceState?> { null }
```

`DetailsPrefetchSurface` remembers a `FocusNeighbourhood` and a
`DetailsPrefetcher.SurfaceId`, provides their `DetailsPrefetchSurfaceState` through a
`CompositionLocal`, and disposes the surface id when it leaves composition. Eligible rows register
themselves in a
`DisposableEffect(rowOrder, rowKey, items)` and remove themselves on dispose. Home passes an
eligibility flag derived from the section type; the reusable `VideoGrid` takes
`detailsPrefetchEnabled: Boolean = false` and Favorites opts in. No prefetch callback is added to
the public UI action flow.

The registry retains absolute row order, including gaps left by ineligible or uncomposed rows. A
vertical candidate is produced only for `rowOrder - 1` or `rowOrder + 1`; it never skips a
Collections row, an error/loading row, or another unregistered row and pretends that a farther row
is one D-pad press away.

Candidates are ordered by how likely the next press is to go there:

1. right — `index + 1`, the dominant movement inside a row
2. left — `index - 1`
3. down — the same index in the next row
4. up — the same index in the previous row

When the adjacent row is shorter, the index is clamped to its last item. This is a prediction, not a
reimplementation of Compose focus search: independent rows can have different scroll offsets, so
the spatially nearest item may differ. Prefetch correctness does not depend on the prediction being
exact.

The integration point already exists: `onItemFocused` on `ReconciledItemFocusState`, used by these
rows. It calls `LocalDetailsPrefetchSurface.current?.onItemFocused(rowKey, itemId)`; without an
opted-in surface and registered row it is a no-op. No new callback is introduced in the UI action
flow.

### Errors

Prefetching is work the user did not ask for, so it may not report anything: no snackbar, no error
state, no touching `ViewState`. Failures are swallowed with `runCatchingCancellable` and logged.

`CachedFeed` helps here — with a stored value present, a failed refresh arrives as
`Cached.RefreshFailed` rather than an exception. Both are ignored.

A background 401 cannot eject the user spuriously: `SessionEvent.Unauthorized` is emitted only when
the token *refresh* fails, not on a single unauthorized response. At worst a prefetch discovers an
already-dead session slightly earlier than the user would have.

### Cancellation and lifecycle

- A change of focus cancels the *scheduled* neighbours of the previous position; they have stopped
  being a prediction. It also cancels a focused-card warm that has not started yet.
- A warm already in flight is left to finish. It is cheap, and `CachedFeed` deduplicates in-flight
  loads by key, so a user pressing OK joins it instead of issuing a second request. Cancelling it
  would sometimes throw away precisely the work this feature exists to do.
- `onSurfaceGone(surfaceId)` drops scheduled work only if that id is still the active surface. An
  outgoing tab or navigation transition therefore cannot dispose after a new surface receives
  focus and erase the new surface's schedule.
- The provider's `DisposableEffect` calls `onSurfaceGone(surfaceId)` when the whole list surface
  leaves composition — including on a move to the details screen or the player. Individual row
  disposal only unregisters that row.
- No new warm starts while the app is off screen; already-started network work may finish.

## Testing

`DetailsPrefetcherTest`, on virtual time, in the shape of `WatchStateSyncInteractorTest`:

- travelling through a row issues no request
- after `focusedDwell` only the focused card is warmed
- after `neighbourDwell` the neighbours are warmed, in the given order
- a change of focus drops every not-yet-started warm for the previous position
- focusing the same card again does not warm it twice
- a failed initial warm can be attempted again
- a failed stale refresh with a usable stored value is recently warmed
- `maxConcurrent` is never exceeded
- no warm starts while the app is off screen, while an in-flight warm may finish
- disposing an old surface does not cancel the active surface's schedule
- a failed warm does not escape

`FocusNeighbourhoodTest`, plain, without Compose:

- candidate order
- the index clamps to the last item of a shorter adjacent row
- an unregistered row contributes no candidate
- a gap in absolute row order is not skipped
- `unregister` removes a row

`ItemDetailsRepositoryTest` additionally proves that:

- `warmItemDetails` and `observeItemDetails` share an in-flight request and cached value
- `warmItemDetails` does not write to `WatchStateRepository`
- `getItemDetailsCacheOnly` returns that shared value without writing to `WatchStateRepository`
- an observer joining a cache-only warm still records the non-stale value it receives, using the
  value's original `updatedAt`

`CachedFeedTest` proves the tier contract: a fresh memory hit does not read Room, a fresh Room value
is promoted to memory, promotion preserves the original expiry rather than granting a new TTL, a
forced refresh cannot be satisfied by a racing promotion, and reads crossing key, namespace, or
generation invalidation cannot resurrect the removed payload. `TypedTtlCacheTest` keeps the
promotion and reload primitives honest: promotion cannot replace a completed value or supersede a
newer load already in flight, while reload atomically reserves the key and bypasses completed data.

`ContentListInteractorTest` proves that focused-card preview delegates to
`getItemDetailsCacheOnly` and does not call the details endpoint through a private cache.

Not covered by unit tests: the final Compose wiring. That is verified by a smoke run on a device,
checking that an eligible Home/content-list/Favorites row registers, that Collections and player
episodes do not, and that a downward neighbour is actually produced.

The Detekt baseline stays empty.
