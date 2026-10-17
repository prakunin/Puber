# QA report

## Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 1996 unit tests, 0 failed, 0 skipped; most tasks up to date on same inputs, lint re-ran)
DEVICE: emulator-5554 (leased, released, left running); worktree's own APK installed via install-preserve (outcome installed, compatible_replace, on-device sha256 = built sha256)
Instrumented: TabHostTelemetryWiringTest 10/10 green; TabFlowLifecycleTest 1/1 green.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (scroll: same rows, shifted one row by focus pivot) | M1 twice on Concerts Show all: after Details + Back the grid is shown, not the tab root; the opener card is on screen. Also Movies and 4K. Scroll position is restored to the same rows, but the grid ends about one row lower because focus lands on the first visible row and pivot-scrolls |
| AC-2 | PARTIAL (grid-level focus only) | Focus after Back is inside the grid and live (RIGHT/LEFT move it), but NOT on the opener card: run 1 opener Hades -> ring on Sam Smith; run 2 opener Lera Lynn -> ring on Snoop Dogg. Both times it is the first card of the first partially visible row. Owner/conductor decides D9 (ShowAll-local fix) vs follow-up (Q1/Q5) |
| AC-3 | PASS | Movies and 4K each with a Show all stack, a root push + Back on 4K, switch to Movies: its grid shown, back to 4K: its grid shown; T2 green on device |
| AC-4 | PASS | session log: `nav back from=Details to=ShowAll`, `scr=ShowAll`, no later line names ContentList; T1 green |
| AC-5 | PASS | TabHostTelemetryWiringTest 10/10 on device (pre-fix 3 red) |
| AC-6 | PASS | grep: only isEnabled hit in NavTelemetry.kt is :63 in recordNav, outside unbind; NavTelemetryTest green in unit run |
| AC-7 | NOT CHECKED (device) / T4 green as stand-in | no logout (worktree build cannot re-login). replacedHostStartsAtTabRootsWhenShownAgain passes in the 10/10 class |
| AC-8 | PASS | tab switches keep stacks (M6/AC-3); Settings tab after a root push: content rendered at 1.5 s and 4.5 s, no spinner, so nothing to record in settings-tab-spinner-stuck from this path |
| AC-9 | PASS | Home bookmarks card -> Details -> Back: exactly one `act OnResume scr=Home`, same card focused; resumeIsDelivered... tests green |
| AC-10 | PASS | TabFlowLifecycleTest 1/1 green on device |
| AC-11 | PASS | static gate green; docs/architecture/navigation.md Tabs names holder owner, key, reason, lifetime |

Plan Task 5 (where focus lands after Back in the Show all grid): not on the opener card, not on the first card of the grid, not nowhere, not on the rail. It lands on the first card of the first (partially) visible row of the restored scroll position, and the grid then pivot-scrolls to it. So the D9 condition (focus not on the opener card) is met; Task 5 applies unless the owner takes the fallback.

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push/docs/superpowers/evidence/2026-10-17-tab-stack-lost-on-root-push-device.md (commit a5568145; mobile-evidence-audit passed)
DEFECTS: none blocking AC-1/3/4/5/8/9/10. AC-2 is the open item above.
UNAVAILABLE: none
MANUAL: AC-7 real logout/relogin on a build that can log in (M4). M2 (rail open) not run because D9 is not implemented.

Other observations, not attributed (base not re-checked): (1) first entry into a Show all grid from the tab root, before any root push, left focus on the expanded rail instead of the grid on Concerts and Movies; (2) Settings tab shows "General" again after leaving and returning (previously "For developers").
Session log turned off at the end; lease released.

## Round 2

Worktree HEAD 6b47873d (evidence commit 59cdb9c3). Worktree status clean before the run.

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, 44 tasks, 1 executed, rest up to date on identical inputs from the implementer run at this HEAD)
DEVICE: emulator-5554 (leased, released, left running); worktree's own APK installed with install-preserve (installed, compatible_replace, destructive_action false, on-device sha256 = built sha256 c81e9e4c...54a8)
Instrumented (make itest, emulator-5554): ShowAllScreenContentFocusTest 2/2 green; TabHostTelemetryWiringTest 10/10 green; TabFlowLifecycleTest 1/1 green.

