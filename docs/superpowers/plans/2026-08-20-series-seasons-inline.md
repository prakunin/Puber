# Seasons on the Series Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a series shows one season at a time under its own hero, and the «Выбрать сезон» panel is
gone from that screen.

**Architecture:** `VideoGrid` — already the component that draws a season heading followed by its
episodes — gains an opt-in mode where a heading and its row are one list item exactly one viewport
tall, so the next season is never half on screen. The series' details page becomes a Column: the
hero over the still on top, that grid underneath at a fixed height. A film's page is unchanged.

**Tech Stack:** Kotlin, Jetpack Compose (foundation 1.12.0) with TV Material3, Koin, JUnit 5 +
MockK, Compose UI test.

## Global Constraints

- The design document is `docs/superpowers/specs/2026-08-20-series-seasons-inline-design.md`. Where
  this plan and the spec disagree, ask rather than choose.
- `./gradlew :app:compileDevDebugKotlin`, `:app:compileDevDebugAndroidTestKotlin`,
  `:app:testDevDebugUnitTest` and `:app:detektDevDebug` must all pass before any commit. The
  androidTest source set compiles separately from the unit tests — a change that breaks it will not
  show up in the other three.
- detekt enforces MagicNumber, ReturnCount (max 3) and LongMethod. Name every dp and fraction.
- `EpisodesPanel` and `VideoGrid`'s existing behaviour belong to the player screen too. Every new
  behaviour is opt-in and defaults to what the player already gets. Do not change the player.
- Do NOT run `connectedAndroidTest`: it uninstalls the app and wipes the television's login. The
  controller runs the device checks.
- Never `git add -A` or `git add .`. The repository owner works in this tree at the same time and
  has uncommitted changes in `app/build.gradle.kts`. Add the paths your task names.
- Never add Co-Authored-By lines to commit messages.

---

### Task 1: One season per viewport in `VideoGrid`

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridUIState.kt`
  (`VideoGrid`, `VideoGridContent`)

**Interfaces:**
- Produces: `VideoGrid(..., rowsFillViewport: Boolean = false)`. When true, each
  `Title` and the `Items` that follows it are drawn as **one** list item, that item fills the
  viewport height, and focus landing anywhere in a row scrolls that row's item to the top. When
  false — the player's case, and the default — nothing changes.

- [ ] **Step 1: Read what is there**

Read `VideoGridContent` in full before editing. It already keys items, tracks `rowOrders` for focus,
and positions the focused card. Your change must not disturb any of that in the default mode.

- [ ] **Step 2: Merge a heading with its row**

Before the `itemsIndexed` call, build the list the grid actually draws:

```kotlin
/** A season heading and the episodes under it, drawn as one item so the pair cannot be split. */
private data class GridSection(
    val title: VideoGridItemUIState.Title?,
    val items: VideoGridItemUIState.Items?,
)

private fun List<VideoGridItemUIState>.asSections(): List<GridSection> = buildList {
    var pendingTitle: VideoGridItemUIState.Title? = null
    this@asSections.forEach { entry ->
        when (entry) {
            is VideoGridItemUIState.Title -> {
                if (pendingTitle != null) add(GridSection(pendingTitle, items = null))
                pendingTitle = entry
            }
            is VideoGridItemUIState.Items -> {
                add(GridSection(pendingTitle, entry))
                pendingTitle = null
            }
        }
    }
    pendingTitle?.let { add(GridSection(it, items = null)) }
}
```

A heading with no episodes still gets an item, so a season the API returns empty does not swallow
the next season's heading.

- [ ] **Step 3: Draw sections when the flag is on**

Keep the existing `itemsIndexed(state.list)` branch exactly as it is for `rowsFillViewport = false`.
Add the second branch: `itemsIndexed(sections, key = { _, s -> "section_${s.items?.rowKey ?: s.title?.title}" })`,
each item wrapped in `Modifier.fillParentMaxHeight()`, drawing the title `Text` and then the row
with the same composables and the same callbacks the default branch uses. Do not duplicate the card
row itself — extract it into a private composable both branches call.

`rowOrders` must keep counting `Items` entries over `state.list`, not over the sections: it is what
the focus machinery reads, and its meaning has not changed.

- [ ] **Step 4: Land a focused row at the top**

Reuse the catalogue's helper rather than writing a second one. It is
`internal fun rememberFocusedListItemScroller(lazyListState: LazyListState): (Int) -> Unit` in
`app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt`.
Call it with the **section** index when a card in that section takes focus, and only when
`rowsFillViewport` is true.

- [ ] **Step 5: Verify**

Run: `./gradlew :app:compileDevDebugKotlin :app:testDevDebugUnitTest :app:detektDevDebug`
Expected: BUILD SUCCESSFUL. Nothing passes the new flag yet.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridUIState.kt
git commit -m "Let a grid draw one season per viewport"
```

