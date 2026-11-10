# Readiness 1: seek-blocked-by-skip-prompt

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md`, plan `docs/superpowers/plans/2026-11-10-seek-blocked-by-skip-prompt.md` (worktree HEAD `1cd4fba6`, base `6b087697`, no `app/` change since base).

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 3, 4, 5 | `skipPrompt_leftAndRightSeekAndFocusStaysOnAction`, `skipPrompt_heldRightSeeksOnEveryRepeat` |
| AC-2 | 3, 4 | `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` |
| AC-3 | 2 | `aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds`, `aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds` |
| AC-4 | 3, 5 | `skipPromptHidden_nextRightSeeks`, `nextEpisodePromptHidden_nextRightSeeks`, M2 |
| AC-5 | 3, 5 | `skipPrompt_appearsWithActionFocused_okSkips` |
| AC-6 | 3, 5 | `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` |
| AC-7 | 3, 4 | `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` |
| AC-8 | 1, 2 | `PlayerVMPromptDismissalTest.seekingAfterCancellingPutsTheNextEpisodeQuestionBack` (`:81`), `PlayerVideoSurfaceTest` (`:16`, `:25`) |
| AC-9 | 5 | manual M1, M5 |

## Citations sampled (all resolve at base)

| Citation | Found |
|---|---|
| `component/PlayerVideoSurface.kt:213` | `playerActionForKeyCode`; maps Left/Right, FF/RW, skip fwd/back to seek actions |
| `component/PlayerTimedActionOverlay.kt:171`, `:181`, `:191-197`, `:266`, `:274`/`:282`, `:309`, `:381` | `focusPrimaryAction`, `BackHandler`, focus `LaunchedEffect(visible)`, button `Row`, the two requesters, `TimedActionButton.focusRequester`, `.focusRequester(focusRequester)` |
| `component/SkipSegmentOverlay.kt:28`, `component/NextEpisodeOverlay.kt:26` | `focusPrimaryAction = false` / `= true`; both are the only callers of `PlayerTimedActionOverlay` |
| `component/PlayerScreenContent.kt:72`, `:77-82`, `:84-89`, `:131` | `PlayerFocusEffects`, both effects, the `PlayerOverlayLayers` call (its only caller) |
| `component/PlayerOverlayLayers.kt:105`, `:112-113`, `:127` | signature, skip overlay gated on `nextEpisodeCountdown == null`, `NextEpisodeOverlay` |
| `component/PlayerFocusRequesters.kt:7-14` | class with six `val FocusRequester` fields |
| `vm/SeekHandler.kt:7`, `:25` | `private const val DEFAULT_SEEK_RESET_TIMEOUT_MS = 1_500L`; `System.currentTimeMillis()` |
| `vm/PlayerVM.kt:142-151`, `:700-703`, `:706`, `:714`, `:1510-1515`, `:1670-1677`, `:1403`, `:1412`, `:1781` | job fields, `launch { delay }` shape, `seekForward`/`seekBackward`, the gated tick block, `onSeekPerformed`, resume / start-over / skip `seekTo` |
| `vm/PlayerVM.kt:1543-1568` | early next-episode fallback: offset 22 000 ms (`PlayerCountdowns.kt:80`), so 2 380 000 of 2 400 000 is inside |
| `UT/vm/PlayerVMTestFixture.kt:42`, `:76`, `:190`, `:192`, `:274-323`; `UT/vm/PlayerVMSkipCountdownFitTest.kt:29-45`, `:53` | fixture class, `callbackSlot`, `startedVM`, `contentState`, `testContentState` (`hasNextEpisode = true`), `playingInto`, `playFor` |
| `model/PlayerViewState.kt:17`, `:94`; `model/PlayerAction.kt:6`; `app/build.gradle.kts:428`; `Makefile` `itest` | `PlayerContentState`, `SkipSegmentUIState`, `PlayerAction`, `ui.test.manifest`, target present |

## Ordering and build

Tasks 1 -> 2 -> 3 -> 4 -> 5 follow their Consumes/Produces. Each task commits with the fast gate green; red tests exist only in Task 3 (instrumented, outside `check.full`) and Task 2 (implemented before its commit). All callers of the changed signatures are inside the plan's file lists.

## P0 rows

Every automated P0 row is marked `red` or `guard`, names its test in backticks and the mutation it catches. AC-9 is a manual P0 (M1, M5), manual by nature.

## Scope and questions

Plan constraints exclude `PlayerVM.kt:1676`, visual constants, Up/Down/Play-Pause forwarding, `focus-request-helper` and `docs/design/`. Blocking Q1 and Q3 are answered; Q2, Q4 non-blocking and answered.

## Advisory (no fix required)

1. Task 5 `requestFocusOrRetryNextFrame` retries only on an exception. With compose BOM `2026.08.00`, `FocusRequester.requestFocus()` returns `Boolean`, so a silent `false` (design risk 1) would not retry; the implementer should retry on `false` too.
2. Task 3's red run needs qa-device mid-implementation (device lease); the conductor must schedule it before Task 4 starts.
