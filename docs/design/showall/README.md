# Show all stand

The Show all screen (`ui/feature/showall`) reworked after the KinoPub web grid
(`.flow/runs/catalogue-poster-grid/reference-web-grid.png`): a grid of framed tiles, each with a
2:3 poster, quality / sound / subtitle badges at the top start, rating pills at the poster foot, and
the Russian and original titles under the poster. Run `catalogue-poster-grid`, part 1 of 3.

Open `index.html`. Arrow keys move focus through the grid the way the remote does; a click on a
tile focuses it. Every slider and switch is remembered in this browser under
`puber.stand.showall.v1`; «Вернуть стартовые значения» clears it.

**Agreed 2026-11-24** (owner: «давай пока как есть, так и делаем»): the starting numbers below are
the agreed values, with the switches as the stand opens: focus frame #938F99, 4K badge green
#4CAF50, pills two per row, title area reserved for 2 + 1 lines, `posters.medium`, scroll to the
edge, watch marks on, **release year in the original-title line** («Ted Lasso · 2020»; the year
alone when there is no original title; the original ellipsizes, the year never does).
**No CC or AC3 badge in this run**: the list data cannot drive them (Ground truth); they move to
the run `catalogue-card-audio-subtitle-badges`. Only HD / 4K badges ship.

## Ground truth

Taken 2026-11-24 from the TV box `192.168.1.110:5555` (1920 × 1080, density 320 = 2.0, app
`com.kino.puber.stage` 1.12.5-dev) and from the box's app-data snapshot.

- **The stage build on the box is not debuggable**, so `run-as` refuses: the image cache and
  `databases/puber.db` could not be read on the device. The `cached_payload` findings below come from
  the box's own app-data snapshot (`make auth-save`, 2026-11-23; read locally, nothing copied into the
  repo). Its `puber.db` is cut 4 KB short, so it was read as raw JSON rather than through SQLite.
- **Show all itself was not reached on the box.** Its entry is the button after the last card of a
  section row, and the 4K «Популярные» row kept paging in more cards under ~150 D-pad presses. The
  closed rail and the content edge were measured instead (screencaps with HW overlays off, restored).
- **Closed rail.** `closeDrawerWidth` = 60 dp. Icons on a 40 dp pitch, first centre at 36 dp; the
  selected tab is a pill `#1E1B23` from x 8 to 119 px (56 dp wide, 4 dp from the edge), 48 dp tall.
  Catalogue cards start at 152 px = 76 dp = 60 dp rail + 16 dp row padding, which is also where Show
  all's `PaddingValues(16.dp)` puts its first column. The stand draws the rail from these numbers.
- **Item-level `subtitles`: not determinable from the cache.** `cached_payload` holds the app's own
  re-encoding of `Item` (key order of the data class, `langs` written as a string), so fields the
  model does not declare are dropped before they reach the cache. No cached item (235 parsed) has a
  `subtitles` key. Every `"subtitles"` in the file is a file-level `List<SubtitleLink>` array
  (`[{"lang":"rus",...}]`, 1953 occurrences), never an int. The list-endpoint type stays unverified:
  it needs a captured raw `GET items` response (a debuggable build with HTTP logging) before
  `Item.subtitles` is added.
- **4K quality value: `quality` = 2160 (JSON int).** 98 cached items carry it, all the 4K-section ones
  (Тед Лассо 66136, Обитель зла 128626, ...). Other values seen: 1080 (106), 720 (2), 480 (2), absent
  (27). So the brief's thresholds hold: 4K at >= 2160, HD at >= 720.
- **`ac3` is 0 on all 208 cached items that carry it**, 4K titles included, while the web grid shows
  AC3 on most cards. The list field looks unused; an AC3 badge fed from it would never light. Open.
- **KinoPub pill = `rating_percentage` / 10 is confirmed** against the reference: Война 96 → 9.6 with
  IMDb 7.1, Условный мент 39 → 3.9 / KP 7.2 / IMDb 4.3 match the web cards exactly.

## Content

