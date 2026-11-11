# Research: grid-focus-restore-after-back

Base e9184b79. Researcher, 2026-11-11. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/` or `.flow/`.

## 1. Request

> Other grid screens restored after a root push and Back may focus the first visible card instead of the card that opened Details (ShowAll got a local fix)

Log scope notes (`state.md`): extend the Show all last-resort grid focus (fallback C) to the live path; the CollectionDetail grid likely also shifts focus one row up when the side rail closes with RIGHT (unverified).

## 2. Understanding

A root push (Details, CollectionDetail) removes the tab host's composition. After Back the tab subtree is composed again from saved state (`rememberSaveable`, lazy scroll state), but nothing Compose keeps in memory, such as a `focusRestorer`'s saved child, comes back with it, and the launch-focus flag is restored as already used. So a grid restores its card only if it actively asks for focus from a saved memory. Show all does this with `GridFocusMemory`, Bookmarks with the shared `rememberReconciledItemFocus`, and History with a ViewModel-held focus key. Collections and CollectionDetail have no active restore, so by code reading they show the reported symptom. Search clears its query and results on every new composition, so it has no card to return to. On the side rail, CollectionDetail is a root screen outside the drawer, so the rail-close concern in the Log cannot occur there. The Show all fallback-C extension is a separate small change in the same helper.

## 3. Current state in code

### Mechanisms

- Root push: `core/ui/navigation/component/FlowComponent.kt:745-765` sends a `RootPuberScreen` to the root router. The tab subtree state survives in the tab host's `SaveableStateHolder` (`docs/architecture/navigation.md` Tabs; run tab-stack-lost-on-root-push). The composition is disposed.
- After Back nothing restores tab focus. `RestoreTabContentFocusEffect` (`FlowComponent.kt:649-664`) fires only when the *tab* stack shrinks. `RootFlowReturnEffect` (`:277-294`) only bumps `rootFocusRestoreVersion` and reconciles anchors. Anchors exist only for `LazyListState` (`PreserveLazyListAnchorOnRootReturn`, `:436-475`). `rememberFocusRequesterOnLaunch` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:51-65`) restores "already requested" from saved state and does not fire again.
- Device fact (tab-stack-lost-on-root-push `qa-report.md` AC-2, round 1): with no active restore, Show all focus landed on "the first card of the first partially visible row". That is the default focus-enter result.
- Rail close: `ContentFocusHandoff.kt:116-131` calls `restoreFocusedChild()` on the tab box each frame and, after 5 grace frames, a blind `requestFocus()`. Mechanism detail: showall-rail-close-focus-shift `research.md` §3.
- Shared active-restore helper: `core/ui/uikit/component/moviesList/FocusTargetState.kt:40-126` (`rememberReconciledItemFocus`). It keeps the id in `rememberSaveable`, requests focus on a fresh composition (`:234-239`), follows list changes (`:192-220`), answers rail handoffs (`:273-293`) and redirects the first focus of a composition (`:142-170`). It is typed to `List<VideoItemUIState>`.

### Grid inventory (`LazyVerticalGrid` in `:app`)

