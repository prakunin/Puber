# Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tests re-run with --rerun: 2305 tests, 0 failures, 0 errors, 0 skipped; detekt and lint pass)
DEVICE: emulator-5554 (puber_tv_36, API 36 TV), leased and released; booted already, no wipe. Final state: clean head c0da5da7 APK installed, on-device sha256 f5ddecacd324e906bbc99ed2e18fc533ea0f831dffcaf501732f2ecb67274cd7 equals the local build.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlayerInteractorTest 59/59 green (saveWatchingTime_success_doesNotMarkItemDetailsStale, markItemDetailsStale_marksTheRepositoryEntryStale); device: saveOk without a mark on head |
| AC-2 | PASS | PlayerVMInvalidationTest 10/10; device, head, 29.5 min in front: 58 saves, 0 marks, 0 refreshNow, switch 5 to 6 with no details load |
| AC-3 | PASS | PlayerVMInvalidationTest exit cases; device: every exit 1 mark and 1 refreshNow (+2.2-3.2 s), mark logged before routerBack |
| AC-4 | PASS | PlayerVMInvalidationTest background cases; device: HOME gives saveOk then mark in the same ms, refresh +2.0 s, reopen adds none |
| AC-5 | PASS | PlayerVMInvalidationTest watchedInFront_* ; device: Watched at episode end in front gave no refresh (head 0 vs base 59) |
| AC-6 | NOT CHECKED | Visible outcome ("Details shows the saved progress") cannot be observed: on this account the item payload carries no per-episode progress, same with the base build (marktime ApiError.Serialization, owned by resume-progress-anomaly). Mechanism seen: Home-launched deep-link session (no Details below), 2 saves in front with 0 marks, exit marks once, details revalidated right after, Details opened later without error and Home bar advanced |
| AC-7 | PASS | CachedFeedTest 54/54 (afterEveryOperationSettles_*, whileAnOperationIsInFlight_* per site, aFailedOrCancelledLoad_*) |
| AC-8 | PASS | CachedFeedTest: aShortOperation..., aKeyWhoseRecordWasRemoved..., aNamespaceDrop...; git diff of CachedFeedTest has no removed line (existing 42 unchanged) |
| AC-9 | PASS | ContentCacheRepositoryTest 37, PlayerVMPlaybackFlowTest 32, PlayerVMMutationAndResultTest 24 green and untouched (git diff vs 40762767 empty for the two PlayerVM files); only PlayerInteractorTest changed assertions; check.full passes |
| AC-10 | PASS | In front, 29 min with one episode switch, same shim and device: base 58 marks / 59 refreshNow / 3 details loads; head 0 / 0 / 2. Exit: 1 mark, 1 refreshNow, 1 details load on both. Back-to-screen-below median (5 exits each, log markers, same on both builds): routerBack 905 base vs 898 head, frame2 1328 vs 1258 ms; line 1478 |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-device.md (plus verify-base-markers.log and verify-head-markers.log); audit passed
DEFECTS: none
UNAVAILABLE: none
MANUAL: AC-6 visible progress on Details needs an account whose item payload carries per-episode progress (or the resume-progress-anomaly fix): play a title from Home, move the position, exit, open its Details within 10 min and read the resume line / episode bar. Notes: (1) M1 "launcher row within ~5 s" was observed on Home (card bar advanced, refreshNow 2.2 s after exit) but the Android TV launcher row itself was not inspected. (2) AC-10 window is 29 min with the episode 1:30 stream stall worked around by a seek on both builds; head ran ep 5 to 6, base ep 3 to 4 (different episodes of the same title). (3) The base measure shim also ignores ApiError.Serialization, as in baseline.md.

