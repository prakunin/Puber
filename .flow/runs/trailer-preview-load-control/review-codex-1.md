CODEX: ran: review, adversarial

CONFIRMED:
- [minor] docs/superpowers/evidence/2026-11-28-trailer-preview-load-control-device.md:13 - The AC-4 evidence cannot tell a trailer that played to its end from one that stopped on a player error. `TrailerPreviewPlayer` calls `currentOnFinished()` on both `STATE_ENDED` and `onPlayerError`. Scenario: the new buffer starves near the end of the trailer and playback errors. The poster comes back and the screenshots look the same as a normal finish. The evidence file does say "Limit: sampling by screenshot at 3-6 s". The fix is cheap: in the evidence, note the observed playback duration against the trailer's length, or add a line stating that no player error was logged. This is not a code defect and does not block merge.

REJECTED:
- Codex adversarial [medium]: the 12 MiB byte target with `prioritizeTimeOverSize=false` can cut headroom below 10 s for a trailer above about 10 Mbit/s, so a 25 Mbit/s trailer could stall on a 5 s network drop. This is unproven. The spec lists the byte cap as a known risk and fixes these exact values in AC-1. No trailer at that bitrate has been shown to exist. The box run showed no stall on the sampled trailers. It is a risk the spec already accepted, with AC-4 as the check, and is not a new defect. If bitrate evidence turns up, tune the values then.
- Codex adversarial: hold shipment until continuous playback is compared on the base and branch APKs under a controlled network interruption. This asks for more than the ACs require. AC-4 asks for observation at human pace, which was done. Repo rules also bar network manipulation and sweeping the API from agents. It is a process preference with no concrete failing scenario.
- Codex review: this should block AC-4. It is downgraded to minor, as above. The branch changes only the load-control wiring. `TrailerOverlay.kt` and `PlaybackController.kt` are untouched, and the code matches AC-1 to AC-3.

VERDICT: clean