| Screen | Host / opens Details by | Focus memory | After root push + Back (code reading) | After rail close RIGHT | Fixed? |
|---|---|---|---|---|---|
| Show all `ui/feature/showall/content/ShowAllScreenContent.kt:104` | Tab-local (from ContentList); root push | `GridFocusMemory` `:151-251`: saveable id + index, `pendingEntry` restore with a 3-frame retry, neighbour on reload, fallback C `:226` | Opener card (device PASS, tab-stack-lost-on-root-push qa round 2; hardening qa) | Same card via `onHandoff` `:234-241` (95fd3a8e; device PASS) | Yes (b0b1740b, a50c4b2f, 95fd3a8e) |
| Bookmarks `ui/feature/bookmarks/component/BookmarksScreenContent.kt:112` | Tab root; root push (`BookmarksVM.kt:68-70`) | `rememberReconciledItemFocus` `:73-79`, requester on the target card `:136-147`, `focusRestorer(itemFocus.focusRequester)` `:121`, `saveFocusedChild()` on click `:130` | Likely the opener: the fresh composition requests the saved id (`FocusTargetState.kt:234-239`). Not seen on a device | Likely the same card (`RestoreItemFocusOnTabReentryEffect`). Not seen on a device | Has the shared pattern |
| History `ui/feature/history/component/HistoryScreenContent.kt:278` | Tab root; root push (`HistoryVM.kt:511-514`) | VM `focusKey` updated on every card focus (`HistoryVM.kt:441-445`); card `LaunchedEffect(requestFocus, contentFocusActive)` (`HistoryCard.kt:51-56`); scroll-to-target `HistoryScreenContent.kt:219-231`; grid `focusRestorer()` `:283` | Likely the opener (the VM survives; the card effect fires on a fresh composition). Not seen on a device | Same card (device, hardening qa "Base comparison") | Has its own pattern |
| Collections `ui/feature/collections/component/CollectionsScreenContent.kt:61` | Tab root; opens CollectionDetail by root push (`CollectionsVM.kt:42`, `CollectionDetailScreen.kt:39`) | Saveable **index** `:58`, requester on that index `:75-88`, `focusRestorer(savedItemFocusRequester)` `:69`; no request effect | Probably the first visible card: nothing requests the saved card, and the fallback acts only when focus enters through the grid's `onEnter`. Unverified | Probably the same card through the grid's `focusRestorer`, which saves on a D-pad exit. Unverified | No |
| CollectionDetail `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:63` | **Root** screen (`CollectionDetailScreen.kt:35-39`), opened from Home and Collections; Details pushed on the root flow | None: no grid state, focus requester, restorer or memory (`:63-78`). Scroll survives through the grid's default saveable state | Probably the first visible card. Unverified | Cannot happen: the drawer and `LocalContentFocusHandoff` exist only in `MainScreenComponent.kt:101-141`; the defaults are `null` / `true` (`ContentFocusHandoff.kt:13`, `FocusOnLaunchRequester.kt:30`) | No |
| Search `ui/feature/search/content/SearchScreenContent.kt:264` | Tab root (`MainUIMapper.kt:97`), also a root push through the Search hotkey (`AppRemoteHotkeyHandler.kt:26-30`); Details by root push (`SearchVM.kt:112-118`) | Grid `focusRestorer()` `:269`; query in plain `remember` `:72` | No card to restore: every new composition sends `ScreenEntered` (`SearchScreen.kt:53-57`), so `resetSearch()` (`SearchVM.kt:46,51-56`) clears the query and results and the input takes focus (`SearchScreenContent.kt:79-87`). By design since 8e2a6007 ("Every new visit starts as a new search") | Probably the same card through the grid's `focusRestorer`. Unverified | N/A: no state to restore |

Notes:
- Actor-mode Search (`ScreensImpl.kt:49-55`) has no production caller (`grep actorItems` finds only tests and the definition), so its Back path is unreachable.
- VideoGrid screens (Favorites, EpisodeSchedule, the player episodes panel) are `LazyColumn`s of rows on `rememberReconciledItemFocus`, not `LazyVerticalGrid`. They are already covered, and `EpisodeSchedulePushBackFocusTest` exists.
- Collections calls `onLoadMore()` from composition (`CollectionsScreenContent.kt:72-74`). Its memory is index-based, so after a reload the saved index may point at a different collection.
- CollectionDetail also has no first-entry focus request (`CollectionDetailScreenContent.kt:46-87`). Where focus lands on first entry is unverified.

### Show all fallback C (live path)

- `ShowAllScreenContent.kt:219-227`: `reconcile()` re-targets when the focused card leaves the list while in the grid (`focusedCardLeft`, `:221`). If all 3 attempts fail, the grid fallback runs only `if (pendingEntry && contentFocusActive())` (`:226`). On the live path focus can then be left nowhere. The decision to keep C entry-only and record the live case here is in showall-focus-restore-hardening `state.md` (Design Q1).

### Tests

| Area | Tests |
|---|---|
| Show all | `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt` (JVM seam); `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`, `ShowAllRailCloseFocusTest.kt` |
| Home → CollectionDetail → Back | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/home/component/HomeCollectionBackFocusTest.kt` (Home side only) |
| History | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/history/component/HistoryScreenContentTest.kt`: edge, menu and paging focus; no recomposition-restore case |
| Search | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/search/content/SearchScreenContentTest.kt` (input → first result, actor mode) |
| Bookmarks, Collections, CollectionDetail | No focus tests. `app/src/test/.../collections/detail/CollectionDetailScreenTest.kt` only asserts that it is a `RootPuberScreen` |
| Rail harness to reuse | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:185-250` |

