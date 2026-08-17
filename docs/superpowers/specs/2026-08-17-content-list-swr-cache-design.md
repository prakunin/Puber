# Stale-while-revalidate cache for catalogue, watching and history screens

## Problem

Picking a tab in the side rail leaves the right-hand pane on skeletons for seconds. Measured
on the Fire TV (AFTKRT, build `1.7.5-dev-deploy`), from the D-pad OK press to real data:

| Surface | First visit after app start | Revisit within ~3 min | Revisit after ~7-9 min |
| --- | --- | --- | --- |
| History | 5.5 / 1.2 s | 0.4 s | 1.1 s |
| Movies | 3.3 / 4.1 s | 0.8 s | 1.9 s |
| Series | 4.2 / 2.8 s | 0.4 s | 2.1 s |
| 4K | 5.7 s | — | 1.8 s |
| TV shows | 4.5 / 2.9 s | 0.45 s | 2.6 s |
| Home | ~0.3 s rows, ~1 s hero | 0.3-0.5 s | 0.55 s |

Two runs per surface where two numbers are given; the spread between runs on identical code
is large enough that no single run supports a claim.

Home is the outlier because it is the only one of these screens that already renders from
`CachedFeed` over `PersistentPayloadStore`. The rest have three causes:

1. Catalogue tabs have no persistent cache at all. Their only cache is `firstPageCache` in
   `ContentListInteractor` — in memory, static in a `companion object` because the interactor
   itself is `scopedOf` and dies with the screen, TTL 3 minutes. That TTL is exactly the
   cliff visible above between a revisit at 3 minutes and one at 9.
2. Switching tabs disposes the outgoing tab's composition, so its Koin scope closes and its
   `SectionVM`s are rebuilt from `Loading` on return. The cache can save the round trip; it
   cannot save the skeleton.
3. The load competes with background traffic: `WatchStateSyncInteractor.syncHistory` reads
   `HISTORY_PAGES_PER_CHUNK = 15` history pages back to back, and `DetailsPrefetcher` holds
   two of five connections warming the focused card and its neighbours.

This spec addresses (1) for catalogue sections, the watching list and history. (2) and (3)
are separate work and are listed as non-goals below.

The earlier spec `2026-08-13-persistent-content-cache-design.md` recorded "no caching of
paginated catalogue lists" as a non-goal. This spec supersedes that one line, on the
strength of the measurements above, and nothing else in it.

## Goals

- Catalogue sections, the watching list and the first page of history render from local
  storage immediately, including after a cold start, then refresh in the background.
- Returning to a tab shows content, not skeletons, whenever anything readable is stored.
- A failed background refresh never replaces content already on screen with an error.
- The "show all" grid inherits the same behaviour without changes of its own.

## Non-goals

- Keeping a tab's composition or `SectionVM`s alive across a switch. Worth doing, separate
  change.
- Rebalancing `WatchStateSyncInteractor` or `DetailsPrefetcher` against foreground loads.
  Also worth doing, also separate.
- Caching pages beyond the first. Page 2 and onwards keep going to the network exactly as
  they do today.
- Offline mode, a freshness indicator, or any user-visible cache setting.

## Policy decisions

Settled before design, and the rest follows from them:

1. **Any readable cache is drawn immediately**, up to the existing 7-day
   `CachedFeed.HardCeiling`. A day-old row on screen beats a skeleton.
2. **After playback, cached first, refresh second.** History and the watching list may show
   the previous position for the moment it takes the revalidation to land, rather than
   returning to skeletons in the app's most frequent flow.
3. **TTL is respected.** A cache still fresh by its TTL means no network call at all on tab
   entry, so a fast switch costs nothing and competes with nothing — the same rule Home
   already follows.

## Design

### `ContentPageCache`

A new singleton in `data/cache/`, registered as `single` next to `PersistentPayloadStore`
and injected into the scoped interactors. It owns three `CachedFeed` instances:

