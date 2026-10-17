# QA report, Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (1994 tests, 0 failed, 0 skipped; NavTelemetryTest 16/16)
DEVICE: emulator-5554 (puber_tv_36), leased and released, left running. Own dev APK installed via install-preserve (outcome installed, sha256 on device equals built 7d479292...88c9)

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | NavTelemetryTest.disposedBindingKeepsTheTopForTheSameHost green |
| AC-2 | PASS | NavTelemetryTest.frozenBindingOfHostADoesNotLeakIntoFreshHostB + existing staleBinding... green |
| AC-3 | PASS | 3 JVM tests (liveBind..., tabSwitchUnbindOldThenBindNew..., tabSwitchBindNewThenUnbindOld...) + itest tabSwitchThenRootPushAndBackNamesTheNewTab green (red pre-fix) |
| AC-4 | PASS | itest backOntoTheHostRecordsNoTabReannounce green (red pre-fix); device M1: no nav tab Home->Home after Back |
| AC-5 | PASS | itests opening...IsRecorded and reselecting...NewInstanceIsRecorded green; device M3: nav tab Home->ContentList and ContentList->ContentList both recorded |
| AC-6 | FAIL | itest tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen still fails at click(BACK_TAG, CHILD_TAG) wait (kt:155), same as pre-fix. Device M2 shows why: log says back to=ShowAll and scr=ShowAll, but the screen after Back is the ContentList row, not Show all |
| AC-7 | PASS | itest backFromRootPushedScreenNeverRecordsMain green (red pre-fix) |
| AC-8 | PASS | check.full green; only NavTelemetryTest and the new itest touched |
| AC-9 | PASS | NavTelemetryTest.unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt green |
| AC-10 | PASS | M1 on device: nav back from=Details to=Home, scr=Home throughout, act Resumed scr=Main, no nav tab Home->Home, no scr=Main; focus kept |

M1 PASS, M2 FAIL, M3 PASS, M4 5/6 (AC-6 fails).

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/docs/superpowers/evidence/2026-10-17-telemetry-tab-host-back-label-device.md (commit 6f9f162c; mobile-evidence-audit passed)

DEFECTS:
1. AC-6 / M2: after a tab-local push (Concerts > "All" row > Show all), then a root push (Details), Back: telemetry records back to=ShowAll and scr=ShowAll (frozen tab-navigator top), but the tab-local screen is not restored: the screen shows the ContentList row. scr stays ShowAll with nothing correcting it. Pre-fix the replayed Open reset scr to the tab root, so for this path the fix is a regression in label accuracy. The instrumented AC-6 test fails at the Child-tag wait with both pre-fix and post-fix builds, so the lost tab-local stack looks pre-existing (not compared against a base APK on device); the open question is whether the frozen label should be taken only when the stack survives, or the restore fixed. Repro: itest class above; or device M2 steps.
2. Observation, not attributed: Settings tab spun forever late in a long session after the Show all / Details / stray player sequence; fresh after am force-stop. Not reproduced, not compared with base.

UNAVAILABLE: none
MANUAL: none
Notes: one stray D-pad sequence briefly opened a player (position 0:00, closed with BACK, state "Not started"). Session log switched off again at the end.


---

# QA report, Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, 1995 tests, 0 failed, 0 skipped; most tasks up to date from the same inputs)
DEVICE: emulator-5554 (puber_tv_36), leased and released, left running. Own dev APK (sha256 2ccd0e77...4a99) installed via install-preserve, outcome installed, compatible_replace, nothing destructive.

Failing-first for the new AC-6 test: at 70200dee TabHostTelemetryWiringTest 5/6, red `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (expected WiringHomeScreen but was WiringChildScreen, kt:164). Back on prakunin/telemetry-tab-host-back-label, HEAD e38d19fe, tree clean. On e38d19fe: 6/6 green.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | re-run: full unit suite green (NavTelemetryTest included) |
| AC-2 | PASS | re-run: full unit suite green |
| AC-3 | PASS | re-run: JVM suite + itest tabSwitchThenRootPushAndBackNamesTheNewTab green (6/6) |
| AC-4 | PASS | re-run: itest backOntoTheHostRecordsNoTabReannounce green; device M1 no nav tab Home->Home |
| AC-5 | PASS | re-run: both itests green; device M3 tab Home->ContentList and ContentList->ContentList recorded |
| AC-6 | PASS | new itest tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack red at 70200dee, green at e38d19fe; device M2: after Show all > Details > Back the screen shows the tab root, log nav back to=ContentList, scr=ContentList, no ShowAll or tab X->X after Back |
| AC-7 | PASS | re-run: itest backFromRootPushedScreenNeverRecordsMain green; no to=Main on device |
| AC-8 | PASS | check.full green |
| AC-9 | PASS | JVM test in the green unit suite (round-1 test unchanged) |
| AC-10 | PASS | device M1: nav back from=Details to=Home, Home shown, same tile focused, act Resumed scr=Main only as host label |

M1 PASS, M2 PASS, M3 PASS, M4 6/6.

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/docs/superpowers/evidence/2026-10-17-telemetry-tab-host-back-label-device.md (Round 2 section, commit f5bfec56; mobile-evidence-audit passed, 19 files)

DEFECTS: none
Observations (not defects of this task, not compared with base): (1) a `nav push_result ShowAll->ShowAll` record 1 ms before `ShowAll->Details` when selecting an item in the Show-all grid; (2) the round-1 Settings spinner did not recur in this session.

UNAVAILABLE: none
MANUAL: none
Notes: some D-pad steps misfocused and opened Details/Similar pages; no playback started, no account state changed on purpose. Session log switched off again.


---

## Review round 1 fixes

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, NavTelemetryTest 18/18; mostly up to date from the same inputs)
DEVICE: emulator-5554, leased and released, left running. Own dev APK (sha256 07fdbb0a...0672) via install-preserve, outcome installed, nothing destructive. Session log off again.

| AC | Status | Evidence |
|----|--------|----------|
| AC-6 | PASS | itest class TabHostTelemetryWiringTest 6/6 on emulator-5554 (includes the new tab-local PUSH assertion) |
| AC-9 | PASS | NavTelemetryTest.unbindWithTelemetryOffRecordsNothing green (reworked per #1) |
| AC-10 / M1 | PASS | device: nav back from=Details to=Home, scr=Home, host Main only as act Resumed, no nav tab Home->Home |
| Finding #1 on device | NOT CHECKED | The described path is not reachable: rail Settings is a tab here, MainVM.onSettingsClick (root push) has no UI caller, no other root-pushed screen has the session-log toggle. BACK on the Settings tab only expands the rail, no nav line. Covered by JVM NavTelemetryTest.unbindWhileOffThenEnabledThenBackNamesTheTabRoot (green) |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/docs/superpowers/evidence/2026-10-17-telemetry-tab-host-back-label-device.md (Review round 1 fixes section)
DEFECTS: none
UNAVAILABLE: none
MANUAL: finding #1 end to end needs a root-pushed screen that can enable the session log (none exists today); otherwise the JVM test stands as the evidence.
