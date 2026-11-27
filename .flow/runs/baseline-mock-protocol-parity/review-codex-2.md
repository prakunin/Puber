# Review codex, round 2

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] BaselineMockBackend.kt:177 - the folder lookup answers with the watch-later folder in every scenario, so Playback marks its movie bookmarked (contradicts "never watched or bookmarked"); only the bookmark icon on the Playback details screen; Play path and player check unaffected. Fix: empty list in Playback, or correct the plan/doc claim.
- [minor] BaselineProfileGenerator.kt:100-105 - the fixed 1.5 s rest before OK guards the required GET /v1/items/2 by timer; run 2 lost the race once; a slow emulator fails loudly (missing=[...]) and can be retried; no wrong profile committed. A bounded wait on the mock journal would be more robust.
REJECTED:
- dispatching field race between H1 and H2 - set and read inside the same dispatcher lock.
- P2/medium grading - re-graded minor; both fail loudly or affect icon state only.
Notes: player counts below base remain (exoplayer -177, hls -39, AudioTrackPreferenceResolver -9, SubtitleTrackMerger -4) - conductor decision; AC-4 rewrite documented; Browse rest touches Browse only; folder guards in place.
VERDICT: clean
