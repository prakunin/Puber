# Review edge, round 5

LENS: edge  ROUND: 5  BASE: 017e975e..123eb814

No findings. [ -s $$f ] && ! grep -qE ... sends a missing/empty baseline-prof.txt to restore; a non-empty all-H file still takes "out of date"; both new tests cover the cases. (Diff and triage only.)

### Unproven
- A leftover startup-prof.txt.bak without baseline-prof.txt.bak is never reached (branch gated on [ -f $$b ]); only if a run died between the two backup copies.
