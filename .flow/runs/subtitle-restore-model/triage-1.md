# Review triage - round 1

Run: subtitle-restore-model   Base: f351d988   Reviewed head: d538e15b

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial; clean). qa-device round 1 runs in parallel.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | minor | `vm/PlayerVM.kt:1867-1869`, `:618-619` | A row the restore picked as a substitute (tolerant filter / language rule) is saved over the stored preference on the next audio pick, though the viewer never picked a subtitle in this media | Read: `hasIntent = intendedSubtitleRow != null \|\| subtitlePickedThisMedia`; the restore sets `intendedSubtitleRow` via `applySubtitleSelection(persist = false)` | fix | Save the intent only after an explicit pick in this media (`subtitlePickedThisMedia`), otherwise pass the stored preference through; VM test per edge F1 |
| 2 | blind F1 | minor | `vm/AudioTrackPreferenceResolver.kt:44-46, 172-179` | A differing readable label filters the row out, so a renamed manifest NAME between episodes turns subtitles Off where the old language rule restored the single same-language row | Read: `isCompatibleWith` includes `matchesPreferredLabel`; candidates feed rules 3-5; design risk 4 named it but no AC requires exclusion by label | fix | Label chooses among same-language rows (rule 4) but does not filter; a single same-language row still restores; AC-6 kept (label picks the variant); tests for rename and two variants |
| 3 | tests F1 | minor | `AudioTrackPreferenceResolverTest.kt:187-201` | Resolver rule 4 (label) decides no test | Read the T-6 cases: they resolve by identity | fix | Add a case only rule 4 decides (more important after #2) |
| 4 | tests F2 | minor | `PlayerVMSubtitlePreferenceTest.kt:296-345` | Pass-through of stored forced flag and label not pinned | Stubs use nulls | fix | Stub forced=false + label, assert the exact preference |
| 5 | tests F3 | minor | `PlayerVMTrackSelectionTest.kt:388`, `PlayerVMSubtitlePreferenceTest.kt:37` | "No wipe" negatives forbid only exact NONE | Read the verifies | fix | `match { it.language == null && it.url == null }` |
| 6 | tests F4 | nit | `PlayerVMTrackSelectionTest.kt:99` | Positive verify leaves the subtitle argument `any()` | Read | fix | `SubtitlePreference.NONE` |
| 7 | blind F3 | nit | `PlayerVMSubtitlePreferenceTest.kt` (`..._explicitOffAfterUnresolved_clearsStored`) | Second half pins mock wiring, not behaviour | Read: stub still returns the old value after Off | fix | Re-stub NONE after the Off and assert NONE passes through |
| 8 | blind F2 | minor | `vm/AudioTrackPreferenceResolver.kt:43` | "Empty language means Off" branch unreachable from stored data | Read: the app never writes `""` | reject | Spec AC-7 (second half) states this resolver contract, ported from upstream f9c9ff38; it is harmless and covered by resolver tests. KDoc to say it is a resolver contract, not a stored state (cheap, folded into #2) |
| 9 | codex adversarial | medium (codex) | `PlayerVM.kt:548-550, 605-608` | Rebuild swaps the intent to another same-language variant | codex-reviewer: code unchanged from base; in-session matcher out of scope | reject | Pre-existing, out of scope (spec Out of scope); not made worse by intent-first |

## Rejected codex claims

- #9 above (rejected by the codex-reviewer with reasons in review-codex-1.md).

## Next

- After qa-device round 1: implementer fix for #1-#7 plus any qa defects; check.fast; round 2: lens edge + codex on the increment, lens tests, qa-device on affected ACs if runtime behaviour changed (AC-6, AC-16).
