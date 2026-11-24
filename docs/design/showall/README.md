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

## Open items

1. **CC and AC3** — follow-up run `catalogue-card-audio-subtitle-badges` (source: file audio tracks and
   subtitle lists, or a verified list field).
2. **Green #4CAF50** becomes a named colour in `Color.kt` beside `KinopoiskOrange` and `ImdbYellow`.
3. **Scroll behaviour on the box** (edge vs pivot) and the focused tile overlapping its neighbours at
   1.05 are judged on the TV after the port (AC-7).
4. **On-device frames** of the new grid replace the missing screencaps of today's grid at verify.
