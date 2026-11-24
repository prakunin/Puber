# Design delta: catalogue-filters

## Governing stand

| Element | Stand | Covers it? |
|---|---|---|
| «Фильтры», «Сброс» and the summary in `ShowAllHeader` (brief.md#AC-1, AC-3, AC-6) | `docs/design/showall/` | Partly. The stand has the header title (section «catalogue-browse-all starting values») but no controls. Stand update needed |
| Filter side panel (brief.md#AC-4, AC-5, AC-10) | none | No stand draws a side panel over the grid. The nearest surface is the player settings panel (`PlayerSidePanel.kt`), which is code only: `docs/design/player-controls/README.md` «Still open» says its 420 dp cap was never dialled. Stand update needed |
| Filtered empty state (brief.md#AC-12) | `docs/design/showall/` | No. Stand update needed |

The screen has a stand, so `designing-a-screen` does not have to run before planning (same as catalogue-browse-all). The run adds a «catalogue-filters» scene to `docs/design/showall/index.src.html` with a slider for each number below, a README section «catalogue-filters starting values», and rebuilds with `python3 docs/design/build.py showall`. The owner dials it at brief.md#AC-13. Until then every number below is a starting value.

The header title numbers (top 12, `SectionTitleStyle` 16 / 20 sp, header → grid 8) are still starting values: catalogue-browse-all shipped without the owner dialling them on the box. This run moves them (below), so the stand's header section is updated in the same edit.

## Numbers that apply (dp at 1080p, 1 dp = 2 px)

| What | Value | Source |
|---|---|---|
| Closed rail | 60 | showall README «Ground truth», `MainScreenComponent.kt:304` |
| Grid side / bottom padding | 16 / 16 | showall README (agreed 2026-11-24), `ShowAllScreenContent.kt:49` |
| Tile height, row gap | 237, 16 | showall README «What that gives» |
| Tile focus scale, overhang | 1.05, 6 dp vertical | showall README |
| Header title | `SectionTitleStyle` 16 / 20 sp Medium, left 16, 1 line, ellipsis | showall README «catalogue-browse-all starting values», `ShowAllHeader.kt:17` |
| Header → grid | 8 (must stay ≥ 6, the tile overhang) | same |
| Compact button | 24 high, 12 side padding, label 11 / 16 sp Medium, tv-material3 `Button` defaults, focus scale 1.1 | catalogue README «Browse entry «Все …»», `BrowseAllEntry.kt:29-31` |
| Panel row, title, section header, panel width and padding | see Element B | `PlayerSidePanel.kt` (code, not a stand) |
| Dialog scrim | 62 % scrim | `TvDialogOverlay.kt:17`, the card context menu on the same grid |

## Element A: header controls (browse grids only)

One line: `[title] 16 [Фильтры] 8 [Сброс] 12 [summary]`, all on the left edge. The title is fixed for the screen's life, so «Фильтры» never moves while the screen is open; «Сброс» and the summary appear together and push nothing that is focusable. Nothing focusable sits left of «Фильтры», so Left goes to the rail (brief.md#AC-3).

| Slider (starting value) | Value |
|---|---|
| header top | **10** dp (was 12) |
| header row height | **24** dp, title centred in it: the title's centre stays at 22 dp, where it is today |
| header → grid | **8** dp, unchanged |
| title → «Фильтры» | **16** dp |
| «Фильтры» → «Сброс» | **8** dp |
| «Сброс» → summary | **12** dp |
| «Фильтры» | compact button, **filled** (Button defaults); label only, no icon (switch «иконка» = Phosphor Funnel 15 dp, the content stand's icon size) |
| «Сброс» | compact button, **outlined** (OutlinedButton defaults, the catalogue stand's «контур» switch); switch to filled |
| summary | **11 / 16** sp Regular, onSurfaceVariant #CAC4D0, 1 line. Parts joined by « · », as in the original-title line: genre first, then sort only when it is not the default (`по обновлению ↓`). «Драма · по году ↓», «по рейтингу ↑», «Драма» |
| row on shelf grids | the same **24** dp row with the title only, so row 1 sits at the same y on every grid |

What that gives: row 1 starts at 10 + 24 + 8 = 42 dp (40 today); row 2 ends at 42 + 237 + 16 + 237 = 532, 538 with the overhang, inside 540. A focused button (scale 1.1) reaches 35.2 dp; a focused row-1 tile reaches up to 36 dp. They do not overlap. A header top of 12 gives a 540 bottom with no spare, so clamp the slider at 11.

Switch «порядок»: `[Фильтры] [summary] [Сброс]` shows the alternative where «Сброс» moves with the summary's length; it starts off.

## Element B: filter panel

A right-hand panel inside `TvDialogOverlay(contentAlignment = CenterEnd)`, built like the player settings panel: a root page with two doors, each opening a page of values.

| Page | Rows |
|---|---|
| root, title «Фильтры» | «Сортировка» (trailing: «по обновлению ↓»), «Жанр» (trailing: «Все жанры» or the genre) |
| «Сортировка» | section «Поле»: по обновлению, по дате добавления, по году, по рейтингу, по просмотрам; section «Порядок»: по убыванию, по возрастанию |
| «Жанр» | «Все жанры», then the genres; «Аниме» absent per brief.md#AC-9; while loading one row with a 20 dp progress indicator; on failure a read-only row «Не удалось загрузить жанры» and a focusable «Повторить» |

| Slider (starting value) | Value | Source |
|---|---|---|
| width | **240 – 420** dp, wraps the widest row | `PanelMinWidth` / `PanelMaxWidth` |
| max height | **500** dp (540 − 2 × 20), vertically centred; a longer page scrolls inside | new |
| corner (start side only) | **20** dp | `PanelCornerRadius` |
| padding start / top / end / bottom | **20 / 20 / 20 / 16** | `PanelPadding*` |
| material | `playerGlass(Strong)`, elevation 20 | `PlayerSidePanel.kt:150` |
| title | **14 / 18** sp SemiBold, 12 dp below | `PlayerSidePanel.kt:168` |
| section header | **11 / 14** sp SemiBold, letterSpacing 0.8, onSurfaceVariant 78 % | `PlayerPanelSectionHeader` |
| row | min **40** dp, padding 12 / 6, radius 12, label **14 / 18** sp Medium, trailing **13** sp at 76 % | `PlayerPanelItem` |
| selected value | primary 16 % container + check 18 dp primary | `PlayerPanelItem` |
| scrim | **62 %** flat (`TvDialogOverlay`, as the card menu on this grid); switch «градиент» = the player's horizontal gradient 0.52 → 42 % | `TvDialogOverlay.kt:17`, `PlayerSidePanel.kt:66-67` |

What that gives: «Сортировка» is 18 + 12 + 2 × 26 + 7 × 40 + 36 = 398 dp, so it fits without scrolling; «Жанр» (about 30 rows) scrolls. Whether the glass made for video reads well over posters under a 62 % scrim is the owner's call on the box (open item 2).

## Element C: filtered empty state

Centred in the grid area: the existing empty text, then 12 dp, then a compact outlined «Сброс». Text: «Ничего не найдено. Активен фильтр: <summary>» (strings for the architect). Without a filter the empty state stays as it is.

## Focus

| From | Key | To |
|---|---|---|
| grid opened | (none) | first card, as today (brief.md#AC-1) |
| any row-1 card | Up | «Фильтры», pinned with `focusProperties { up = … }`: geometric search from columns 3-6 would land on «Сброс» or nowhere |
| row 2+ card | Up | the card above, unchanged |
| «Фильтры» | Down | the row-1 card focused last (the grid's remembered child), else the first card |
| «Фильтры» | Left | side rail (brief.md#AC-3) |
| «Фильтры» | Right | «Сброс» if shown, else stays |
| «Фильтры» | Up | stays |
| «Сброс» | Left / Down / Up / Right | «Фильтры» / as «Фильтры» Down / stays / stays |
| «Сброс» | OK | the button leaves; focus goes to «Фильтры», never to the rail |
| «Фильтры» | OK | panel, root, first row «Сортировка» (brief.md#AC-4) |
| panel, any page | Up / Down | rows of the page; Left / Right are trapped (`TvDialogOverlay`) |
| root door | OK | its page, focus on the selected value |
| value row | OK | selects it (check moves), focus stays |
| sub-page | Back | root, focus on the door it came from |
| root | Back | panel closes, focus on «Фильтры» (brief.md#AC-4) |
| after a close that changed the query | (re-page) | focus stays on «Фильтры» while the list reloads; when the new list lands, focus moves to the first card (owner decision, state.md architect Q2). If the list lands empty, focus stays on «Фильтры» and Down reaches the empty-state «Сброс» |
| filtered empty state | Down from «Фильтры» | empty-state «Сброс» |

Focus visibility: header buttons use the Button focused state (onSurface container, inverseOnSurface #313033 label, scale 1.1); panel rows use `PlayerPanelItem`'s focused container, onSurface at 30 %. The selected row and the focused row must look different (primary 16 % vs onSurface 30 %); on the stand they meet when the focused row is the selected one, and there the check icon carries the selection. The always-visible filled «Фильтры» pill above the posters is checked on the box: if it outweighs the cards, the fallback is the «контур» switch, not a code guess.

## Shared constants: parameterise, do not retune in place

| Constant | Lives in | Also drawn by | What to do |
|---|---|---|---|
| `EntryHeight` 24, `EntryHorizontalPadding` 12, label 11 / 16 sp | `BrowseAllEntry.kt:29-31` (private) | catalogue tab entry | Move to one shared compact-button metric or component in `core/ui/uikit`; the entry and both header buttons read it |
| `PanelMinWidth`, `PanelMaxWidth`, `PanelCornerRadius`, `PanelPadding*`, `PlayerPanelItem`, `PlayerPanelSectionHeader`, `playerGlass` | `ui/feature/player/component/PlayerSidePanel.kt`, `PlayerGlass.kt` | player settings, About, Episodes panels | Lift the panel chrome and rows into `core/ui/uikit` with the values as parameters; the player passes today's values. The player's `AnimatedVisibility` and gradient scrim stay in the player |
| `DialogScrimAlpha` 0.62 | `TvDialogOverlay.kt:17` | every context menu | Do not change; a different scrim for the panel is a parameter |
| `HeaderPadding` top 12 | `ShowAllHeader.kt:15` | Show all only | Change to 10 here, and in the stand README |
| `GridTopPadding` 8 | `ShowAllScreenContent.kt:50` | Show all only | Unchanged |

## Open

1. Placement of «Сброс» relative to the summary (switch «порядок»).
2. Glass material over the poster grid vs a plain surface: the material was built for video; the owner judges on the box.
3. Value OK behaviour: stays on the page (start, the same on both pages) or returns to root after a genre pick (saves a press). Owner on the stand.
4. «Фильтры» icon: off at start.
