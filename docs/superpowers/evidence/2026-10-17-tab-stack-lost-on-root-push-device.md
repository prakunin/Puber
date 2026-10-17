# Device evidence: tab stack lost on root push (round 1)

Slug `tab-stack-lost-on-root-push`, worktree HEAD `5a3d76f0` (fix `e1a0b4d1`, base `f14dcc84`), size M.

## Build and device

- Device: `emulator-5554` (AVD `puber_tv_36`, API 36 TV, already paired). Lease held, released at the end; emulator left running.
- Build: this worktree's own `:app:assembleDevDebug`, sha256 `cf124382...68b2`; the on-device `base.apk` sha256 is identical.
- Install: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`, `destructive_action: false`, signer unchanged (`6b291812...49a83e`). No uninstall, clear, downgrade or signer change.
- Input: D-pad keyevents only. Session log switched on and off through Settings > For developers (D-pad); OFF again at the end (screenshot checked). Log lines quoted below are `type/scr/cmd/from/to` fields only.
- No logout was done (AC-7 on device not run, see below).

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL. Unit tests aggregated from the result XMLs: 1996 tests, 0 failures, 0 errors, 0 skipped. Most tasks were up to date from the implementer run on the same inputs; lint re-ran.

## Instrumented classes (`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=<fqcn>`)

- `com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`: 10 tests, 0 failed, exit 0 (before the fix: 3 red, see `.flow/runs/tab-stack-lost-on-root-push/qa-prefix-red.md`).
- `com.kino.puber.core.ui.navigation.component.TabFlowLifecycleTest`: 1 test, 0 failed, exit 0.

## M1 (AC-1, AC-2, AC-4): AC-1 PASS, AC-2 PARTIAL (grid-level focus), AC-4 PASS

Concerts tab, "Trending" row, "Show all" button (DPAD_CENTER) opens the grid (tab-local push). Observation on entry: the rail
expanded with focus on the rail item; DPAD_RIGHT moved focus into the grid first card (see Other observations).
DPAD_DOWN x4 then DPAD_RIGHT: noted card "Hades / Ballads of the Underworld" (4th row, middle), grid scrolled 3+ rows.
DPAD_CENTER: Details. KEYCODE_BACK once.
- Log: `nav push_result ShowAll->ShowAll`, `nav push_result ShowAll->Details`, then `nav cmd=back from=Details to=ShowAll` with `scr=ShowAll`,
  then `act Resumed scr=Main`. No later line names `ContentList`; the Back label names the tab-local screen (AC-4).
- Screenshot after Back (3 s): the Show all grid, not the tab root. Scrolled: same rows as before (the noted card still on screen, one row
  lower because of the focus pivot below). One Back press left Details.
- Focus after Back: focus ring on "Sam Smith / London Coliseum", the first card of the first (partially visible) row, not on the
  Hades card that opened Details. The grid scroll then aligned that row to the focus pivot (about one row lower than before).
  Focus is inside the grid and live: DPAD_RIGHT moved it to the neighbour ("David Fray"), DPAD_LEFT back.
- Repeat with a second card ("Lera Lynn / Bush Hall", bottom-right of the visible rows): after Back the ring is on "Snoop Dogg /
  iHeartRadio", again the first card of the first partially visible row; the opener card is on screen. Same result both times.
- DPAD BACK once: tab root shown (`nav cmd=back from=ShowAll to=ContentList`), focus on the Trending card; BACK again: rail expands.
- Where focus lands (plan Task 5): neither on the card that opened Details, nor on the first card of the grid, nor nowhere, nor on the rail:
  on the first card of the first visible row of the restored scroll position (grid-level).

## M3 (AC-9): PASS

Home tab, "My bookmarks" first card (DPAD_CENTER, series Details), KEYCODE_BACK, 3 s. Log after Back: `nav cmd=back from=Details to=Home`
(`scr=Home`), `act Resumed scr=Main`, exactly one `act OnResume scr=Home`. Screenshot: Home content, the same card focused, no crash.
(The same flow on Home also gave one `OnResume` on a plain tab switch, as before.) No player or trailer was opened.

## M6 and AC-3 (tab switches keep stacks, root push keeps both): PASS

Movies tab: "New releases" "Show all" opened the grid, DPAD_DOWN, DPAD_CENTER (Details), BACK: grid shown (`nav cmd=back from=Details to=ShowAll`).
Rail to 4K, "Popular" "Show all" opened the grid, Details and BACK: 4K grid shown. Rail to Movies: its Show all grid shown
(`nav tab ContentList->ContentList`). Rail to 4K: its Show all grid shown. Neither tab showed its root.

## M5 (AC-8 Settings): no spinner

After the 4K root push and Back, rail to Settings: the Settings content ("General", device info) was fully rendered 1.5 s and 4.5 s
after the switch; no spinner. (The Settings tab has no UI path that root-pushes itself in this build, see the previous run's evidence.)

## AC-6 and AC-11 reads

- `NavTelemetry.kt`: the only `Telemetry.isEnabled` is at `:63` in `recordNav`; `unbind` has no gate.
- `docs/architecture/navigation.md` Tabs: names the holder owner (`TabComponent`), the key (`PuberCurrentTab`), the reason (Voyager nested dispose),
  and the lifetime (dies with the host).

## AC-7 on device: NOT RUN

A real logout or session expiry cannot be completed from a worktree build (no credentials, no re-login); not attempted, per the brief.
Stand-in: `TabHostTelemetryWiringTest.replacedHostStartsAtTabRootsWhenShownAgain` (T4), green.

## Other observations (not attributed to this change)

1. On the first entry into a Show all grid from the tab root (Concerts and Movies, both runs, before any root push) the rail expanded with
   the focus on the rail item instead of inside the grid; DPAD_RIGHT then focused the grid's first card. Not checked on the base build.
2. After switching away from the Settings tab and back, it showed "General" again instead of the previously selected "For developers".
   Not checked on the base build.
