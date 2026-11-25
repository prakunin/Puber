LENS: state-path  ROUND: 4  BASE: ef5987ec (incremental b0cee092..d6ec6134)

### F1 [minor] `tabPickHandoff_arrivingEmptyResetTakesFocus` never starts a TabPick handoff, so it passes on the round-2 code it is meant to catch
- Where: ShowAllRailCloseFocusTest.kt:233-248
- Problem: `beginHandoff(TabPick)` from Closed returns null (`ModalNavigationDrawer.kt`); the return is not checked; the Initial handoff has settled; `expectsNewContent` false; Empty takes «Сброс» through the cold-open path.
- Failure scenario: restore the round-2 guard; the test stays green.
- Trace: setContent -> beginInitialHandoff(1) -> grid focus -> settle(1) -> Closed; beginHandoff -> null; Empty composed with bornDuringRequest null, pending null; request fires with or without the guard.
- Fix: Left, wait Open; `runOnIdle { assertNotNull(beginHandoff(TabPick)); host.state = Empty }`; assert «Сброс» focused; record a red run against the round-2 guard.

Production change holds on all four paths: outgoing pick skips (ids increase; flips with contentFocusActive in one snapshot; outgoing tab disposed via key(currentTab.key)); arriving return fires and the FlowComponent handoff settles on it; rail Dismiss unchanged; cold open unchanged. Ruled out: Empty first appearing on the outgoing tab during the pick window takes «Сброс» only transiently (tab disposed on swap; arriving effect retries).
