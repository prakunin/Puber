# Review edge, round 2

LENS: edge  ROUND: 2  BASE: b27d8e40

The recipe does not protect startup-prof.txt (backup, leftover check and EXIT-trap restore cover only baseline-prof.txt, Makefile:177-198). Plugin bytecode (benchmark-baseline-profile-gradle-plugin 1.5.0, MergeBaselineProfileTask.writeProfile): File.delete() on the output, then writeText only if rules are non-empty; with saveInSrc/mergeIntoMain that output is the source file.
- A normal Gradle failure stops before the merge: both files untouched.
- The implementer's scratch runs (all journeys @Ignore'd but rail; only startup sets includeInStartupProfile) exited 0 with no startup rules: merge deleted startup-prof.txt; recipe took its success path.

### F1 [minor] The restore covers only baseline-prof.txt, so a restore after the merge leaves an old baseline next to a new or missing startup profile
- Where: Makefile:189-197
- Problem: the re-mark sed failing on the success path and INT/TERM/HUP during the merge both restore only baseline-prof.txt; startup-prof.txt stays new or missing (between delete() and writeText); "restoring the original" is half true.
- Fix: back up startup-prof.txt the same way (missing-file marker when absent, temp-copy then mv, restored in the EXIT trap, removed on success), or document git checkout as the recovery.

### F2 [minor] New since round 1: the "out of date" rule throws away the original when a run was SIGKILLed after Gradle wrote the new profile
- Where: Makefile:179-186
- Problem: after a SIGKILL between the merge and the re-mark, $f is a new S/P-only generation; the comparison fails and the original .bak is deleted; round-1 code restored it.
- Fix: also treat the .bak as current when $f has no H-marked -> rules (a stripped or fresh profile is never hot); restore then.

### F3 [minor] A Gradle daemon can write the profile after the EXIT trap has restored it
- Where: Makefile:192-197
- Problem: signals kill the client; a merge already running in the daemon finishes its delete-and-write after the restore.
- Fix: --no-daemon for this call, or wait for the build to stop before restoring; at least name the window in the recipe comment.

Holds: temp-copy precedence correct, *.new.bak ignored; stripped-compare handles S/P originals; script port guard before generation_started; changed tests fail for the claimed reasons; rail KDoc matches.

### Unproven
- Makefile:187, 179-186: exits before the trap leave the 5039 server up for a direct make run.
- Makefile:194-197: Gradle exit 0 with an empty startup profile deletes startup-prof.txt and reports success (BaselineProfileContentTest would fail before a commit).
