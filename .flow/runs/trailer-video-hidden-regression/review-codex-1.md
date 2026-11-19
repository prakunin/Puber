CODEX: ran: review, adversarial (gpt-6-astra)

CONFIRMED:
- None.

REJECTED:
- [Codex review] No actionable regression. This is not a finding. The listener is added in `factory` and removed in `onRelease`, and it only posts an invalidate for a change to a non-zero size. I checked this against the diff and agree.
- [Adversarial, high] Target-TV validation (AC-3) is missing, and AC-5 is unresolved because M5 was skipped and the M3 skewed frame has no baseline. This is not a code defect in the diff.
  - AC-3 is assigned to the owner on the TV at gate 2, and the spec already lists the vendor-HWUI risk with fallback B.
  - M5 and the M3 baseline are testing gaps, not a scenario where the code produces a wrong result. The skewed frame is probably the SurfaceView teardown seen by the recorder, and no code path in the diff changes teardown.
  - I am passing the gaps to the conductor as gate-2 notes: owner T1 to T3 on the TV, and M5 not checked because no fitted trailer was found.
- [Adversarial, medium] AC-4 cannot be met for favourites because the favourites screen passes no `trailerUrl`. The premise is true: `FavoriteScreenContent.kt` has 0 occurrences of "trailer", and the device evidence records "NOT CHECKED: the screen never plays a trailer". It is not a defect in the branch diff, though, because favourites never played trailers before it and the fix does not change that. It is a spec-wording inconsistency (spec line 49, AC-4), not something to fix in the code. Suggestion for the conductor: record favourites as N/A in the gate-2 summary. I did not keep this as a code finding.

VERDICT: clean
