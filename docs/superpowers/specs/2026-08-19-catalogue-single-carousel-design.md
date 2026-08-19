# One carousel per screen, and a lighter section title

Status: approved design, pending implementation
Date: 2026-08-19
Scope: `ContentListScreen` only — the catalogue tabs. `HomeScreen` is out of scope.

## Problem

Open a catalogue tab and the area below the detail panel shows one carousel plus a slice of
the next one. The numbers say why. The device is 1920x1080 at density 320, so the screen is
960x540 dp. `ContentListScreenContent.kt:122-146` splits it with `DetailsWeight = 1` and
`ContentWeight = 1`, giving the section list 270 dp. One section is a `titleLarge` heading
(~30 dp with padding) over a 150 dp card row (`SectionRow.kt:170`) — about 185 dp. Two of
those do not fit in 270 dp, and one and a half do.

The section heading is also heavier than it needs to be: `MaterialTheme.typography.titleLarge`,
which the app never overrides — the block is commented out at `Type.kt:28-34`, so it falls
through to TV Material3's 22 sp default.

## Decisions

| Question | Decision |
| --- | --- |
| How to fit exactly one carousel | The section becomes a page: one section fills the list viewport |
| Panel/list split | Unchanged, 1:1 — the cards grow instead of the panel |
| Card height | ~190 dp, tuned on the device; the look to hit is "light", not packed |
| Section title | Same Roboto, 16 sp Medium instead of 22 sp Normal |
| Hero carousel | Also a full-height page, so the rule has no exceptions |
| Other screens | Untouched |

## Design

### 1. The section is a page

`ContentListScreenContent.sectionItem` currently emits two list items per section,
`title_${id}` and `content_${id}` (`:227-268`). They merge into one `section_${id}` — a
`Column` carrying `Modifier.fillParentMaxHeight()`, with the heading above the row. `heroItem`
(`:186-212`) gets the same modifier.

Binding the block to the viewport rather than to a sum of heights is the whole point: the
guarantee then survives any later change to the font, the padding, or the card size. Picking
heights that happen to add up to 270 dp would be undone by the very typography change this
same spec makes.

The list's `contentPadding = PaddingValues(bottom = HorizontalVideoItemHeight)` (`:171`) goes
away. It exists so the last row can scroll clear of the bottom edge; with page-sized items it
only adds a blank screen after the last section.

### 2. Empty sections

`SectionState.Empty` hides the cards but not the heading (`SectionRow.kt:74`), so an empty
section currently shows a stray title. At one section per screen that becomes a whole screen
holding nothing but a heading.

An empty section is therefore emitted **without** the heading and **without**
`fillParentMaxHeight`, collapsing to no height. It is still emitted, not skipped:
`SectionRowContent` carries the `LaunchedEffect` that fires `onRowEmpty`
(`SectionRow.kt:65-69`), which is how focus hands off to the nearest non-empty row. Dropping
the composable would break that handoff.

### 3. Typography

A named style lives beside `Typography` in `Type.kt` and is used at the one call site,
`ContentListScreenContent.kt:275`:

```kotlin
val SectionTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.15.sp,
)
```

`titleLarge` is **not** overridden. It is shared with `HomeScreenContent.kt:262`,
`SeekIndicator.kt:41`, `ResumeDialog.kt:129`, `EpisodesPanel.kt:207`, `PlayerSidePanel.kt:112`,
`TvContextMenu.kt:127` and `VideoGridUIState.kt:134`; changing it in the theme would move all
of them.

### 4. Card height

A new `PuberTheme.Defaults.CatalogueRowItemHeight = 190.dp`, read only by the catalogue row
(`SectionRow.kt:170`) and its shimmer (`ShimmerSectionRow.kt:42`) so the placeholder does not
jump against the real cards. Width follows from the existing 16:9 ratio — about 338 dp, so
roughly 2.6 cards across the usable width instead of today's 3.3.

The shared `HorizontalVideoItemHeight` stays at 150 dp for `VideoItemHorizontal.kt:61`,
`SearchScreenContent.kt:286`, `HistoryScreenContent.kt:394` and `CollectionCard.kt:43`.

Page arithmetic: 270 dp less a ~20 dp heading, an 8 dp gap and a 190 dp row leaves ~50 dp of
air at the bottom of each page. That slack is the "light" look, and it is also the headroom a
focused card needs when it scales.

### 5. What this risks

Three things change behaviour that tests in this repo do not cover, and each is checked on the
device:

- **Restored scroll position.** `PreserveLazyListAnchorOnRootReturn` (`:120`) anchors by item
  index. Merging two items into one shifts every index, so returning from the details screen
  must be re-checked.
- **Scrolling between pages.** `PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true)`
  (`:166`) treats an item as settled when it is fully visible. An item exactly the size of the
  viewport is the boundary case for that test; `parentFraction = 0f` may be needed.
- **Focus handoff past an empty section**, per §2.

### 6. Testing

No ViewModel behaviour changes, so this spec adds no unit tests — there is nothing they could
assert that would not simply restate the layout code.

`app/src/androidTest/.../contentlist/content/SectionRowFocusTraversalTest.kt` already covers
D-pad traversal in this package and is run on the device.

The rest is a visual judgement and is settled by looking: build, install on the TV, screenshot,
and tune. **190 dp and 16 sp are a starting point, not a result.** The spec is complete when
the screen looks light, not when those two numbers are in the code.

## Out of scope

`HomeScreen`, which keeps its current row height and heading style — the two screens will
differ until someone decides they should not. Changing the panel/list split. Snap-scrolling.
