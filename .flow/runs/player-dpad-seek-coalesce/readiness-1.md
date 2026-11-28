# Readiness 1: player-dpad-seek-coalesce

Verdict: **NOT READY**

Inputs: spec and plan in the worktree at `2e92c054` (code identical to base `8c3117ea`), [test-design.md](test-design.md).

## AC coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | Task 2 | `everyPressInAHoldShowsTheCumulativeTarget`, `aHoldNearTheEndClampsTheTargetToTheDuration` |
| AC-2 | Task 2 | `aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget`, `aLongHoldKeepsAtLeastOneWindowBetweenSeeks` |
| AC-3 | Task 2 | `aSingleBackwardPressSeeksOnceToZero` |
| AC-4 | Task 3 | `startOverDropsThePendingSeek`, `resumeDropsThePendingSeek`, `skipSegmentDropsThePendingSeek` |
| AC-5 | Task 3 | `backgroundSavesThePendingTarget`, `backPressedSavesThePendingTarget`, `aQualitySwitchCommitsThePendingTargetFirst` |
| AC-6 | Task 2 | `thePositionTickKeepsThePendingTarget` |
| AC-7 | Task 4 | `PlayerVMSeekBurstTest` rewrite and new case, `PlayerVMSkipCountdownFitTest`, `PlayerVMPromptDismissalTest` |
| AC-8 | Task 5 | Manual 1-4 |

## Citations checked

All PlayerVM, PlaybackController, SeekHandler, SeekStallRecovery, detekt and test-file citations below were read at `8c3117ea`.

| Citation | Result |
|----------|--------|
| `PlayerVM.kt:154` `seekBurstJob`, `:200` `seekHandler`, `:659-660` routing, `:728-731` `startSeekBurst`, `:733-749` seek fns, `:751-767` `showSeekIndicator` | resolve |
| `PlayerVM.kt:456, :476, :528` `switchStream`; `:1030` `resetMediaSession`; `:1058`, `:1516` callers | resolve |
| `PlayerVM.kt:1465`, `:1484`, `:1860-1862`, `:1587` tick, `:1757-1764` `onSeekPerformed`, `:1898-1901`, `:2040`, `:2054`, `:2066` | resolve |
| `PlaybackController.kt:146`, `:532`, `:568-578`, `:641-652` | resolve |
| `SeekHandler.kt:7`, `:17-38`; `SeekStallRecovery.kt:18-22` | resolve |
| `config/detekt/detekt.yml:677-679` (`maxJumpCount: 1`) | resolves |
| `PlayerVMSeekBurstTest.kt:25-31`, `:48-52`, `:84`, `:103`, `:120`, `:181`; `SeekHandlerTest.kt:34`, `:42-53`, `:57` | resolve |
| `PlayerVMOverlayAutoHideTest.kt:194-199`, `PlayerVMPlaybackFlowTest.kt:349`, `:403-416`, `PlayerVMPromptDismissalTest.kt:288` | resolve |
| `PlayerVMTestFixture.kt:79` `callbackSlot` | wrong: `callbackSlot` is at `:77`; `:79` is `createdViewModels` |
| `PlayerVMTestFixture.kt:91` duration `2_400_000` | wrong: duration is at `:93`; `:91` is `isPlaying` |
| `PlayerVMTestFixture.kt:120` `mapper.formatTime` | wrong: `formatTime` is at `:123`; `:120` is `loadSegments` |

## Build and order

Every task keeps the build green: Task 1 changes the `SeekHandler` constructor and its only other callers (`SeekHandlerTest.kt:34, :45, :57`) together. The only production constructor is `PlayerVM.kt:200` `SeekHandler()`. Tasks 2 to 4 depend on the task before them. Task 2 keeps `PlayerVMSeekBurstTest` green until Task 4: each case sends one seek, and the hand `onSeekPerformed` still runs.

## Test design

Every P0 unit row names a backticked test, is marked `red` or `guard`, and names the mutation it catches. AC-8 is device by nature (Manual 1-4). Scope stays inside Out of scope: `PlaybackController`, `component/`, DI and resources are untouched. No open question blocks.

## Fix list

1. Plan, Fixture table: correct the three `PlayerVMTestFixture.kt` citations to `:77` (`callbackSlot`), `:93` (duration) and `:123` (`mapper.formatTime`).
2. Test design and plan, `thePositionTickKeepsThePendingTarget` (P0 `guard`): the tick runs every 500 ms from `startPositionUpdates` (`PlayerVM.kt:1572-1576`), so "two presses, then `advanceTimeBy(200)`" crosses no tick unless the presses are timed for it, and the test then passes on the Task 1 code too. State when the first press happens relative to the tick (for example, advance to 400 ms after start before the first press) and add the required red check: the case fails on the Task 1 commit.

## Notes (non-blocking)

- `selectBufferPreset` and `toggleFastDns` (`PlayerVM.kt:984, :996`) re-create the player from `playbackController.currentPosition` while a target can be pending. The throttle job still sends the pending seek to the new player, so playback ends at the target. No plan change is needed. The reviewer should confirm this.
- The spec's Problem table cites `PlaybackController.kt:550-558` for the switch re-seek. The position read is at `:532` and `:538`, and the Design section cites `:532`.
