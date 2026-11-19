# Gate - detail-poster-trailer-flicker - round 1

**Decision:** CONCERNS
**Head:** a1da31c8   **Base:** 76a8bc2a

## Done as asked

`git diff 76a8bc2a...a1da31c8 -- app/` follows option A (spec Design D1-D6). One `PosterStill` comes after a conditional `TrailerPreviewPlayer`, and a `graphicsLayer` alpha fades it out. The alpha comes from `posterStillAlpha`/`posterFadeTarget`: `tween(300)` towards 0 and `snap()` back. `rememberHeldUrls` holds the URLs while `Loading` has an empty list. The request uses `useExistingImageAsPlaceholder(true)` with `PosterCrossfade` (200 ms, memory hits included). `SkeletonAsyncImage` gains `skeletonOnlyUntilFirstImage = false`, and only `PosterStill:612` passes it.

The diff makes no change to VM state, TextureView or the text skeleton (Out of scope). It adds one thing the spec does not describe: `POSTER_HOLD_MS = 3_000` (`VideoItemGridDetails.kt:74, 694-707`), from triage-1 row 1. It limits how long the hold lasts. See Concerns 2.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `posterStillAlpha_isOpaque_whenTrailerNotRenderedYet`, `skeletonVisible_hidden_whenOptInAndImageShownBefore`; M2 | Unit tests green (test-results 17:19Z); headless emulator: no SurfaceView layer (evidence round 1) | NOT CHECKED (manual, section 6 item 1) |
| AC-2 | `posterFadeTarget_isZero_whenTrailerRendered`, `posterStillAlpha_followsFade_whenTrailerRendered`; M2 | same | NOT CHECKED (manual, item 1) |
| AC-3 | `posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero`; M3 | same | NOT CHECKED (manual, item 2) |
| AC-4 | `heldPosterUrls_*`, `posterCrossfade_*`, `posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent`; M1, M4 | Evidence round 2: 57 catalogue frames, max grey 0.0013, no empty frame. Screencap cannot show crossfade smoothness | PASS (no skeleton or empty frame); smoothness NOT CHECKED (manual, item 4) |
| AC-5 | `skeletonVisible_shown_whenOptInButNoImageYet`, `heldPosterUrls_returnsCurrent_whenNothingHeldYet`; M1 step 1 | qa: "first-focus capture happened after the poster loaded" | NOT CHECKED (could be checked on the headless emulator; Concerns 1) |
| AC-6 | `heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters`, `posterCrossfade_returnsNone_whenResultIsError`; M6 | No broken URL was available; nothing tests the `urlIndex` advance on error | NOT CHECKED (manual, item 5) |
| AC-7 | `posterFadeTarget_isZero_whenTrailerRendered`; M5 | no video layer | NOT CHECKED (manual, item 1) |
| AC-8 | M7 | Favourites: 36 frames clean. Details hero renders, and BACK returns to the grid. The trailer part was not reached | PASS (non-trailer part); trailer part NOT CHECKED (manual, item 3) |
| AC-9 | code review, `skeletonVisible_staysVisibleWhileLoading_whenOptInOff` | Re-checked: default `false` (`SkeletonAsyncImage.kt:27`), and `PosterStill` is the only caller that passes it | PASS |
| AC-10 | `check.full`; VM tests unchanged | qa round 2 green at 9ede97aa. `git diff 76a8bc2a...HEAD -- app/src/test/kotlin/com/kino/puber/ui/feature` is empty. a1da31c8 touches only the evidence file, and qa-report.md (18:24:08) is newer than it (18:24:02), so I did not re-run it | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Failed details request holds the old poster for good | blind F1, edge F1 (minor) | fixed | 9ede97aa: `POSTER_HOLD_MS`, `heldPosterUrls_returnsCurrent_whenHoldExpired` |
| Poster request wiring untested | tests-1 F2 (major) | fixed | 9ede97aa: `posterRequest` extracted, `posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent` |
| `posterFadeTarget` 1F branch untested | tests-1 F3 | fixed | 9ede97aa: `posterFadeTarget_isOne_*` |
| Error-branch test too weak | tests-1 F4 | fixed | 9ede97aa: asserts the class of `Transition.Factory.NONE` |
| KDoc on the wrong declaration | blind F2, ux F1 (nit) | fixed | KDoc now sits directly above `heldPosterUrls` |
| Single-still identity and opt-in wiring have no guard | tests-1 F1 (major) | partial fix plus manual | Constant test added; the gap is carried to section 6 and Concerns 3 |
| Empty frame while a failed primary URL falls back | codex-1 (minor) | deferred | New run `.flow/runs/detail-poster-fallback-empty-frame/` (stage idea) |
| Trailer ACs untested on a device | codex-1 [high] | rejected, accepted by me | Not a code defect: the ACs are manual by nature (spec R1), section 6 |
| Opt-in test only pins the constant | tests-2 F1 (minor) | accepted, by me too | Guarding it needs a Compose harness, which Q4 ruled out |
| Hold-expiry `LaunchedEffect` untested | tests-2 F2 (minor) | accepted, by me too | Needs a Compose runtime; manual item 6 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `fade.value` is read inside the `graphicsLayer` lambda, so the panel does not recompose each frame (R4). Each `Loading` toggle starts one `LaunchedEffect`. The bitmap stays in memory while the trailer plays (R6, negligible). |
| Reliability | A details request that never answers now releases after 3 s. Back-to-back `Loading` does not restart the timer, so a sustained scroll falls to the skeleton (edge-2 Unproven; Concerns 2). The empty frame after a failed URL is deferred. R5: `useExistingImageAsPlaceholder` is `@ExperimentalCoilApi`, and any Coil bump must re-check AC-4. |
| Maintainability | Follows JUnit 5 and the pure-content-composable convention. Seams are `internal`, and the obsolete `AnimatedVisibility` imports and the second copy are gone. detekt and lint are green. |
| Security / privacy | The evidence audit passed (`mobile-evidence-audit.sh`: `evidence_audit_status=passed`). The diff has no credentials or account data. Both installs were same-signer install-preserve (`compatible_replace`), with no authorised `install -r`. |
| TV | No new focusables. BACK from the details hero returned to the grid (evidence round 1). |

