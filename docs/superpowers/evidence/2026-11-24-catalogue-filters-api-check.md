# Catalogue filters: box API check (2026-11-24)

Read-only probe of the KinoPub catalogue API as the app's own client sees it. Device: owner's TV box `192.168.1.110:5555`, package `com.kino.puber.stage`. Build: worktree `catalogue-filters` at base b7a7d99e (dev debug APK installed by `make itest`, app data kept). Method: a throwaway instrumented test (deleted afterwards, never committed) took `KinoPubApiClient` from the app's Koin, called `getItems(type = "movie", page = 1)` first (succeeded, token valid), then issued raw `GET genres` with the stored bearer token and typed `getItems` calls. Only GET requests, no account mutations, no playback. Only status, top-level keys, counts and public catalogue ids / titles were logged; no token or raw body.

## Checks

| # | Request | Result | Status |
|---|---|---|---|
| 1 | `GET genres` (no type) | HTTP 200, top-level keys `items`, `status`; each item has `id`, `title`, `type`; 115 items, a mix of video, music and documentary genres, with repeated titles under different ids (for example two "Биография": 3 and 78) | PASS |
| 2a | `genres?type=movie` | 200, 30 items, includes 25 «Аниме» and 9 «Драма» | PASS |
| 2b | `genres?type=serial` | 200, 30 items, same set as movie | PASS |
| 2c | `genres?type=concert` | 200, 31 items (music genres plus «Балет»), no 25 | PASS |
| 2d | `genres?type=documovie` | 200, 47 items, no 25 | PASS |
| 2e | `genres?type=docuserial` | 200, 47 items, no 25 | PASS |
| 2f | `genres?type=tvshow` | 200, 7 items (Выживание, Интелектуальные, Кулинария, Путешествия, Развлекательные, Реалити-шоу, Ток-шоу), no 25 | PASS |
| 2g | `genres?type=4k` | 200, 30 items, same set as movie / serial, includes 25 and 9 | PASS: non-empty |
| 3 | `getItems(type = movie, sort = <field><dir>)`, first 10 ids vs `updated-` | Table below | see below |
| 4 | `getItems(type = serial, genre = 9)` (id of «Драма» from the serial list) | 50 items on page 1, every item's `genres` holds 9; `pagination.total` 79 pages vs 162 unfiltered (items 3944 vs 8056) | PASS |
| 5 | `getItems(quality = 4k, genre = 128, sort = year-)` (128 from the 4k list) | 50 items, every item holds genre 128, years 2026 x10 and non-increasing over the page; total 12 pages (571 items) vs 83 pages (4134 items) for 4k `year-` unfiltered | PASS |

### Check 3: sort fields

Base is `updated-` (first ids 129274, 129238, 129241, ...). "Same as base" means the first 10 ids equal the base's.

| Field | `-` (DESC) | empty (ASC) | `+` |
|---|---|---|---|
| updated | same as base (is the base) | differs (5575, 5578, ...) | same as base: ignored |
| created | differs (129274, 129256, ...) | differs (1, 2, 3, ...), oldest first | same as base: ignored |
| year | differs; item year 2026 x10, non-increasing | differs; years 1900, 1902, 1903, ... non-decreasing | same as base: ignored |
| rating | differs; item `rating` 3953, 3375, ... non-increasing | differs, but item `rating` is 0, 0, 0, 0, -4, -1, -2, ...: not monotonic in the returned field | same as base: ignored |
| views | differs; 1636943, 1314627, ... non-increasing | differs; 0, 0, ..., 1, 2, 3 non-decreasing | same as base: ignored |

The `+` suffix is not a direction: it is dropped and the default order returns. The app's `SortDirection.ASC.apiValue` is the empty string, which is the working ascending form.

## Answers

- Genres response shape: `{ "status": ..., "items": [ { "id", "title", "type" } ] }`, a wrapper object like the item lists, not a bare array. `getGenres` has to unwrap `items` (AC-10); the extra `type` key is ignored by `ignoreUnknownKeys`.
- `genres?type=4k`: non-empty (30 items, identical to the movie and serial lists). For `all_4k` the genres request can use `"4k"`; `null` would return the 115-item mixed list, which includes music and documentary genres that make no sense on the 4K tab.
- Sort fields and directions that change the order: `updated`, `created`, `year`, `rating`, `views`, each in DESC (`-`) and ASC (empty suffix). Only `updated-` equals the default. `year` and `views` are verifiably monotonic both ways on the returned fields; `rating` DESC is monotonic on the item `rating` field, while ASC reorders but its keys are not the returned `rating` (not a defect for the filter, but a "worst first" label cannot be checked against that field). `kinopoisk_rating` and `imdb_rating` were not probed (the design does not offer them).
- Genre filter on serial: works (every item holds the genre, total drops from 162 to 79 pages).
- Genre plus sort on 4K: works (genre honoured, years non-increasing, total drops from 83 to 12 pages).
- Notes for the design: the genre lists differ per type, so the panel should request `type` = the grid's type (movie, serial, 4k, concert, documovie, docuserial, tvshow); the no-type list holds duplicate titles under different ids; the stage app's token was valid, so a worktree build did not lose the session.
