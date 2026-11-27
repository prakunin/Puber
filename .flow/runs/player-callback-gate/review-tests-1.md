LENS: tests  ROUND: 1  BASE: cec5f7fc

### F1 [minor] Nothing tests how the controller wires `onLoadStarted` into the gate
- Where: /home/dev/orca/workspaces/Puber/player-callback-gate/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:303 (`gate.onLoadStarted(loadEventInfo.dataSpec.uri.toString())`)
- Problem: `PlaybackCallbackGateTest` only checks the gate on its own: the gate gets `onLoadStarted("B")` and then reports the error. No test checks that the controller actually feeds B's load start into the gate. That depends on the line being there, on reading `dataSpec.uri` (not the redirected `loadEventInfo.uri`), and on that URI matching `stream.url` exactly. No device test fails a stream after a switch:
  - `hls404AndMalformedPlaylist_failWithoutUnboundedRetry` and `runPlaylistFailure` start with `prepare`, and `prepare` sessions never re-prepare.
  - The three `PlaybackControllerStaleCallbackTest` cases pass whether or not `streamLoadStarted` is ever set.
- Failure scenario: someone deletes the line, or compares the wrong URI (redirect, cache key, a changed `PlaybackMediaItemFactory` URI). Then every switch session stays "not loaded", so B's first real error after any quality switch or `NEXT_URL` fallback is quietly answered with one extra `prepare()` before it is reported. No test catches this. It is the design's R11 case, but it would happen on every switch instead of only in a rare ordering.
- Fix: add one instrumented case to `PlaybackControllerStaleCallbackTest` or `PlaybackControllerNetworkTracksTest`. Prepare a healthy A, `switchStream` to a B route that answers 404 or a malformed playlist, then assert one `StreamFailure` with B's URL and one request to B in `requestJournal` (no second request from a re-prepare).

### Notes (not findings)
- Suite: I ran `check.full` in the worktree. It finished with EXIT=0, BUILD SUCCESSFUL, and all baseline-profile tooling tests passed. Gradle reported the tasks as up to date, so it reused the implementer's earlier run on the same sources rather than recompiling.
- Red run: `itest-red.xml` lists 3 cases with 1 failure. `switchStreamAfterQueuedFailure_...` fails with `StreamFailure(NEXT_URL, url=.../progressive.mp4)` and B is never requested. `itest-1..3.xml` each list 3 cases with 0 failures. The 10 rows of `PlaybackCallbackGateTest` were red on base because the class did not exist. Every P0 row in test-design.md has a test under the same name.
- Each gate unit test fails if its rule is removed. I checked the generation check, the per-session retry flag, the `fromSwitch` check, and the URL comparison in `onLoadStarted`.
- AC coverage:
  - AC-4 and AC-5 are checked by code review plus the guard device tests.
  - AC-6 is the qa device check on the box.
  - AC-7 is a doc read.
  - Every AC is covered by a test or a documented check.
- The qa lead ("Track 1" in the Audio row after a quality switch): the diff cannot produce that text.
  - The label comes only from `notifyTracksUpdated()` at PlaybackController.kt:873 (`format.label ?: format.language ?: "Track ${index + 1}"`), which reads the live `player.currentTracks`.
  - The gate decides whether that function is called, not what it reads. A call it drops (an old session's listener after the rebind) would have read the same live `currentTracks` on base.
  - A stale report that went missing would leave the previous names ("01. Дубляж (RUS)") on screen, not "Track 1".
  - "Track 1" means the format of the stream switched to carries neither a label nor a language. That points to the stream's data, for example a quality served as a container without labelled audio, not to this diff.

### Unproven
- /home/dev/orca/workspaces/Puber/player-callback-gate/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:873 Audio label falls back to "Track 1" after a quality switch. To make it a finding, master would need to show the real name on the same item and the same switch, which would put the cause in code path differences this review did not find.
