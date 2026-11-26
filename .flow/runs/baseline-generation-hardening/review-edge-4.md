# Review edge, round 4

LENS: edge  ROUND: 4  BASE: 91a9b7ba

No blocker or major findings; round-3 fixes broke nothing (diff read, suite not run).

### Unproven
- Makefile:196 the discriminator ! grep -qE '^[SP]*L.*->' $f reads a missing or empty baseline-prof.txt as stale and drops the backups (SIGKILL between the merge's delete and write plus a surviving daemon); git checkout recovers.
- Makefile:~215 a signal between the successful re-mark and the trap re-arm still runs the old restore over the finished profile; one statement wide.
