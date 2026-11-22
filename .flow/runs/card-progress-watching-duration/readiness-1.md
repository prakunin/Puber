# Readiness 1: card-progress-watching-duration

**Verdict: READY**

Base c506d0bd; worktree HEAD 7a768c24 (spec only, production code equals base). Plan read uncommitted from the worktree.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | WSR `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` (P0 red) |
| AC-2 | Task 1 | VIM `mapShortItem_progress_forPartiallyWatchedMovie`, WSR `resolve_readsAMoviesPositionAndLengthFromItsFirstVideo` (P0 red) |
| AC-3 | Task 1 | three VIM rows, WSR `recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength` |
| AC-4 | Task 1 | IWS two rows, VIM `mapShortItem_progress_null_whenMoviesVideoIsWatched`, WSR P1 row |
| AC-5 | Task 1 | WSR `resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields` plus existing guards |
| AC-6 | Task 2 (and DSM run in Task 1) | DSM whole class unchanged (P0 guard) |
| AC-7 | verify (plan, end of Task 2) | Manual check 1-5, box `192.168.1.110:5555` |
| AC-8 | Task 2 | full gate |

## Citations sampled

| Citation | Resolves to | Result |
|---|---|---|
| `WatchStateRepository.kt:496-510` | `private fun Item.toWatchStateOrNull()`, gate at `:499` | ok |
| `WatchStateRepository.kt:227` | `fun resolve(item)` | ok |
| `WatchStateRepository.kt:418-431` | `progressPercent` getter | ok |
| `WatchStateRepository.kt:289-301` | `recordFromHistory` | ok |
| `Models.kt:143`, `:163-177` | `WATCHED_STATUS`, `Item.isFullyWatched()` | ok |
| `Models.kt:548-553` | `WatchingInfo`, `duration` default 0 with KDoc | ok |
| `VideoItemUIMapper.kt:111-113`, `:116-127` | `watchState`, `toLocalWatchState` | ok |
| `DetailsScreenUIMapper.kt:367-383`, `:402-405` | `buildMovieResumeLine`, private `WatchingInfo.length` | ok |
| `WatchStateDao.kt:132-169` | `upsertFromServer` UPDATE, overwrites `progress_duration` | ok |
| `detekt.yml:81-83`, `:756-759` | `ComplexCondition` 3, `ReturnCount` 3 | ok |
| WSR `:39`, `:595-615`, `:678-694`; DSM `:336`, `:345`, `:359` | `var now = 42L`, the rewritten test, `historyEntry`, the three named DSM tests | ok |
| `Models.kt:262`, `:266-267` (plan Task 1) | `Video.duration` is `:261`; `watched`/`watching` are `:265-266` | off by one, symbol unambiguous |

## Build and order

Task 1 adds the helpers, makes `toWatchStateOrNull` `internal` and deletes `toLocalWatchState` in one commit; the red tests use only test-helper parameters and existing production APIs (IWS has its own `WATCHED_STATUS` at `IWS:7`), so they compile on base. Task 2 consumes only Task 1's helpers.

Red claims checked against base code: AC-1/AC-2/AC-4 items carry no item-level fields, so the base gate returns null; the R1 test writes at `now = 100` over a history row stamped 42, and the fake DAO (`WSR:770-783`) overwrites as the SQL does, so base stores length 0. The two guards pass on base.

## Test design P0 rows

Every automated P0 row names its test in backticks, is marked `red` or `guard`, and names the defect it catches. AC-7 (device) and AC-8 (check) are P0 without a red/guard mark; neither is an automated unit test.

## Scope and questions

Plan stays out of the DAO guard and the `resolve()` order. Q2 (blocking) answered; Q1, Q3, Q4 answered.

## Notes for the implementer (non-blocking)

| # | Note |
|---|---|
| 1 | `HomeInteractor.kt:165` also filters on `watchStateRepository::isFullyWatched`; spec risk 3 names only `ContentListInteractor.kt:177`. Same reasoning applies (list payloads carry no `videos`); reviewers should confirm. |
| 2 | Base Details uses `video?.duration ?: item.duration?.total` (null-only fallback); `movieLength()` skips a 0 `videos[0].duration`. DSM fixtures do not hit a 0 length, so AC-6 does not see the difference. |
