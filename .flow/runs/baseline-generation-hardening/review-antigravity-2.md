# Review antigravity, round 2

ANTIGRAVITY: unavailable (quota/outage; skipped, not waited). The findings below are the reviewer's own read of the round-2 diff and the Makefile recipe.

CONFIRMED:
- [major] Makefile:177-198 - the baseline-profile recipe backs up and restores only baseline-prof.txt; startup-prof.txt in the same directory is never backed up and the EXIT trap never restores it. app/build.gradle.kts:450-462 sets saveInSrc = true and mergeIntoMain = true; the plugin writes both files there and (recalled, not verified) clears old *-prof.txt before writing, matching the implementer's observation (startup-prof.txt deleted after failed runs).
  - Scenario: the merge step clears the directory, then the run fails or is interrupted; baseline-prof.txt is restored, startup-prof.txt stays deleted or half-written; BaselineProfileContentTest (reads startup-prof.txt) fails; a commit would drop the startup profile. A successful run that emits no startup rules also leaves no startup-prof.txt unnoticed.
  - Fix: back up and restore both files with the same .bak/temp scheme; after success fail if startup-prof.txt is missing or empty; or copy the whole baselineProfiles/ directory and restore on any non-success exit.
- [minor] Makefile:178-185 - the stale-.bak check compares the stripped .bak against the current profile; if an interrupted run had finished Gradle, a newly generated profile without the H re-mark is kept and the notice does not say so. Low impact.

REJECTED:
- Half-written .bak restored later - copy goes through $$t then mv; rm -f $$t on failure; tested.
- Failed re-mark leaves the profile modified - exit 1 fires the EXIT trap; .bak restores.
- HUP/INT/TERM traps skip cleanup - each calls exit N, which fires EXIT.
- Early port refusal could kill a foreign server - case runs before any server is touched; tested.
- railShows KDoc change is comment-only.

VERDICT: changes needed
