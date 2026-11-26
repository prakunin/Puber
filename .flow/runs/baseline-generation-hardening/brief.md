# Brief: baseline-generation-hardening

Kind: improvement. Size: M. Tracks: none. Device verification: yes (emulator host.docker.internal:5555, generation only).
Research: research.md (sections 3, 6, 8). All four findings still apply on master b27d8e40.

## Request

Fix the deferred findings F2-F5 of release-startup-compilation review-edge-2.md on the baseline profile generation route. F1 and the "not paired" warning are gone (hermetic-baseline-generation).

## Why M, not S

Files are few (Makefile, script, generator, .gitignore, docs/release.md), but the baseline profile is a named risk area and the proof needs a full generation run that rewrites the checked-in profile. Rail fix may add a tag to production source (fallback only).

## Findings (code, verified)

| ID | Where | Defect (confirmed) |
|----|-------|--------------------|
| F3 | Makefile:166-179 | `cp f f.bak` unconditionally (a leftover .bak from an aborted run is overwritten by a stripped profile, losing the original); trap covers INT TERM only, not HUP or EXIT; `*.bak` not in .gitignore |
| F4 | tools/generate-baseline-profile.sh:16-26 | one `adb connect` and one `get-state`; a just-started emulator still `offline` is refused |
| F5 | tools/generate-baseline-profile.sh:47-54,74,78 | `cleanup` kills the adb server on :5038 on every exit, including a preflight refusal where generation never started; the make target also uses 5038 (Makefile:124-125,143,150-172), shared with `itest` |
| F2 | BaselineProfileGenerator.kt:128-144,190-196 | `railShows` looks for the next title anywhere on screen; Series shows the "4K" section title (TabTypeConfig.kt:54-57,96-103), so the check passes with the rail not reopened; for the last entry it waits for its own title |
| Stale text | Makefile:157, script:38, docs/release.md:112-113 | API 34 refusal says "benchmark reset keeps app data"; with synthetic auth that is no longer the reason |

## Decisions taken (owner standing rule: recommended answers)

- Generation gets its own private adb port, default 5039, overridable (`BASELINE_ADB_PORT`), so it cannot disconnect or kill `itest`'s 5038 server (research Q1).
- A leftover .bak is restored with a printed notice, not refused (Q2).
- Rail check: focused-item / rail-scoped check first; a `testTag` on the rail `Column` (MainScreenComponent.kt:286-296) only if UiAutomator cannot see it (Q3).
- Profiles written by the proving run are committed if `BaselineProfileContentTest` passes and the egress journal is empty, else restored with `git checkout` (Q4).

## Acceptance criteria

- AC-1. Given a leftover `baseline-prof.txt.bak` or `startup-prof.txt.bak`, when `make baseline-profile` starts, then one decision is made from `baseline-prof.txt` for both files: only if it exists, is non-empty and has no method rule without H, both .bak files are removed with an "out of date" notice and the profiles are kept; if it is missing, empty, or has a method rule without H (stripped copy or unfinished merge), each existing .bak is moved back over its profile and a notice says so; only then new backups are made (absence of a file is a valid state).
- AC-2. Given generation is interrupted by INT, TERM, HUP or a normal failure, when the target exits, then `baseline-prof.txt` equals its pre-run content and no .bak remains (observed with a killed run; SIGKILL excepted, covered by AC-1 on the next start).
- AC-3. Given `*.bak` in `app/src/main/generated/baselineProfiles/`, when `git status` runs, then it is not listed as untracked.
- AC-4. Given the emulator was just connected and reports `offline` for a few seconds, when the script runs, then it polls `get-state` up to about 30 s and proceeds on `device`; given it never comes up, it exits 2 naming the last state seen and `ssh mac-emu start`.
- AC-5. Given the script refuses in preflight, when it exits, then no adb server is killed; given generation ran, then the generation server is killed and a concurrent server on 5038 is untouched (check `adb -P 5038 devices` before and after).
- AC-6. Given `make baseline-profile`, when it connects, disconnects and kills, then it does so only on the generation port (5039 or the override), never on `ITEST_ADB_PORT`.
- AC-7. Given the Series screen shows a "4K" section, when the Series entry is left with LEFT but the rail did not reopen, then the journey fails instead of passing; given the rail reopened, it passes, including after the last entry.
- AC-8. Given a full generation on the emulator against the loopback mock, when it ends, then it exits 0, `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` passes on the result, and the egress journal shows no request to a real host.
- AC-9. Given API 34 refusals, when printed or documented, then the reason text no longer claims "the benchmark reset keeps app data" (Makefile, script, docs/release.md).

## Risks

- Backup lives in the plugin's generated dir; whether baselineprofile 1.5.0 `mergeIntoMain` touches extra files there is unverified (no failure seen in two prior runs).
- Restoring a leftover .bak after a run killed between re-marking `H` and `rm -f` discards a finished generation; notice must say so.
- UiAutomator `focused` on the merged rail item node is unverified; needs the emulator run. Fallback adds production `testTag` plus `testTagsAsResourceId`, then a box check of the rail (192.168.1.110:5555, stage package).
- The proving run rewrites both profile files; commit or restore deliberately. Order with baseline-mock-protocol-parity is free but both rewrite the same profile.
- Device rules: generation and its evidence only on the emulator (`ssh mac-emu start`, stop after if no other lease); no live KinoPub API calls in scripts; generation hits only the loopback mock, no loops or retries beyond the preflight poll.

## Open questions

None blocking; Q1-Q4 above answered by the recommended answers (Blocks: no).