## 4. Related work

| Run | Stage | Relation |
|---|---|---|
| tab-stack-lost-on-root-push | done (db16ba9d) | Origin of this idea (design-architect D9, open Q2; brief Q1). AC-2 device evidence of the first-visible-card landing. 6b47873d was squashed into b0b1740b on master |
| showall-focus-restore-hardening | done (55bb5bfd) | Added fallback C (a50c4b2f); Design Q1 deferred the live-path C to this run |
| showall-rail-close-focus-shift | done (2438f9c8) | `onHandoff` (95fd3a8e); Q3 deferred the CollectionDetail rail case here. Its `research.md` §3 holds the handoff/default-enter analysis |
| side-rail-focus-handoff | done | Designed `RestoreItemFocusOnTabReentryEffect`, `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md` |
| showall-first-entry-rail-focus | idea | Same file, `ShowAllScreenContent.kt`; sequence after this run if fallback C changes |
| showall-entry-restore-foreign-landing | idea | Same `reconcile()` gate (`:222`); a live-path C change touches the same lines |
| c2a81dd6 (2026-08-18) | commit | Made CollectionDetail a root screen so Home's anchor restore works |
| 8e2a6007 (2026-08-18) | commit | Introduced Search's reset on every composition |

Duplicate: none.

## 5. External findings

- `FocusRestorer.kt` (androidx-main, fetched 2026-11-11; the project uses Compose BOM 2026.08.00, ui 1.12.0 per the showall-rail-close-focus-shift research; whether the tag matches main is unverified). `saveFocusedChild()` runs from `onExit` and stores the focused child's `compositeKeyHash`, also registering it in `LocalSaveableStateRegistry` under `"pfc"+hash`. `restoreFocusedChild()` falls back to that registry entry. The fallback requester runs only inside `onEnter` when the restore fails. Whether a direct or default-enter request on an ancestor invokes a nested grid's `onEnter` is not shown in that file (unverified). So a saved card hash may in principle survive the root push through the tab holder's registry. No code path calls `restoreFocusedChild()` after a root Back (§3), so this does not change the analysis. https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusRestorer.kt
- Default enter, the scroll focus group and `restoreFocusedChild` recursion: sources and the reasoning in showall-rail-close-focus-shift `research.md` §5, not re-fetched.

## 6. Options

**A. Adopt existing active-restore helpers per screen (recommended).**
- CollectionDetail: wire `rememberReconciledItemFocus(rowKey = "collection_${id}", items, isTargetRow = true, requestAfterFrame = true)` with the target-card requester and `focusRestorer(itemFocus.focusRequester)`, as Bookmarks does (`BookmarksScreenContent.kt:73-79,118-147`). This also gives a first-entry focus on the first card.
- Collections: replace the passive index with an id-keyed memory plus an entry request. The shared helper takes `VideoItemUIState` only, so either a small local memory or a widened helper is needed.
- Show all: run fallback C on the live path too (`:226`), with a JVM case in `GridFocusMemoryTest`.
- Pros: no navigation or contract change; uses patterns already proven on devices.
- Cons: three screens, each needing a device check; Collections needs an id-based memory of its own.
- Size **M** (TV focus risk area, three screens).

**B. Promote `GridFocusMemory` to a shared, id-generic grid helper** in `core/ui/uikit/component/` and use it in Show all, Collections and CollectionDetail.
- Pros: one grid restore pattern (entry, reload neighbour, handoff, fallback C) and one JVM seam.
- Cons: it moves a helper hardened over three runs. Its handoff and `contentFocusActive` parts do nothing on a root screen. Bookmarks and History stay on other patterns, so the codebase keeps three mechanisms.
- Size **M**.

**C. Generic root-return restore in `FlowComponent`.** Register `LazyGridState` anchors and a focused-key callback in `RootAnchorCaptureRegistry` next to `PreserveLazyListAnchorOnRootReturn`, and have each grid opt in.
- Pros: symmetric with Home and ContentList; it covers any future grid.
- Cons: it touches the shared navigation contract (`FlowComponent.kt:364-475`) and still needs per-grid opt-in and card requesters.
- Size **M-L**.

Recommendation: **A**, preceded by a device baseline on e9184b79 (Collections, CollectionDetail, Bookmarks, History: card → Details or CollectionDetail → Back, and the rail close for the tab grids). The baseline confirms which screens actually show the symptom before code changes.

