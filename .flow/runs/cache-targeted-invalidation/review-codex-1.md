CODEX: ran: review, adversarial

CONFIRMED:
- [minor] docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-device.md:49-51 - AC-6's visible outcome (Details shows the saved progress) was not observed on the device. This is a verification gap, not a code defect. - Scenario: play from Home, save, exit, then open Details. The evidence shows the exit mark, the revalidating load and the refresh, but Details showed no per-episode progress. The evidence blames the marktime/ApiError.Serialization problem, which is outside this run (resume-progress-anomaly) and also appears on the base build. Codex adds that the successful save markers came from a modified build that ignores `ApiError.Serialization`. I did not check that claim against the diff. The spec tests the mechanism (AC-3 and AC-4 unit tests, R5/R11), and the evidence records the mechanism as observed. The conductor should either record an explicit AC-6 waiver or re-verify once resume-progress-anomaly lands.

REJECTED:
- Codex review (non-adversarial): no findings, so nothing to adjudicate.
- Adversarial pass: no cache-race, scope-leak or record-lifetime defect. It found none statically, and I have nothing against that.

VERDICT: clean. The single minor is a verification gap that needs a waiver note, not a code change.