## Manual checks for the owner

Use a windowed emulator or a device that renders video, with auto-trailer on, the worktree dev APK installed with install-preserve, and `adb -s <serial>`.

1. AC-1, AC-2, AC-7: in the catalogue, rest on a card with a trailer for more than 2 s. Expect: the poster stays at full opacity while the trailer buffers (no grey skeleton, no black frame), then fades out over the playing video in about 300 ms. On a letterboxed trailer after the fade, no poster pixels or grey tint show around the video.
2. AC-3: let a trailer play to its end. Then start another and press `DPAD_RIGHT` while it plays. Expect: the poster is back in the same frame the video leaves, with no skeleton or empty frame. In the second case it then crossfades to the next card.
3. AC-8: open a details screen whose hero has a trailer and repeat steps 1-2. Open favourites and repeat step 4. Press `BACK` and check that focus returns.
4. AC-4: press `DPAD_RIGHT`/`DPAD_LEFT` across a row you have already scrolled (warm), then across a row you have not visited (cold). Expect: a soft 200 ms crossfade from poster to poster, with no hard cut on warm changes.
5. AC-6: only if a card with a broken first wide poster is known. Expect: the next URL loads and the previous card's poster does not stay.
6. The hold bound: with the network off, move to a new card. Expect: the old poster gives way to the skeleton after about 3 s. Also hold `DPAD_RIGHT` for more than 3 s and judge whether the skeleton that appears is acceptable.

## Must fix

none

## Concerns for the owner

1. AC-5 is `NOT CHECKED`, but it is not manual by nature: a screencap right after the grid opens, on the headless emulator, can show the first-focus skeleton. It rests on `skeletonVisible_shown_whenOptInButNoImageYet` and on the outer `placeholder(request == null)` being unchanged.
2. The 3 s hold bound is behaviour the spec does not describe. During a sustained D-pad scroll that never lets details land, the panel falls to the skeleton after 3 s (manual item 6). This is no worse than base.
3. The core of the request, removing the trailer-time flash, has not been observed at all. Nothing guards the wiring that carries it (one still keeping its slot, `hasShownImage = true` in `onSuccess`, the opt-in argument in `PosterStill`). Manual items 1-3 are the only check.
