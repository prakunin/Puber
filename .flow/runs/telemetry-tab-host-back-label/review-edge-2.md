LENS: edge  ROUND: 2  BASE: bdd2c792
### F1 [minor] The spec still says the freeze is skipped when telemetry is off, but `unbind` now always freezes
- Where: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md:77 (D8), :156 (D11), :172, :220 (R7), :251 (AC-9 test row)
- Problem: The increment changed `TabHostTelemetry.unbind` (NavTelemetry.kt:42-46). It now calls `handle.reappearingTop()` and stores a frozen `Binding` whether or not telemetry is on. It also removed the test `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`. Only the plan was updated. The spec still says four things that are now untrue:
  - D8: "The freeze work in `unbind` is gated on `Telemetry.isEnabled` ... with the log off nothing is read".
  - D11: the lambda "is never invoked" when off.
  - R7: "no read when off".
  - The AC-9 test row: it names the deleted test and requires "both invocation counts 0".

  AC-9's own wording ("no binding or freeze work records anything") still holds, and `unbindWithTelemetryOffRecordsNothing` checks it. The design text and the test-design row no longer match the code or any existing test.
- Failure scenario: A reader at gate 2, or the follow-up run `tab-stack-lost-on-root-push` that has to edit D12/D11, trusts D8. They "restore" the `Telemetry.isEnabled` gate in `unbind`, which brings back the round-1 F1 defect: telemetry turned on from Device Settings while the host is covered labels Back as `Main`. They may also look for an AC-9 test that does not exist.
- Fix: Amend the spec:
  - D8 and R7: the freeze in `unbind` runs regardless of `Telemetry.isEnabled`. It makes one read and allocates one object, and records nothing. Give the round-1 edge F1 reason.
  - D11: drop "never invoked when off".
  - AC-9 row: point to `unbindWithTelemetryOffRecordsNothing` and `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`.

The round-1 fixes hold:
- **#1 (edge F1)** `unbind` freezes unconditionally (NavTelemetry.kt:42-46). `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` binds with `top = { other }` and `reappearingTop = { home }`, so it fails if the gate comes back (the result falls back to `MainScreen`) or if the wrong lambda is frozen (`ScreenScreen`).
- **#2** The itest now asserts PUSH `from == WiringChildScreen` (TabHostTelemetryWiringTest.kt:156-160). Swapping `top` and `reappearingTop` in FlowComponent.kt:593-597 would turn it red. I did not run it, because it needs a device.
- **#3** `tearDown` calls `TabHostTelemetry.resetForTests()`, which clears frozen bindings no matter which handle made them.
- **#4** The two-argument `bind` is gone. The only production caller (FlowComponent.kt:593) uses the three-argument form, and the tests pass `reappearingTop` explicitly through `bindSame`.

The increment adds no new unhandled boundary:
- **Telemetry off at dispose**: the freeze now reads `navigator.items.firstOrNull()` there as well. That is a snapshot-stack read, it is null-safe, and it was already done when telemetry was on.
- **Frozen binding left after logout or NewRoot while off**: it is inert, because the host identity check in `topOf` skips it. This is accepted risk R5.
- **Tab changed while covered**: accepted risk R3, unchanged.
