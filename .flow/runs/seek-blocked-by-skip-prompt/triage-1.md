# Review triage - round 1

Run: seek-blocked-by-skip-prompt   Base: 6b087697   Reviewed head: b6660c74

Sources: review-blind-1.md, review-edge-1.md (no findings), review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-ux-designer-1.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, ux F1, edge unproven 2 | major | `component/PlayerTimedActionOverlay.kt:274-280` | The cancel X is still a D-pad focus target: Left/Right can no longer reach it, but Up from the seek bar lands on it (device shot m4), after which Right seeks and the action is unreachable while OK cancels | Read the Row: only the action has the requester, the X button has no focus restriction; ux measured the X focused in `m4-prompt-under-controls-chip-idle.png` | fix | Make the X unfocusable for the D-pad (`focusProperties { canFocus = false }`), keep pointer click; Back stays the dismiss key (Q1). Re-capture M4 |
| 2 | blind F2 | minor | `component/SkipSegmentOverlay.kt:32` | OK on the skip prompt now skips instead of cancelling | Spec Q1 / AC-5 decide exactly this | reject | Intended: Q1 answered (action focused, OK skips, Back dismisses), recorded in spec Decisions |
| 3 | blind F3, codex | minor | `vm/PlayerVM.kt:1641-1647` | The late credits load raises the next-episode prompt during a seek burst | Read `loadSkipSegments`: calls `startNextEpisodeCountdown()` with no `seekBurstJob` check; tick at `:1527` would offer it after the burst since `creditsSegment` is set | fix | Skip the direct call while a burst is active; add a VM test |
| 4 | blind F3 (second half), codex adversarial | minor | `vm/PlayerVM.kt:1377` | `onPlaybackEnded` is not gated by the burst | Tick only runs while playing, so a gated end-of-media prompt would never come back; prompt forwards seek keys; same as base | reject | Gating would leave a frozen last frame with no prompt; AC-3 scopes seeks landing inside a segment or lead-in, not end of media |
| 5 | tests F1 | major | `androidTest/.../PlayerPromptFocusAndKeysTest.kt:147,167,184` | Three red-designated Compose tests never had a valid red run after the matcher fix | Plan record says "red pending re-run after b7854fbb"; qa ran the suite only at 35f9866e | fix | qa-device red run of the class on production identical to base (b7854fbb) and record the failing lines in the plan |
| 6 | tests F2 | major | `test/.../PlayerVMSeekBurstTest.kt:94-114` | `everySeekInTheBurstRestartsTheWindow` survives a non-restarting window | Walked the mutation: checks only at 3 000 ms right after a seek that clears the prompt | fix | Assert null after each `wait(1_000)` before the next seek |
| 7 | tests F3 | minor | `vm/PlayerVM.kt:728` | No test starts a burst with `SeekBackward` | All burst tests use `SeekForward` | fix | Add one `SeekBackward` case |
| 8 | tests F4 | minor | `component/PlayerScreenContent.kt:86-90,109-113` | The refocus-on-hide branch and its retry have no test that fails without them | Both hide tests pass on base (red run) | fix | Record in the plan: AC-4 refocus-on-hide is device-only (M2), the two Compose tests are guards |
| 9 | ux F2 | minor | evidence M3 | Skip-prompt focus has no device screenshot | Worktree build has no skip-segment data (qa M3) | defer to the owner's manual check | Carried as NOT CHECKED to gate 2 (AC-9, M3); not a code defect |
| 10 | ux F3 | minor | evidence `m4-prompt-under-controls-chip-idle.png` | The M4 shot does not show "seek bar keeps focus" without an Up press | Shot taken after Up taps (qa report) | fix | qa re-captures M4 without Up, then one after Up (shows #1 fixed) |
| 11 | ux F4 | nit | evidence `...-device.md` | Screenshots described as 1080p, are 1280x720; M4 px figures wrong | ux measured | fix | qa corrects the evidence text |

## Rejected codex claims

- `onPlaybackEnded()` must be gated by the burst window - the tick runs only while playing, so a suppressed end-of-media prompt would never return; the prompt forwards seek keys so a held key does not get stuck (codex-reviewer's own rejection, confirmed).

## Next

- Fix round 1: implementer fixes #1, #3, #6, #7, #8 -> check.fast -> lens edge + codex on the incremental diff, lens tests (tests changed) -> qa-device: red run #5 at b7854fbb, AC-5/AC-6/AC-3 re-check, M4 re-capture (#10), evidence fix (#11). Unproven lines: 5 (blind 2, tests 1, edge 2).
- Also recorded as idea runs from this run: `prompt-key-passthrough` (Up/Down, Play/Pause through the prompt, spec Q4) and `introdb-imdb-id-prefix` (qa M3 observation).
