# Review triage - round 1

Run: card-progress-watching-duration   Base: c506d0bd   Reviewed head: 92d557be

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial; model gpt-6-astra). No extras (track none, no Compose layout in the diff).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests (codex adversarial raised the same gap, rejected as unproven) | major | `docs/superpowers/evidence/card-progress-watching-duration/2026-11-22-card-progress-watching-duration-device.md:13-19`, `WatchStateRepository.kt:509` | AC-7 not observed on the box; design risk 1 / R5 (a newer Details read with `videos[0].watching = {status 0, time 0}` replaces a watched history row) has no unit test | qa-report marks AC-7 NOT CHECKED; `grep "status = 0" WatchStateRepositoryTest.kt` finds nothing | fix (test part) | Add a WSR unit test pinning R5 behaviour; the device observation stays NOT CHECKED and goes to the owner as a manual check at gate 2 (playback writes to the owner account, not covered by the standing authorization) |
| 2 | tests | minor | `DetailsScreenUIMapper.kt:371`, `Models.kt:189-190` | The first-video watched verdict and the zero video length fallback on the Details resume line are not pinned by any DSM test | `grep "watched = 1\|duration = 0" DetailsScreenUIMapperTest.kt` only hits episodes | fix (cheap) | Add two DSM cases as the reviewer proposes |

## Rejected codex claims

- [adversarial, medium] AC-7 never exercised - a verification gap, not a code defect; merged into #1 and carried to the gate as NOT CHECKED.

## Next

- Fix round: implementer adds the three tests (tests only, no production change), then check.fast and the touched tests; re-review with lens tests, lens edge and codex on the incremental diff. qa not re-run (no runtime code change). Unproven lines: 9 (blind 4, edge 4, tests 1).
