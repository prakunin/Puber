# Review tests, round 3

LENS: tests  ROUND: 3  BASE: 4071d839 (4071d839..91a9b7ba)
Suite: 27/27. No blocker or major. Restore/trap armed before old backups; stale detection drops backups only when baseline-prof.txt has an H rule; .none.bak handled; each triage-2 fix has a failing-if-broken test (guard marked); startup temp-copy leak caught by no_bak; .gitignore covers .new.bak, startup .bak, .none.bak.

### F1 [minor] Spec says the stale check runs per file; the recipe makes one decision for both, which is correct
- Where: spec :33 (AC-1), :107-108 (D4, D10)
- Problem: checked-in startup-prof.txt has 272 H rules and is never stripped, so a per-file check would always drop its backup; D10 names the temp file .bak.tmp, code uses .new.bak.
- Fix: amend AC-1 and D10: decided once from baseline-prof.txt for both backups; temp file .new.bak.

### F2 [minor] When old backups are dropped as out of date, no test checks that the startup backups go too
- Where: test_stale_bak_does_not_overwrite_newer_profile; Makefile rm -f $$b $$sb $$sn
- Fix: plant startup-prof.txt.none.bak or a stale startup .bak; assert startup-prof.txt unchanged and no_bak.

### F3 [nit] test_leftover_bak_restores_absent_startup_profile does not check the exit code or the notice
- Fix: [[ "$CODE" -ne 0 ]] and grep the restore notice.

### Unproven
- SIGKILL inside restore() between mv $$b and mv $$sb.
- Leftover branch with baseline-prof.txt missing - handled (grep fails -> restore), untested.
