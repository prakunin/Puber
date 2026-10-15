CODEX: ran: review, adversarial

CONFIRMED:
- [major] app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:100-101 - `export()` runs `writer.export()` on the writer thread without draining the sink queue first. Spec D8 lists "on export" as a drain trigger. `TelemetryWriter.export` only flushes the folders. Scenario: recording is on and the user hits an error or an action, then presses Share within 5 s of the last drain. The events are still in the queue, so the ZIP leaves out the newest activity. The pause-triggered drain (`TelemetryRuntime.kt:71`) runs after the archive exists. Fix: call `drain(force = true)` (or `drainQueue` plus `finishDrain`) at the start of the export task when `running`. Add a test that records an event and exports without advancing the periodic drain.
- [minor] app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:64-77 - `export()` never enforces the storage budget. `enforceBudget()` runs only from `openFile` (line 133) and keeps JSONL at or below 18 MiB before a new file opens, with a 2 MiB file on top. The ZIP is then added without a budget check. Scenario: logs sit near 20 MiB with recording off, then Share is pressed. The directory exceeds 20 MiB by the compressed ZIP size until the next recording opens a file. The overshoot is bounded by one ZIP, and text compresses well, so graded minor. Replacing the previous ZIP keeps it to a single archive.

REJECTED:
- (Both Codex runs reported the same two findings; none further to reject. Severity re-graded by codex-reviewer: drain finding raised from P2/medium to major, budget finding lowered to minor.)

VERDICT: changes needed
