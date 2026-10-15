CODEX: ran: review, adversarial (model gpt-6-astra)   BASE: f351d988  HEAD: d538e15b

CONFIRMED:
- none

REJECTED:
- [Codex adversarial, medium] `PlayerVM.kt:605-608`: after a rebuild, `onTracksUpdated` swaps `intendedSubtitleRow` for a different same-language variant when the original SDH row is gone and one other non-forced English row remains; an audio pick then saves the substitute. Does not hold as a defect of this branch: the swap code is unchanged from base (`findSameSubtitleTrackIndex` untouched; `intendedSubtitleRow = rows[shown]` at PlayerVM.kt:548-550 is the same at f351d988); the spec's out-of-scope list excludes changing the in-session matcher; base also saved from `intendedSubtitleRow`; the scenario needs different subtitle rows across a rebuild of one stream; the matcher still checks forced and falls back to language only with exactly one candidate; intent-first order (AC-8) does not make it worse. Pre-existing trade-off; possible follow-up.
- [Codex plain review] No actionable regressions.

VERDICT: clean
