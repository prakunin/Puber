# Review codex, round 3

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] Makefile:192 - the stale-backup test (grep -qE '^H[SP]*L.*->' $f) assumes a fresh merge result has no H rule; the real merged output has 1183 H rules (app/build/intermediates/baselineprofiles/main/merged/baseline-prof.txt and the raw benchmark output). A SIGKILL after the merge and before the re-mark goes to the "out of date" branch, deleting both originals and keeping a half-marked merge result - the case triage-2 #2 claimed fixed; the harness fake emits only S/P rules. Recoverable with git checkout; SIGKILL only. Fix: an explicit completion/staleness marker file, or a fake merge with mixed H and non-H rules and another discriminator.
- [minor] Makefile:192 - one restore-or-drop decision for the pair from baseline-prof.txt alone; spec D10 says per file. Needs a manual edit between a SIGKILL and the retry. Fix the recipe or amend D10.
REJECTED:
- Signal between rm -f $sb $sn and rm -f $b - adjacent rm calls; unproven.
- Daemon writes after the trap restored - accepted by D12, documented.
- Absent baseline-prof.txt aborts at cp - always checked in; refuses and changes nothing; unproven.
- H flag cannot show which run wrote the startup file - folded into the second item.
VERDICT: changes needed (one minor fix to the discriminator; rest minor or accepted)
