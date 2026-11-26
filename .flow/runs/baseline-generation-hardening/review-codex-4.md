# Review codex, round 4

CODEX: ran: review (no adversarial pass)
CONFIRMED:
- [minor] Makefile:196 - if ! grep -qE ... $$f treats a missing baseline-prof.txt as "no method rules" (grep exit 2); the branch deletes both backups; the next cp $$f fails; tracked original recoverable with git checkout. Fix: missing $$f -> restore branch; only an existing file with no non-H method rule is out of date; add a missing-file test.
REJECTED:
- A persistently failing startup restore after the baseline restore succeeded - double fault; || return 1 / || exit 1 stop on the first failure.
- Trap re-arm, restore || exit 1 and the test additions are consistent with triage-3.
VERDICT: clean
