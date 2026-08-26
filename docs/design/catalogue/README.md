# Catalogue screen — Фильмы, Сериалы and the rest of the tabs

The tab screen behind every catalogue entry in the side rail: a details panel across the top half
and, below it, one section of horizontal cards at a time. Stood up 2026-08-26 against
`ContentListScreenContent.kt`, `SectionRow.kt` and `VideoItemHorizontal.kt` as they stand in
`834ee23`. Nothing has been dialled yet — the stand opens on exactly what the code carries.

Published copy: <https://claude.ai/code/artifact/4c3c6094-b6d2-4f1a-885b-07d768b9ac64>

«Фильмы», «Сериалы», «Концерты», «Документальные», «Док. сериалы», «ТВ-шоу» and «4К» all draw
exactly this — a panel over one section at a time, differing only in the section list
`TabTypeConfig` hands them. «Мультфильмы» and «Аниме» put a `HeroCarousel` above the sections as
well, and are the two tabs this stand does not stand for; both ship disabled. «Фильмы» is what it
is built on, because it is the only tab whose sections are in the device cache.

## Where the numbers came from

**Not from frames off the television.** The stage app on KM6 is logged out — the secure prefs are
an empty `<map />` and the screen is the QR pairing code — so the geometry here is read out of the
Compose source, not measured off a screencap. What *is* ground truth:

- **1920 × 1080 at density 320** — `dumpsys window displays` on KM6, so 1 dp = 2 px, as in every
  stand here.
- **The content** — `databases/puber.db`, `cached_payload`. All five section queries for «Фильмы»
  are cached and every item they name is cached with them, so the stand shows the rows the screen
  would have shown at 15:43 on 2026-08-26. Titles, years, ratings, plots, durations and the
  watched/part-watched marks are run through a replica of `VideoItemUIMapper`; the marks come from
  the device's own `watch_state` table.
- **The typography** — decompiled out of `tv-material-1.1.0.aar` rather than remembered:
  `titleSmall` 14/20 sp Medium, `bodySmall` 12/16, `labelSmall` 11/16 Medium, `labelLarge` 14/20
  Medium. `Card` corner 8 dp, focused border 3 dp in `colorScheme.border` = `#938F99`
  (`NeutralVariant60` — note the `content` stand writes `#E6E0E9` for the same token, and that is
  wrong; `Neutral90` is `#E6E1E5` and it is `onSurface`, not `border`).

Each section carries 14 of its items rather than all 20–50, and the wide posters are re-encoded
to 1100 px — a card is 675 device px wide, and the panel's frame is 1350, which at the scale a
browser actually shows the stage lands back near 1∶1. Together that keeps the built page near
3 MB. Both are limits on the stand, not on the screen.

**Still to do when KM6 is paired again:** take `screencap` frames of the tab and check the two
numbers the source cannot settle — the real width of the closed rail (`MainScreenContentBody`
hardcodes a 60 dp inset that no measurement backs) and the visible tab list, which is a user
preference (`NavigationPreferencesRepository.getVisibleTabs`), not the fixed set drawn here.

## What the stand reproduces

- **The page layout.** `DetailsWeight : ContentWeight = 1 : 1` puts the panel and the rows at
  270 dp each, and `fillParentMaxHeight()` makes every section a page of that same 270 dp — so
  exactly one row is on screen and Down moves a whole page, not a row.
- **The frame that runs under the rows.** `expandMediaIntoContent` measures the still at a true
  16:9 — 675 × 380 dp — while the panel reports only 270, so 110 dp of picture, and the 48 dp
  fade at its bottom edge, land behind the section heading and the cards.
- **The remote's real scroll.** `PositionFocusedItemInLazyLayout` puts its `BringIntoViewSpec` in
  `LocalBringIntoViewSpec`, and that CompositionLocal reaches the nested `LazyRow` as well as the
  column it was meant for. So a card already fully visible does not move the row at all, and one
  past the edge lands its left edge at 30 % of the width — neither at the edge nor centred. Each
  section keeps its own position, the way each has its own `LazyListState`.
