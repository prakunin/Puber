LENS: edge  ROUND: 5  BASE: ef5987ec (incremental d6ec6134..3e5248d0)

No findings. Nothing run (instrumented); verdicts from reading ShowAllScreenContent.kt:183-200 and ModalNavigationDrawer.kt at 3e5248d0.

- `tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus`: passes on the fix, fails under its mutation. Empty remembers no id (initial handoff starts after composition); Reset[1] takes focus; `reveal()` Closed -> Open pulls focus into the rail (ModalNavigationDrawer.kt:290-292); `beginHandoff` returns 2, HandingOff; `contentFocusActive` and `outgoingDuringTabPick` both true restart the effect; on the fix no request and `ContentFocusHandoffEffect` steps aside (ContentFocusHandoff.kt:115); with `!outgoingDuringTabPick` deleted Reset[1] is focused, the rail does not pull back during HandingOff, and line 233 fails.
- `tabPickHandoff_arrivingEmptyResetTakesFocus`: passes on the fix, fails under the round-2 condition. Empty first drawn during request 2 remembers 2; the request fires; `ContentFocusHandoffEffect` (key Unit, no id) stays out of the way; under the round-2 condition the skip holds for the whole pending handoff and `waitUntil` times out after 12 s.
- Node count 2 holds in both. Red/green runs not yet recorded; qa-device on the box.

### Unproven
- ShowAllRailCloseFocusTest.kt:225, :244 both tests open the rail with `reveal()`; no test checks that Left from the centred empty-state «Сброс» reaches the rail and not «Фильтры».
