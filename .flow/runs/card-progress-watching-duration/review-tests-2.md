LENS: tests  ROUND: 2  BASE: c506d0bd

No findings.

I reviewed the incremental range 92d557be..41b96bda. It adds three tests and a note in the plan's Implementation record, and changes no production code.

- **R5 test, triage item 1:** `WatchStateRepositoryTest.recordFromServer_aNotWatchedFirstVideoReplacesAWatchedHistoryRow` first writes a watched history row, then has a later Details read report `videos[0].watching = {status 0, time 0}`. This is the scenario triage asked for. The test would fail if the read stopped being recorded (`toWatchStateOrNull` returning null), because the row would stay watched and `assertFalse` would fail. It would also fail if the newer-wins rule broke, since the setup moves `now` from 42 to 100. In the fake DAO, `upsertFromServer` honours the local-pending flag and the `updatedAt` comparison the same way the real Room queries do. The test fixes the current behaviour as the expected one; whether that behaviour is wanted stays open and goes to the owner at gate 2 with AC-7, as triage decided.
- **Details tests, triage item 2:** `DetailsScreenUIMapperTest` gained two tests, and each would fail if its change were reverted:
  - **Watched video:** the test gives the first video `watched = 1`. If `isMovieWatchedAnywhere` were removed, the item would carry no watched verdict, and the 118 s position against a 120 s length would show the "2 s left" line instead of the finished line.
  - **Zero-length video:** if `takeIf { it > 0 }` were dropped from `movieLength()`, the length would be 0. The time left would then be null and the line would fall through to "not started, 120", not the expected `movieLeftLine(90)`.
- **Guard marking:** the plan marks these three tests as guards. They are not P0 rows in the test design, so the rule requiring a red run on the unchanged code does not apply.
- **Test run:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests WatchStateRepositoryTest --tests DetailsScreenUIMapperTest`, executed (not cached), exit 0: WatchStateRepositoryTest 38 tests 0 failures 0 skipped; DetailsScreenUIMapperTest 27 tests 0 failures 0 skipped.

AC-7 is still not observed on the box. That stays a manual check for the owner at gate 2, per triage-1.md; it is not a finding for this round.
