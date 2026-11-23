LENS: edge  ROUND: 2  BASE: 9158cf82
### F1 [major] The AC-7 PASS covers the clause "focus ring moves one row in every start", but the evidence holds one screenshot, and that screenshot may come from none of the 25 counted starts
- Where: docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md:130 (and :111)
- Fix: split the verdict. Latency clause PASS (0 of 25 over 199 ms). Focus clause checked on one start, name it, the other 24 NOT CHECKED; hand the gap to the owner at gate 2, or restate the clause.

### F2 [minor] The AC-6 limit "ready <= 2500 - 300 = 2200 ms" mixes two clocks, and set 1 contradicts it
- Where: evidence :129. The cap counts from begin() (RevealGate.kt:105, StartupReveal.kt:69), ready from process start; begin is about 125-155 ms after process start, so the limit is about ready <= 2330 ms on the line's clock. Set 1 rows 1, 5, 8 (ready 2286, 2307, 2283) are grace; row 6 (2323) is the first cap.
- Fix: state the limit as ready - begin <= cap - G (about 2330 ms on the line's clock); cite rows 1, 5, 8 and 6.

### F3 [minor] The AC-6 verdict gives the wrong ready range for set 2 and the control
- Where: evidence :129. Tables give 1662-2001 (set 2) and 1718-1911 (control); 1485 appears nowhere.
- Fix: replace the range.

### F4 [minor] The onRevealed KDoc states inFlight is above 0 for READY; the gate allows 0
- Where: app/src/main/java/com/kino/puber/core/startup/RevealObserver.kt:29; StartupMarks.kt:29 omits the held requests as the main GRACE reason.
- Fix: "can be above 0 for READY (requests started after ready) and is always above 0 for GRACE (held requests still running)"; add the held requests to StartupMarks.kt:29.

### F5 [nit] Manual step 4 attaches K2 "at detection +2.5 s" to the control starts, which have no detector
- Where: spec :159 (test design Manual step 4).
- Fix: anchored starts get K2; control starts get none.

Checked and correct: onImagesIdle KDoc vs RevealGate.kt:98; ready_in_flight / ready_drained text vs RevealGate.kt:73-74, :99; anchor-B figures (97-232, 126-330, 296-502 ms); anchor A 523-782 ms after the mark; 0 of 25 first keys over 199 ms.

### Unproven
- RevealObserver.kt:23 onImagesIdle is also skipped before begin() (the begun guard); matters only if a pre-begin image is expected as img_idle.

(Condensed by the conductor from the reviewer's return; findings, locations and fixes kept as returned.)