48 real cards in two switchable sets, the 24 most-viewed of each in the snapshot: «4K · Популярные»
(quality 2160) and «Сериалы · 1080 / 720 / 480» (HD and no-badge cases, three titles without an
original title: Условный мент, Трудно быть богом, Каменская). Mapped as `VideoItemUIMapper` would,
plus the proposed split: title before «/» on line one, the rest as the original title. Posters are
`posters.medium` (250 × 375) from `m.staticpop.net`; the first six of each set also carry
`posters.big` for the tier switch. Unwatched-episode counters are placed by hand (Следопыт 3, Монолог фармацевта 12, Вампир поневоле 2) so the stand carries no account data. The watched eye (Тед Лассо, Игра престолов) and progress bars
(Достать ножи 42 %, Анатомия страсти 70 %) are placed by hand: the snapshot has no watch state.

## Starting numbers

All in dp / sp at 1080p (1 dp = 2 px). Web-grid proportions converted at 6 columns; grid padding
kept from today's code.

| | start | today |
|---|---|---|
| колонок | **6** | 3 (16:9 cards) |
| закрытое меню | **60** | 60 |
| поле сетки по бокам / сверху | **16 / 16** | 16 / 16 |
| между колонками / рядами | **12 / 16** | 16 / 32 |
| поле плитки | **5** | — |
| скругление плитки / постера | **10 / 7** | — |
| заливка плитки | **14 %** surfaceVariant #49454F | — |
| контур плитки | **1** dp, border #938F99 at **22 %** | — |
| значки: высота / кегль / зазор | **11 / 7 / 3** | — |
| значки: поле по бокам / от края постера / скругление | **4 / 5 / 3** | — |
| пилюли: высота / кегль / значок / зазор / от низа | **13 / 8 / 8 / 3 / 5** | — |
| подложка значков и пилюль | **78 %** чёрного (было 62 %: тонула на светлых постерах) | — |
| название: кегль / интерлиньяж, до 2 строк | **10 / 12**, weight 500 | — |
| оригинал: кегль / интерлиньяж, 1 строка с многоточием | **8 / 10**, onSurfaceVariant #CAC4D0 | — |
| постер → название / название → оригинал | **5 / 1** | — |
| рамка фокуса | **2** dp, border #938F99 | 3 dp on other cards |
| увеличение под фокусом | **1.05** | — |
| полоса просмотра | **3** | 3 |

Switches open on: CC / AC3 by data (none light), year in the original-title line, 4K badge
green as on the web, pills two per row (KinoPub + Kinopoisk, IMDb below), title area reserved for
2 + 1 lines so every tile in a row has the same height, medium poster, scroll to the edge.

What that gives: tile 134.5 dp (269 px), poster 124.5 × 187 dp = 249 × 374 px, so
`posters.medium` (250 px) is enough with no upscale; above 125 dp of poster width the spec panel
says big is needed. A row is 237 dp, so 2.1 rows are on screen and 12 tiles whole.


## catalogue-browse-all starting values (2026-11-24)

Run `catalogue-browse-all` (`.flow/runs/catalogue-browse-all/design-ux.md`, Element B). A fixed
one-line title above the grid. It is not a grid item and not focusable, so first focus stays on the
first tile. The "Agreed 2026-11-24" grid numbers above do not change. Not dialled yet: the owner
dials these on the TV box at AC-11.

| | start | |
|---|---|---|
| поле над заголовком | **12** dp | |
| кегль / интерлиньяж | **16 / 20** sp Medium | `SectionTitleStyle`, the catalogue shelf heading |
| поле слева | **16** dp | = the grid's side padding, the edge of column 1 |
| строк | **1**, ellipsis | |
| заголовок → сетка | **8** dp | the grid's top `contentPadding`; must stay at or above the 6 dp focus overhang |
| сетка по бокам / снизу | **16 / 16** dp | unchanged |

What that gives: row 1 starts at 12 + 20 + 8 = 40 dp, row 2 ends at 40 + 237 + 16 + 237 = 530 dp,
536 with the 1.05 focus overhang, so two whole rows stay on screen (today row 2 ends at 506).

