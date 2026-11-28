# Gate - room-wal-journal-mode - round 1

**Decision:** CONCERNS
**Head:** b6b0cd05   **Base:** 8c3117ea

## Done as asked

`git diff 8c3117ea...HEAD` touches code in two files: one `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` call plus comment in `PuberDatabase.create` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:42-45`), and two added tests in `PuberDatabaseMigrationTest.kt` (existing tests unchanged). Driver, pool, schema, version and migrations are untouched, as the plan's Global constraints require. Spec option A, nothing excluded.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `productionBuilderOpensTheFileInWalWithNormalSync`, `productionBuilderSwitchesARollbackJournalFileToWal` | qa-report.md; evidence file: itest on 192.168.1.110:5555, 33 run, 0 failures. Guards only: they pass on base too (see Concerns) | PASS |
| AC-2 | Existing `PuberDatabaseMigrationTest` tests, `CachedPayloadDaoTest`, `PersistentPayloadStoreDbTest`, `WatchStateDaoTest` | Same itest run; diff adds lines only to the test file | PASS |
| AC-3 | Manual check 1 on the box | Evidence file: install-preserve `compatible_replace`, on-device base.apk sha256 matches the build, logged in, badges unchanged, 0 crash entries | PASS |
| AC-4 | Manual check 2 on the box | Evidence file: header `2 2`, `ro.config.low_ram` empty | PASS |
| AC-5 | `check.full` | Re-run by the judge at b6b0cd05 (qa-report predates the last commit): exit 0 | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Guards cannot fail on a non-low-RAM device; "green on base" not recorded | review-tests-1 F1, review-blind-1 F1, codex-1 | Fixed (record) | b6b0cd05, plan Implementation record "Red run" |
| WAL brings synchronous NORMAL; durability trade-off not stated | review-blind-1 F2 | Fixed (comment) | b6b0cd05, `PuberDatabase.kt:43-44` |
| Low-RAM transition unverified | review-codex-1, review-codex-2 (rejected, unproven) | Rejection accepted: no concrete failure; carried as a Concern | triage-2.md |

Antigravity skipped both rounds (quota, owner: do not wait). Codex ran both rounds.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One builder call at startup; no change on non-low-RAM devices; low-RAM devices drop per-commit journal fsync |
| Reliability | Existing rollback-journal file switches on open (P1 guard); power-cut rollback of last commits under NORMAL accepted in the comment |
| Maintainability | Follows the existing builder style; test constants named; no dead code |
| Security / privacy | No secrets or account data; `tools/mobile-evidence-audit.sh` on the evidence dir: `passed` |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

1. The only behaviour change is on low-RAM devices, and no test or device in this run exercises that path: on the box and emulator `AUTOMATIC` already gives WAL, so removing `PuberDatabase.kt:45` would leave every test green. Accepted in the spec's Test design (score 3); the line is guarded by review only.
2. On low-RAM devices, local-only `is_local_pending` watch rows written just before a power cut can now roll back (WAL + NORMAL instead of TRUNCATE + FULL). Accepted in the code comment.
