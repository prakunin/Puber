CODEX: ran: review
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt:20-22 - The KDoc on `preferredTextLanguage` says an empty preferred language means an undetermined row "shows nothing". That wording is stronger than the code. The branch only passes "" to Media3 and does not disable text selection. Media3's default-flag or forced-track selection can still pick a track. Scenario: a blank or `und` row has no override, and the stream has a default-flagged subtitle track. That track can still be shown. The code behaves as it did before the branch, so the defect is only the comment. Soften it to "prefers no language; Media3's default or forced selection may still apply".
REJECTED:
- Empty language preference should disable text selection for the fallback, and the device test should cover it - the behaviour is unchanged from base (Codex says the same). The diff only extracts a helper and adds string-level tests. The spec does not ask for guaranteed silence, so a code change would be scope creep. Only the comment wording stands, as the minor item above.
VERDICT: clean
