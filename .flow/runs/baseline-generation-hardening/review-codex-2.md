# Review codex, round 2

CODEX: ran: review, adversarial (gpt-6.2-sol)

CONFIRMED:
- [major] Makefile:177-198 - startup-prof.txt is not protected; the recipe backs up and restores only baseline-prof.txt. :app:generateBaselineProfile writes the whole generated directory (saveInSrc, mergeIntoMain, app/build.gradle.kts:457-459); the implementer's scratch runs left startup-prof.txt deleted and baseline-prof.txt modified. A failed/partial run leaves a half-reverted pair. Fix: back up and restore both files (including when startup-prof.txt did not exist); harness test where fake Gradle deletes/replaces startup-prof.txt then fails.
- [minor] Makefile:177-185 - the fix-round "out of date" rule can discard the only original after a SIGKILL between Gradle's rewrite and rm of .bak; the next run backs up Gradle's interrupted output. Needs a state marker or tolerant comparison; test it.
- [minor] Makefile:177-188 - a direct make run failing the stale-.bak handling or the backup copy exits before the EXIT trap; the private adb server on 5039 stays up. Install the server-kill trap first, the profile restore after the backup exists.

REJECTED:
- rm -f $$b failure after success - unproven.
- Make recovery unconditional - trade-off behind the second minor, would overwrite a newer profile.

VERDICT: changes needed
