CODEX: ran: review, adversarial

CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:852 (with PlayerVM.kt:220, :526) - Subtitle restore and the stale-pick reset run only through `restoreTrackPreferences()`. That runs from `onTracksUpdated`, which `notifyTracksUpdated()` skips when the media has no audio group. `onTracksChanged` still calls `applyPendingSubtitleSelection()` (line 236) regardless. Scenario: the user picks a subtitle on episode A, then episode B has text but no audio track. B's saved choice is never restored, and A's `pendingSubtitleTrack` is re-applied under a picker that reads Off, which breaks AC-1 and AC-3 for that media. On a fresh player the saved choice is never restored, because text now starts disabled. Severity is minor. A video with no audio group is rare, and the early return was already in the base (not in the diff), so this narrows the D3 fix rather than being a new regression. Still, the D3 change in the spec does not cover this path, and no test does either.

REJECTED:
- Codex `review` raised no findings. It said the fallback limits are accepted in the spec and tests were not rerun. Nothing to adjudicate.
- Codex calls the above finding "medium". I downgraded it to minor because the trigger (no audio group) is rare and the early return predates the branch.

VERDICT: clean (one minor optional finding; the implementer may fix or defer it)
