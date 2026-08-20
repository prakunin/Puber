# Details on one screen — design

**Goal:** the film's own screen shows everything about the film. No second page to open for the
rest of the description, and nothing that matters is below the fold.

## What is wrong now

The details screen is a three-page vertical pager. Page one carries the title, ratings, a
`2026, Комедия Великобритания` line, the duration and a description truncated to
`FIRST_PAGE_DESCRIPTION_LINES`, with the action buttons underneath. Page two — reached by scrolling
down — repeats the ratings under an «Информация» heading, then gives the full description again, a
focusable row of actor chips, and a grid of twelve label/value rows. Page three lists similar items.

Two things are wrong with that. The description is cut on the page where it is read, and the page
that completes it is a separate screen the user has to know about. And half of page two's grid —
year, genres, countries, duration — is already on page one, so the second page is mostly a reprint.

## The screen

One hero. The still, or the trailer once it starts, occupies the right three quarters and the full
height of the page rather than a panel at the top. The text sits on top of its left edge over the
same scrim the catalogue uses.

```
┌────────────────────────────────────────────────────────────────┐
│ Падение сэра Дугласа Уитерфорда                                │
│ The Fall of Sir Douglas Weatherford        (one Text, 2 lines) │
│ IMDb 6.1   KP 2.5                                              │
│ 2026 · Комедия · Великобритания · 1 ч 35 мин ─────────┐        │
│                                        (line runs over the art)│
│ Одержимый своим великим предком Дугласом                       │
│ Уитерфордом, Кеннет стремительно погружается в                 │
│ пучину безумия, когда его тихий городок захватывает            │
│ съёмочная группа фэнтези-сериала…                              │
│                                                                │
│ 1080p · Объёмный звук · 16+ · Дубляж · 3 дорожки · Субтитры 2  │
│ Режиссёр: Имя · В ролях: А, Б, В, Г ──────────────────┘        │
│                                                                │
│ [▶ Смотреть фильм] [Трейлер] [⤴] [🔖] [👁]                      │
└────────────────────────────────────────────────────────────────┘
```

### Two measures, not one column

The description paragraph wraps at about 460 dp: a long line is hard to read, and the eye loses the
next one. The single-line rows — the meta line, the facts line, the credits line — are not bound by
that. They run to 62 % of the screen width, over the artwork, and end in an ellipsis rather than
wrapping. A list of countries that used to fold onto a second line now simply continues over the
picture.

62 % is where the scrim reaches zero, so no part of a line lands on an unmuted frame.

### What each row holds

| Row | Content |
| --- | --- |
| Title | the mapper has already split `Русское / Original` onto two lines; this row carries both |
| Ratings | IMDb, KP, PUB — as now |
| Meta | year · genres · countries · duration, or · seasons for a series |
| Description | the plot |
| Facts | quality · surround · age rating · translation · audio tracks · subtitles |
| Credits | `Режиссёр: …` and `В ролях: …`, joined by · |

Year, genres, countries and duration appear once, in the meta row. The facts row carries only what
the meta row does not. A row whose values are all missing is not drawn at all.

### The description scrolls itself

The description takes whatever height is left between the rows above it and the rows below, so the
layout cannot overflow whatever the plot's length. When the text is taller than that space it
scrolls on its own, and the scroll loops:

- **3 s still** at the top, so the opening is readable before anything moves.
- **Scrolls at 18 dp/s**, slow enough to read along with.
- **2 s still** at the bottom.
- **Snaps back** to the top — no reverse scroll, which reads as a mistake — and the cycle repeats.

It runs only when the text actually overflows, and it stops while the full-screen trailer is over
the screen or the episodes panel is open, both of which hide it.

Measured over the 182 items in the device's cache, the plot is 342 characters at the median and 646
at the ninetieth percentile: most titles will not scroll at all, and the ones that do are the long
tail this exists for.

### Series

The same layout, with the meta row carrying the season count instead of a duration and the existing
series buttons. The episodes panel is unchanged.

## What goes

`DetailsInfoPage` and its parts — the «Информация» heading, the actor chip row, the info grid — are
deleted. The chip row is focusable today but has no click handler, so nothing that responds to the
remote is lost.

The pager keeps two pages: the film, and «Похожее» below it. Its indices and the chevron move
accordingly.

`DetailsInfoUIState` stays as the mapper's output but is reshaped: the label/value rows become two
prepared strings, `factsLine` and `creditsLine`, alongside the description and ratings the screen
still uses.

## Risk to watch

An auto-scroll is a coroutine loop that animates for as long as the screen is up. Compose's test
clock treats a running animation as work in progress, so the instrumented tests that touch this
screen — `TopTabDetailsBackFocusTest` among them — are where a hang would show. The loop must
therefore be driven by the composable's own lifecycle and be inert when the text fits, which is the
common case.
