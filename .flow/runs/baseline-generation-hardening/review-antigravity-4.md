# Review antigravity, round 4

ANTIGRAVITY: ran
CONFIRMED:
- [minor] Makefile:196 - if ! grep -qE '^[SP]*L.*->' $$f treats grep exit 2 (file missing) as "no non-H method rule"; regression from this round (the old discriminator sent a missing file to restore). SIGKILL during the plugin merge after it deleted baseline-prof.txt: next run prints "out of date", removes the backups, fails at cp; original backup gone. Empty/truncated file same path. Recoverable via git. Fix: stale only when $$f exists (non-empty) and has no non-H method rule; restore otherwise; test with the profile absent and a .bak present.
REJECTED:
- Nothing else; triage-3 items show no defect (re-armed trap runs kill-server only; restore || exit 1 runs before cp).
VERDICT: changes needed (one minor regression)