Title text (switch «текст заголовка»): the browse grid and the «Все» shelf show the entry label
(«Всё в 4К», «Все концерты», ...); other shelves show `<tab> · <shelf>` («Фильмы · Новинки»), with
the same " · " separator as the original-title line. Switch «заголовок → нет» shows the agreed grid
without it.

## catalogue-filters starting values (2026-11-24)

Run `catalogue-filters` (`.flow/runs/catalogue-filters/design-ux.md`, Elements A-C). Switch
«сцена» = «catalogue-filters» (the stand now opens on it; «сетка (согласовано)» is the grid and header
above, unchanged). Not dialled yet: the owner dials these on the TV box at AC-13. The "Agreed
2026-11-24" grid numbers do not change.

**Header (browse grids).** One row, all on the left edge: `[title] 16 [Фильтры] 8 [Сброс] 12 [summary]`.
The title is fixed for the screen's life, so «Фильтры» never moves. «Сброс» and the summary appear only
while a filter is active.

| | start | |
|---|---|---|
| поле над рядом | **10** dp, slider max **11** | was 12; at 12 row 2 ends at exactly 540 with the focus overhang |
| высота ряда = кнопки | **24** dp | title (16 / 20 sp) centred in it: centre stays at 22 dp |
| ряд → сетка | **8** dp | unchanged; must stay ≥ 6 (tile overhang) |
| заголовок → «Фильтры» / «Фильтры» → «Сброс» / «Сброс» → сводка | **16 / 8 / 12** dp | |
| кнопка | **24** high, side padding **12**, label **11 / 16** sp Medium, focus scale **1.1** | = `BrowseAllEntry` |
| «Фильтры» / «Сброс» | **filled** (Button) / **outlined** (OutlinedButton) | switch «Фильтры / Сброс» |
| значок «Фильтры» | **off**; on = Phosphor Funnel **15** dp, gap **6** | switch «Фильтры» |
| сводка | **11 / 16** sp Regular, #CAC4D0, 1 line, ellipsis | genre, then sort only when not «по обновлению ↓»: «Драма · по году ↓» |
| полка | the same 24 dp row with the title only | switch «фильтры: сетка» |

What that gives: row 1 starts at 10 + 24 + 8 = 42 dp; row 2 ends at 532, 537.9 with the 1.05 overhang,
inside 540. A focused button reaches 35.2 dp, a focused row-1 tile starts at 36.1: no overlap.

**Panel.** Right edge, vertically centred, over the grid in `TvDialogOverlay`. Built like
`PlayerSidePanel`: root «Фильтры» with two doors (Сортировка, Жанр, each with its current value on
the right), each opening a page.

| | start | source |
|---|---|---|
| ширина | **240 – 420** dp, by the widest row | `PanelMinWidth` / `PanelMaxWidth` |
| высота | content, max **500** dp; a longer page scrolls under its title | new |
| скругление | **20** dp, start side only | `PanelCornerRadius` |
| поля start / top / end / bottom | **20 / 20 / 20 / 16** | `PanelPadding*` |
| материал | `playerGlass(Strong)`: surface 72 % with onSurface 15 % → primary 5.5 % → surface 84 %, 1 dp rim, elevation 20 | `PlayerGlass.kt` |
| заголовок | **14 / 18** sp SemiBold, **12** dp to the rows | `PlayerSidePanel` |
| раздел | **11 / 14** sp SemiBold, letterSpacing 0.8, onSurfaceVariant 78 %, 8 above / 4 below | `PlayerPanelSectionHeader` |
| строка | min **40** dp, padding **12 / 6**, radius **12**, label **14 / 18** sp Medium, trailing **13** sp at 76 % | `PlayerPanelItem` |
| выбранное / фокус | primary 16 % + check **18** dp primary / onSurface 30 % | `PlayerPanelItem` |
| затемнение | **62 %** flat; switch: the player's gradient (0 → 42 % from 52 % of the width) | `TvDialogOverlay.kt:17` |

