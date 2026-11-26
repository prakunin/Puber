# Review edge, round 3

LENS: edge  ROUND: 3  BASE: 4071d839
Round-2 fix holds (both files backed up/restored; trap armed before any backup; stale only when baseline has an H rule); suite passes; worktree clean.

### F1 [minor] A signal between the two success-path rm calls leaves the old baseline next to the new startup profile
- Where: Makefile:213 (rm -f $$sb $$sn; rm -f $$b;)
- Problem: the EXIT trap still runs restore during the success-path deletes; a Ctrl-C during the first rm restores only baseline-prof.txt.bak.
- Fix: re-arm the EXIT trap without restore (kill-server only) before deleting the backups.

### F2 [minor] The leftover restore no longer stops when the move fails, so the next line overwrites the original backup
- Where: Makefile:198 (restore;) then :202
- Fix: restore || exit 1 in the leftover branch; restore returns non-zero on a failed mv/rm.

### F3 [nit] Two tests of the new leftover paths leave part of the behaviour unchecked
- Where: test-baseline-profile-tooling.sh:205, :291
- Fix: seed a stale startup .bak in the stale test and assert the newer startup content; add exit-code and notice checks to the absent-startup test.

### Unproven
- Makefile:184-186 SIGKILL inside restore after mv $b $f before mv $sb $s.
- Makefile:194 stale check assumes the merge output has no H flag (real if a raw generated baseline-prof.txt contains ^H rules).