---

### Task 2: The series loses its «Выбрать сезон» button

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt`
  (the button list, around line 166)
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenState.kt`
  (`DetailsAction`)
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapperTest.kt`

- [ ] **Step 1: Change the existing test first**

`map_seriesButtons_doNotIncludeWatchedActionOrDuplicateTrailerAction` asserts
`assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.SelectSeasonClicked))`.
Change that line to expect `0`, and run it to watch it fail. That is the test for this task; do not
add another one that says the same thing.

- [ ] **Step 2: Delete the button**

Remove the `DetailsButtonUIState.TextButton` whose action is `DetailsAction.SelectSeasonClicked`
from the series branch. Leave every other button alone.

- [ ] **Step 3: Delete the actions the screen no longer sends**

Remove `SelectSeasonClicked` and `CloseSeasonsPanel` from `DetailsAction`, and their branches in
`DetailsVM.onAction`, along with `showSeasonsPanel()` and `hideSeasonsPanel()`. Leave
`seasonsPanelVisible` on the state for now — Task 3 removes it, and removing it here would break the
screen mid-task.

`DetailsVM.onBackPressed` has a `state?.seasonsPanelVisible == true -> hideSeasonsPanel()` branch:
delete that branch, so Back goes straight to closing the screen.

The string `video_details_button_select_season` becomes unused. Leave it: the player may want it,
and an unused string costs nothing. Say in your report that you left it.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:compileDevDebugKotlin :app:compileDevDebugAndroidTestKotlin :app:testDevDebugUnitTest :app:detektDevDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/details app/src/test/kotlin/com/kino/puber/ui/feature/details
git commit -m "Drop the button that opened the seasons over everything"
```

---

### Task 3: The season list under the series hero

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenState.kt`
  (drop `seasonsPanelVisible`)
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt`

**Interfaces:**
- Consumes: `VideoGrid(rowsFillViewport = true)` from Task 1.

- [ ] **Step 1: Split the page for a series**

`DetailsMainPage` currently draws a `Box` holding the media and the hero column over it. For a
series — `state.episodes != null` — it becomes:

```kotlin
Column(modifier = modifier) {
    Box(modifier = Modifier.fillMaxWidth().weight(1F)) {
        VideoDetailsMedia(modifier = Modifier.fillMaxSize(), ...)
        HeroColumn(...)
    }
    VideoGrid(
        modifier = Modifier
            .fillMaxWidth()
            .height(SEASON_AREA_HEIGHT),
        state = episodes,   // the non-null value the `state.episodes != null` branch was taken on
        rowsFillViewport = true,
        initialFocusedItemId = state.initialEpisodeFocusId ?: state.currentEpisode?.id,
        onItemClick = { episode -> onAction(DetailsAction.EpisodeSelected(episode)) },
        onItemContextMenu = onEpisodeContextMenu,
    )
}
```

