Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 256 unit result files, 0 failures; tasks were largely UP-TO-DATE from the implementer's run at the same HEAD af7d459a; PersistentPayloadStoreTest 19 tests, CachedFeedTest 56 tests, all green)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage; lease taken and released; auth-save taken first)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow: red at fd6e8233 (SQLiteBlobTooBigException), green at af7d459a on the box |
| AC-2 | PASS | PersistentPayloadStoreDbTest.readReturnsNullAndDeletesARowTooBigForTheCursorWindow: red at fd6e8233 (same exception), green at HEAD |
| AC-3 | PASS | unit: writeOfAnOversizedPayloadDeletesTheKeysOlderRow, writeAllStoresTheSmallPayloadAndDropsTheOversizedKey green |
| AC-4 | PASS | unit: writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes and the parameterized exact-limit/+1 case green |
| AC-5 | PASS | unit: CachedFeedTest.aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession (+ load variant) green |
| AC-6 | PASS | before: shelf and grid show the error, 120 SQLiteBlobTooBigException lines, window 2097152 B, 2439355 B row. After install of the fixed APK (sha256 matches on device) and after a cold start: shelf and grid show cards, 0 exceptions in the app pid logcat, both runs |
| R2 (P1), R3 (P1), R6/R7/R8 (P1/P2) | PASS | PersistentPayloadStoreDbTest 4/4 and CachedPayloadDaoTest 11/11 on the box (incl. deleteOversizedKeepsARowWithinTheLimit, aMultiByteRowJustUnderTheLimitRoundTrips, aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent); R6/R7/R8 unit rows green |
| R12 (log carries no payload) | NOT CHECKED | no refused write happened on the box (the row healed on read), so no warning line was produced; the unit tests do not assert log content |
TESTS ADDED: none (the implementer's tests covered every AC)
EVIDENCE: /home/dev/orca/workspaces/Puber/cached-payload-row-too-big/docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md (committed in wt: a4b27373 Add the device evidence for the cached payload row size fix; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes for the conductor: red-run record for the plan's Implementation record: PersistentPayloadStoreDbTest at fd6e8233 on the box, 2 tests, 2 failures, both SQLiteBlobTooBigException; green at af7d459a 4/4; CachedPayloadDaoTest 11/11. The worktree is back on prakunin/cached-payload-row-too-big with a clean tree. The box still holds the fixed dev APK (1.12.6-dev). The oversized row was deleted from the box's cached_payload on first read, so the before state cannot be reproduced again without re-planting it. The rail overlay stays open over the browse grid after opening it from the chip (seen before and after the fix, not part of this change).

---

Round 2 (affected ACs only, HEAD 69c61413)

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 256 suites, 2555 tests, 0 failures, 0 skipped)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage; auth-save taken first; lease taken and released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1, AC-2, R2, R3, R6/R7/R8 (read path) | PASS | PersistentPayloadStoreDbTest 5/5 and CachedPayloadDaoTest 11/11 on the box at HEAD, including the new anAsciiPayloadOfExactlyTheLimitRoundTripsThroughTheStore |
| AC-6 | PASS | fixed APK at HEAD (sha256 68baf62d... matches on device), two cold starts: 4K «Все» shelf and «Всё в 4K» grid show cards, 0 SQLiteBlobTooBigException in each app pid logcat (pids 8770, 9242) |
| AC-3, AC-4, AC-5, R12 | unchanged from round 1 | AC-3/4/5 unit tests green in the full run; R12 stays NOT CHECKED (no refused write on the box) |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/cached-payload-row-too-big/docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md (Round 2 section, committed 6f3ba67c; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Note: itest reinstalls the same dev APK (install path changed, sha256 identical); the box holds the HEAD dev APK 1.12.6-dev.
