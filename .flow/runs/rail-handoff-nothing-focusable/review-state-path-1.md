LENS: state-path  ROUND: 1  BASE: c9e98129

No findings.

Traced at fda01538; none ends in a wrong state or loses focus:
- Back at a root (Dismiss): starts a Dismiss, arms, toast; a second Back during the pending handoff exits while the arm is live, else swallowed; a 5 s fail ends Closed and the rail LaunchedEffect clears leftover rail focus; then unhandled Left reveals, and Back (arm expired) reveals via RailClosedBackHandler.
- Pushed screens: no arm (!tabStack.canPop false), ExitDisarmOnTabPush clears earlier arms; Back during the handoff swallowed; fail ends Closed, as on base.
- Right: Dismiss, no arm; further Right swallowed; fail ends Closed. A stale arm survives only Back, settle, Left, Right within 3 s, which base exits on too.
- Center, Dismiss: acceptsPickOf and beginHandoff both refuse; re-picking the selected tab while Open goes through onRailAction (disarm) then a Dismiss.
- Center, Initial: pick of another tab → ItemSelected (disarm) then beginHandoff(TabPick) in the same click, new id before recomposition; arriving tab records it (remember(contentInstanceKey), FlowComponent.kt:657); outgoing tab stops at the outgoing-tab guard (ContentFocusHandoff.kt:115). Center on the selected tab refused. Initial fail ends Closed; after a pick a root fail reopens, as intended.
- Center, TabPick: corrective pick restarts with a new id; the mistaken tab cannot settle; budget restarts. Selected tab refused. Fail reopens at a root, Closed on a pushed screen.
- Dialog re-entry: beginInitialHandoff only from Closed, so a dialog over a pending Dismiss/TabPick keeps its kind; timeout gated on LocalContentFocusActive. The kind == Dismiss condition in the exit check (R8) matters: without it, a live arm when a dialog's Initial starts would let one Back close the app. With it, correct.
- Kind and paired fields: written together in startHandoff/settleHandoff/failHandoff; not saved; restore gives Closed.
