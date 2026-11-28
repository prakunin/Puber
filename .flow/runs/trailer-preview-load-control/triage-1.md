# Review triage - round 1

Run: trailer-preview-load-control   Base: 8c3117ea   Reviewed head: ffd9d243

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | `TrailerPreviewLoadControlTest.kt:22`, plan Implementation record | P0 red AC-2 test was green on base and is not marked guard | Implementation record says "green on base" for this test; Deviations says none | fix | Red run with a stub that passes mis-ordered durations, record the IllegalArgumentException failure |
| 2 | blind F1 | minor | `TrailerPreviewLoadControlTest.kt:11-24` | Tests repeat the constants and do not check that each field reaches the right builder slot | Read the tests: they assert the constant and that build() does not throw | fix (cheap) | Add a behaviour check on the built `DefaultLoadControl` (stops loading past 15 s buffered, starts playback at 1 s) if it runs on the JVM; if media3 needs Android classes there, record that and leave it. The `setLoadControl` wiring stays covered by AC-3 review and the AC-4/AC-5 device checks |
| 3 | codex | minor | evidence:13 | AC-4 evidence cannot tell a finished trailer from one that stopped on a player error (both call `onFinished`) | `TrailerPreviewPlayer` calls `currentOnFinished()` on STATE_ENDED and on `onPlayerError` | fix (cheap) | qa round 2 on the box: play one preview to its end, compare the observed duration with the trailer length and check the stage process's log for a player error (filtered to the app pid, nothing broad committed) |
| 4 | tests F2 | nit | `TrailerPreviewLoadControlTest.kt:22-23` | `assertNotNull` on a non-null result asserts nothing | Read the test | fix (cheap) | Drop the dead assertion |
| 5 | tests F3 | nit | plan Implementation record | Record names `e7a54763`; the reviewed commit is `94339eaf` | `git log 8c3117ea..ffd9d243` | fix (cheap) | Correct the hash |
| 6 | edge, blind, codex (unproven) | - | `TrailerPreviewPlayer.kt:192` | High-bitrate trailers hit the 12 MiB cap first | Spec risk 1, accepted with AC-4 as the check | reject | Accepted risk in the approved spec; no high-bitrate trailer shown (unproven) |

## Rejected codex claims

- 12 MiB cap can stall a 25 Mbit/s trailer on a network drop - unproven, accepted spec risk.
- Hold shipment for controlled network-interruption comparison - beyond the ACs; repo rules bar network manipulation.

## Next

- Fix round: implementer fixes #1, #2, #4, #5 (tests and record only); qa round 2 on the box for #3; review edge + codex + tests on the incremental diff; unproven lines: 3