ACS:
| AC | Status | Evidence | Re-run |
|----|--------|----------|--------|
| AC-1 | PASS | M1 on Concerts Show all, two openers (Swan Lake in the second visible row, Blues Brothers Band deeper): after Details + Back the grid is shown with the same rows at the same positions as before Details; the one-row shift of round 1 is gone | yes |
| AC-2 | PASS | Focus ring on the opener card in all three runs (Swan Lake, Blues Brothers Band, Katseye in the left column), also after a tab switch away and back (Movies Tenzing, 4K Silo); ShowAllScreenContentFocusTest 2/2. Round 1 PARTIAL is closed | yes |
| AC-3 | PASS | Movies and 4K each with a Show all stack and Details + Back; rail Movies -> 4K -> Movies -> 4K: each shows its own grid, never its root | yes |
| AC-4 | PASS | session log: every return `nav cmd=back from=Details to=ShowAll`, `scr=ShowAll`, no ContentList line after | yes |
| AC-5 | PASS | TabHostTelemetryWiringTest 10/10 | yes |
| AC-6 | PASS | unchanged since round 1 (static read, no production change in navigation) | no |
| AC-7 | NOT CHECKED (device) / T4 green as stand-in | no logout (worktree build cannot re-login); replacedHostStartsAtTabRootsWhenShownAgain in the 10/10 class | stand-in only |
| AC-8 | PASS | tab switches keep stacks; Settings tab rendered "General" at 1.5 s and 4.5 s, no spinner | yes |
| AC-9 | PASS | Home bookmarks card -> Details -> Back: exactly one `act OnResume scr=Home` after the back, same card focused | yes |
| AC-10 | PASS | TabFlowLifecycleTest 1/1 | yes |
| AC-11 | PASS | docs unchanged since round 1 | no |
| M2 (K13) | PASS | rail opened 0.5 s after Back from Details: focus on the rail item at 0.5 s and 4 s, grid behind at the restored position, not stolen; DPAD_CENTER on a rail item switched the tab | yes |

TESTS ADDED: none (ShowAllScreenContentFocusTest was added by the implementer and ran green)
EVIDENCE: /home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push/docs/superpowers/evidence/2026-10-17-tab-stack-lost-on-root-push-device-round2.md (commit 59cdb9c3; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: AC-7 real logout/relogin on a build that can log in (M4).

Notes: (1) A LEFT sent in the instant after Back, before focus existed, is dropped by the app (no rail, focus then on the opener); that is input timing, not a focus steal. (2) Clicking the rail item of the tab already shown resets that tab to its root (`nav cmd=back from=ShowAll to=ContentList`); seen when my script sent DPAD_CENTER with the rail open, not attributed to this change. (3) Round 1 observations unchanged and not attributed: first entry into Show all leaves focus on the rail; Settings tab shows "General" again after a tab switch.
Session log turned off at the end (screenshot checked); lease released.

## Base comparison: Settings sub-selection

Triage-1 row #4 / review-tests F2. Build: master f14dcc84 (base), main checkout dev debug APK, installed with install-preserve (outcome `installed`, compatible_replace, data preserved). Device: emulator-5554 (leased, released).

Steps (D-pad only): Settings tab -> list -> "For developers" selected (section shows "For developers") -> rail -> Movies (New/All content visible) -> rail -> Settings.

| Pass | After returning to Settings |
|------|-----------------------------|
| 1 | "General" |
| 2 | "General" |

Result: base RESETS the Settings sub-selection after a tab switch (General shown, twice; a third incidental pass via History also showed General). The reset is therefore not a regression of this run (HEAD behaves the same); `settings-subselection-reset` stays a separate idea. Row #4 can be closed as "pre-existing".

Side note: an earlier scripted attempt mis-navigated the rail (rail selects only on CENTER, content grabs focus after a tab opens) and briefly opened a History detail and started a movie player for a few seconds before BACK; no other mutation. Evidence screenshots were kept outside the repo; no account data in this section.

## Review round 1 fixes (HEAD b5919b85)

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, mostly up to date on same inputs)
DEVICE: emulator-5554 (leased, released, left running); worktree APK installed via install-preserve (installed, compatible_replace, on-device sha256 = built sha256)
Instrumented: ShowAllScreenContentFocusTest 5/5, TabHostTelemetryWiringTest 10/10, TabFlowLifecycleTest 1/1 green.

| AC / row | Status | Evidence |
|----------|--------|----------|
| AC-1 | PASS | Concerts Show all, Details + Back on a second-row card and on a card many rows deep: same grid rows, not the tab root |
| AC-2 | PASS | Focus ring on the opener in both runs (Hades; Valeriy Leontiev, deep in the grid, outside the first viewport); FocusTest 5/5 |
| M2 (triage #1) | PASS | Rail opened 0.4 s after Back keeps focus (Concerts, then DOWN to Documentary movies); focus not pulled into the grid |
| Opener-removed fallback (triage #2) | NOT CHECKED on device | needs hide-watched reload (playback to the end, excluded); evidence is the itest neighbour-fallback case, 5/5 green |
| Scrolled case (triage #3) | PASS (itest) | new FocusTest cases green; red half not run |

TESTS ADDED: none
EVIDENCE: docs/superpowers/evidence/2026-10-17-tab-stack-lost-on-root-push-device-round3.md (committed 62825360; audit passed)
DEFECTS: none
