LENS: tests  ROUND: 5  BASE: ef5987ec (incremental d6ec6134..3e5248d0)

No findings. Static trace; nothing run (instrumented; mutation runs need production edits, qa-device's).

- `tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus`: green on the fix (initial handoff not a tab pick, [1] takes focus; `reveal()` pulls focus into the rail; `beginHandoff` from Open non-null (ModalNavigationDrawer.kt:161); `outgoingDuringTabPick` true, effect skips; `ContentFocusHandoffEffect` skips (ContentFocusHandoff.kt:115); rail focus unchanged so the HandingOff redirect (ModalNavigationDrawer.kt:339-340) never fires). Red under the mutation (relaunch requests [1]; `assertIsNotFocused` at :233 fails).
- `tabPickHandoff_arrivingEmptyResetTakesFocus`: green on the fix (Empty composed during the pending id; request moves focus to [1]). Red under the round-2 condition (no request, nothing focuses [1], waitUntil times out after 12 s).

review-tests-4 F1 and F2 fixed. F3 still open (qa-device): red and green on the box for both tests must be recorded before gate 2.

### Unproven
- ShowAllRailCloseFocusTest.kt:228-231 depends on the rail's focus not changing Open -> HandingOff; a green first run on the box settles it.
