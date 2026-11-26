# Review antigravity, round 3

ANTIGRAVITY: ran
CONFIRMED:
- [minor] Makefile:185-192 - restore() moves $$b back first, and $$b is the only "backup pending" marker; a SIGKILL between mv -f $$b $$f and the startup restore/removal leaves $$sb or $$sn without $$b; the next run skips leftover handling and later can restore the wrong startup profile or leave $$sn. Double fault (SIGKILL inside microseconds during a failure-path restore). Restoring $$s before $$f closes it. Optional.
REJECTED:
- None.
Reviewer's own checks hold: SIGKILL between merge and re-mark restores the originals; SIGKILL after re-mark before rm -f $$b keeps the finished result; both files backed up/restored together; merge writing no rules (baseline absent) -> grep exit 2 -> restore; early EXIT trap and temp cleanup hold; harness covers these states.
VERDICT: clean
