# Review triage - round 2

Run: manual-quality-hls4   Base: 0823528b   Reviewed head: 07aba956 (incremental from 173b3da9)

Sources: review-edge-2.md (no findings), review-tests-2.md (F1 minor), review-codex-2.md (ran: review, adversarial; 1 minor), review-antigravity-2.md (ran, clean). Blind and state-path not re-run (fix round touches the cap helper and tests only; state-path traced the cap path in round 1).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, codex | minor | `app/src/main/java/com/kino/puber/ui/feature/player/vm/VideoCapParameters.kt:23`; `PlaybackControllerNetworkTracksTest.kt:117-140` | no test fails if the Auto branch stops restoring the physical-display viewport: on the emulator the default viewport is unbounded and the test does not read `isViewportSizeLimitedByPhysicalDisplaySize` | read the test and VideoCapParameters; the plan's record states the Auto check cannot discriminate on the emulator | accept | round 2 minor (test strength only, production code correct per edge-2 and codex-2); listed for the owner at handover. Cheap follow-up: set a 100x100 viewport before the uncapped switch, or assert the flag |

## Rejected codex claims

- none

## Next

- no fixes (round 2: blockers and majors only), go to judge; unproven lines: 3 (edge 2, tests 1)
