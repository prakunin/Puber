CODEX: ran: review, adversarial
CONFIRMED:
- None.
REJECTED:
- [high] ContentCacheRepository.kt:232: `flowOn` lets queued values bypass the generation guards, so `observeItemDetails` could hand an old-session value to `recordFromServer` after `clear()` (unproven). I read the flows and `ItemDetailsRepository.kt:27`.
  - Before this branch, the same window existed. The collector ran inline, and `recordFromServer` suspends on `currentGeneration()` (`WatchStateRepository.kt:256`), so `clear()` could already interleave there.
  - The branch widens that window only by the `flowOn` buffer. Reaching it needs a `clear()` (logout or domain switch) to complete in the milliseconds between a details emission being queued and being collected.
  - Logout and domain switch are Settings actions. No input or caller was shown that has a details collector live at that moment.
  - The watch-state rows are written with the watch-state generation, which `clear()` does not bump. The spec's AC-6 covers only "no feed serves a value from before" `clear()`, and that holds for reads started after it.
  - No concrete scenario, so it is rejected as `unproven`. The implementer could still weigh a cheap collector-side generation check as hardening. That is not a defect in the diff.
- The plain `review` pass found no regressions, so nothing was rejected there.

VERDICT: clean