## 7. Risks

- **Code reading only.** Apart from Show all and History's rail close, no grid in the table has been checked on a device. Bookmarks and History could still misbehave, for example if a resume refresh replaces items in the first frames. The baseline is the guard.
- **Focus stealing.** An entry request must not fire while a dialog or the rail owns focus. Bookmarks and History gate on `LocalContentFocusActive`. CollectionDetail has no rail, but its context menu dialog (`CollectionDetailScreenContent.kt:81-85`) must not lose focus to a late request.
- **Double restore.** Adding a request effect next to an existing `focusRestorer` (Collections `:69`) creates two mechanisms. This is the race class the Show all runs removed (showall-rail-close-focus-shift research §7).
- **Index vs id.** Collections' index memory survives `onLoadMore` appends but not a reorder or reload.
- **Same-file conflicts** in `ShowAllScreenContent.kt` with showall-first-entry-rail-focus and showall-entry-restore-foreign-landing.
- **Device verification required** (AGENTS.md: TV focus, D-pad, navigation). CollectionDetail must be reached from both Home and Collections.

## 8. Open questions

1. **Which screens are in scope?** Recommended: Collections and CollectionDetail, plus a device baseline on Bookmarks and History. Fix Bookmarks or History only if the baseline shows the symptom.
2. **Fix shape: A (per-screen existing helpers), B (shared `GridFocusMemory`) or C (`FlowComponent` grid anchors)?** Recommended: **A**.
3. **Include the Show all live-path fallback C?** Recommended: **yes**. It is a one-condition change at `ShowAllScreenContent.kt:226` with a JVM case, and the hardening run deferred it to this run.
4. **Search clears its query and results after Details → Back (`SearchScreen.kt:53-57`). Is that wanted?** Recommended: out of scope here. Record a separate idea, since it is a product behaviour, not focus. Device behaviour is unverified.
5. **Drop the CollectionDetail rail-close concern from the Log?** Recommended: **yes**. CollectionDetail is a root screen with no drawer (§3 inventory). Confirm on the device during the baseline.

## 9. Sources

Files: `AGENTS.md`; `docs/architecture/navigation.md`, `docs/architecture/ui-and-compose.md`; `.flow/config.yaml`; `ui/feature/showall/content/ShowAllScreenContent.kt`; `ui/feature/bookmarks/component/BookmarksScreenContent.kt`, `ui/feature/bookmarks/vm/BookmarksVM.kt`; `ui/feature/collections/component/CollectionsScreenContent.kt`, `ui/feature/collections/vm/CollectionsVM.kt`, `ui/feature/collections/model/CollectionsViewState.kt`; `ui/feature/collections/detail/CollectionDetailScreen.kt`, `.../detail/component/CollectionDetailScreenContent.kt`; `ui/feature/history/component/HistoryScreenContent.kt`, `HistoryCard.kt`, `ui/feature/history/vm/HistoryVM.kt`; `ui/feature/search/SearchScreen.kt`, `.../content/SearchScreenContent.kt`, `.../vm/SearchVM.kt`; `ui/ScreensImpl.kt`; `ui/feature/main/component/MainScreenComponent.kt`, `ui/feature/main/model/MainUIMapper.kt`, `ui/feature/main/vm/MainVM.kt`; `core/ui/navigation/component/FlowComponent.kt`; `core/ui/navigation/AppRemoteHotkeyHandler.kt`; `core/ui/uikit/component/moviesList/FocusTargetState.kt`, `VideoGridUIState.kt`; `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`; `core/ui/uikit/component/drawer/ContentFocusHandoff.kt`; `data/preferences/NavigationPreferencesRepository.kt`; the tests listed in §3.

Commits: b0b1740b, 6b47873d (pre-squash), a50c4b2f, 95fd3a8e, 2438f9c8, 55bb5bfd, db16ba9d, c2a81dd6, 8e2a6007.

Runs: grid-focus-restore-after-back, tab-stack-lost-on-root-push (state, brief, design-architect, qa-report), showall-focus-restore-hardening (state), showall-rail-close-focus-shift (state, research), showall-first-entry-rail-focus, showall-entry-restore-foreign-landing, side-rail-focus-handoff.

URLs: https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusRestorer.kt (fetched 2026-11-11).