Pages: «Сортировка» = section «Поле» (по обновлению, по дате добавления, по году, по рейтингу, по
просмотрам) and section «Порядок» (по убыванию, по возрастанию); 398 dp tall, no scrolling. «Жанр» =
«Все жанры» plus the genres, about 1266 dp, so it scrolls at 500. While loading: one row with a 20 dp
spinner; on failure a read-only «Не удалось загрузить жанры» and a focusable «Повторить» (on the stand it
loads after 1.2 s). The genre titles are placeholders: the box check (api-check, 2026-11-24) counted 30
movie genres and named only «Драма» (9) and «Аниме» (25); the other 28 titles here are plausible, not
read from the box. «Аниме» is left out (brief.md#AC-9).

Filtered empty state (switch «выдача» = «пусто»): «Ничего не найдено. Активен фильтр: <summary>», 12 dp,
outlined «Сброс». With no filter it stays «Ничего не найдено».

**Focus on the stand** (arrows, Enter = OK, Esc = Back): row-1 card Up → «Фильтры»; «Фильтры» Left →
rail, Right → «Сброс» if shown, Down → the row-1 card it came from; OK → panel, root, «Сортировка».
Panel: Up / Down move, Left / Right trapped; OK on a door opens its page on the selected value; OK on a
value moves the check. Back: page → root on the door it came from → panel closes, focus on «Фильтры».
A change applies on close only; the grid restarts from the top (the stand's cards do not change).
«Сброс» OK clears the filter and puts focus on «Фильтры».

Switches that open on the start value: Сброс before the summary («порядок»), genre pick stays on the
page («выбор жанра»), «Фильтры» without the icon, filled / outlined, flat scrim, filter «Драма · по
году ↓» applied.

Open: placement of «Сброс» against the summary; glass over posters vs a plain surface (judged on the
box); genre pick stays or returns to root; the icon. The panel width follows the widest row including
the check, so it can change by a few dp when the selection moves to a longer row.

## catalogue-filters-country-year-status starting values (2026-11-25)

Not dialled on the box; every number below is a starting value. Scene «catalogue-filters», panel
«Страна», «Год», «Статус»; switch «статус» swaps the door for a toggle row; slider «ширина: Страна».
Inherited numbers (glass, padding, rows, focus) are those of the section above.

| What | Start value |
|---|---|
| root doors | «Сортировка», «Жанр», «Страна», «Год», «Статус»; «Статус» only on `all_serial`, `all_docuserial`, `all_tvshow` |
| root trailing values | «Все страны» or the country; «Любой год» or the preset; «Любой статус» or «Завершённые» |
| root height | 5 doors 266 dp, 4 doors 226 dp, no scroll |
| «Страна» page | «Все страны», then 102 countries in API order; width fixed 320 dp (slider 280-420); ten whole rows and a cut eleventh |
| «Год» page | «Любой год», «2026», «2025», «2020-е», «2010-е», «2000-е», «1990-е», «1980-е», «до 1980»; 426 dp, no scroll |
| «Статус» page | «Любой статус», «Завершённые» |
| failure | «Не удалось загрузить страны», then «Повторить» |
| summary | genre · country · year · status · sort: «Драма · США · 2010-е · Завершённые · по году ↓» |

Focus: OK on a door opens its page on the selected value, or the first row; the country page opens
scrolled to the selected country before focus is requested; Up on the first row and Down on the last stay;
a value row selects and stays on the page; Back returns to the door it came from.

Open for the owner: «Статус» as a door with a page (start) or one toggle row «Только завершённые»;
country page width 320 or the widest country title; reaching the end of 102 countries takes about 103
presses, a «Популярные» section is a follow-up. The stand's country titles are placeholders in
alphabetical order, not taken from the box.

## Open items

1. **CC and AC3** — follow-up run `catalogue-card-audio-subtitle-badges` (source: file audio tracks and
   subtitle lists, or a verified list field).
2. **Green #4CAF50** becomes a named colour in `Color.kt` beside `KinopoiskOrange` and `ImdbYellow`.
3. **Scroll behaviour on the box** (edge vs pivot) and the focused tile overlapping its neighbours at
   1.05 are judged on the TV after the port (AC-7).
4. **On-device frames** of the new grid replace the missing screencaps of today's grid at verify.
