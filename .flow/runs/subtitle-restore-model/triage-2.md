# Review triage - round 2

Run: subtitle-restore-model   Base: f351d988   Reviewed head: 630e2074 (incremental on a99cdff9)

Sources: review-edge-2.md (no findings), review-tests-2.md, review-codex-2.md (ran: review, adversarial), qa-report.md round 2.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major (test gap) | `PlayerVMSubtitlePreferenceTest.kt:110-116`; `PlayerVM.kt:1006, 1866` | Nothing pins the per-media reset of `subtitlePickedThisMedia`, now the only gate against saving a substitute | Read: after the NONE re-stub both outcomes save NONE | fix | VM test: pick in media 1, next episode, restore resolves a substitute, audio pick saves the stored preference unchanged |
| 2 | codex 1 | minor | `PlayerVM.kt:548-552, 1866` | After an explicit pick of A, an in-session remap to B (quality switch / rebuild) is saved as the viewer's choice on a later audio pick | Read: the save writes `intendedSubtitleRow`, which `onTracksUpdated` may replace with a same-language row | fix | Save a snapshot of the last explicit pick (`pickedSubtitlePreference`) instead of the remapped row; test pick A, report B, audio pick -> stored A. The in-session remap itself stays (triage-1 #9, out of scope) |

## Next

- Implementer fix #1, #2 after qa-device round 2; check.full; one small round 3 (edge + tests on the increment) under the owner's standing instruction (beyond the 2-round cap); no device re-pass if the change stays in the VM save path (unit-covered).

## Outcome

- #1 and #2 fixed in 89c68077 (plan record 2e8d4be5). Round 3 on 8606de32..2e8d4be5: review-edge-3.md and review-tests-3.md, no findings. Conductor: check.full green, 1637 tests; co-authored 0. Unexplained Off at qa round-2 start: implementer found no defect (most likely the gated restore before the first report with text, or a round-1-build audio-pick save already fixed in c81474e8); recorded for the judge. No device re-pass (VM save path only, unit-covered). Go to judge.
