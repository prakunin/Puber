# One Catalogue Carousel Per Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A catalogue tab shows exactly one carousel below the detail panel — never a slice of the next one — with a lighter section heading.

**Architecture:** Each section stops being two list items (heading, row) and becomes one item that fills the list viewport, so the "one block per screen" rule holds by construction rather than by heights that happen to add up. The card height and the heading style become catalogue-scoped values; the shared ones the rest of the app uses are left alone.

**Tech Stack:** Kotlin, Compose TV Material3.

**Spec:** `docs/superpowers/specs/2026-08-19-catalogue-single-carousel-design.md`

## Global Constraints

- File content and commit messages are English. Never add a `Co-Authored-By` trailer.
- Dependency versions come from `gradle/libs.versions.toml`; never hardcode. This plan adds no dependencies.
- **Scope is `ContentListScreen` only.** `HomeScreen` keeps its current row height and heading style.
- **Do not change `PuberTheme.Defaults.HorizontalVideoItemHeight`** (150 dp). It is shared with `VideoItemHorizontal.kt:61`, `SearchScreenContent.kt:286`, `HistoryScreenContent.kt:394` and `CollectionCard.kt:43`.
- **Do not override `titleLarge` in the theme.** It is shared with `HomeScreenContent.kt:262`, `SeekIndicator.kt:41`, `ResumeDialog.kt:129`, `EpisodesPanel.kt:207`, `PlayerSidePanel.kt:112`, `TvContextMenu.kt:127` and `VideoGridUIState.kt:134`.
- Content composables stay pure: state in, `onAction: (UIAction) -> Unit` out.
- Compile check: `./gradlew :app:compileDevDebugKotlin`
- Full check: `./gradlew testDevDebugUnitTest :app:detektAll`
- `HistoryVMTest.loadMore_isSerializedAndAppendsTheNextPage()` is a known pre-existing flake, unrelated to this work. Ignore it if it appears; do not fix it.
- The target device is 1920x1080 at density 320 — **960 x 540 dp**. The list viewport is half the height: **270 dp**.

## File Structure

| File | Responsibility | Task |
| --- | --- | --- |
| `core/ui/uikit/theme/Type.kt` | Gains the catalogue heading style | 1 |
| `core/ui/uikit/theme/PuberTheme.kt` | Gains the catalogue row height | 1 |
| `ui/feature/contentlist/content/SectionRow.kt` | Cards and the "show all" tile use the catalogue height | 1 |
| `ui/feature/contentlist/content/ShimmerSectionRow.kt` | Placeholder matches the real card height | 1 |
| `ui/feature/contentlist/content/ContentListScreenContent.kt` | Heading style; section and hero become full-height pages; bottom padding removed | 1, 2 |

Task 1 is cosmetic and low-risk. Task 2 is the structural change and carries every behavioural risk. Task 3 is the on-device tuning pass that decides the final numbers, and it must be run by someone with the TV.

---

### Task 1: Catalogue-scoped heading style and card height

Nothing here changes layout structure — only the two values the spec scopes to the catalogue, plus the call sites that read them.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/theme/Type.kt`
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/theme/PuberTheme.kt:13-20`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/SectionRow.kt:169-171`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ShimmerSectionRow.kt:42-43`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt:275`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `com.kino.puber.core.ui.uikit.theme.SectionTitleStyle: TextStyle` — top-level val in `Type.kt`
  - `PuberTheme.Defaults.CatalogueRowItemHeight: Dp` — 190.dp

There is no unit test in this task. It changes two constants and the call sites that read them; a test could only restate the values, and the spec settles the look on the device in Task 3.

- [ ] **Step 1: Add the heading style**

In `Type.kt`, after the `Typography` declaration, add:

```kotlin
/**
 * The heading above a catalogue carousel. Deliberately not `titleLarge`: that style is shared
 * with the home screen, the player and the context menu, and this heading needs to sit quietly
 * above the row rather than compete with the film titles on the cards.
 */
val SectionTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.15.sp,
)
```

`TextStyle`, `FontFamily`, `FontWeight` and `sp` are already imported in this file.

- [ ] **Step 2: Add the catalogue row height**

In `PuberTheme.kt`, inside `data object Defaults`, after `HorizontalVideoItemHeight`:

```kotlin
        /**
         * Card height for the catalogue rows only, where one section fills the list viewport.
         * The shared [HorizontalVideoItemHeight] stays at its smaller value for search, history,
         * collections and the shared horizontal card.
         */
        val CatalogueRowItemHeight = 190.dp
```

- [ ] **Step 3: Give the shared card an optional height**

`VideoItemHorizontal` hardcodes the shared constant at `VideoItemHorizontal.kt:60`, and it has eight callers across home, show-all, search, bookmarks, history, collections, the player's episode panel and the catalogue row. It therefore takes a parameter defaulting to today's value, so seven of those eight are untouched.

Its current signature is:

```kotlin
@Composable
fun VideoItemHorizontal(
    modifier: Modifier = Modifier,
    state: VideoItemUIState,
    onClick: () -> Unit,
    onContextMenu: (() -> Unit)? = null,
)
```

Change it to:

```kotlin
@Composable
fun VideoItemHorizontal(
    modifier: Modifier = Modifier,
    state: VideoItemUIState,
    onClick: () -> Unit,
    itemHeight: Dp = PuberTheme.Defaults.HorizontalVideoItemHeight,
    onContextMenu: (() -> Unit)? = null,
)
```

and at `:60` use the parameter instead of the constant:

```kotlin
            .height(itemHeight)
