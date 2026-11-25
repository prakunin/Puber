LENS: state-path  ROUND: 3  BASE: f88f3079

### F1 [minor] The isOpen guard drops a tab pick that corrects a mistaken pick while the new tab is still loading
- Where: MainScreenComponent.kt:315
- Problem: a tab pick starts a handoff that can stay pending up to 5 s (ContentFocusHandoff.kt:24) while the arriving tab's first page loads; focus stays in the collapsed rail (icons and highlight visible, Up/Down still move). The guard now ignores Center there. Before, the second pick switched the tab (ItemSelected sent; beginHandoff null; the newer tab composed during the pending request settled it).
- Failure scenario: rail Open on Home; pick Movies by mistake while its first page is in flight (HandingOff); Down to History, Center: ignored. Movies loads, settles: Movies with the rail Closed; the History pick is lost silently. If Movies is empty, the handoff fails after 5 s and the rail reopens.
- Fix: accept and document in spec Risk 4, or allow the click during HandingOff when handoffExpectsNewContent is true and block it only during a Back or Right handoff (expectsNewContent == false) and in Closed. The B3 test is a Back handoff, so it stays green either way.
- Trace: Open Center Movies → ItemSelected, beginHandoff(true), pendingHandoffId n, expectsNewContent true, HandingOff; Movies composed with bornDuringRequest n, nothing to focus, focus stays in the rail; Down → History icon; Center: isOpen false, ignored; Movies data → settleHandoff(n) → Closed on Movies.

Checked, no lost pick: Open (Center, Down/Up then Center; isOpen read at click time; re-picking the selected tab still starts beginHandoff(false)); Closed reached by Left from content (reveal in the same focus move); Closed via the unhandled-Left path (reveal before item focus); Closed after a failed handoff over a pushed screen (ignored until the clear, intended); HandingOff after Back or Right (ignored, B3 intent).
