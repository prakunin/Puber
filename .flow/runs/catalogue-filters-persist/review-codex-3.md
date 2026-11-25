CODEX: ran: review, adversarial

CONFIRMED:
- [minor] ShowAllScreenContent.kt:190-196 - the `handoffExpectsContent` guard reads the drawer-wide `LocalContentFocusHandoff.expectsNewContent`, so it also suppresses the Reset request on an arriving ShowAll: returning to a tab whose retained stack ends on a filtered-empty ShowAll; `filtersCanFocus` saved true; the handoff's generic `requestFocus` can land on «Фильтры» and set `headerHoldsFocus`; when the flag goes false the effect skips Reset. Focus ends on «Фильтры», one Down away from Reset. Not ruled out statically; not a blocker; needs a retained filtered-empty ShowAll on a tab (not verified in ordinary use). Fix: defer only the outgoing instance with the `bornDuringRequest != requestId` check (ContentFocusHandoff.kt:115), or accept and record.

REJECTED:
- [P2] verify the tab-pick handoff on device; the renamed test does not exercise a tab pick - a verification-process point, not a defect; reasonable under AGENTS.md.
- The edge-2 F1 scenario itself - the guard fixes it.

VERDICT: changes needed (minor only; the conductor may accept it as a known residual)
