CODEX: ran: review, adversarial   RANGE: c6ea3789..7ca85b93
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:537-539 - `textlessReport` treats every empty text-track report as transient, with no end condition.
  - Scenario: manifest-only subtitles are playing on an HLS stream and the viewer picks a row. A quality switch or fallback then lands on a stream with no text groups and no API side-loads.
  - Result: each later empty report keeps the old rows and `selectedSubtitleIndex`, so the picker shows an unrenderable subtitle as selected. The `rowIndex` lookup always finds the row in the retained list, so `selectSubtitle(off)` is never sent. This contradicts the AC-6 disappearing-row rule (dropped row ends at Off). Before the fix round the empty report cleared the rows.
  - Fix direction: keep the rows only while the stream is switching or preparing. Reconcile when the player is ready, or after a short window. Add a test where the replacement stream ends with no text tracks.
  - Both the plain review and the adversarial pass raised this independently; re-derived from the code. Narrow edge case, not a regression of the quality-switch fix itself.
REJECTED:
- Adversarial: the quality-switch fix "needs another fix and device verification" as a whole - same issue as the confirmed finding; not counted separately.
- Plain review: "other changes appear consistent" - no finding to adjudicate.
VERDICT: changes needed (one minor)
