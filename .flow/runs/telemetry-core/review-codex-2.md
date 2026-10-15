CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:147-157 - `enforceBudget` sorts every non-`.jsonl` file ahead of the logs and deletes them first, so it can delete the zip returned by the latest `export()`. - Scenario: logs plus the fresh zip exceed 18 MiB (`TOTAL_BUDGET_BYTES` minus `MAX_FILE_BYTES`). The user taps Share, and `export()` closes the current log. The next recorded event calls `openFile()` and then `enforceBudget()`, which deletes the zip before the chooser's recipient reads the FileProvider URI. The share then fails or sends nothing. The window only opens when the log directory is near its budget, and the stale-zip cleanup is itself the fix for round-1 row 10. The remedy is to spare the most recent archive, for example by keeping the last exported zip name and skipping it in the loop, and to add a near-budget test.
REJECTED:
- none. The plain `review` pass reported no actionable regressions, and the adversarial pass produced one finding, which holds (severity downgraded from high to minor).
VERDICT: changes needed