```

Add `import androidx.compose.ui.unit.Dp`. Every existing caller passes arguments by name, so none of them needs editing — confirm that with `grep -rn "VideoItemHorizontal(" app/src/main/java` before moving on, and say in your report if any caller turns out to be positional.

Then in `SectionRow.kt`, at the `VideoItemHorizontal(...)` call around `:151`, add:

```kotlin
                        itemHeight = PuberTheme.Defaults.CatalogueRowItemHeight,
```

- [ ] **Step 4: Match the "show all" tile**

Still in `SectionRow.kt`, the trailing tile at `:169-171` sizes itself from the shared constant and would end up shorter than the cards beside it:

```kotlin
                        Box(
                            modifier = Modifier
                                .height(PuberTheme.Defaults.CatalogueRowItemHeight)
                                .aspectRatio(PuberTheme.Defaults.HorizontalVideoItemAspectRatio),
                            contentAlignment = Alignment.Center,
                        ) {
```

- [ ] **Step 5: Match the shimmer**

In `ShimmerSectionRow.kt`, `ShimmerVideoItem` at `:40-48`:

```kotlin
            .height(PuberTheme.Defaults.CatalogueRowItemHeight)
```

This file is catalogue-only, so it follows the catalogue height. Leaving it at 150 dp would make the placeholder jump when real cards arrive.

- [ ] **Step 6: Apply the heading style**

In `ContentListScreenContent.kt`, the heading at `:270-276`:

```kotlin
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = title,
            style = SectionTitleStyle,
        )
```

Add `import com.kino.puber.core.ui.uikit.theme.SectionTitleStyle`. If `MaterialTheme` is then unused in the file, remove its import — detekt flags unused imports.

- [ ] **Step 7: Compile and run the full check**

Run: `./gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

Run: `./gradlew testDevDebugUnitTest :app:detektAll`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/theme/ \
        app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemHorizontal.kt \
        app/src/main/java/com/kino/puber/ui/feature/contentlist/content/
git commit -m "Give catalogue rows their own card height and heading style"
```

---

### Task 2: One section per screen

The structural change. A section stops being two list items and becomes one item sized to the viewport.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt:166-212` (list padding, hero) and `:255-305` (`sectionItem`)

**Interfaces:**
- Consumes: `SectionTitleStyle` and `PuberTheme.Defaults.CatalogueRowItemHeight` from Task 1.
- Produces: one list item per section, keyed `section_${config.id}` with `contentType = "section"`. The keys `title_${config.id}` and `content_${config.id}` no longer exist.

No unit test here either: this is layout, and `ContentListVM` is untouched. The behavioural risks are named in Step 5 and are checked on the device in Task 3.

- [ ] **Step 1: Merge the two items into one page**

Replace the whole body of `private fun LazyListScope.sectionItem(...)` — keep its parameter list exactly as it is — with:

```kotlin
    item(key = "section_${config.id}", contentType = "section") {
        val rememberedOnItemClick = remember(config.id) {
            { item: VideoItemUIState -> onAction(CommonAction.ItemSelected(item)) }
        }
        val rememberedOnItemFocused = remember(config.id) {
            { item: VideoItemUIState -> onAction(CommonAction.ItemFocused(item)) }
        }
        val rememberedOnSectionFocused = remember(index) {
            { onSectionFocused(index) }
        }
        val rememberedOnShowAll = remember(config.id, isLastSection) {
            if (isLastSection) {
                { onAction(ContentListAction.ShowAll(config)) }
            } else {
                null
            }
        }
        val row: @Composable () -> Unit = {
            SectionRowContent(
                state = sectionState,
                config = config,
                isTargetRow = isTargetRow,
                rowOrder = index,
                onItemClick = rememberedOnItemClick,
                onItemContextMenu = { onContextMenu(it, sectionVm) },
                onItemFocused = rememberedOnItemFocused,
                onSectionFocused = rememberedOnSectionFocused,
                onRetry = { sectionVm.onAction(CommonAction.RetryClicked) },
                onLoadMore = { sectionVm.onAction(CommonAction.LoadMore) },
                onShowAll = rememberedOnShowAll,
                onRowEmpty = onRowEmpty,
            )
        }

        if (sectionState is SectionState.Empty) {
            // No heading, and no page: a section with nothing in it must not own a screen.
            // It is still composed rather than skipped, because SectionRowContent carries the
            // LaunchedEffect that reports the row empty and hands focus to the nearest
            // non-empty one.
            row()
        } else {
            Column(modifier = Modifier.fillParentMaxHeight()) {
                val title = config.titleRes?.let { stringResource(it) } ?: config.title
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    text = title,
                    style = SectionTitleStyle,
                )
                Spacer(modifier = Modifier.height(8.dp))
                row()
            }
        }
    }
```

