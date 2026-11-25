LENS: edge  ROUND: 4  BASE: ef5987ec (incremental b0cee092..d6ec6134)

### F1 [major] `tabPickHandoff_arrivingEmptyResetTakesFocus` never starts a handoff, so it would pass on the round-2 code it is meant to guard against
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:233-248; refusal at app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:158-165
- Problem: `beginHandoff` refuses from Closed (`DrawerValue.Closed -> null`). `setUpScreen()` starts Closed and the test never opens the rail, so `pendingHandoffId`/`handoffKind` stay null, `expectsNewContent` is false, and «Сброс» takes focus through the plain cold-open path.
- Failure scenario: restore the round-2 guard (no `bornDuringRequest` check) - the regression of state-path-3 F1 - and the test still passes.
- Fix: open the rail first (focus, Left, wait Open), assert `beginHandoff(TabPick)` returns non-null, swap to Empty, assert «Сброс» focused and `HandingOff`. With this setup only the `bornDuringRequest` check can move focus to «Сброс». Record the red run on the round-2 guard.

Production change holds: outgoing skips (the outgoing test is real: Open -> HandingOff flips contentFocusActive); arriving fires; restarted pick N then M treats the N-arrival as outgoing; cold-open initial handoff and Dismiss unchanged; Empty after settle fires; un-keyed `remember` is fine (ShowAllEmpty is recreated per Empty).
