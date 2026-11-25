CODEX: ran: review, adversarial
CONFIRMED:
(none)
REJECTED:
- [adversarial, medium] CatalogueFilterRepository.kt:36-51 uses `apply()`, so a process kill right after Apply or Reset can lose the write - `apply()` updates the in-memory map immediately and flushes to disk asynchronously. The window is a few milliseconds, and Android also flushes pending `apply()` writes when an Activity stops. No ordinary use, AC or real caller produces a kill inside that window. AC-1 is a kill and restart after the filter has been applied, and the device evidence covers it. Rejected as `unproven`.
- [adversarial, medium] ShowAllHeader.kt:84-99: Reset is gated by `filtersCanFocus` while the restored query is still `Loading`, so it is unreachable if that query is slow - The gate is deliberate (spec line 69), covered by `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`. The latch opens as soon as the state leaves `Loading` (Content, Error or Empty). Back still leaves the screen. The base commit already gates "Фильтры" the same way. Rejected as `unproven`.
- [review] No actionable regressions - nothing to adjudicate.
VERDICT: clean
