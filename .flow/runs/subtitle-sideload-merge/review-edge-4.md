LENS: edge  ROUND: 4  BASE: a97ee2b1  RANGE: 2903fc50..871b06a6

No findings.

The fix closes edge-3 F1 and breaks none of the three paths checked (PlayerVM.kt at 871b06a6: `restoreSubtitlePreference` :599-616, `onTracksUpdated` :536-578, `applySubtitleSelection` :869-882, `resetMediaSession` :994-995, `saveTrackPreferences` :1856-1862):
- Edge-3 F1: after Fast DNS / buffer preset the first report `[Off, English]` gives `shown = Off`; the saved `rus` does not resolve; the fallback only calls `playbackController.selectSubtitle(Off)` and `intendedSubtitleRow` stays Russian; the next report with Russian finds it by identity and step 8 sends it; `saveTrackPreferences` still writes `rus`. Test `rebuildFastDns_keepsIntent_whenOtherStreamLacksRow_thenRestoresItWhenRowReturns` asserts this.
- First media, saved row does not resolve: intent null, `shown = Off`, fallback sends Off - same controller call and intent as the old `applySubtitleSelection(Off, persist = false)`; nothing persisted; resolving path unchanged.
- Next-media deferral: intent and flags reset; a text-less report returns before the restore; the first report with text applies the saved row or sends the shown Off row; an audio pick while waiting passes the saved subtitle through.
- Picker never says Off while text plays: the fallback sends exactly the row the picker shows; out-of-range index sends nothing (unreachable after the text guard).
Read-only; tests not run by this reviewer.
