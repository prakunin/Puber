# Readiness, round 3 (Tasks 5-7, series delta)

Verdict: **READY**

Spec and plan at worktree commit 69eeca1d (worktree clean). Base 08f50b81. Tasks 1-4: [readiness-2.md](readiness-2.md).

### AC coverage (delta)

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 (route record) | Task 6 | `seriesRoute_servesSeriesInBrowse_movieInPlayback` (P0 red), `browseScenario_requiresSeriesRoute` (P1 red) |
| AC-4 | Task 5 | `fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture` (P0 guard), `serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin` (P0 red), `movieFixture_carriesNoTrackData` (P0 guard), `validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture` (P0 red), four P1 rows |
| AC-5, AC-6, AC-7, AC-8 | Task 7 | Manual 1-3, Perf (thresholds unchanged, descriptors >= 163) |

### Citations sampled

| Citation | Claim | Result |
|---|---|---|
| `BaselineMockBackend.kt:126`, `:142-203`, `:154-155`, `:183-186` | first-match dispatch per server, `routesFor`, `json()` chunked on h1, generic `/v1/items/\\d+` route | resolves |
| `BaselineMockBackend.kt:88-95`, `:106-108`, `:320-321`, `:323-325` | `startIteration`, `missingRequiredRoutes`, description `"$method $pathText"`, full-path `matches` (so `/v1/items/2` cannot match `/v1/items/12`) | resolves |
| `BaselineMockBackend.kt:349-352`, `:362-369`, `:372-375`, `:377` | fixtures KDoc, `validate()`, `NAMES`, `BANNED_KEY` with four keys | resolves |
| `BaselineFixtureContentTest.kt:41-44`, `:62-65`, `:79-86`, `:119-128`, `:130-146` | fixtures map of every `*.json` (picks up `item_serial.json`), `decodedItem()`, watched-movie test, ban test, posters/HLS test | resolves |
| `BaselineMockSelfTest.kt:81-84`, `:86-93`, `:95-107`, `:203-218` | `get`/`call`, `hitStartupRoutes`, `assertCheckFails`, `containsAll` startup test | resolves |
| `Models.kt:46-88`, `:145`, `:302-324`, `:437-444`, `:542-571`, `:579-587`, `:610-613` | `Item` fields, `isSeriesLike`, `Audio`/`AudioType`/`AudioAuthor`, `SubtitleLink`, `Season`/`Episode`/`WatchingInfo`, `VideoFile`, `Translation`; the planned `item_serial.json` sets every non-default field | resolves |
| `DetailsPrefetcher.kt:38`, `:76`, `:92`, `:123` | 750 ms neighbour dwell, `onFocused`, neighbour warm, `awaitSettled` | resolves |
| `BaselineProfileGenerator.kt:52`, `:67`, `:75`, `:114`, `:167`, `:176-180`; `BaselineProfileTestControl.kt:60`; `ContentCacheRepository.kt:38` | `clearTarget`, journey idles, player check, `iteration scenario=` log line, item feed | resolves |

Checked by reading: the `browseScenario_requiresSeriesRoute` hits cover every other Browse-required route (`:165`, `:174-175`, `:179`, `:182-183`, `:187-195`), so `missingRequiredRoutes == listOf("GET /v1/items/2")` holds. `item.json` carries `"type":"movie"` and `"id":{{ID}}`, as the Playback half of `seriesRoute_*` expects.

### Order, build, scope

| Check | Result |
|---|---|
| Order | 5 -> 6 -> 7: Task 5 adds `item_serial` to `NAMES` and narrows the ban; Task 6 consumes it; Task 7 regenerates |
| Build | Each of Tasks 5 and 6 ends with the fast gate, the generator compile and one commit; Task 7 with the full gate |
| Scope | No `app/src/main/java` or `res` file; no `imdb`/`trailer`; `seasons`/`subtitles` only in `item_serial.json` (Q5); Q4-Q7 answered, none blocking |
| P0 rows | Each names its mutation, is `red` or `guard` and names its test in backticks; device rows are manual checks |
| Stop rules | R10 miss and player-check failure are recorded as Blocked, `required` not relaxed, no rerun loop; thresholds not loosened |

### Notes (non-blocking)

1. Spec Test design Manual 1 step 3 still says "the four new ones" (round 1) and does not list `GET /v1/items/2` matched per BrowseAndDetails iteration; plan Task 7 states both. qa should check against Task 7.
2. D12 (id 2 warmed by the neighbour prefetch in Browse) is inferred, not traced; R10 makes a miss a named failure.

### Fix list

None.
