# QA report: update-dialog-focus-trap

## Round 3

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, 46 tasks)
DEVICE: emulator-5554 (puber_tv_36), leased and released. Evidence build 1.12.1 (uncommitted, restored, git clean), Update never pressed, final smoke on committed 1.12.3-dev. Instrumented: ContentFocusGateTest 3/3, UpdatePromptOverlayFocusTest 7/7, PlayerLaunchFocusGateTest 2/2.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | 3 cold starts: Update focused at the first dump with the prompt (6.7-8.4 s, network) and 5 s later; Home loads beneath. |
| AC-2 | PASS | Settings path: Update focused at first dump after Center; Back-dismiss run too. |
| AC-3 | PASS | Cold starts, Settings path and Details link: Up/Down/Left/Right stay on Update/Not now, rail closed. |
| AC-4 | PASS | Back (cold run 1): prompt gone, hero focused; Settings path Back: see (a). |
| AC-5 | PASS | Eight D-pad moves in the prompt: node tree bounds identical before/after, one focused node, no card focus or scroll. |
| AC-6 | PASS | Back and Not now (cold): hero focused, Down first card, Right second card. |
| AC-7 | PASS | Instrumented updatePromptUpdateButtonSendsUpdateClicked; Update never pressed on device. |
| AC-8 | PASS | Not now: focus on settings section General, rail closed, Down/Up move within settings. |
| AC-9 | PASS | Unchanged since round 2 (device) and round 1 instrumented; not re-run. |
| AC-10 | PASS | check.full green. |
| (a) triage #4 | PASS | Settings path Available + Back: prompt dismissed, no rail items, focus on General [220,174][740,270], Down/Up/Right move within settings. |
| (b) triage #3 Details link | PASS | puber://content/items/100 cold: Update focused 1-9 s, D-pad stays in prompt, Not now and Back both land on Watch movie [22,718][251,814]. |
| (b) Player target | NOT CHECKED | action=play starts playback and writes account watch progress (mutation). Covered by PlayerLaunchFocusGateTest 2/2. |
| (c) Checking focus | NOT CHECKED on device | Checking lasts under ~1 s: a screencap burst caught the Checking prompt for one frame, uiautomator dump (~1 s) always saw Available; no extra Enter sent (could hit Update). Covered by instrumented updatePromptCheckingTakesFocusFromContentBeneath (pass). |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/update-dialog-focus-trap/docs/superpowers/evidence/2026-11-20-update-dialog-focus-trap-device.md (audit passed, committed in the worktree)
DEFECTS: none
UNAVAILABLE: none
MANUAL: optional, owner: Player content link (puber://content/items/<id>?action=play) with the prompt up, and the Checking state, if a slow network is at hand; both have instrumented coverage.

## Earlier rounds (short)
- Round 2: PASS, all 10 ACs (cold-start launch race fixed by b3307eb7 and 59a2ebb8; evidence build 1.12.1, never pressed Update).
- Round 1: FAIL. Defect 1 (cold start: Update never focused, D-pad moved Home beneath), defect 2 (dismiss left focus on the opened rail); AC-1/2/3/4/6/8 failed, AC-5 not checked.
