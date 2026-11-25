Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 2574 unit tests, 0 failures, 0 skipped; detekt prints "There were 7 compiler errors found during analysis" for detektDevDebugUnitTest. The same 7-error line appears at base 4db1b061 (main checkout, ./gradlew :app:detektDevDebugUnitTest), so it is not introduced by this change.)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage). Dev APK at HEAD 6111240e, sha256 0d5ea942...21c3, installed by install-preserve (outcome installed, compatible_replace), on-device sha256 equal. auth-save taken first. Lease released, local db copies deleted.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PersistentPayloadStoreTest rows ran green in the full run |
| AC-2 | PASS | ContentCacheRepositoryTest aListMerge... and anOversizedDetailsRecordIsStillServedInFull... green |
| AC-3 | PASS | aCardOnlyRowServesTheList..., detailsOverACardOnlyRow... green |
| AC-4 | PASS | aCardOnlyRowKeepsTheCardInAnOfflineList... green |
| AC-5 | PASS | CachedFeedTest aRefusedLoadCrossedByABulkWrite... (both) green |
| AC-6 | PASS | CachedFeedTest put/load/supersede rows, markingAnOversizedDetailsRecordStale..., D1 row, weight test green |
| AC-7 | PASS with a deviation | id 12855 (Gold Rush, 17 seasons, was 1091191 B on the box): after details open, force-stop, run-as db copy: row 3995 B, no "episodes", no "detailsUpdatedAt"; 0 "Refused cached payload" lines and 0 FATAL in logcat. Id 50344 (was 1079168 B) also became a 3114 B card-only row. Deviation: the title is not from the 4K grid (no oversized row on the box is 4K; ids tried: Game of Thrones 8740 is 315 KB so not oversized, then 12855). 4K grid browsed separately. |
| AC-8 | PASS with the same deviation | cold start, 4K «Всё в 4K» grid shows cards; Gold Rush details open with seasons 1-17 and episodes (opened from search); row stays 3995 B without episodes. |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback/docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md (audit passed, committed b25678c6)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes: (1) The box had 6 pre-existing item rows over 1 MiB (4 remain), written by pre-cap builds; the fix replaced the two opened ones with card-only rows. (2) The absence of the Refused line is meaningful only together with the row result, since no Timber output of that line was ever observed in this run. (3) A key sequence on cold start opened a hero details and a playback resume prompt; left with BACK, nothing confirmed.

---

Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 2576 unit tests, 0 failures, 0 skipped)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage). Dev APK at HEAD 9ac3dba7, sha256 0bb46cbf...d024ec, install-preserve outcome installed (compatible_replace); on-device sha256 equal. auth-save taken first. Lease released, local db copies and screenshots deleted.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-5 (unit) | PASS | CachedFeedTest refused-load/bulk-write rows plus the new ownership rows green in the full run (2576 tests, 0 failures) |
| AC-7 (device) | PASS, with positive control | id 9365 (Deadliest Catch, 22 seasons; was 1134468 B, with episodes): after details open and force-stop, row 3075 B, no "episodes", no "detailsUpdatedAt". Logcat shows "Refused cached payload content:v2:item:9365: 1127606 chars, fallback persisted (2613 chars)", sizes only, no payload; 0 FATAL. |
| AC-8 (cold start) | PASS | cold start: Serials section shows cards; Deadliest Catch details open from search with seasons and episodes over the card-only row. |
| T3 / T4 | PASS | log line observed (T3); the logcat check now has a positive control; the manual row is marked as guard on base, the red runs being unit rows AC-2..AC-4. |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback/docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md (audit passed, committed: 2b79669c Add the round 2 device evidence for the card-only fallback log line)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes: (1) the same deviation as round 1 remains: the title is a docuserial opened from search, not from the 4K grid. (2) Key presses on cold start twice landed on a Home card (details opened, once a resume prompt); left with BACK, nothing confirmed. (3) Rows over 1 MiB on the box: 3 left (17262, 74866, 15218).

---

Round 3

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*CachedFeed*' -> pass (CachedFeedTest 67, CachedFeedMemoryWeightTest 2; 0 failures, 0 skipped). No check.full, as instructed (log-text change).
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage). Dev APK at HEAD b8b5e344, sha256 767f81ef...236283, install-preserve outcome installed (compatible_replace); on-device sha256 equal. auth-save taken first. Lease released, local db copies and screenshots deleted.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 (device, logcat condition) | PASS | id 15218 (PAW Patrol, 13 seasons; was 1150034 B, with episodes): after details open and force-stop, row 3616 B, no "episodes", no "detailsUpdatedAt". Logcat: "Cached payload content:v2:item:15218 over the row limit (1141033 chars); stored the fallback (2815 chars)" present (sizes only); 0 "Refused cached payload" lines; 0 FATAL. |
| AC-8 (cold start) | PASS | force-stop, cold start, details of 15218 from search opened with seasons 1-13 and episodes over the card-only row; row stayed 3616 B and the same line was logged again after the refetch. |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback/docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md (round 3 added, round 2 log quote marked superseded; audit passed; committed as the HEAD commit "Add the round 3 device evidence for the reworded card-only fallback log line")
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes: (1) same deviation as before: title opened from search, not the 4K grid. (2) Rows over 1 MiB left on the box: 17262, 74866 (22462 is under the limit). (3) One key sequence on cold start landed on a Home hero (Outlander details); left with BACK, nothing started or confirmed.
