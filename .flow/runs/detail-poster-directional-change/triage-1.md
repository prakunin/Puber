# Review triage - round 1

Run: detail-poster-directional-change   Base: 01d1d305   Reviewed head: 6eb4d15c

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-ux-designer-1.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | ux F1, codex | major | `VideoItemGridDetails.kt:638-642, :885-912` | ~47 dp strip at the right screen edge on a Left press (catalogue full-bleed); the D2 remedy (100 ms still fade-in) would not remove it because the width follows translation | qa M2 and ux frame measurement agree; ux withdrew the D2 remedy | fix | ux option (b): outgoing layer stays opaque under the still until the motion ends, then leaves; option (c) overscan only if the owner still sees the seam on the TV. Replaces the D2 decision (standing instruction: recommended) |
| 2 | blind F1, edge F1 | major | `VideoItemGridDetails.kt:640-650` | When the new card's last poster URL fails, the old poster stays at alpha 1 for 3 s under the new text, and `shownImage` keeps the stale image so a later change slides out a picture that was not on screen | read onError: only advances urlIndex; ready and shownImage untouched | fix | onError on the last URL ends the hold (record ready/settled) and clears `shownImage` |
| 3 | ux F2 | major | `FavoriteScreenContent.kt:103-109`; evidence M3 | Favourites side-by-side layout never measured on the device; M3 recorded the catalogue (rail did not switch content, API 502 at the time) | ux observed rail highlight move without content change; matches qa note | fix | qa re-runs M3 on the real Favourites screen (side-by-side) incl. right-edge strip and held-key retarget; qa checks whether the rail not switching content also happens on the base build |
| 4 | blind F2 | minor | `VideoItemGridDetails.kt:826-832` | Interrupting a running transition drops the older outgoing layer in one frame | read rememberPosterTransition: new record takes only the previous still | fix | cheap enough in round 1: hand the interrupted layer's current alpha/offset over, or keep drawing it until its own end |
| 5 | codex | minor | `VideoItemGridDetails.kt:873, 911` | `outgoingImage` stays strongly referenced after the transition ends | read PosterTransition: non-null val | fix | nullable, cleared when the transition ends |
| 6 | ux F3 | minor | `docs/design/catalogue/README.md:166-177` | Motion section reads as agreed; misses edge strip and retarget rule | read the section | fix | mark values pending AC-10 (owner on the TV), add the edge-strip option and retarget lines |
| 7 | tests F1 | major | plan Implementation record; test-design.md rows | Twelve P0 "red" tests were green on the prescribed stubs | record says so; stubs return the expected value | fix | implementer records them as guards in the plan record, with one targeted mutant red run each where cheap (directionTo without id match, heldDescription always previous, cardEnterAlpha always 1) |
| 8 | tests F2 | minor | `VideoItemGridDetails.kt:837-850` | 3 s cap timer wiring untested; M6 did not see >3 s | read: same shape as rememberHeldUrls, keyed on `holding` | accept | copies the shipped rememberHeldUrls wiring keyed on the same flag; a device step needs network off on the shared emulator. Listed for the owner |

## Rejected codex claims

- none

## Next

- Fix round 1: items 1, 2, 4, 5, 6, 7 by the implementer; then check.fast, lens edge + codex on the incremental diff, lens tests (tests changed), qa on AC-1, AC-2, AC-3, AC-7, AC-8 and M3 on Favourites side-by-side. Unproven lines: 9 (blind 4, edge 3, tests 1, plus ux note on the rail).
