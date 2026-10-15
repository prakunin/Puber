CODEX: ran: review, adversarial   RANGE: a99cdff9..630e2074

CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1866 - After an explicit pick of subtitle A, `subtitlePickedThisMedia` stays true until `resetMediaSession`. `onTracksUpdated` (lines 548-552) can then replace `intendedSubtitleRow` with a same-language, same-forced row B when a quality switch or rebuild reports B but not A. A later audio pick passes the `subtitleRestoredForCurrentMedia && subtitlePickedThisMedia` guard and stores B instead of A; the next media restores B, not the viewer's pick. The fix commit's own comment says only an explicit pick should replace the stored subtitle. Impact small: B is the row shown at that point, and the remap is base behaviour (triage #9 out of scope). Missing regression test (pick A, report B, pick audio, stored value stays A). Suggested fix: store a snapshot of the last explicit pick separately from the remapped row.

REJECTED:
- Codex plain review: no actionable regressions.

VERDICT: changes needed (one minor finding, which the conductor may defer)
