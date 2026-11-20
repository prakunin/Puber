# Gate - update-dialog-focus-trap - round 1

**Decision:** CONCERNS
**Head:** fd11c1fc   **Base:** 0bc3ab0e

## Done as asked

The diff (`git diff 0bc3ab0e...fd11c1fc -- app/src/main`) implements spec D1-D7: root gate from `App.kt` through `FlowComponent(screenFocusActive)` and `ContentFocusGate` (AND with outer values), D4 re-read in `rememberFocusRequesterOnLaunch`, directional `onExit` trap in `TvDialogOverlay`, retried claim in `UpdatePromptOverlay`, `outerActive` ANDed into both rail Back handlers. Additions from fix rounds stay inside the root gate: handoff held while gated (`ContentFocusHandoff.kt`), handoff started on gate close (`MainScreenComponent.kt`), `FocusHolder` in Checking/Downloading, Details `ActionRow` and Player claims gated. No window `Dialog`, no general root-modal API, `app/build.gradle.kts` untouched.

Deviations: D8 `GateFocusRestoreEffect` sets `saved[0] = true` whether or not `saveFocusedChild()` succeeded (`FlowComponent.kt` GateFocusRestoreEffect); D8 says restore only when the save returned true. The spec's Q4 row still reads "Checking keeps no focus trap" while the code and `state.md` Decisions (Q4 revised) give Checking a focus target.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims`, `ContentFocusGateTest` (3); M1 | qa-report round 3; evidence round 3, cold runs 1-3 | PASS |
| AC-2 | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`, `updatePromptRetriesTheFocusClaimUntilTheButtonCanTakeIt`; Settings path | evidence round 3, Settings path | PASS |
| AC-3 | `updatePromptDpadInAllDirectionsStaysInsidePrompt`, `tvDialogOverlayDpadDoesNotLeaveToContentBeneath`; M1 | evidence round 3, cold runs, Settings, Details link | PASS |
| AC-4 | M1 Back (launch order), Settings path Back (focused-content order) | evidence round 3, run 1 and (a) | PASS |
| AC-5 | M1 run 2, node-tree bounds before/after eight moves | evidence round 3, run 2 | PASS |
| AC-6 | M1 Back and Not now | evidence round 3, runs 1-3 | PASS |
| AC-7 | `updatePromptUpdateButtonSendsUpdateClicked` (`UpdatePromptOverlayFocusTest.kt:128`) | instrumented 7/7, round 3 | PASS |
| AC-8 | Settings path, Not now | evidence round 3; lands on section General, not the check row | PASS |
| AC-9 | `TvContextMenuLongPressTest`, `BookmarksScreenContentFocusTest`, `tvDialogOverlayClearFocusStillLeavesTextField`; ApiDomainDialog on device | evidence round 1 (instrumented), round 2 (device); `TvDialogOverlay.kt` unchanged since d845c534 | PASS |
| AC-10 | check.full | qa-report round 3 (10:56:17), after the last code commit 87842639 (10:40:32); fd11c1fc is evidence only | PASS |

Sampled: all twelve named tests resolve in `app/src/androidTest`; AC-7 asserts exactly `[UpdateClicked]`; `tools/mobile-evidence-audit.sh docs/superpowers/evidence` -> `passed`. check.full not re-run (qa-report newer than the last commit).

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Downloading/Checking drop focus out of the prompt (major + minor) | blind F1/F2, edge-1 F2; triage-1 #1, #2 | fixed | cf8938fb |
| Details/Player launch claims ignore the gate (major) | edge-1 F1; triage-1 #3 | fixed | 0fe2ffd7; evidence (b) Details link |
| AC-4 Back in focused-content order unobserved (major) | tests-1 F1; triage-1 #4 | fixed | evidence round 3 (a) |
| Re-arm test misses D4 branch; D-pad test lacks Later assert; retry loop untested (minor) | tests-1 F2, F4, F5; triage-1 #5-#7 | fixed | 87842639 |
| Main gate wiring and D8 untested (minor) | blind F3, tests-1 F3; triage-1 #8 | deferred | run `update-gate-wiring-tests` (stage idea) |
| `LaunchedEffect(state)` restarts per progress tick on a fast download (minor) | codex-2; triage-2 #1 | accepted | real, bounded to a few seconds after Update; re-read `UpdatePromptOverlay.kt:59-68` |
| Player Content-state gate checks untested (minor) | tests-2 F1; triage-2 #2 | accepted | gate present in `PlayerScreenContent.kt` both effects |

No rejections in triage-1 or triage-2; both accepts spot-checked against the code.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Two static locals flip twice per prompt (spec R4); retry loop bounded at 16 x 50 ms. No hot-path work. |
| Reliability | Back, empty states (Checking/Downloading) and dismiss covered on device; `updatePromptOpen` from a `SideEffect` cannot stick while the root host lives. D8 saved-flag deviation is untested; device dismisses landed correctly. |
| Maintainability | Follows AGENTS.md conventions; no duplicated helper; `FocusDelayMs` still used. Spec Q4 row is stale against the code. |
| Security / privacy | No secrets or account data in the diff; evidence audit passed; Update never pressed, Player link skipped to avoid an account mutation. |

## Manual checks for the owner

Optional; not ACs, both have instrumented coverage.
1. Player content link with the prompt: build the worktree with `currentVersion` lowered (Q2 recipe), cold `am start -a VIEW -d "puber://content/items/<id>?action=play"`; expect Update focused, D-pad stays in the prompt, Not now returns focus to the player controls. Writes watch progress to the account.
2. Checking state on a slow network: Settings > General > Check for updates now; while the spinner shows, press Down: focus stays in the prompt, the settings row beneath does not react.

## Must fix

none

## Concerns for the owner

1. Spec `docs/superpowers/specs/2026-11-20-update-dialog-focus-trap.md` Open questions Q4 still says Checking gets no focus trap; update the row to the revised decision before ship.
2. D8 deviation (`saved[0] = true` after a failed save) is unproven harm and untested; owned by `update-gate-wiring-tests`, which may remove D8.
3. Accepted minor: on a download whose progress ticks faster than 100 ms, the prompt may hold no focus until progress pauses (triage-2 #1).
