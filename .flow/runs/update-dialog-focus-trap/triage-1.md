# Review triage - round 1

Run: update-dialog-focus-trap   Base: 0bc3ab0e   Reviewed head: 5994eedd

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial; clean).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, edge F2 | major | `UpdatePromptOverlay.kt` DownloadingContent; `ContentFocusHandoff.kt:73` | After Update the Downloading state has no focusable node, focus leaves the overlay and the rail's HandingOff redirect sends it into the gated content | Read DownloadingContent (no focusable) and redirectFocusToContent (plain requestFocus, no gate check); scenario holds | fix | Give the states without buttons (Downloading, Checking) a focus target that the primary requester points to, so focus never drops out of the overlay |
| 2 | blind F2 | minor | `App.kt:139`, `UpdatePromptOverlay.kt:59` | Checking gates the screens but takes no focus; Enter on the settings item beneath restarts the check | Same root as #1; Q4 had accepted it, the review shows a concrete double-check | fix | Folded into #1 (cheap); Q4 revised |
| 3 | edge F1 | major | `DetailsScreenContent.kt:392-396`; `PlayerScreenContent.kt:83-97`; `ResumeDialog.kt:51` | Content-link cold start opens Details or Player over Main; their launch focus claims ignore the gate and take focus from the prompt | Read the Details ActionRow LaunchedEffect: unconditional requestFocus; LauncherVM builds newRootScreens(main, target) under the root gate | fix | Gate those claims on LocalContentFocusActive (keyed); qa adds a content-link launch to M1 |
| 4 | tests F1 | major | evidence round 2; `MainScreenComponent.kt:104-119` | Back dismissal in the AC-2 order (prompt over focused content, Settings path) never observed | Evidence file: Settings path dismissed only with Not now | fix | qa round 3 runs Back on the Settings path and records it |
| 5 | tests F2 | minor | `ContentFocusGateTest.kt:75-98` | Re-arm test never reaches the D4 branch | Read the test: gate closed from the start, no coroutine scheduled | fix | Reopen the gate at the end of launchRequestScheduledBeforeGateClosesDoesNotTakeFocus and assert focus |
| 6 | tests F4 | minor | `UpdatePromptOverlayFocusTest.kt:131-152` | D-pad test does not assert focus reached Not now | Read the test | fix | assertFocusOn after Right and after the last Left |
| 7 | tests F5 | minor | `UpdatePromptOverlayFocusTest.kt:97-116` | No test fails if the retry loop is reverted | Implementer's own record: guard passes on base | fix | Late-attaching button so a single attempt fails |
| 8 | blind F3, tests F3 | minor | `InitialDrawerContentFocusTest.kt`; `FlowComponent.kt:659-680` | Main's gate wiring and D8 have no instrumented test | Read the test: copies the LaunchedEffect | defer | new run update-gate-wiring-tests |

## Rejected codex claims

- none (codex clean)

## Next

- Fix round: implementer fixes #1-#3, #5-#7; then check.fast, edge + codex + tests lenses on the incremental diff from 5994eedd, qa round 3 on AC-1..AC-6, AC-8 incl. #4 and the content-link launch; unproven lines: 8
