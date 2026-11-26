# Design delta: catalogue-filters-country-year-status

## Governing stand

`docs/design/showall/` governs the change: section «catalogue-filters starting values (2026-11-24)» (`docs/design/showall/README.md:120-187`) draws the header, the panel and its two doors. It has no «Страна», «Год», «Статус» doors or pages, so the run updates the stand: the «catalogue-filters» scene in `docs/design/showall/index.src.html` gets the three doors and their pages with a slider for each new number below, the README gets a section «catalogue-filters-country-year-status starting values», and `python3 docs/design/build.py showall` rebuilds it. `designing-a-screen` does not have to run before planning. The owner dials it at brief.md#AC-11.

The catalogue-filters numbers were never dialled on the box (README:122-123), so everything below, inherited or new, is a starting value.

## Numbers that apply (dp / sp at 1080p, 1 dp = 2 px)

Inherited unchanged, quoted from README «catalogue-filters starting values», in code in `core/ui/uikit/component/panel/TvSidePanel.kt`:

| What | Value |
|---|---|
| panel width | 240 - 420, by the widest row (`TvSidePanelSurface` `IntrinsicSize.Max`) |
| panel height | content, max 500, vertically centred; a longer page scrolls under its title (`ShowAllFilterPanel.kt:40`) |
| corner / padding start, top, end, bottom | 20 start side only / 20, 20, 20, 16 |
| material | `panelGlass(Strong)`, elevation 20; scrim 62 % flat |
| title | 14 / 18 sp SemiBold, 12 to the rows |
| row | min 40, padding 12 / 6, radius 12, label 14 / 18 sp Medium (2 lines max), trailing 13 sp at 76 %, 16 before trailing |
| selected / focused | primary 16 % + check 18 primary, 12 before it / onSurface 30 % |
| loading / failure | one row with a 20 spinner / read-only error 14 sp onSurfaceVariant, then focusable «Повторить» |
| header summary | 11 / 16 sp Regular, #CAC4D0, 1 line, ellipsis, parts joined by « · » |

New:

| What | Start value | Why |
|---|---|---|
| root doors | «Сортировка», «Жанр», «Страна», «Год», «Статус» in this order; «Статус» only on `all_serial`, `all_docuserial`, `all_tvshow` (brief.md#AC-5) | existing doors keep their place; the conditional door is last, so its absence moves nothing |
| root trailing values | «Все страны» or the country; «Любой год» or the preset label; «Любой статус» or «Завершённые» | trailing = the selected row's label, as «Все жанры» on the genre door |
| root height | 5 doors: 18 + 12 + 5 × 40 + 36 = 266; 4 doors: 226 | no scroll |
| «Страна» page | «Все страны», then 102 countries in API order (alphabetical, AC-1 evidence) | Q3 |
| «Страна» page width | fixed **320** (slider 280 - 420); a lazy page has no intrinsic width | see Shared constants; rows wrap to 2 lines past it |
| «Страна» visible rows | (500 - 20 - 16 - 30) / 40 = 10.85: ten whole rows and a cut eleventh, the cut row is the scroll cue | |
| «Год» page | «Любой год», «2026», «2025», «2020-е», «2010-е», «2000-е», «1990-е», «1980-е», «до 1980» | Q1. Years are numbers, not «этот год» / «прошлый год», so a stored filter reads the same next year. Values sent: `y`, `2020-2029` ... `1980-1989`, `1900-1979` (AC-1 evidence: `-1979` is 502, `y1-` is a single year) |
| «Год» page height | 18 + 12 + 9 × 40 + 36 = 426 | no scroll; no section headers |
| «Статус» page | «Любой статус», «Завершённые» | Q2: «Выходящие» dropped, `finished=0` is ignored on every target (AC-1 evidence) |
| «Страна» failure text | «Не удалось загрузить страны», then «Повторить» | as the genre page (brief.md#AC-9) |
| summary order | genre · country · year · status · sort (sort only when not «по обновлению ↓»): «Драма · США · 2010-е · Завершённые · по году ↓» | filters before ordering; 1 line, ellipsis |

Open, for the owner on the stand:

1. «Статус» with two values: a door with a page (start, the AC-5 wording and the existing pattern) or a single toggle row «Только завершённые» on the root (one press less). Switch «статус».
2. Country page width 320, or the widest country title (a measured constant).
3. Reaching «Япония» takes about 103 presses down. A short «Популярные» section above the alphabet is a follow-up idea, not this run (Q3 fixed API order).

## Focus

Inherits the catalogue-filters focus table (`.flow/runs/catalogue-filters/design-ux.md#Focus`). New rows:

| From | Key | To |
|---|---|---|
| panel opened | (none) | root, «Сортировка», as today; a restored country / year / status does not move it (brief.md#AC-10) |
| «Страна» / «Год» / «Статус» door | OK | its page, focus on the selected value; none selected: the first row («Все страны», «Любой год», «Любой статус») |
| «Страна» page, selected country far down | open | the list is scrolled so the selected row is visible before focus is requested; it must not open on «Все страны» and jump |
| «Страна» page, countries loading | open | «Все страны»; when the list lands, focus moves to the selected country if there is one |
| «Страна» page, load failed | open | «Повторить»; OK reloads and focus lands as for loading |
| any page | Up on the first row / Down on the last | stays (no wrap), as Sort and Genre |
| any page | Up / Down | next row; the list scrolls to keep the focused row whole, the title does not scroll |
| value row | OK | selects it, check moves, focus stays on the page (as Sort and Genre today) |
| new page | Back | root, on the door it came from (`door` state extended to the three pages) |

Focus visibility: every new focusable row is a `TvSidePanelItem`, so focused = onSurface 30 %, selected = primary 16 % + check. On «Страна» the selected row and the focused row usually differ (open on selected, then move), so both states must be visible at once; at the cut eleventh row the focused row is always scrolled whole.

## Shared constants: parameterise, do not retune in place

| Constant | Lives in | Also drawn by | What to do |
|---|---|---|---|
| `TvSidePanelSurface` width rule `widthIn(240, 420)` + `width(IntrinsicSize.Max)` | `core/ui/uikit/component/panel/TvSidePanel.kt` | player settings, About, Episodes panels | Add an optional fixed-width parameter for a page whose content is a lazy list; the default keeps the intrinsic rule. Do not drop `IntrinsicSize.Max` for everyone |
| `TvSidePanelItem` metrics (40, 12 / 6, 14 / 18 sp, check 18) | same | player panels | Use as is; no new row style |
| `PanelMaxHeight` 500 | `ShowAllFilterPanel.kt:40` | filter panel only | Unchanged; the country list scrolls inside it |
| Page scroll | `ShowAllFilterPanel.kt:88` (`Column` + `verticalScroll` around every page) | Sort, Genre pages | The country page must not sit inside the outer `verticalScroll` (a lazy list in it fails with infinite height); give it its own scrolling container under the title |
| `DialogScrimAlpha` 0.62 | `TvDialogOverlay.kt:17` | every context menu | Do not change |
