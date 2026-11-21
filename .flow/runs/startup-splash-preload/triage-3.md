# Triage round 3: startup-splash-preload

Incremental range reviewed: e6e4431c..e5a579a1. Sources: review-edge-3.md (no findings), review-tests-3.md (no findings), review-codex-3.md (clean).

| ID | Sources | Severity | Finding | Verdict | Note |
|----|---------|----------|---------|---------|------|
| R3 | edge-3 unproven | - | A caller on another thread between clearInFlightWalk and complete() starts a second walk | reject | Unproven; at worst one redundant walk, usually short-circuited by isKnownReachable. |
| R4 | tests-3 unproven | - | inFlightWalk === walk identity check untested | reject | Unproven; needs a re-entrant caller inside a microsecond window. |

No fix items. T8-T10 confirmed fixed by all three reviewers. Review stage closes; remaining: qa-device round 2 result (delta device evidence, AC-13/AC-15) and a short device re-check of head e5a579a1 if qa built from e6e4431c.

## Addendum after judge-1 (qa Round 2b observations)

| ID | Source | Severity | Finding | Verdict | Note |
|----|--------|----------|---------|---------|------|
| Q1 | qa Round 2b defect 1 | minor | 1 of 9 cold starts under host load 9-15 revealed with skeletons 8 ms after first_content, before the cap; cause unknown (possibly image requests starting after the two-frame wait) | defer | Moved to idea startup-first-input-on-tv (measure on TV; consider waiting for the first image request instead of two frames). Not reproduced at lower load. |
| Q2 | qa Round 2b defect 2 | minor | One content-link start revealed at 4693 ms (3.7 s after Content ready), likely the cap delayed by a RenderThread/shader stall on swiftshader | defer | Same idea run; unproven on real hardware. |
| Q3 | judge-1 note | - | AC-3 device request count (M7) not captured on the emulator | accept | Unit-proven (AppStartupTest.warmUpAndHomeShareOneRequestPerEndpoint); listed for the owner. |
