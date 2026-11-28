# Readiness 2: player-dpad-seek-coalesce

Verdict: **READY**

Inputs: spec (worktree, uncommitted edit on `2e92c054`), plan (worktree, untracked), [test-design.md](test-design.md); code at base `8c3117ea`. Round 1: [readiness-1.md](readiness-1.md).

## Round 1 fix list

| # | Fix | Result |
|---|-----|--------|
| 1 | Plan Fixture table: `PlayerVMTestFixture.kt` citations | Fixed: `:77` `callbackSlot`, `:93` duration `2_400_000L`, `:123` `mapper.formatTime` all resolve |
| 2 | `thePositionTickKeepsThePendingTarget` timing and red check | Fixed: plan Task 2 timing table and test-design.md:53 put the presses at tS+400 and tS+450, the tick at tS+500 inside the window (closes tS+650); marked P0 `red` on the Task 1 commit, and Task 2 "Done when" requires it |

## Citations checked this round

| Citation | Result |
|----------|--------|
| `PlayerVM.kt:386` `startProgressSync()` in prepare | resolves |
| `PlayerVM.kt:1572-1576` `startPositionUpdates` loop, `delay` first | resolves; first tick 500 ms after start, as the table assumes |
| `PlayerVM.kt:1587` tick `currentPosition` write | resolves |
| `PlayerVM.kt:2071` `POSITION_UPDATE_INTERVAL_MS = 500L` | resolves |
| `PlayerVM.kt:1623-1627` `isAnythingShowingPosition` gates the write on `controlsVisible` | resolves; fixture content has `controlsVisible = true` (`PlayerVMTestFixture.kt:293`), so the tick writes and the red check is real |
| `PlayerVMTestFixture.kt:77`, `:93`, `:123` | resolve |

The round 1 citation sample (readiness-1.md, Citations checked) still holds: no code changed since `8c3117ea`.

## Coverage, build order, scope

Unchanged from readiness-1.md: every AC maps to a task and a Test design row, every task leaves the build green, P0 unit rows name a backticked test, `red` or `guard`, and the mutation caught; scope stays inside Out of scope; no open question blocks.

## Notes (non-blocking, carried)

- The spec Problem table still cites `PlaybackController.kt:550-558` for the switch re-seek; the read is at `:532`, which the Design section cites.
- `selectBufferPreset` / `toggleFastDns` (`PlayerVM.kt:984, :996`): reviewer to confirm the pending seek lands on the re-created player.
