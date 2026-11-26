# Review codex, round 1 (03265fbc)

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt:52-56 - refreshTransport() launches an independent IO read each time and never cancels the earlier one, so a slow older read can overwrite a newer snapshot (screen opens with its read in flight, user presses Start, network switches Ethernet -> Wi-Fi, run-start read finishes first, older read publishes Ethernet beside the new results). Window is milliseconds; next refresh corrects it. Fix: keep one transportJob and cancel it before relaunching. The AC-5 test finishes the first read before the run starts, so it does not cover this ordering.
REJECTED:
- AC-7 D-pad and locale verification incomplete because the device run never reached diagnostics - evidence state, not a defect; the account ban blocked the run; gate decision.
- Compose evidence predates the corrected focus assertion and does not exercise Voyager or Koin wiring - no concrete failing scenario; unproven.
VERDICT: changes needed
