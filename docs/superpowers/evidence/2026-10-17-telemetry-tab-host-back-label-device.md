# Device evidence: telemetry tab host Back label (round 1)

Slug `telemetry-tab-host-back-label`, worktree HEAD `4be887ec` (base `bdd2c792`), size M.
No raw session files, logcat, tokens or UI dumps are recorded here: line structure and the needed `nav`/`scr`/`act`
fields only.

## Build and device

- Device: `emulator-5554` (AVD `puber_tv_36`, API 36 TV, already paired). Lease held, released at the end; emulator left running.
- Build: this worktree's own `:app:assembleDevDebug`, sha256 `7d479292...88c9`; the on-device `base.apk` sha256 is identical.
- Install: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`, `destructive_action: false`,
  signer unchanged (`6b291812...49a83e`). No uninstall, clear, downgrade or signer change.
- Session log toggled only through the D-pad (Settings > For developers > Session log); switched OFF again at the end.
- One unintended action: a stray D-pad sequence while the focus was not where assumed opened a player for a concert item
  for a few seconds ("Buffering", position 0:00); BACK closed it at once, the detail page still read "Not started".
  No bookmark or watch-state change was made on purpose.

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` on `4be887ec`: BUILD
SUCCESSFUL, 1994 unit tests, 0 failures, 0 skipped (`NavTelemetryTest` 16 of 16), detekt and lint green.

## M4 instrumented class

`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`
(exit 2): 6 tests, 5 passed, 1 failed.

| Test | AC | Result |
|------|----|--------|
| backFromRootPushedScreenNeverRecordsMain | AC-7 | PASS (RED before the fix) |
| backOntoTheHostRecordsNoTabReannounce | AC-4 | PASS (RED before the fix) |
| tabSwitchThenRootPushAndBackNamesTheNewTab | AC-3 | PASS (RED before the fix) |
| openingAnotherTabIsRecordedAndSetsTheScreen | AC-5 | PASS |
| reselectingTheCurrentTabWithANewInstanceIsRecorded | AC-5 | PASS |
| tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen | AC-6 | FAIL: `ComposeTimeoutException: Condition still not satisfied after 1000 ms` in `click(BACK_TAG, CHILD_TAG)` (`TabHostTelemetryWiringTest.kt:155`), the same wait as before the fix |

The Child tag never reappears after Back: the tab-local screen pushed on the tab navigator is not restored after the
root push is popped. See M2 for the same behaviour on the device.

## M1 (AC-10): PASS

Cold start, Home, Session log on, DPAD_CENTER on a Home tile (Details), KEYCODE_BACK, 3 s wait. Session lines after the
open: `nav push_result Home->Details`, then `nav cmd=back from=Details to=Home` (`scr=Home`), `act Resumed scr=Main`
(host VM label, as specified), `act OnResume scr=Home`, `frames scr=Home`. Every `scr` after the Back record is `Home`
except the `frames` window that was open over Details (`scr=Details`, written after the `nav back` line, as designed)
and the `act Resumed` host line. No `scr=Main` line, no `nav tab Home->Home`, no `to=Main`. Screenshot after Back:
Home content, the same tile focused, no crash.

## M3 (AC-5): PASS

From Home: rail to Movies, DPAD_CENTER: `nav tab Home->ContentList`, `scr=ContentList`. Rail reselect of the same tab,
DPAD_CENTER: `nav tab ContentList->ContentList` recorded, `scr=ContentList` (a rail reselect builds a new `PuberTab`
instance, so the replay guard does not swallow it). Both tab lines present.

## M2 (AC-6): FAIL (label disagrees with the screen)

Concerts tab, "All" row, "Show all" button (tab-local push), DPAD_CENTER, then a tile in the grid, DPAD_CENTER (Details),
KEYCODE_BACK, 4 s wait.
- Log: `nav push_result ContentList->ShowAll`, `nav push_result ShowAll->Details`, then
  `nav cmd=back from=Details to=ShowAll`, `act Resumed scr=Main`, `act ItemFocused scr=ContentList`, and a later
  `meta dropped scr=ShowAll` line, so `scr` stays `ShowAll` after Back.
- Screenshot after Back (5 s, 2 s later again): the Concerts catalogue row ("All" with "Show all") is on screen, not the
  Show-all grid. The tab-local screen was lost when the tab navigator was disposed under Details. A second run through
  Details from the grid returned to the catalogue row as well.
- Net: the frozen top (`ShowAll`) is what the tab navigator held at dispose, but it is not what is shown after Back, and
  nothing re-announces the real screen: `scr=ShowAll` while the user sits on ContentList. Before the fix the replayed
  `Open` would have reset `scr` to the tab root (`ContentList`), accidentally right. The AC-6 instrumented failure is the
  same condition.

## Other observation (not attributed)

After the long session above, the Settings tab showed a spinner and never loaded (reselect and a Home/Settings round trip
did not help). After `am force-stop` and a relaunch it loaded at once. No `Main` or `X->X` lines are involved; cause not
established, not compared against the base APK.

## Evidence audit

`tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage`: `evidence_audit_status=passed` (19 files scanned).

# Round 2 (worktree HEAD `e38d19fe`)