with `private val SEASON_AREA_HEIGHT = 234.dp`, commented: the heading and one card, which is what
leaves the hero its 306 dp on a 540 dp screen.

A film keeps exactly the `Box` it has now. Do not put a film through the Column branch.

- [ ] **Step 2: Take the panel off this screen**

In `DetailsScreenContent`, delete the `EpisodesPanel(...)` call, `seasonsPanelFocusRequester` and
the `LaunchedEffect` that focuses it. Delete `seasonsPanelVisible` from `DetailsScreenState.Content`
and every read of it — including `SelfScrollingText`'s `enabled` argument, which keeps its other two
conditions. `EpisodesPanel` itself stays in the player's package, untouched.

`DetailsVM.startTrailerPreview` reads `seasonsPanelVisible` too; drop that condition.

- [ ] **Step 3: Verify**

Run all four gates. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/details
git commit -m "Put the seasons under the series hero"
```

---

### Task 4: Down from the last season

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridUIState.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt`

The hero column carries
`onDirectionKey(Key.DirectionDown, enabled = hasSimilarItems, onKey = onNextPageRequested)`. On a
series the hero is no longer the bottom of the page, so the season list has to be the one that lets
the key through — and only when there is no season below.

The grid already knows which row has focus. `VideoGridContent` holds
`gridFocus: VideoGridFocusState`, whose `rowFocus.focusedRowKey` is the `rowKey` of the focused row
(`FocusTargetState.kt:278`, `:317`). Compare it with the last `Items` entry's `rowKey` and the
question is answered without guessing from scroll offsets.

**Interfaces:**
- Produces: `VideoGrid(..., onDownFromLastRow: (() -> Unit)? = null)`. Null — the player's case and
  the default — changes nothing.

- [ ] **Step 1: Let the grid report the key**

In `VideoGridContent`, on the `Box` that already wraps the `LazyColumn`:

```kotlin
val lastRowKey = remember(state.list) {
    state.list.filterIsInstance<VideoGridItemUIState.Items>().lastOrNull()?.rowKey
}
```

and on that `Box`'s modifier:

```kotlin
.onPreviewKeyEvent { event ->
    val handler = onDownFromLastRow
    val isDownPress = event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown
    val atLastRow = lastRowKey != null && gridFocus.rowFocus.focusedRowKey == lastRowKey
    if (handler != null && isDownPress && atLastRow) {
        handler()
        true
    } else {
        false
    }
}
```

Returning false everywhere else leaves the list's own downward movement exactly as it is.

- [ ] **Step 2: Wire it on the series page**

In `DetailsMainPage`'s series branch, pass
`onDownFromLastRow = onNextPageRequested.takeIf { hasSimilarItems }`. The hero column keeps its own
handler for films, where the hero is still the bottom of the page.

- [ ] **Step 3: Test it**

In `DetailsScreenContentTest`, add a series fixture with two seasons and one similar item. Assert
that DOWN from an episode of the first season puts an episode of the second season on screen, and
that DOWN again shows the similar item. Model it on `downFromTheButtonsReachesTheSimilarItems`,
already in that file. Use a short description so the auto-scroll never starts, and give the episodes
distinct titles so `onNodeWithText` can tell the seasons apart.

- [ ] **Step 4: Verify**

Run all four gates. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridUIState.kt app/src/main/java/com/kino/puber/ui/feature/details app/src/androidTest
git commit -m "Reach the page below from the last season"
```

---

## Notes for the controller

- The television check is yours. `puber://content/items/86536` is a two-season series;
  `puber://content/items/126301` is a film and must be unchanged.
- `adb exec-out screencap` blanks while a trailer plays. Turn the auto-trailer off in
  `shared_prefs/navigation_preferences.xml` via `run-as` for the check, and put it back afterwards.
- Watch for: a season row that is half visible; the hero jumping when a season changes; DOWN
  stranding focus at the last season; and the description scrolling while a season list has focus.
