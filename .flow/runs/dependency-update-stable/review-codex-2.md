CODEX: ran: review (round 2, incremental `c62b3ae1..2d24287e`, model gpt-6-astra; adversarial pass not repeated for a docs and line-ending diff)

CONFIRMED:
- (none)

Round-1 major, AC-10: **partly resolved.** The owner's standing decision in state.md Decisions says to carry it to the gate as NOT CHECKED. The round-2 evidence at `docs/superpowers/evidence/2026-11-12-dependency-update-stable-device.md` (round 2 section) narrows the gap:
- **Now shown:** `WatchStateDaoTest` passed 8/8 and `CachedPayloadDaoTest` passed 6/6 on emulator-5554 with Room 3.0.3 and sqlite 2.7.1. Between them they cover inserts, updates, `upsertAllFromServer` (`@Transaction`), prune, upsert/replace and delete. The app stayed installed, with no uninstall or clear.
- **Still not shown:**
  - Nothing runs `withWriteTransaction` (`DatabaseTransaction.kt:22`), the path the spec's risk 5 names for the Room 3.0.2/3.0.3 transaction and cancellation fixes.
  - Nothing writes through a file-backed database across a restart, which is what AC-10 literally asks.
  - The on-device progress save still fails on base too, with the cause unknown.
- **Severity:** I downgrade it from major to minor. The generated DAOs and the `@Transaction` method now work on the new versions, the bumps are patch releases, and the failure reproduces on base.
- **Gate wording:** the gate text should say AC-10 is only partly covered by the DAO suites and name the missing `DatabaseTransaction` path.

REJECTED:
- Codex raised no findings, so there is nothing to reject.
- I checked the diff myself.
  - **`.gitattributes`:** `*.bat text eol=crlf` and `gradlew text eol=lf` are correct. `git ls-files --eol` shows both wrapper scripts as `i/lf`, and the `gradlew.bat` diff is line endings only.
  - **Worktree copy:** `gradlew.bat` shows `w/lf` against `attr eol=crlf`. That is harmless, because git converts it to CRLF on the next checkout.
  - **Spec edits:** media3 1.11.0 now appears consistently in the Target versions table, C3, risk 8, R10 and the AC-9 rows.
  - **Plan record:** the added note accurately describes the fixes.

VERDICT: clean