# Round 2 (affected ACs: AC-2, AC-3, AC-4, triage #1)

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug --rerun-tasks -> pass (BUILD SUCCESSFUL; 2307 tests, 0 failures, 0 errors, 0 skipped; PlayerVMInvalidationTest 11; detekt and lint pass) on head a5f79b4f
DEVICE: emulator-5554 (puber_tv_36), leased and released; no wipe. Final state: clean head APK installed, on-device sha256 ad4607c524af27ff123b2457737c5bc2b6b4e1eb99340eaf8c26d992f1d57533 equals the local build.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-2 | PASS | PlayerVMInvalidationTest 11/11; device, head: 75 s in front, 2 saves, 0 marks, 0 refreshNow |
| AC-3 | PASS | device: exitStart, saveOk, markItemDetailsStale, routerBack (mark before routerBack), refreshNow +2.0 s; 1 mark, 1 refresh |
| AC-4 | PASS | device: HOME gives pauseForBackground, saveOk, markItemDetailsStale in the same ms, refreshNow +2.0 s |
| Triage #1 (NonCancellable flush) | PASS | 4 controlled runs of HOME then deep link to the same title within about 100 ms (stack replaced): the mark and refresh landed every time, including 3 runs where the final save never reported; unit coverage in PlayerVMInvalidationTest |
Other ACs not re-run this round (runtime change limited to pauseForBackground); round 1 results stand.
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-round2-device.md; audit passed
DEFECTS: none
UNAVAILABLE: none
MANUAL: none new. Notes: (1) the real Android TV launcher Continue-watching row was not driven; the same stack replacement was produced with a VIEW deep link to the same title. (2) In run 4 the new screen's details load came 125 ms before the mark; the stale mark is picked up by the revalidation afterwards, as on exit. (3) One uncontrolled attempt (12:22:42) saw no mark, with no save confirmed in front before it, so a mark may not have been owed; recorded in the evidence as not counted. (4) Quick HOME then reopen under about 1 s never reaches onStop, so only the poll-triggered timing exercises the path.

# Round 3 (TV box)

All before/after values of this run now come from the owner's TV box 192.168.1.110:5555 (owner's decision 2026-11-22); the emulator numbers in Round 1 and 2 stay as history.

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; mostly UP-TO-DATE: HEAD ef2d1c20 has the same app/src as the round 2 head, git diff a5f79b4f HEAD -- app/src is empty; not re-run with --rerun-tasks)
DEVICE: 192.168.1.110:5555 (the owner's TV box), leased and released; auth-save done first; no wipe, no uninstall. Final state: clean HEAD APK installed, on-device sha256 ad4607c524af27ff123b2457737c5bc2b6b4e1eb99340eaf8c26d992f1d57533 equals the local build.
Step 0: on the box, base build without the Serialization ignore: every watching/marktime save fails with ApiError.Serialization (Malformed response), no saveOk. Same ignore shim used on both measured builds. Relevant for resume-progress-anomaly: it reproduces on the box account.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-2 | PASS | box, head, 29.8 min in front with one episode switch (7 to 8): 61 saves, 0 marks, 0 refreshNow, no details load at the switch |
| AC-3 | PASS | box, head, 11 exits: 1 mark logged before routerBack, 1 refreshNow 1.5-2.1 s after exitStart each |
| AC-4 | PASS | box, head: Home gives pauseForBackground, saveOk, mark 1 ms later, refreshNow +2.0 s; reopening adds none |
| AC-5 | PASS | box, head: episode end in front (13:56:30) gave no refreshNow (0 in window vs 60 on base) |
| AC-10 | PASS with caveat | box, 29.2 min base vs 29.8 min head: marks 59 vs 0, refreshNow 60 vs 0, details loads 2 (one at the switch) vs 1 (none at the switch). Exit: routerBack median 188 base (n=7) vs 187 head (n=11); frame2 median 1147 base vs 1218 head all (1285 first batch of 6, 1144 ep 9 batch of 5); pass line base+150 = 1297. Caveat: first head batch ran near an episode end, base mid-episode, not controlled |
| AC-6 | NOT CHECKED | Details renders as a white frame in adb screencap on the box (also for an unrelated title), and marktime does not decode on this account. Home card badge/bar and History bars advanced after exit |
| Launcher Continue watching row (M1) | NOT CHECKED | no row for the stage app on the box launcher; AndroidTvHomePublisher.ensureChannel fails with "Failed to add logo, so channel (ID=269) was not created"; code unchanged by this run, predates it |
| AC-1, AC-7, AC-8, AC-9 | unchanged | unit-level results of Round 1/2 stand (same app/src) |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md (plus verify-box-base-markers.log and verify-box-head-markers.log); audit passed
DEFECTS: none in the change. Observed outside it: (a) marktime Serialization on this account (resume-progress-anomaly); (b) TV home channel creation fails on the box (logo), so no launcher row is ever published.
UNAVAILABLE: none
MANUAL: AC-6 visible outcome: on a screen where Details is capturable (or by eye on the TV), play a title from Details, move the position, exit, read the resume line/episode bar. Launcher row: needs the channel-logo failure fixed first.