The round-1 AC-6 result above is superseded: AC-6 was rewritten (the label names the screen shown after Back, today the
tab root) and the fix is `f7862f5f`. Notes on no raw logs: only `nav`/`act`/`scr` fields were read from the session file.

## Build and device

- Device `emulator-5554` (`puber_tv_36`), lease held and released, emulator left running.
- Build: own `:app:assembleDevDebug`, sha256 `2ccd0e77...4a99`. `tools/android-apk-install-preserve`: outcome `installed`,
  `compatible_replace`, `destructive_action: false`, signer `6b291812...49a83e` unchanged. No uninstall, clear or downgrade.
- Session log switched on and off through the D-pad (Settings > For developers); off again at the end.

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL; 1995 unit
tests, 0 failures, 0 skipped; detekt and lint green. Most tasks were up to date from the implementer's run on the same
inputs.

## Instrumented class TabHostTelemetryWiringTest

- Failing first, at `70200dee` (new test, old production code): 6 tests, 1 failed:
  `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`, `expected WiringHomeScreen but was WiringChildScreen`
  (`TabHostTelemetryWiringTest.kt:164`). Branch restored afterwards, HEAD `e38d19fe`, tree clean.
- On `e38d19fe`: 6 of 6 passed (exit 0).

## M2 (AC-6): PASS

Concerts tab, "All" row, "Show all" (tab-local push), DPAD_CENTER; a grid tile, DPAD_CENTER (Details); KEYCODE_BACK; 4 s.
- Screenshot after Back: the Concerts catalogue row ("All" with "Show all"), the tab root; focus on the tile that was
  focused before. The Show-all grid is not shown.
- Session lines: `nav push_result ContentList->ShowAll`, `nav push_result ShowAll->ShowAll` (1 ms before the next, see
  observation), `nav push_result ShowAll->Details`, then `nav cmd=back from=Details to=ContentList` with `scr=ContentList`,
  `act Resumed scr=Main` (host label), `act ItemFocused scr=ContentList`. Every non-key record after the Back line has
  `scr=ContentList` or the host `Main` action; no `ShowAll`, no `nav tab X->X` from the Back.
- Second pass, Concerts root tile -> Details -> Back: `nav back from=Details to=ContentList`, same result.

## M1 (AC-10): PASS

Home: rail Home, a bookmark tile, DPAD_CENTER (Details), KEYCODE_BACK: `nav push_result Home->Details`, then
`nav back from=Details to=Home`, `act Resumed scr=Main`, no `nav tab Home->Home`, no `to=Main`; Home screen shown, same
tile focused.

## M3 (AC-5): PASS

`nav tab DeviceSettingsFlow->ContentList` (cold start into Concerts), `nav tab ContentList->Home`,
`nav tab Home->ContentList` (Movies), `nav tab ContentList->ContentList` on rail reselect, twice.

## Observations (not attributed)

- A `push_result ShowAll->ShowAll` record appears right before `ShowAll->Details` on item selection in the Show-all grid
  (same instant). Not part of any AC; not compared with the base APK.
- Settings loaded normally in this session (the round-1 spinner did not recur).
- Several navigation attempts landed on Details or Similar pages through D-pad misfocus (rail not focused when assumed);
  no playback was started, no state changed on purpose.

# Review round 1 fixes (worktree HEAD `b256e3a5`)

Fixes under test: triage-1 #1 (unbind freezes the top even while telemetry is off), #2 (AC-6 itest asserts the tab-local PUSH), #3 (`resetForTests`), #4 (2-arg `bind` removed).

## Build and device

- Device `emulator-5554` (`puber_tv_36`), lease held and released, emulator left running.
- Build: own `:app:assembleDevDebug`, sha256 `07fdbb0a...0672`. `tools/android-apk-install-preserve`: outcome `installed`, `compatible_replace`, `destructive_action: false`, signer `6b291812...49a83e` unchanged. No uninstall, clear or downgrade.
- Session log toggled through the D-pad only (Settings > For developers); OFF again at the end (screenshot checked).

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL (most tasks up to date from the implementer run on the same inputs; lint re-ran). `NavTelemetryTest`: 18 tests, 0 failures, 0 skipped, including `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` and `unbindWithTelemetryOffRecordsNothing`.

## M4 instrumented class (AC-6)

`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`: 6 tests, 0 failed (exit 0).

## M1 (AC-10) re-run: PASS

Rail Home, a bookmark tile, DPAD_CENTER (Details), BACK. Session lines: `nav push_result Home->Details`, `nav back from=Details to=Home` (`scr=Home`), `act Resumed scr=Main` (host label). No `nav tab Home->Home`, no `to=Main`; Home shown, same tile focused.

## Finding #1 on device: NOT CHECKED (not reachable)

The brief path (open Settings from the rail as a root push, enable the log there, Back) does not exist in this build: the rail Settings item is a tab (`TabType.Settings`, `nav tab ... DeviceSettingsFlow`), and `MainVM.onSettingsClick` (the root push) has no caller in the UI. Enabling the log on the Settings tab and pressing BACK only expands the rail and records no `nav` line; the host is not covered, so this is not the finding scenario. No other root-pushed screen exposes the session-log toggle. The finding is covered by the JVM test `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (green) only.

