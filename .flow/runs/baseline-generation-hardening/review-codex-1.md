# Review codex, round 1

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] tools/generate-baseline-profile.sh:82-84 (cleanup :52) - generation_started=1 is set before make validates the port, so a refused port still gets kill-server (BASELINE_ADB_PORT=5038 kills a concurrent itest server; 5037 the default). Breaks the AC-5/AC-6 intent; needs a deliberate forbidden override. Fix: validate the port in the script before setting the flag, or let Make own the kill.
- [minor] Makefile:175-181 - no set -e; a failed cp or sed does not stop the recipe; EXIT trap installed only after cp; a partial .bak can be moved over the profile on exit and becomes the next AC-1 leftover. Fix: cp $$f $$b.tmp && mv $$b.tmp $$b || exit 1 before the trap; abort if the final sed fails.
REJECTED:
- Concurrent runs consume each other's .bak (adversarial, high) - unproven; no caller runs two generations in one checkout.
- Failed sed after Gradle success still deletes the .bak (adversarial) - folded into the Makefile finding.
- Different port overrides share the profile files - same concurrency claim, unproven.
VERDICT: changes needed