| Surface | Value | Serializer | TTL |
| --- | --- | --- | --- |
| Catalogue section, incl. "show all" | `PaginatedResponse<Item>`, already filtered | `PaginatedResponse.serializer(Item.serializer())` | 30 min |
| Watching list | `List<Item>`, **unsorted** | `ListSerializer(Item.serializer())` | 2 min |
| History first page | `PaginatedResponse<History>` | `PaginatedResponse.serializer(History.serializer())` | 2 min |

Both API models are already `@Serializable`; no parallel model layer appears.

The singleton is not a convenience. `CachedFeed`'s memory tier and its in-flight
de-duplication are per instance, and `ContentListInteractor` is `scopedOf`: a feed held as
its field would lose the memory tier on every tab switch, and two screens showing the same
section would issue two requests for one key. This replaces the `companion object` static
`firstPageCache` with an object that can be substituted in tests.

The watching list is stored **before** sorting; `RecentlyPlayedOrder.sort` runs on each
emission after the read. Storing it sorted would freeze the recently-played order at write
time, which is the one thing that list exists to express.

Sections are stored **filtered** — the same value `firstPageCache` holds today, under the
same key: `config.id`, shortcut, type, shortcut types, sort, quality, genre, required genre
id, anime filter mode, `showAnime`, `hideWatched`. Every setting that decides what a page
contains is in the key, so flipping one cannot serve another's cache.

Keys are added to `CacheKeys` (`SectionPrefix`, `HistoryPrefix`, `WatchlistPrefix` and their
builders), keeping the whole key space in the one file that is meant to hold it.

`KinoPubConfig.CURRENT_API_DOMAIN` is dropped from the section key, where it appears today.
A domain switch goes through `ApiDomainInteractor.clearDomainSensitiveCaches`, which moves
`store.generation`, which `CachedFeed` already watches and answers by dropping both tiers.
Home relies on that same mechanism. Kept in the key, the domain only leaves dead rows behind.

### Catalogue sections and "show all"

`ContentListPagingVM.loadPage` splits in two. Everything after the response arrives — the
empty-page counters, the `keepWalking` decision, `replace`/`setNextPage` — moves into
`publish(response, isFirstPage)`. The first page then subscribes:

```kotlin
interactor.observeFirstPage(config, force): Flow<Cached<PaginatedResponse<Item>>>
```

and calls `publish` on each `Cached.Value`. Pages 2 and onwards keep calling
`interactor.loadPage(config, page)` unchanged.

Four consequences worth stating:

- The walk counters (`emptyPageChain`, `resumeRounds`, `publishedAnyItems`, `currentPage`)
  reset at the start of every first-page publication, not once per load. Otherwise the
  cached page and the fresh one add up into a single walk and the section believes it has
  passed twice as many empty pages as it has.
- `Cached.RefreshFailed` is logged and nothing more. The error path stays as it is today for
  the case that still deserves it: nothing cached and `load` throws, which reaches
  `errorHandlerGeneral`.
- Scroll position survives. `CachedFeed.load` is a one-shot flow per screen opening, not a
  live subscription: both emissions arrive within the first fraction of a second, so a
  revalidation cannot land a minute later and discard pages the user has scrolled into.
- The section still starts in `Loading`, and the goal is that nobody sees it: the state
  lasts until the first emission, which is a memory-tier hit within the process and a single
  Room read after a restart. The claim this design makes — and what the test asserts — is
  that no `Loading` appears *between* the cached emission and the fresh one.
- `force = true` — cache first, network guaranteed — is what the existing refresh signals
  map onto: `RetryClicked`, and returning from details or the player with a
  `ContentChangeSet`.

Because `ShowAllVM` pages the same section configs through the same interactor, it inherits
all of this without a change of its own.

### Watching

`FavoriteVM.loadData()` becomes a collection of `interactor.observeWatchlist()`, applying
`RecentlyPlayedOrder.sort` and updating view state on each emission. The details of the
first card load after the first emission and go through `ItemDetailsRepository`, which has
its own `CachedFeed`, so the side panel draws from cache too instead of showing a spinner.

