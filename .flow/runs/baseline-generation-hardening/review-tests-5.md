# Review tests, round 5

LENS: tests  ROUND: 5  BASE: 017e975e

No findings. Shell suite all PASS incl. test_leftover_bak_is_restored_when_profile_is_missing and _is_empty.
- Missing-profile test asserts the restore notice, no "out of date", final content, no .bak.
- Empty-profile test checks final content (enough to catch the regression).
- Red runs recorded for both.

### Unproven
- Spec AC-1 says nothing about a missing or empty profile; no round 4 delta in the spec.
