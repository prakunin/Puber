CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:49-57,195-201 - Stale-event isolation runs only in `startOnWriter`, and `drainQueue` does not re-check `t`. Scenario: a producer passes `isEnabled` in `record()`, stalls before `queue.offer()`, and resumes after expiry and the next session's `startOnWriter`. The old event is written under the new sid with a negative `t`. The window needs a thread to stall across a whole expire plus a user re-enable, so it is practically unreachable. The effect is one mis-attributed event. Optional fix: drop events with `t < originElapsedMs` in `drainQueue` as well (one line). Does not block the merge.
REJECTED:
- Codex `review` (non-adversarial) reported no actionable regressions.
- The adversarial verdict "needs-attention" and its suggested session-generation tag and latch test are not warranted: a `t >= origin` check in `drainQueue` closes the gap without a new generation field; Codex's medium grade is too high for this race.
- Nothing against the other round-2 fixes (newest zip never evicted, first_content only on content states, monotonic expiry).
VERDICT: clean (one optional minor finding)
