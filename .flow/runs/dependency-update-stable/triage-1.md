# Review triage - round 1

Run: dependency-update-stable   Base: 4db9c0d4   Reviewed head: c62b3ae1

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, codex | major | evidence `2026-11-12-dependency-update-stable-device.md` AC-10; test-design AC-6 instrumented P1 | No executed evidence that Room 3.0.3 / sqlite 2.7.1 writes persist: AC-10 NOT CHECKED, and the planned `WatchStateDaoTest` / `CachedPayloadDaoTest` instrumented suites were never run | qa-report AC-10 is NOT CHECKED; no itest run in plan record, qa report or evidence; both test files exist under `app/src/androidTest/kotlin/com/kino/puber/data/db/` | fix | qa-device runs both suites via `make DEVICE=<serial> itest` on the worktree build and records the result (tests only, no production code) |
| 2 | edge F1, tests F2 | minor | spec :36, :64, :95, :120, :142, :160 | Spec still names media3 1.11.1 as the target outside the gate-1 delta row | read the spec; catalog keeps `media3 = "1.11.0"` (`gradle/libs.versions.toml:27`) | fix | implementer aligns the spec text with the Decisions row |
| 3 | blind F1 | nit | `gradlew.bat` | Wrapper regeneration stored `gradlew.bat` as CRLF (base blob LF), so the diff rewrites the whole file and no `.gitattributes` pins line endings | `git show HEAD:gradlew.bat` CRLF vs base LF, as reported; no `.gitattributes` at repo root | fix (cheap) | implementer stores `gradlew.bat` with LF in the index and adds `.gitattributes` (`*.bat text eol=crlf`, `gradlew text eol=lf`), so the diff shows only the template change |
| 4 | tests F3 | minor | `app/src/main/java/com/kino/puber/data/api/KtorPlugins.kt:106` | Ktor 3.6.1 deprecates `ByteReadChannel.readRemaining(max)` used by `readTextLimited`, untested | `warnings-c4.log` has the new DEPRECATION line, `warnings-base.log` does not | defer | new run `ktor-readremaining-deprecation` (production logging path; migrating it needs its own test, out of scope for a version bump) |

## Rejected codex claims

- Version changes need checking against the target table - codex itself verified the diff matches, media3 excepted by the gate-1 delta.
- The explicit debug credentials in the signing fallback - in scope, recorded in Decisions, signature unchanged (AC-5).

## Next

- Fix round: implementer (#2, #3), then qa-device runs the two DAO instrumented suites (#1); re-review: lens edge + codex on the incremental diff (docs and .gitattributes only; no test or runtime code change). Unproven lines: 6 (wrapper jar checksum closed by the conductor: sha256 621008af...daf8 equals services.gradle.org gradle-9.9.1-wrapper.jar.sha256).
