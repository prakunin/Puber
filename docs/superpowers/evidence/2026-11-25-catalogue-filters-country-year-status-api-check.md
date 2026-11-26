# Catalogue filters part 2: box API check, country / year / finished (2026-11-25)

Read-only probe of the KinoPub catalogue API as the app's own client sees it (AC-1 of run catalogue-filters-country-year-status). Device: owner's TV box `192.168.1.110:5555`, package `com.kino.puber.stage`. Build: worktree `catalogue-filters-country-year-status` at base 90e69aff (dev debug APK installed by `make itest`, app data kept). Method as in `2026-11-24-catalogue-filters-api-check.md`: a throwaway instrumented test (deleted afterwards, never committed) took `KinoPubApiClient` from the app's Koin, called `getItems(type = "movie", page = 1)` first (succeeded, token valid), then issued raw `GET countries` and `GET items` through the client's own HTTP client (stored bearer token). Only GET requests, no account mutations, no playback. Only status, keys, counts and public catalogue ids / titles were logged; no token or raw body. Every `items` call asks for page 1 (50 items); "pages" is `pagination.total`, "items" is `pagination.total_items`. "Honoured" means every item on page 1 satisfies the param and the total changed.

## Countries

| Fact | Value |
|---|---|
| Request | `GET countries`, HTTP 200 |
| Shape | wrapper object, top-level keys `status`, `items` (not a bare array: the current `getCountries` decode is wrong, as AC-2 assumes) |
| Item keys | `id`, `title` |
| Count | 102, no duplicate titles |
| Order | alphabetical by Russian title (Австралия, Австрия, Азербайджан, ... Югославия, Япония); ids are not ascending (10, 16, 79, 82, 89, ...) |
| Ids used below | США = 1, Франция = 8 |

## getItems with country

Param `country=<id>`. Every item on page 1 holds the country in `Item.countries`.

| Target | Unfiltered pages / items | country=1 (США) | country=8 (Франция) | Honoured |
|---|---|---|---|---|
| movie | 651 / 32536 | 290 / 14461, 50 of 50 | 85 / 4212, 50 of 50 | yes |
| serial | 162 / 8058 | 58 / 2860, 50 of 50 | 6 / 282, 50 of 50 | yes |
| documovie | 107 / 5349 | 53 / 2625, 50 of 50 | 10 / 454, 50 of 50 | yes |
| docuserial | 58 / 2893 | 30 / 1456, 50 of 50 | 3 / 112, 50 of 50 | yes |
| concert | 96 / 4784 | 25 / 1207, 50 of 50 | 7 / 322, 50 of 50 | yes |
| tvshow | 9 / 414 | 4 / 176, 50 of 50 | 1 / 3, 3 of 3 | yes |
| quality=4k | 83 / 4139 | 60 / 2968, 50 of 50 | 6 / 269, 50 of 50 | yes |

All HTTP 200.

## getItems with year

Item `year` on page 1 checked against the param. All HTTP 200 unless stated.

| Target | year=2020 (pages / items; item years) | year=1970-1979 | year=1900-1979 | year=2020-2030 | Honoured |
|---|---|---|---|---|---|
| movie | 19 / 912; all 2020 | 40 / 1983; 1970..1979 | 97 / 4844; 1937..1979 | 160 / 7963; 2021..2026 | yes |
| serial | 9 / 426; all 2020 | 2 / 95; 1971..1979 | 3 / 141; 1934..1979 | 70 / 3499; 2021..2026 | yes |
| documovie | 6 / 270; all 2020 | 1 / 27; 1970..1978 | 2 / 78; 1904..1978 | 27 / 1332; 2020..2026 | yes |
| docuserial | 3 / 145; all 2020 | 1 / 4; 1970..1979 | 1 / 9; 1939..1979 | 22 / 1075; 2020..2026 | yes |
| concert | 3 / 121; all 2020 | 2 / 73; 1970..1979 | 2 / 97; 1962..1979 | 35 / 1716; 2020..2026 | yes |
| tvshow | 1 / 24; all 2020 | 1 / 3; 1975..1978 | 1 / 7; 1961..1978 | 3 / 112; 2020..2026 | yes |
| quality=4k | 4 / 187; all 2020 | 3 / 111; 1970..1979 | 5 / 234; 1935..1979 | 42 / 2073; 2020..2026 | yes |

Range forms:

- `year=y` is exact, `year=y1-y2` is inclusive on both ends, and an upper bound past the current year (2030) is accepted.
- A lower bound of 1900 works as an open lower bound: `year=1900-1979` returns years up to 1979 with the real minimum far lower (1904 on documovie), so "до 1980" can be sent as `1900-1979`.
- `year=-1979` (no lower bound): HTTP 502 on all seven targets. Do not send it.
- `year=1980-` (no upper bound): HTTP 200 but treated as the single year 1980 (every item 1980, totals tiny: movie 214 items, serial 13, documovie 2, docuserial 2, concert 9, tvshow 1, 4k 20). It is not an open range. Use `1980-<current year or later>` instead.

## getItems with finished

Item `finished` on page 1 (decoded value counts) and totals against the unfiltered total.

| Target | finished=1 (pages / items; values) | finished=0 (pages / items; values) | Verdict |
|---|---|---|---|
| movie | 1 / 20; true x20 | 651 / 32536 (= unfiltered); false x50 | `1` honoured, but movies hold `finished` false almost everywhere, so the filter returns 20 odd items; not meaningful. `0` ignored |
| serial | 95 / 4743; true x50 | 162 / 8058 (= unfiltered); false x45, true x5 | `1` honoured; `0` ignored |
| documovie | 10 / 484; true x50 | 107 / 5349 (= unfiltered); false x44, true x6 | `1` honoured; `0` ignored |
| docuserial | 34 / 1653; true x50 | 58 / 2893 (= unfiltered); false x33, true x17 | `1` honoured; `0` ignored |
| concert | 1 / 4; true x4 | 96 / 4784 (= unfiltered); false x50 | `1` returns 4 odd items; not meaningful. `0` ignored |
| tvshow | 3 / 122; true x50 | 9 / 414 (= unfiltered); false x44, true x6 | `1` honoured; `0` ignored |
| quality=4k | 7 / 350; true x50 | 83 / 4139 (= unfiltered); false x47, true x3 | `1` honoured; `0` ignored |

All HTTP 200. `finished=0` returns the unfiltered list on every target (same totals, items with `finished` true among them): the server drops it, so a "Выходящие" option cannot be sent as `finished=0`. Only `finished=1` filters, and it is only meaningful on serial, docuserial, tvshow (and documovie, where `finished` is set on some items).

## Answers

- Countries: wrapper `{status, items:[{id, title}]}`, 102 items, alphabetical by title.
- country: honoured on all seven targets.
- year (`y`, `y1-y2`, `1900-y2`): honoured on all seven targets; `-y2` is HTTP 502; `y1-` is read as the single year `y1`.
- finished=1: honoured on serial, docuserial, tvshow, documovie and 4k; on movie and concert it returns a handful (20 and 4) of items that carry `finished` true, the rest of those types hold false, so the status door should stay off them.
- finished=0: ignored on every target (full unfiltered list returned).
- Throwaway test file removed from the worktree; nothing committed.
