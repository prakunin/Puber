# Review triage - round 3 (beyond the 2-round cap, owner's standing instruction)

Run: subtitle-sideload-merge   Base: a97ee2b1   Reviewed head: f4b311e0 (incremental on 7ca85b93)

Sources: review-edge-3.md, review-tests-3.md, review-codex-3.md (ran: review, adversarial), qa-report.md round 3 (no defects; AC-4/6/7, D-4, D-8 PASS; AC-1 PASS on Psyren S1E1 manual quality).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex 1 | minor | `vm/PlayerVM.kt:599-615, 868-880` | After a same-media rebuild (Fast DNS, buffer preset) the restore fallback re-applies the displayed Off row through `applySubtitleSelection`, which nulls `intendedSubtitleRow`; the intent is lost when the current stream lacks the picked row | Read: fallback `applySubtitleSelection(content.selectedSubtitleIndex, persist = false)`; `applySubtitleSelection` sets `intendedSubtitleRow = subtitle.takeUnless { it.isOff }`; spec delta table says "Rebuild: intent kept" | fix | Fallback re-sends the displayed row to the controller without touching `intendedSubtitleRow` or persisting; regression test |
| 2 | tests F1, edge F2 | minor | `vm/PlayerVM.kt:935-936, 947-948` | No test sends `ToggleFastDns` / `SelectBufferPreset`; the restore re-arm on rebuild is unguarded | grep: neither action in app/src/test | fix | VM tests: rebuild re-sends the pick; rebuild with a text-less first report; Fast DNS variant (and buffer preset if the fixture allows) |

## Rejected codex claims

- None.

## Next

- Implementer fix (#1, #2), check.full; conductor checks the increment; lens tests + edge on the small increment; no device re-pass (VM-only change, covered by unit tests). Then judge.

## Outcome

- #1 and #2 fixed in 871b06a6. Round 4 on the increment 2903fc50..871b06a6: review-edge-4.md and review-tests-4.md, no findings. Conductor: check.full green (1593 tests), co-authored 0. No device re-pass (VM-only change, unit-covered). Go to judge.
