CODEX: ran: review, adversarial
CONFIRMED:
- None.
REJECTED:
- [P2/high] An oversized-key refusal after the accepted upsert can delete a newer valid row written by a concurrent batch (`PersistentPayloadStore.kt:95-118`). Not a defect worth changing. A concurrent write to the same key was already last-writer-wins before this change, so which batch ends up on disk was never ordered. The worst outcome here is a missing disk row. That is a cache miss, refetched on the next query, and the memory tier still holds the value. It needs an oversized payload and a concurrent small write for the same key, and no evidence shows that happening. Codex's claim that cancellation leaves a half-applied batch is also unproven, because a stale row surviving only means the next read returns it or the sweeper removes it. Rejected as `unproven`, with no wrong result reaching the user.
- [P2] `refuse` calls `encodeToByteArray()` on a rejected payload only to log its size, which could cause an allocation failure on a TV. Rejected as `unproven`. The payload string is already held in memory and the copy is a few MB at most. Nothing shows heap pressure at that size, and the refusal path is rare.
- The adversarial note that the spec's manual D-pad check across grid rows is unverified is a verification gap, not a code defect. The conductor already holds that in the qa report.
VERDICT: clean