`Modifier.fillParentMaxHeight()` comes from `LazyItemScope`, which is the receiver inside `item { }` — it must sit on the `Column` at the item's top level, not on anything nested deeper.

Add imports: `androidx.compose.foundation.layout.Column`, `androidx.compose.runtime.Composable`. `Spacer`, `height`, `padding`, `fillMaxWidth`, `remember` and `stringResource` are already imported.

- [ ] **Step 2: Make the hero a page too**

In `heroItem` (`:186-212`), both branches get the full height. Replace the placeholder `Spacer` and add the modifier to the carousel:

```kotlin
            if (state.heroItems.isEmpty()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(),
                )
            } else {
                HeroCarousel(
                    items = state.heroItems,
                    onItemClick = { itemId ->
                        onAction(ContentListAction.HeroSelected(itemId))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(),
                    // D-pad up out of the top row lands here, inside the same focus group as the
                    // rows, so nothing else reports that the focused card is no longer focused.
                    onFocusedItemChanged = { onAction(ContentListAction.TrailerPreviewStopped) },
                )
            }
```

The `280.dp` height and its `height` import go away if nothing else in the file uses them.

- [ ] **Step 3: Drop the bottom content padding**

In `ContentListLayout`, remove the `contentPadding` argument from the `LazyColumn` at `:171`:

```kotlin
                    .focusRestorer()
                    .focusGroup(),
            ) {
```

It exists so the last row can scroll clear of the bottom edge. With page-sized items it only adds a blank screen after the last section. Remove the `PaddingValues` import if it becomes unused.

- [ ] **Step 4: Compile and run the full check**

Run: `./gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

Run: `./gradlew testDevDebugUnitTest :app:detektAll`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Write down what you could not verify**

In your report, state plainly that these three behaviours changed and are unverified by any test in this repo, because Task 3 is where they get checked:

1. `PreserveLazyListAnchorOnRootReturn` (`:120`) anchors the list by item index. Two items per section became one, so every index shifted.
2. `PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true)` (`:166`) treats an item as settled once it is fully visible. An item exactly the size of the viewport is the boundary case for that condition.
3. Focus handing off past an empty section now runs through a zero-height item rather than one with a visible heading.

Do not attempt to fix these speculatively. Report them.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt
git commit -m "Give each catalogue section a screen of its own"
```

---

### Task 3: Tune it on the television

**This task needs the TV and must be run by whoever has it — the device is the only instrument that can settle it.** The spec is explicit: 190 dp and 16 sp are a starting point, and the work is done when the screen looks light, not when those numbers are in the code.

**Files:**
- Modify (tuning only): `app/src/main/java/com/kino/puber/core/ui/uikit/theme/PuberTheme.kt`, `app/src/main/java/com/kino/puber/core/ui/uikit/theme/Type.kt`, `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt`

**Interfaces:**
- Consumes: everything from Tasks 1 and 2.
- Produces: the final values, and a verdict on the three risks from Task 2 Step 5.

- [ ] **Step 1: Install and open a catalogue tab**

```bash
make run DEVICE=<serial>
```

Then navigate to a catalogue tab (Фильмы) and take a screenshot:

```bash
adb -s <serial> exec-out screencap -p > /tmp/catalogue.png
```

A capture of roughly 8 KB means a video surface is on screen and the frame is blank — a trailer is playing over the panel. Move focus to a card without a trailer, or capture within the first two seconds of focus landing.

- [ ] **Step 2: Judge the three risks**

- Scroll down two sections, open a card with OK, press Back. The list must return to the section you left, not to the top.
- Arrow down through the sections. Each step must land on a whole section with no slice of a neighbour above or below.
- If scrolling between sections stutters or leaves a section part-way, try `parentFraction = 0f` on `PositionFocusedItemInLazyLayout` at `ContentListScreenContent.kt:166` — an item exactly the viewport's size is the boundary case for `keepFullyVisibleItemInPlace`.

- [ ] **Step 3: Run the focus traversal test on the device**

```bash
./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kino.puber.ui.feature.contentlist.content.SectionRowFocusTraversalTest
```

Expected: PASS. If it fails, the page layout broke D-pad traversal — that is a real regression, not a test to adjust.

- [ ] **Step 4: Tune the numbers with the user**

Show the screenshot. Adjust `CatalogueRowItemHeight`, `SectionTitleStyle`'s `fontSize`, and the 8 dp gap in `sectionItem` until the screen reads as light. Re-install and re-screenshot after each change.

The card width follows the height through the existing 16:9 ratio, so a taller card also means fewer cards across: at 190 dp roughly 2.6 fit in the usable width, against 3.3 today. If the row starts to feel empty, that trade is the reason.

- [ ] **Step 5: Commit the settled values**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/theme/ \
        app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt
git commit -m "Settle catalogue carousel sizing on the device"
```

---

## Out of scope

`HomeScreen`, which keeps its current row height and heading style. Changing the panel/list split. Snap-scrolling.
