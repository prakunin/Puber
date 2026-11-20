CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:1000-1006 - When A→B is interrupted mid-entrance by a B→C change, C's outgoing layer copies B's partial still alpha and offset. `outgoingAlpha` is now a plain `val` and `outgoingOffset` does not animate until C settles. B's own job still finishes and clears A's layer within about 250 ms. If C's poster is cold and slow, the panel then shows B's picture dimmed and shifted over the panel background, with an edge strip, until C loads or the 3 s hold ends. Scenario: hold the D-pad across three cards while the third poster is not cached. Nothing here is a crash or a leak. It only weakens the opaque-hold guarantee on a rapid double change followed by a slow load. I graded it minor because it needs a cold poster after a mid-motion retarget and lasts only for the load time. A fix would let the captured layer finish to alpha 1 and offset 0 while it waits. I confirmed this by reading the diff. No test or device run covers it.
REJECTED:
- Codex `review` pass: reported no actionable regressions. Nothing to adjudicate.
VERDICT: changes needed