- **Both marks.** The eye badge and the 3 dp progress bar are on the items that actually carry
  them in `watch_state` — seven watched, two part-watched among the 57.

## What the stand is asking

Nothing is dialled yet; these are the questions it was built to answer.

| | сегодня |
|---|---|
| панель по высоте | **270** dp — половина экрана под одну карточку |
| страница секции | **270** dp — одна секция на экран, ряд ниже не виден |
| поле у заголовка / у ряда | **16 / 16** dp — вдвое дальше от края, чем на экране контента (8–11) |
| поле над заголовком | **0** dp — заголовок прижат вплотную к низу панели |
| высота карточки | **190** dp |
| строк в названии карточки | **2** — соседние карточки читаются с двух разных высот |
| название в панели | **по центру**, а факты и описание под ним — по левому краю |
| строк описания | **не задано** — длинный текст ложится на заголовок и карточки |
| растворение справа | **36 × 180** dp — короче карточки на 5 dp сверху и снизу |
| «Показать все» | только в последней секции, «Все» |

Four of these are defects rather than choices, and the spec panel under the stage names each one
as it happens:

1. **The description has no `maxLines`.** `ContentListScreenContent` leaves `descriptionMaxLines`
   at `Int.MAX_VALUE`, the `Column` does not clip, and the longest plot in the device's own cache
   (Одержимая, 126466) runs to 23 lines and overruns the panel by 210 dp — straight through the
   section heading and into the cards. Switch «описание → самое длинное в кэше» to see it; the
   stand picks whichever plot in the set is longest rather than naming one. The `Ellipsis` already
   on that `Text` never fires, because nothing ever limits the lines.
2. **`FadeGradient` is sized from `VideoItemHeight`** — the 180 dp poster card — not from
   `CatalogueRowItemHeight`, which is 190. Centred in a 222 dp row it leaves 5 dp of card
   unfaded above and below the gradient.
3. **The section heading and the cards sit on two different left edges.** They agree at 16 dp
   today only because `padding(horizontal = 16.dp)` and `PaddingValues(16.dp)` happen to match;
   they are two independent numbers. Vertically the heading has no number at all — it is the
   first child of the section's `Column` and sits flush against the bottom of the panel, which is
   what an overrunning description then lands on. The stand carries a «поле над заголовком»
   slider for the padding that does not exist yet; it would be a `padding(top = …)` on that
   `Text` in `SectionListItem`.
4. **«Показать все» is only in the last section**, which is «Все» — the one section where there
   is nowhere further to go. «Новинки» and «Горячие» page in more instead.

The title in the panel is centred — `align(CenterHorizontally)` plus `TextAlign.Center` in
`VideoDetailsDescription` — while the ratings, the facts and the plot under it are all left
aligned, so nothing in that column shares an axis. Left alignment is a switch on the stand
(«название в панели»). It is one parameter, not a rewrite, but `VideoDetailsDescription` is
shared with the Favourites screen, where the description has its own column rather than lying
over a picture; parameterise it rather than retuning in place, the way `MediaScrim` already had
to be.

One more, not a size and so not on a slider: the facts line reads
`"${year}, ${genres} ${country}"` — no separator between the genres and the country, so it prints
«Триллер, Ужасы, Детектив Южная Корея».

## Dialling it

The stand remembers what is dialled: every slider, every switch and the section on screen go into
`localStorage` under `puber.stand.catalogue.v1` on each render, and come back on the next load —
checked against the sliders' own bounds, so a value left over from an older edition of the stand
cannot get in. It is per-browser and goes nowhere else. Where storage is unavailable — a private
window, cleared data, a thumbnail capture — the stand opens on the values the code carries, and
says nothing about it. «Вернуть значения кода» restores those and clears what was stored.

## Agreed sizes

Dialled 2026-08-26 and carried by the code. The stand now opens on these.

