LENS: edge  ROUND: 2  BASE: c62b3ae1..2d24287e (full base 4db9c0d4)

### F1 [major] AC-10 is still open after the triage #1 fix: the real-database write transaction has never run
- Where: docs/superpowers/evidence/2026-11-12-dependency-update-stable-device.md:27 (round 2 block) and :12 (AC-10 row); app/src/main/java/com/kino/puber/data/db/DatabaseTransaction.kt:22
- Problem: The round 2 DAO suites ran and passed (8 + 6), but only against an in-memory database with direct DAO calls. The evidence says so itself and still marks AC-10 `NOT CHECKED`. Two things that change with Room 3.0.1 -> 3.0.3 still have no run: the write through `PuberDatabase.transactions()` -> `withWriteTransaction` (spec Risk 5 names it as the risky path), and an existing file-backed database opened and written by the new Room. The round 1 device row shows the DB opening and the rows surviving a restart. Progress save failed there, on base and on the new build alike, so the round 1 device run never succeeds at a write through this path.
- Failure scenario: a Room 3.0.3 change to transaction or cancellation handling in `withWriteTransaction` breaks the watch-state write in `WatchStateRepository` on the real database. Every check recorded so far would still pass: the DAO suites skip the transaction wrapper, `WatchStateRepositoryTest` uses `DatabaseTransaction.Direct`, and the device run cannot tell this apart from the save failure base already has.
- Fix: this is not a code change. Either the owner explicitly accepts AC-10 as partial at gate 2 (record it in the spec's Decisions table, citing the base save failure as a separate defect), or run one check that goes through `transactions()` on a file-backed `PuberDatabase`: an instrumented test, or a device scenario whose write does not hit the pre-existing `Playback progress save failed`. Triage #1 should not count as closed until one of these is done.

### F2 [nit] Plan task text still targets media3 1.11.1
- Where: docs/superpowers/plans/2026-11-12-dependency-update-stable.md:88
- Problem: Triage #2 aligned only the spec. Task C3 in the plan still says `:27 media3 1.11.0 -> 1.11.1`. The blocker is recorded further down, but anyone re-running the task from its step list would make the bump that gate 1 rejected.
- Fix: change it to `media3` unchanged at 1.11.0 (gate 1 delta), or add a note pointing to the blocker record.

Checked and found correct:
- `.gitattributes` gives `gradlew` `eol=lf` and `*.bat` `eol=crlf`.
- `git ls-files --eol` shows both wrapper scripts as `i/lf` and `git status` is clean, so the change to the wrapper .bat file now shows only the template change.
- No other `1.11.1` target is left in the spec outside the Decisions row.

### Unproven
- .gitattributes:1-2 Only the wrapper scripts are pinned. Other scripts (`tools/agentw`, `tools/*.sh`, `.claude/hooks/guard-bash.sh`) stay unpinned and could pick up CRLF under `core.autocrlf=true`. This only becomes real if a contributor or CI checks out on Windows.
