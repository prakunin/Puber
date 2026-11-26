# Review tests, round 2

LENS: tests  ROUND: 2  BASE: b27d8e40

Range 6063ddec..4071d839; tooling suite 21/21; no blocker or major introduced.
On startup-prof.txt: the recipe's $f/$b/$t all name baseline-prof.txt; it never copies, writes or deletes startup-prof.txt. Only Gradle touches it: the plugin's MergeBaselineProfileTask (1.5.0) has a "No startup profile rules were generated" warning and a delete call, and runs only after the generator tests pass, so a test failure leaves both files untouched. The observed deletion fits a scratch run with startupProfile ignored and the rail journey passing (Gradle exit 0, merge rewrote baseline partially and deleted startup); the recipe treated it as success. BaselineProfileContentTest (in check.full) reads startup-prof.txt and would catch a missing file before a commit.

### F1 [minor] Only baseline-prof.txt is restored, so a failure after Gradle's merge leaves a mismatched pair
- Where: Makefile:180-198
- Problem: the EXIT trap restores only baseline-prof.txt; fix #3 makes a post-merge failure reachable (re-mark sed fails after Gradle exit 0); INT/HUP/TERM during the merge too; the sandbox has no startup-prof.txt, so no test sees it.
- Fix: back up and restore startup-prof.txt with the same tmp+mv scheme (absence is a valid state); fake Gradle rewrites/deletes it; assert both files in failure and signal tests. Cheaper: reword the message/spec.

### F2 [minor] Leftover .bak: the restore notice no longer fits, and the "generation finished, then killed" case keeps a cold profile without a test
- Where: Makefile:182-190
- Problem: restore now fires only when no generation finished, but its notice still says a finished generation is discarded; a SIGKILL between Gradle exit 0 and rm -f $b goes to the "out of date" branch, which drops .bak and keeps un-marked S/P rules; a later failed run restores that cold profile.
- Fix: drop the "generation finished" clause; in the out-of-date branch keep .bak and refuse (exit 1) or name the cold-profile risk; add a test with a fresh unmarked generation differing from the stripped .bak.

### F3 [minor] The spec's AC-1 and D4 still describe an unconditional restore
- Where: spec :33, :58
- Fix: amend AC-1 and D4 to the conditional restore; add the new test to the spec's test table.

### F4 [nit] test_failed_backup_copy_aborts_before_gradle does not assert that the temp file is removed
- Where: test-baseline-profile-tooling.sh:196-205
- Fix: [[ ! -e "$T/repo/$PROFILE_REL.new.bak" ]].

Other checks: red runs recorded for the 4 new red tests; 2 base-green tests marked guards; reconnect scratch proof recorded; strip/re-mark sed order holds; check.full still does not run the tooling test (conductor at ship).

### Unproven
- Makefile:194 Gradle non-zero after the merge task wrote - needs a failing task after the merge.
