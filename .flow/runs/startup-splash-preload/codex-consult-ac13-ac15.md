# Codex consult on AC-13/AC-15 (2026-11-21, gpt-6-astra, read-only, verbatim conclusion)

Choose A: an explicit owner waiver of the emulator thresholds, followed by real-TV validation before calling performance verified.
The evidence supports an emulator limitation as a hypothesis, not a proven diagnosis.

- Round 2 moved decoding before reveal and improved both latency p95 measurements; another decode fix lacks evidence for the remaining stall.
- Limiting Coil parallelism could help CPU contention on a weak TV, but could also prolong image readiness and increase cap hits. Don't ship that trade-off unmeasured.
- Removing the fade is a useful future controlled experiment, not an established fix: Round 1b found no distinct fade cost, and revealed measures fade start, so shortening it cannot fix AC-13.
- Reject C for now: it exposes unfinished image work sooner, potentially restoring Round 1c's sluggish first interaction and weakening amended AC-4's image-ready guarantee.
- AC-15's percentages compare different populations: head excludes covered keys; base includes cheap spinner keys. Nevertheless, 330-930 ms first responses remain a real concern.
- Swiftshader and host contention cannot predict a weak CPU paired with a real GPU; neither improvement nor failure transfers reliably.
- Validate release builds against master 8428c946 on a representative weak TV, measuring launch-to-visible-content, dropped inputs, and first meaningful focus response at both fixed and reveal-relative anchors.

No code change recommended yet. Record AC-13/AC-15 as unmet and waived - not passed - and keep real-TV performance acceptance outstanding.
