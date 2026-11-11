# Test design: showall-rail-close-focus-shift

Base 6b087697. Size S, kind bug, tracks none (no perf). Fix shape A: handoff-aware `GridFocusMemory` in
`app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`, latching
`LocalContentFocusHandoff.pendingRequestId`. No plugin test-design template exists; this follows the job layout.

Levels (role addendum): `unit` (JUnit 5), `instrumented` (only `make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (manual, evidence file).
Existing fixtures reused:
- `GridFocusMemoryTest` (23 cases): inner `Harness(saved, index, items, restoreOverride, onFrame)` with `FakeFrameClock`, injected `requestRestore`, `requestGrid`, `contentFocusActive` (`activeVar`). Its header states the fake clock does not model recomposition: it proves decision logic only.
- `ShowAllScreenContentFocusTest` (5 tests, androidTest): `performDirection`, `focusedTitle`, `awaitAnyFocus` helpers.
- `MainSideMenuFocusTraversalTest.kt:185-250`: real `DrawerState` plus `ContentFocusHandoffEffect` harness. The only place the 5-frame grace and the blind tab-box request exist, so the only place the bug reproduces below device level.

In the unit rows, `onHandoff()` is the plan's placeholder name for the new suspend method. The test uses whatever name implementation picks.

## Risk profile

| # | Risk | P | I | Score | Test at the level where it fails |
|---|---|---|---|---|---|
| R1 | Handoff gets no answer from Show all, focus lands one row up (the bug) | 3 | 3 | 9 | T-I1 (drawer harness, red on base), T-U1, device D1 |
| R2 | Focus stealing: late or repeated attempt takes focus from the user, a dialog or a context menu (`!inGrid`, single-fire per id) | 2 | 3 | 6 | T-U3, T-U4, T-U5, T-I3, device D5 (M2) |
| R3 | Latched id read directly goes null on settle and cancels the effect, or re-fires on recomposition | 2 | 3 | 6 | T-I1 (fails if the effect never runs), T-I4 (fails on re-fire). Not catchable in unit: the fake clock has no recomposition |
| R4 | Null memory (first entry) regression, overlap with `showall-first-entry-rail-focus` | 2 | 3 | 6 | T-U2, T-I2, device D4 |
| R5 | Remembered card dropped by reload while rail open, wrong target | 2 | 2 | 4 | T-U6 (decision), T-I5 optional |
| R6 | Card not composed when the attempt runs (scrolled far away), request misses and blind landing still happens | 2 | 2 | 4 | T-I1b (scrolled position), device D2. Known limit, accepted as today |
| R7 | Double restore with the existing `pendingEntry` path on Back from Details | 1 | 3 | 3 | T-U7 (pendingEntry untouched), existing 5 instrumented tests (AC-8) |
| R8 | Back-close and tab re-pick take a different path than RIGHT | 1 | 2 | 2 | Accepted with note: all three call the shared `beginHandoff(expectsNewContent = false)`; one device check D3 covers it |

Scores of 6 and above all have a test at the failing level. R6 and R5 below 6 get one test each at cheap level.

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 | T-I1 `showAllRailCloseKeepsFocusOnTheLowerRowCard` | Card focused in a lower visible left-column row; LEFT opens the real drawer, RIGHT closes; the same card is focused, not the top-row one. Red on base |
| AC-1 | device | P0 | D1 | The same on the emulator, 3 repeats, on the real screen |
| AC-2 | instrumented | P1 | T-I1b `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling` | Same card after LEFT then RIGHT for a top-row card and for a card after the grid scrolled |
| AC-2 | device | P1 | D2 | Top row, scrolled position, 3 repeats each |
| AC-3 | device | P0 | D3 (Back and tab re-pick) | Back and re-picking the current tab return to the same card |
| AC-3 | instrumented | P2 | T-I6 `showAllTabRepickKeepsFocus` | Only if the harness already exposes `beginHandoff(expectsNewContent = false)` without extra code; otherwise left to D3 |
| AC-4 | unit | P0 | T-U2 | Null memory: the handoff method makes no request (restore events empty, no `grid`) |
| AC-4 | instrumented | P1 | T-I2 `showAllRailCloseWithoutMemoryBehavesAsBase` | No remembered card: RIGHT leaves the first card focused exactly as base, nothing extra requested |
| AC-4 | device | P1 | D4 | First entry, focus on the rail, RIGHT: first card focused as before |
| AC-5 | unit | P0 | T-U3 | `inGrid` true: new id causes no request |
| AC-5 | unit | P0 | T-U4 | Same latched id delivered twice causes one request set (single fire per id) |
| AC-5 | unit | P0 | T-U5 | `contentFocusActive` false at the start or turning false mid-attempts: no request and returns false, no `grid` fallback |
| AC-5 | instrumented | P1 | T-I3 `showAllHandoffResponseDoesNotStealFocusFromADialog` | Focus held outside the grid by a dialog or popup stays there after the handoff id settles |
| AC-5 | instrumented | P1 | T-I4 `showAllRecompositionAfterSettleDoesNotRefocus` | After the handoff settled, the user moves focus to another card, a recomposition happens, focus stays where the user put it |
| AC-5 | device | P0 | D5 (M2 from hardening run) | A rail opened right after Back is not taken from the user |
| AC-6 | unit | P1 | T-U6 | Remembered card removed from items while the rail is open (grid unfocused), a new id arrives: request goes to the neighbour at the same index (`restoreId == 6`), which `resolveRestoreId` already guarantees |
| AC-6 | device | P2 | none | Reload while the rail is open cannot be produced reliably on a device. Covered by T-U6 only |
| AC-7 | unit | P0 | T-U1 `onHandoff_rememberedCardAndGridUnfocusedRequestsThatCard` plus T-U2, T-U3 | Red on base (method absent, nothing requests). The 23 existing cases stay green |
| AC-8 | instrumented | P0 | The 5 existing `ShowAllScreenContentFocusTest` tests, unchanged | Re-entry and fallback behaviour intact |
| AC-8 | unit | P1 | T-U7 `onHandoff_leavesPendingEntryUntouched` | After a handoff with a saved card, a later `reconcile()` still behaves as a pending entry would (no double restore on Back from Details) |
| AC-8 | review | P2 | diff check | History grid and `HistoryCard.kt` not in the diff |
| AC-9 | instrumented | P0 | T-I1 | Hosts `ShowAllScreenContent` in the `MainSideMenuFocusTraversalTest.kt:185-250` harness. Shown red once on base, green after the fix |

Counts: P0 9 rows, P1 8, P2 3 (table rows, several rows are the same test at two levels). Distinct tests below.

## Test list

Unit (extend `GridFocusMemoryTest`, same Harness, add a handoff call to it; use the existing `FakeFrameClock`):
- T-U1 P0: saved = 5 at index 4, items 1..10, `inGrid` false. Run the handoff method. Expect restore events end with `restore:5:true`, no `grid`.
- T-U2 P0: saved = null. Expect no events at all.
- T-U3 P0: grid reported focused (`onGridFocusChanged(true)`) then the handoff. Expect no events.
- T-U4 P0: the handoff run twice with the same id (the latch lives in the composable, so the unit-level guard to check is that a second call for the same id after success makes no further `requestRestore` call; if the once-per-id guard is placed in the composable, move this to T-I4 and say so in the plan).
- T-U5 P0: `activeVar = false` before the call, and flipped false on frame 2: no restore after the flip, no `grid`.
- T-U6 P1: saved 5 dropped from items before the call: last restore event `restore:6:true`.
- T-U7 P1: saved card present (`pendingEntry` true from construction), handoff then `reconcile()`: no additional restore beyond what base `reconcile()` does; `grid` fallback semantics unchanged.

Failing-first: T-U1. Check on base by reading the old code: `git -C <wt> show 6b087697:app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt` lines 171-235 have no handoff method, and `reconcile()` returns early when `pendingEntry` is false and `inGrid` is false (`:206-211`), so a rail close produces zero restore events. T-U1 therefore cannot compile on base (red by absence). The behaviour-red test is T-I1, which needs no new API.

Instrumented (new file beside `ShowAllScreenContentFocusTest.kt` or in `MainSideMenuFocusTraversalTest.kt`'s package, using its drawer harness with real `DrawerState` and `ContentFocusHandoffEffect`; run via `make DEVICE=<serial> itest TESTS=<fqcn>` with a leased emulator, never `connected*AndroidTest`):
- T-I1 P0: grid with enough items for 3+ visible rows; focus a left-column card in the lowest visible row; LEFT (rail opens), RIGHT (handoff). Await the settle of the 5-frame grace. Assert `focusedTitle()` equals the card focused before. Must be red on base (lands on the top-row left card), green after. Capture the base red run once as evidence.
- T-I1b P1: same for a top-row card and after scrolling the grid.
- T-I2 P1: no card focused before (focus starts on the rail), RIGHT: assert the same card focused as on base (the first card).
- T-I3 P1: hold focus on a dialog or popup node while the handoff id settles: the grid takes nothing.
- T-I4 P1: after settle, move focus to another card, force recomposition: focus unchanged.
- T-I5 P2 optional: reload removes the remembered card while the drawer is open.
- T-I6 P2 optional: tab re-pick, see traceability.

## Bugs

Failing-first test: T-I1 (behaviour, red on base, written first). T-U1 follows immediately after the method exists. Base reading confirms red: with `pendingEntry` false and `inGrid` cleared by the rail, nothing calls `requestRestore`, so the 5-frame grace ends and the blind tab-box request lands on the top-left card of the topmost laid-out row (brief root cause 2 and 3).

## Manual checks (device, qa-device; lease, `adb -s`, worktree APK via `tools/android-apk-install-preserve`)

D1 (AC-1) Show all, card in the lowest visible row of the left column.
1. Focus that card, note its title.
2. LEFT (rail opens), RIGHT (rail closes). Repeat 3 times.
Expect: same card focused each time, the row above never gets focus; the rail closes fully. Must not: focus on the card one row up, focus lost to nowhere, rail stuck open.

D2 (AC-2) Same sequence with the topmost visible left-column card, then after scrolling down two rows. 3 repeats each. Expect: same card. Must not: scroll position jumps or changes the card.

D3 (AC-3)
1. Focus a left-column card, LEFT to open the rail, press Back. Expect: rail closes, same card focused.
2. Open the rail, re-pick the current tab (OK on it). Expect: same card focused.
Must not: app exits, tab content reloads and loses the card, wrong row.

D4 (AC-4) Enter Show all fresh (first entry, nothing remembered, focus on the rail), press RIGHT. Expect: the first card focused as on base. Must not: any new jump or focus request.

D5 (AC-5, hardening M2) Press Back from Details into Show all, immediately open the rail with LEFT. Expect: the rail stays open and keeps focus. Must not: focus is pulled back to the grid. Also open a context menu or dialog from a card: focus stays in it.

Evidence: write to the run's evidence file; run `tools/mobile-evidence-audit.sh <evidence-dir>` before sharing. No uninstall, data clear or downgrade.

## perf track

Off. Not applicable.

## UNTESTABLE

- AC-6 on a real device: a reload cannot be triggered deterministically while the rail is open. Covered by T-U6 (and optional T-I5).
- The once-per-id latch (`remember` of `pendingRequestId`) cannot be proven by the unit harness (fake clock, no recomposition). It is covered only by T-I1 and T-I4 and the device checks.
- Compose-source link of the root cause (androidx-main default Enter rule) is `plausible` per the brief; not asserted by any test, tests assert only the observable focus outcome.
