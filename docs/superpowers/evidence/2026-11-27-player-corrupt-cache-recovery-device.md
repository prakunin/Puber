# Device evidence: player-corrupt-cache-recovery

Date: 2026-11-27. Build: worktree HEAD 4ce45e30 (round 2 rows: head a8607ccc) (base 8ffdbca1), instrumentation flavor, built with ./tools/agentw.
Device: emulator host.docker.internal:5555 (sdk_google_atv64_arm64, Android 16), leased and released. Test-only change, so no box run.
Command: make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerResourcesTest

| AC | Steps | Expected | Observed | Result |
|----|-------|----------|----------|--------|
| AC-1 | Class run at HEAD by QA (2026-11-27-player-corrupt-cache-recovery-itest-qa.xml) | Corrupt step waits for error, then StreamFailure(NEXT_URL, url) present, no Error | 3 tests, 0 failures | PASS |
| AC-2 | Three class runs by the implementer (itest-1..3.xml) plus the QA run | All cases pass each time | 3 tests, 0 failures in each of 4 runs | PASS |
| AC-3 | Implementer mutant without writeCorruptCacheEntry(url) (itest-mutant.xml), not re-run by QA | Fails on the AC-1 wait | 1 failure of 3: AssertionError: corrupt-cache failure: condition stayed false for 15000 ms; events=[Tracks(audio=[AudioTrackUIState(index=0, label=Track 1, language=, isFallbackLabel=true)], selectedIndex=0, subtitles=null), State(isPlaying=true, isBuffering=false, position=0), Tracks(audio=[AudioTrackUIState(index=0, label=Track 1, language=, isFallbackLabel=true)], selectedIndex=0, subtitles=null), StreamReady(url=https://127.0.0.1:18768/media/progressive.mp4), State(isPlaying=true, isBuffering=false, position=0), State(isPlaying=false, isBuffering=false, position=4015), Ended, State(isPlaying=false, isBuffering=false, position=4015)], requests=[RequestEntry(id=2, method=GET, path=/media/progressive.mp4, queryKeys=[], headers={}, range=null, routeId=progressive, outcome=Completed(status=200))] | PASS (implementer artifact, QA read the XML) |

| AC-6 (judge-1 fix, head a8607ccc) | Three class runs at head a8607ccc by the implementer (itest-fix1-1..3.xml), run at 19:19:56, 19:20:07, 19:20:15; commit adds assertFalse(corrupt.sawReady) and a late Error re-check after releaseAndDrain (no PlaybackEvent.Error in drainedEvents) | Class green with the two new assertions | 3 tests, 0 failures, 0 errors, 0 skipped in each of 3 runs (XMLs read by QA; diff read, not re-run by QA, no mutant for the new assertions) | PASS |

No raw logcat, tokens or UI dumps are stored; only JUnit result XMLs.
