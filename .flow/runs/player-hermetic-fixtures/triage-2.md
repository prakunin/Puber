# Review triage - round 2

Run: player-hermetic-fixtures   Base: f53f2144   Reviewed head: 73f3b5a4 (incremental ce54fc41..73f3b5a4)

Sources: review-edge-2.md (no findings), review-tests-2.md, review-codex-2.md (ran: review, adversarial), review-antigravity-2.md (ran: clean). Blind lens not re-run (incremental round per the flow).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex 1 | minor | `PlaybackControllerNetworkTracksTest.kt:250-257` | Delayed-segment half can pass vacuously: first BUFFERING precedes the segment request, gate opens early | Read the test: gate opens on the first BUFFERING state, no pending-request check | defer | new run player-device-tests-hardening (round 2: minors are not fixed in this run) |
| 2 | codex 2 | minor | `PlaybackControllerNetworkTracksTest.kt:80-107` | Chunkless-tracks proof weak: ungated 360p variant may be selected; `activeRequestCount > 0` is any request | Read the routes: only 720p/english segments gated | defer | player-device-tests-hardening |
| 3 | tests F1 | minor | `PlaybackControllerResourcesTest.kt:56` | `getCachedSpans(url).isEmpty()` assertion lost when `removeResource` came back in 73f3b5a4 | git diff ce54fc41..73f3b5a4 of the file | defer | player-device-tests-hardening |
| 4 | tests F2 | nit | `PlaybackControllerResourcesTest.kt:39` | Test name still claims recovery; doc row says "outcome not asserted" | Read | accept | name kept for the upstream mapping (AC-6); doc row is explicit; KDoc pointer folded into player-device-tests-hardening |

## Rejected codex claims

- Journal eviction (HermeticRequestJournal.kt:54-59, :94-95) - unproven: needs more than 256 requests with one in flight; no test does that.
- 720p fixture at H.264 Level 3.0 - unproven: plays on the device in runs 5 and 6; fixture bytes are upstream's (Q3).
- Queued-callback probe ordering (StaleCallbackTest.kt:102-111) - does not hold: the test is @Ignore'd for the reproduced bug and a timeout still fails it.

## Next

- No blockers or majors remain: go to judge. Unproven lines: 4 (edge 3, tests 1).