### History

The most expensive of the three. `HistoryPageLoader.loadDepth()` reads several pages in a
row to fill the rendered depth, and `HistoryRuntimeState` (570 lines) runs a state machine
with reconciliation on top. Only the first page is served from cache; the remaining depth
loads from the network as it does now.

This is the last step of the work and carries an explicit decision point: if
stale-while-revalidate does not fit that state machine without restructuring it, the step
stops and the restructuring is discussed as its own change rather than absorbed here. Its
payoff is also the smallest — History is the fastest of the cold tabs measured, and its
1.2-5.5 s spread is explained by the background history sync rather than by page loading.

### Invalidation

The existing signals stop touching stored data and instead carry `force = true` into the
next read: returning with a `ContentChangeSet`, display-setting changes, a watch-state index
move, and a bookmark toggle through `SavedItemInteractor`. `CachedFeed.load(force = true)`
emits the cached value first and still goes to the network, which is policy decision 2
exactly, with no extra Room write and no new method on `CachedFeed`.

The watch-state index needs one carried-over rule. A filtered page is baked against an index
version, and the version cannot go in the key — as `ContentListInteractor` already
documents, that would leave a dead row behind on every write. The "last seen version" check
moves from `dropFirstPageCacheIfWatchStateMoved` into `ContentPageCache`, and its
consequence changes from clearing the cache to forcing the next read.

`invalidateFirstPageCache()` survives for its other job: clearing `freshPagers`, whose
`FreshSectionPager` instances hold pagination cursors that must still reset.

Logout and domain switches need nothing new, and not by luck: both routes —
`SessionExpiredHandler` in `App.kt` and `ApiDomainInteractor.clearDomainSensitiveCaches` —
call `store.clear()`, which empties the whole table and bumps `store.generation`. New key
prefixes are therefore covered by construction, and an in-flight revalidation that crosses
a wipe is withdrawn by the generation check `CachedFeed` already performs.

### Error handling

`RefreshFailed` is silent apart from a log. A throw from `load` with nothing cached follows
today's path into `errorHandlerGeneral`. A payload written by a build whose models differed
fails to decode, and `CachedFeed.readUsable` already drops the row rather than paying for
the failed decode on every read.

## Testing

Written test-first, against the existing suites: `CachedFeedTest`, `CacheKeysTest`,
`ContentListInteractorTest`, `SectionVMTest`, `ContentListVMTest`, plus the favorites and
history packages.

- `ContentPageCacheTest`, on injected time: TTL boundaries, the 7-day ceiling, and an index
  version move producing a forced read rather than a cleared entry.
- `SectionVMTest`: two emissions produce `Content(cached)` then `Content(fresh)` with **no
  intervening `Loading`**; `RefreshFailed` leaves content standing and raises no error;
  nothing cached plus a failure still errors as today; walk counters reset between
  emissions.
- Favorites: sorting is applied to every emission, and the stored value is unsorted.
- `ContentListInteractorTest`: updated for the removal of `firstPageCache`.
- Device pass: switching tabs shows no skeletons on a second visit, and D-pad focus does not
  move when the fresh emission replaces the cached one.

## Work order

1. `ContentPageCache`, cache keys and TTLs, with tests. Wired to nothing.
2. Catalogue sections. "Show all" comes with them.
3. Watching.
4. History, with the decision point above.
5. Verification on the device: the same screen-recording frame-diff protocol as the
   baseline, two runs per surface, compared against the table at the top of this document.

## Risks

- The payload table grows by roughly a megabyte (about 5 sections across 9 tabs at ~20 KB
  each), plus history and the watching list. Room handles that, and the 7-day ceiling prunes
  it.
- A stale "Fresh" row can be shown for a moment. Accepted by policy decision 1.
- History may not accommodate the change without restructuring its state machine. Contained
  by making it the last, separately decidable step.
