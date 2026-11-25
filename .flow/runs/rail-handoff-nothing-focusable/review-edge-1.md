LENS: edge  ROUND: 1  BASE: c9e98129

No findings.

Boundaries checked and handled: Back during a pending handoff (exits only for Dismiss + armed; arm only from Back at a root; tab pick and push/pop disarm; stale arm swallowed; armed Back during an Initial from a dialog swallowed; Left-Right-Back while armed already exited on base); picks during a handoff (acceptsPickOf and beginHandoff agree in every state; selected tab refused in Initial/TabPick; all picks refused in Dismiss); restart (new id; earlier content cannot settle; old id neither settles nor fails; arriving tab gets the full 5 s; ItemSelected before beginHandoff); failed handoff (kind cleared; reopen only for TabPick at a root; leftover rail focus cleared; Left and Back reopen); process death (kind not saved; HandingOff restores as Closed); require(kind != Initial) (no production caller passes Initial); dialog during a Dismiss (beginInitialHandoff refuses outside Closed; Dismiss paused; RailOpenBackHandler disabled).

### Unproven
- FocusOnLaunchRequester.kt:72 Content loading after an Initial handoff failed to Closed: rememberFocusRequesterOnLaunch asks once ~100 ms after composing and marks done even if it fails; ContentListScreenContent.kt:68 composes it before data loads. A cold start slower than 5 s may leave a real tab root (Home, Movies) relying on the rows' FocusTargetState effects alone; base reopened the rail. AC-6 covers only the probe. Real if a tab root stays with nothing focused after a throttled cold start.
- MainScreenComponent.kt:125 Dialog re-entry over content that just became empty (last favourite removed via the context menu): the Initial handoff now fails to Closed with nothing focused (base: Open). The spec table covers it, but AC-9 says "as on base".
- ModalNavigationDrawer.kt:163 Two Center presses within one frame restart the handoff with an id composed content cannot settle; rail reopens after 5 s. tv-material clicks fire on key-up, so probably impossible from a remote.
