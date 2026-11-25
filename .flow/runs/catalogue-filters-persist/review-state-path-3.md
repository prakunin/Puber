LENS: state-path  ROUND: 3  BASE: ef5987ec (incremental 18d72ea4..b0cee092)

### F1 [minor] The new guard also stops «Сброс» on the arriving tab, so going back to a tab whose top screen is the filtered-empty grid puts focus on «Фильтры»
- Where: ShowAllScreenContent.kt:192-196
- Problem: `expectsNewContent` is true for the whole TabPick handoff for both tabs; it does not say whether this content is outgoing. `ContentFocusHandoffEffect` checks that with `bornDuringRequest != requestId` (`ContentFocusHandoff.kt:108,115`). An arriving ShowAll skips its request; the handoff's blind `contentFocusRequester.requestFocus()` (after 5 grace frames) lands on «Фильтры» (`filtersCanFocus` rememberSaveable true), sets `headerHoldsFocus`, settles; the restarted effect then does nothing. The plan's "fires once the handoff settles" does not happen.
- Failure scenario: tab A shows the filtered-empty grid with focus on «Сброс»; Left, pick tab B, open the rail, pick tab A: focus ends on «Фильтры». Only when the Empty state survives the tab switch. Down reaches «Сброс» (minor).
- Trace: beginHandoff(TabPick) id N; tab A composes with bornDuringRequest N; ShowAllEmpty skips; handoff requestFocus lands on «Фильтры»; settle(N), flag false; effect restarts, headerHoldsFocus true, nothing. Final: «Фильтры».
- Fix: `val bornDuring = remember { LocalContentFocusHandoff.current?.pendingRequestId }`; skip only when `expectsNewContent && bornDuring != pendingRequestId`. Or accept and correct the plan sentence.

Requested traces: tab pick away from the empty grid - fixed (flag and contentFocusActive change in one snapshot; outgoing ShowAll gone before settle). Rail Dismiss back - correct. Cold open - unchanged (no pending handoff).
