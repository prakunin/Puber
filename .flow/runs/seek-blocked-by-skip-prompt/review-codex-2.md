CODEX: ran: review, adversarial (model gpt-6-astra, range b6660c74..HEAD)
CONFIRMED:
- none
REJECTED:
- none. Neither Codex pass reported a finding.

Checks by the codex-reviewer:
- Triage row #1: In `PlayerTimedActionOverlay.kt`, `focusProperties { canFocus = false }` sits in the `Surface` modifier chain ahead of the Surface's own internal focus target. So it applies to the X button, and the click handler stays for pointer use. Whether the X really leaves D-pad focus still has to be confirmed on the device, in the M4 re-capture.
- Triage row #3: The guard `seekBurstJob?.isActive != true` in `loadSkipSegments` matches the existing burst suppression. The tick still offers the prompt once the burst ends, and the new test `creditsLoadedMidBurstRaiseNoNextEpisodePromptUntilTheBurstEnds` covers both halves.
- Triage rows #6 and #7: The new null assertions after each `wait(1_000)` close the mutation gap in `everySeekInTheBurstRestartsTheWindow`. `aSeekBackwardBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds` is the added `SeekBackward` case.
- Not run: Codex did not run tests or the device.

VERDICT: clean