| | было | стало |
|---|---|---|
| панель по высоте | 50 | **67** % |
| край подложки | 16.7 | **10** % кадра |
| подложка на краю | 80 | **90** % |
| растворение снизу | 48 | **50** dp |
| ширина блока описания | 37.5 | **60** % ширины |
| поле по бокам (описание) | 16 | **15** dp |
| поле сверху (описание) | 4 | **5** dp |
| кегль названия | 16 | **20** sp |
| название → рейтинги | 8 | **4** dp |
| рейтинги → факты | 4 | **5** dp |
| кегль фактов | 11 | **10** sp |
| интерлиньяж фактов | 16 | **15** sp |
| интерлиньяж описания | 16 | **15** sp |
| поле над заголовком | 0 | **30** dp |
| кегль заголовка секции | 16 | **11** sp |
| заголовок → ряд | 8 | **2** dp |
| поле ряда | 16 | **0** dp |
| между карточками | 16 | **10** dp |
| кнопка «Показать все» | 60 | **43** dp |
| высота карточки | 190 | **115** dp |
| поле по бокам (карточка) | 12 | **7** dp |
| между годом и рейтингами | 16 | **9** dp |
| кегль названия карточки | 14 | **10** sp |
| интерлиньяж названия карточки | 20 | **9** sp |
| название в панели | по центру | **по левому краю** |
| название карточки | две строки | **одна с многоточием** |
| «Показать все» | только в «Все» | **в каждой секции** |

Three of the four defects above go with them. The description now has room for the longest plot
in the cache — 14 lines in the taller, wider panel — though nothing still limits it, deliberately:
a longer one from the API would overrun again, and truncating on our own judgment is not the
trade this repo makes. The heading's own 30 dp of air is what it lands on instead. «Показать все»
is now in every section. The heading and the cards are further apart than ever — 16 dp against 0 —
and that is the dialled answer, not an oversight: the cards run to the very edge of the content
and the heading does not.

`FadeGradient` is still sized from `VideoItemHeight`, so at a 115 dp card its 180 dp now overhangs
the row by 32 dp either side. Invisible on black, still wrong, still open.

## What the port had to break apart

Four of these numbers lived in constants that other screens read. None was retuned in place:

- **`DetailsWeight` / `ContentWeight`** are also the favourites screen's split, where the lower
  half is a grid of full-height posters and 33 % would crush it. The catalogue got
  `CatalogueDetailsWeight` / `CatalogueContentWeight` of its own.
- **`DescriptionWidthFraction` and `MediaWidthFraction`** used to be one derivation — the picture
  was whatever the text left plus a third of the text's width. 60 % text against a 75 % picture is
  more overlap than that could express, so the full-bleed layout now names both outright and the
  side-by-side layout keeps its 3∶5 weights.
- **`VideoDetailsDescription`'s padding, alignment and type** are drawn on the favourites screen
  too. They moved into a `DescriptionLayout` object with a `Default` and a `Catalogue`, the same
  shape `MediaScrim` already had.
- **`VideoItemHorizontal`'s card padding and rating spacing** are drawn by ten screens. They are
  parameters now, defaulting to what those ten already had.

One behaviour had to come apart with them. Whether a row paged in more content rode on the same
flag as the show-all button, so putting the button in every section would have stopped every
section paging. They are separate questions now: `loadsMore` is its own parameter, false only for
«Все», whose grid is the whole catalogue anyway.

## Where it lands in code

| стенд | код |
|---|---|
| каркас, страница секции | `ContentListScreenContent.kt` — `ContentListLayout`, `SectionListItem` |
| панель, кадр, подложка | `VideoItemGridDetails.kt` — `MediaScrim.Catalogue`, `ExpandedMediaLayout` |
| поле ряда, зазор, растворение | `SectionRow.kt`, `FadeGradient.kt` |
| карточка | `VideoItemHorizontal.kt`, `PuberTheme.Defaults.CatalogueRowItemHeight` |
| заголовок секции | `Type.kt` — `SectionTitleStyle` |
| закрытое меню | `MainScreenComponent.kt` — `closeDrawerWidth` |

`VideoItemGridDetails` and `VideoItemHorizontal` are shared: the details screen, search, history,
favourites and collections all draw one or the other. Parameterise before retuning either — see
what `MediaScrim` already had to do for the same reason.
