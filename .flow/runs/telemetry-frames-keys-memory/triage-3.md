# Review triage - round 3

Run: telemetry-frames-keys-memory   Base: cd16eb47   Reviewed head: 4ccfb96c (increment 91096592..4ccfb96c)

Sources: review-edge-3.md, review-tests-3.md, review-codex-3.md (ran: review, adversarial). qa-device round 3 (qa-report.md, Round 3; evidence 4d9c4e31).

Triage-2 rows #1-#6: confirmed fixed by edge, tests and codex (ada5bcf5, d7419c23, dc31d2e1, 3c1a7208). qa round 3: tab switches, no TabRoot wrapper, push chain Back, relaunch without stale tab, toggle-off with probes detached, key_lat, mem, D5 spot all PASS.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex, qa defect 1 | minor | `FlowComponent.kt:588-591`, `NavTelemetry.kt:33-46` | Back from a root-pushed screen (Details) onto the tab host records `to=Main` and `scr=Main`, because the tab binding is disposed while the host is covered | Read the DisposableEffect unbind and the root runner's record order; qa reproduced it 4 times: `scr=Main` lasts 130-380 ms until the tab re-announces (`nav tab Home->Home`), one 2-frame window and one `act Resumed` line carry Main, then it settles on Home | defer | Cap reached (no round 4, recorded decision). Transient and self-correcting within 0.4 s on device; new run telemetry-tab-host-back-label |
| 2 | tests F1 | minor | `FlowComponent.kt:272,588-591`, `MainScreen.kt:39` | The composition wiring of the tab host binding and the runner's `navChanged` choice have no automated test | Read the tests: all drive `bind` / `recordNavCommand` by hand; holds. qa round 3 covered the wiring on device except Show all | defer | Folded into telemetry-tab-host-back-label (Compose-level test of the binding) |
| 3 | qa slip | unknown | side rail / tab host | After rapid BACK presses the rail showed Home with History content; LEFT did not open the rail; recovered by force-stop | Not reproduced or analysed; may predate the branch | defer | new run rail-content-mismatch-rapid-back |

NOT CHECKED carried: a true tab-local push (Show all) on device, no reachable Show all button; Back that pops a parent navigator on device (Back in Settings only moves focus), unit-covered.

## Rejected codex claims

- None.

## Next

- No fixes in this round (cap). Go to judge with #1-#3 deferred and listed for the owner.
