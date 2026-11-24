# Design delta: catalogue-browse-all

## Governing stands

| Element | Stand | Covers it? |
|---|---|---|
| Browse entry above the first shelf (brief.md#AC-1, AC-2) | `docs/design/catalogue/` | No. The stand has no tab-level control. Stand update needed |
| One-line title over every Show all grid (brief.md#AC-6) | `docs/design/showall/` | No. The stand starts the grid at `padTop` 16 and has no header. Stand update needed |

Both screens have a stand, so `designing-a-screen` does not have to run before planning. The run must update both stands: README rows and an element in `index.src.html` with sliders for the numbers below, then `python3 docs/design/build.py catalogue showall`. The owner dials them at AC-11. Until then the numbers below are starting values, not agreed ones.

**The catalogue stand is stale.** Its "Agreed sizes" table (2026-08-26) no longer matches the code. 85bfd1d2 retuned the rows, and neither the README nor `index.src.html` was updated:

| | stand README | code at e4d75215 |
|---|---|---|
| panel / rows split | 67 % | 62 / 38 (`PuberTheme.kt:33-34`) |
| card height | 115 dp | 150 dp (`CatalogueRowItemHeight`) |
| section heading | 11 sp | 16 / 20 sp Medium (`Type.kt:50` `SectionTitleStyle`) |
| row padding / card gap | 0 / 10 dp | 16 / 16 dp (`SectionRow.kt` `RowHorizontalPadding`, `CardSpacing`) |

The stand update has to start from the code values first. Otherwise the entry gets dialled against a layout that no longer ships.

## Numbers that apply (dp at 1080p)

| What | Value | Source |
|---|---|---|
| Closed rail | 60 | `MainScreenComponent.kt:304`, measured on the box (showall README) |
| Catalogue rows viewport | 540 x 0.38 = 205.2 | `CatalogueContentWeight` |
| Section page | 30 air + 20 heading + 2 + 150 card = 202 | `ContentListScreenContent.kt` `SectionTitleTopPadding` / `SectionTitleToRow`, catalogue README "поле над заголовком 30", "заголовок → ряд 2" |
| Section heading side padding | 16 | `SectionTitleSidePadding` |
| Show all grid padding sides / top | 16 / 16 | showall README (agreed 2026-11-24), `ShowAllScreenContent.kt:48` |
| Grid tile height, row gap | 237, 16 | showall README "What that gives" (2 + 1 title lines reserved) |
| Tile focus scale | 1.05 (overhang 237 x 0.025 = 6 dp vertical) | showall README |
| Button label | 11 sp | content README "кегль кнопки 11" (only agreed button type on a stand) |

## Element A: browse entry (catalogue)

**Placement: inside the first section's page, in its 30 dp air band. It is not a list item of its own.** The band above the heading becomes `[4 top][24 button][2]` = 30. The heading and the cards of section 0 therefore sit at the same y as on every other page (title 30-50, cards 52-202). The page keeps its 202 dp, the one-section-per-page rule (`ContentListScreenContent.kt:149-152`) holds, and `sectionListItemIndex` keeps its value. A separate list item would cut about 27 dp off the shelf while the entry is focused (a 30 dp entry plus the 202 dp page in a 205.2 dp viewport), which is the half-row peek the page rule exists to stop.

| Slider (starting value) | Value |
|---|---|
| entry → panel (top air) | **4** dp |
| entry height | **24** dp |
| entry → section heading | **2** dp |
| label | **11 / 16** sp, Medium (`labelLarge` resized), 1 line |
| inner side padding | **12** dp |
| left edge | **16** dp from the content edge, the heading's `SectionTitleSidePadding` (76 dp = 152 px from the screen edge) |
| width | wraps the label; not stretched |
| colours, shape, focus scale | tv-material3 `Button` defaults, the same as the row's «Показать все» (`SectionRow.kt:187-205`) |

The budget check: focus scale 1.1 on 24 dp grows 1.2 dp at each edge, which fits inside 4 dp above and 2 dp below. The `LazyColumn` clips at its top, so a button of 30 dp (the content stand's height) would leave no air and get its focused top edge cut. 30 is not a safe slider maximum without taking air from the heading. Clamp the slider at 26.

Labels (unit-tested per brief.md#AC-1): «Все фильмы», «Все сериалы», «Все концерты», «Все док. фильмы», «Все док. сериалы», «Все ТВ-шоу», «Всё в 4К».

**Focus:**

| From | Key | To |
|---|---|---|
| tab opened | (none) | first card of the first shelf, as today. The entry is not the launch focus: a focused entry leaves the panel with no film |
| any card of the first shelf | Up | entry |
| entry | Down | the first shelf's remembered card (its `focusRestorer`), not the card under the entry |
| entry | Left | side rail |
| entry | Up / Right | stays (nothing focusable above; nothing to the right) |
| Show all grid opened from the entry | Back | entry (brief.md#AC-7) |

Focus visibility: the default `Button` focused state (light container with dark label, scale 1.1). Unfocused it stays a filled pill. Check on the box that an always-visible pill above the shelf does not outweigh the cards. If it does, the owner's fallback is a quiet unfocused container (transparent with an outline), and that becomes a stand switch, not a code guess.

While the entry is focused the panel keeps the last focused card. Whether the trailer preview should stop then is for the architect (`ContentListScreenContent.kt:189-198` only stops it when focus leaves the rows).

## Element B: Show all header

**A pinned header above the grid, not a grid item.** It stays when the grid scrolls, and catalogue-filters adds its controls to it (brief.md#Q2). It is not focusable in this run, so first focus stays on the first card (AC-6).

| Slider (starting value) | Value |
|---|---|
| header top | **12** dp |
| title style | `SectionTitleStyle` 16 / 20 sp Medium, default content colour. The same heading as the catalogue shelves |
| title left edge | **16** dp (= `GridContentPadding`, the edge of column 1) |
| lines | 1, ellipsis |
| header → grid (grid `contentPadding` top) | **8** dp. It must stay at or above the 6 dp tile focus overhang, or the grid's clip cuts the focused tile in row 1 |
| grid sides / bottom | 16 / 16, unchanged |

The budget check: tiles in row 1 start at 12 + 20 + 8 = 40 dp. Row 2 ends at 40 + 237 + 16 + 237 = 530 dp, plus the 6 dp focus overhang = 536, which is at most 540. Two full rows stay on screen at rest, as today (row 2 ends at 506 today).

Text: browse grid and «Все» shelf grid show the entry label («Все фильмы», ..., «Всё в 4К»). Other shelves show `<tab title> · <section title>` («Фильмы · Новинки», «4К · Популярные»), with the same " · " separator as the poster card's original-title line.

## Shared constants: parameterise, do not retune

| Constant | Where | Why |
|---|---|---|
| `SectionTitleTopPadding` 30 | `ContentListScreenContent.kt:174` | Every section uses it. Section 0 replaces it with a header slot whose parts (4 + 24 + 2) sum to it. The 30 stays the reference |
| `SectionListItem` | `ContentListScreenContent.kt:138` | Shared with `SectionRowFocusTraversalTest`. Add an optional slot above the heading; do not fork it |
| `SectionTitleStyle` | `Type.kt:50` | Also used by `EpisodesPanel.kt:223`. The grid header reuses it as is |
| `GridContentPadding` 16 | `ShowAllScreenContent.kt:48` | Split into sides/bottom 16 and top 8 under the header. Do not change the single value |
| `Button` look of «Показать все» | `SectionRow.kt:194` | The entry uses the same component with its own height and padding constants. The row button keeps its 40 dp default |

## Open

| ID | Question | For |
|---|---|---|
| D1 | Section 0 `Empty` collapses to nothing (`SectionListItem`, `ContentListScreenContent.kt:145-148`), so the entry would vanish with it. Starting answer: the band moves to the first non-empty section | architect |
| D2 | Filled or quiet unfocused entry | owner on the box (AC-11) |
| D3 | Re-base the catalogue stand on the code numbers before adding the entry | this run's stand update |
