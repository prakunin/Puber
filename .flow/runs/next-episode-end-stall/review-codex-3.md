CODEX: ran: review (adversarial pass not requested for this incremental round)
CONFIRMED:
- None. Codex found nothing in `ce2444ea..babd220b`.

Round-2 findings:
1. Resolved (was minor, `closing` missing). `PlayerVM.kt:1422` now begins `if (closing || episodeSwitchInProgress || !countdownDismissed) return@launch`. The new test `leavingInsideTheDeferralOfAnEndedDismissedEpisodeDoesNotSwitch` presses Back twice, advances 2 ms, and asserts no switch and no `release()`. The fix does not track or cancel the job. That is enough, because the block only reads state, and `closing` is set before the job reads it.
2. Resolved (was minor, a panel opened in the 1 ms window drops the advance). The re-check at lines 1423-1428 now calls `controlsStateMachine.markPlaybackEnded()` and sets `advanceWhenPanelCloses = true` when a panel is open. This matches what the immediate path does at lines 1393 and 1404. `onPanelClosed` (line 865) therefore finds the pending advance. The new test `aPanelOpenedInsideTheDeferralOfAnEndedDismissedEpisodeStartsTheNextOneOnClose` covers this and asserts the switch to 1x2 on `ClosePanel`.

REJECTED:
- None. Codex raised no claims this round.

Tests and device verification were not run, and static review only. The diff changes `PlayerVM.kt` plus two unit tests and the plan doc.

VERDICT: clean
