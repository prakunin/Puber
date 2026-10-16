# Review triage - round 2

Run: telemetry-frames-keys-memory   Base: cd16eb47   Reviewed head: 5eba2142 (increment 284bd7a0..5eba2142)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial). Blind lens is not re-run on increments. qa-device round 2 (qa-report.md, Round 2): all re-checked ACs PASS, triage-1 #11 closed (evidence corrected, 91096592).

Triage-1 rows #1, #2, #3, #6-#10: confirmed fixed by edge, tests and codex (29622208..b11a39e3). Row #11: closed by qa.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F2, codex | minor | `TelemetryRuntime.kt:67-70,103-107` | Inline detach on toggle-off runs before a still-queued attach; the attach then starts frame tracking and memory sampling with the log off, and nothing detaches them (regression of triage-1 #3) | Read `setEnabled(false)`: `flushProbesInline` then `detachProbes(probesDetached = true)` posts nothing; `plantProbes` posts attach; `ActivityProbes.attach` has no enabled check; holds | fix | pending: posted attach returns early when the sink is off, and the idempotent detach is still posted after the inline flush; test toggles on then off and drains the main queue only afterwards |
| 2 | edge F1, codex | minor | `NavTelemetry.kt:21-28`, `TabComponent.kt:111` | `TabHostTelemetry.content` is process-wide, set only on `TabCommand.Open`, never reset and not the tab navigator's real top: a new MainScreen after NewRoot is named after the old tab, and Back to the host after a tab-local push names the tab root instead of the pushed screen (partial hole in triage-1 #2) | Read the assignment and `navClassOf`; both scenarios follow from the code | fix | pending: bind the value to the live tab navigator's top while the host is composed, clear it on dispose; tests for the stale-after-NewRoot and tab-local-push cases |
| 3 | edge F3 | nit | `NavTelemetry.kt:9,28` | core navigation imports the feature `MainScreen` to detect the tab host | Read the import; holds | fix | pending: marker interface in core implemented by `MainScreen` (cheap, and part of #2's rework) |
| 4 | tests F1 | minor | `NavigatorChangedByTest.kt:48-51` | FinishFlow is tested only with a one-screen child, so it cannot tell FinishFlow from Back | Read the test; holds | fix | pending: one assert with a two-screen child |
| 5 | tests F2 | minor | `FlowComponent.kt:228-229,272,315-322` | The #1 fix is tested against a copy of the pop logic; nothing ties `navigatorChangedBy` to the pop that actually runs | Read `navigatorToPop` vs `onBackWithNavigator`; holds | fix | pending: test runs the real pop routine (made internal) and asserts the navigator whose stack shrank is the one returned |
| 6 | tests F3 | minor | `NavTelemetry.kt:35`, `TabComponent.kt:111` | Neither `nav.from` on the tab host nor the tab host assignment is tested | Read NavTelemetryTest; holds | fix | pending: assert `from` is the tab content on a push from the host; the new binding from #2 gets its own test |

qa notes, not defects: one push writes two `nav` lines about 1 ms apart, and a redundant `nav tab` follows a Back to a tab; `scr` ends up right in both. The Back-pops-parent path could not be driven on this build (Back in Settings only moves focus) and stays unit-covered.

## Rejected codex claims

- Codex's suggestion that `navigatorChangedBy` / `navigatorToPop` may pick the wrong navigator: rejected by the codex-reviewer itself; it follows the same parent traversal as `onBackWithNavigator`.

## Next

- Review cap (2 rounds) reached with fixes outstanding. Both code findings are regressions introduced by the round-1 fixes and are cheap. Decision (owner, standing instruction: recommended): one extra bounded fix round 3 for rows #1-#6, then edge + codex + tests on the increment and qa-device on AC-12 / AC-S2-OFF; anything still open after round 3 goes to the owner, no round 4.
