CODEX: ran: review, adversarial
CONFIRMED:
- [minor] ShowAllVM.kt:226-245 dispatchListState reads currentQueryGeneration at render time; a page queued before changeQuery renders old items under the new key; with a conflated Loading, the new first page arrives without a remount and AC-5 first-card focus may not fire. One stale frame, no stale final data. Fix: carry the generation in the page or stamp it when Restart is handled; VM test.
REJECTED: adversarial "high"/hold shipment (overgraded); publishLock insufficient for AC-8 (guards hold for unqueued pages).
VERDICT: clean
