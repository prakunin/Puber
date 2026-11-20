# Device evidence: detail-poster-directional-change

- Build: worktree `detail-poster-directional-change` at e78cd5ad, `:app:assembleDevDebug`, `com.kino.puber.stage` 1.12.1-dev, apk sha256 `029e92ac8ec50f65a224ee18db795340c4480d9d4db93366130532bb99d1bce2` (matches the on-device `base.apk`).
- Install: `tools/android-apk-install-preserve` -> `outcome: installed`, `compatible_replace`, data preserved.
- Device: `emulator-5554` (AVD puber_tv_36, API 36, 1920x1080), leased for the run, released.
- Method: `screenrecord` (12 Mbit/s) while sending one D-pad key, frames extracted with ffmpeg. The emulator encoder writes sparse frames (about 4-7 distinct frames across a 250 ms transition), so direction and blending are readable, smoothness is not. No frames are committed: they show account content.
- Instrumented: `make DEVICE=emulator-5554 itest TESTS=...` for the two suites below.

| Check | AC | Steps | Expected | Observed | Result |
|---|---|---|---|---|---|
| I1 | AC-1, AC-3 | `SectionRowFocusTraversalTest` (whole class) | 7 tests green incl. `dpadRightReportsFocusedPositionWithRowKeyAndIndex` | 7 tests, 0 failures | PASS |
| I2 | AC-3 | `VideoGridInitialFocusTest` (whole class) | 10 tests green incl. `dpadRightReportsFocusedPositionWithRowKeyAndIndex` | 10 tests, 0 failures | PASS |
| M1 | AC-1, AC-7 | Movies catalogue, warm card, key 22 (Right) | new poster and text enter from the right, old leave to the left and fade | frame 2: new poster offset to the right over the fading old poster (old Snowbound poster ghost visible, moving left); frames 3-4: old text faded, new text slightly right of its rest position; settled by frame 5. No skeleton in poster or text. | PASS |
| M2 | AC-2 | same, key 21 (Left) | mirrored | new poster enters from the left, old moves right and fades; text in the rail area not drawn over the rail icons. A dark strip about 35-40 dp wide shows at the right screen edge for one or two frames while the new poster is left-shifted (owner judges, AC-10). | PASS |
| M3 | AC-3 | Watching/Favourites tab (heart), grid, key 22 then 21 | both directions as in M1/M2 | Right: new poster and text from the right; Left: new poster and text from the left, old fades; no skeleton. This screen is full-bleed with a poster row below, not side by side. | PASS |
| M4 | AC-5 | Movies: key 20 to the next row; Details (23) then Back (4); open the Series tab | crossfade with no horizontal shift | row change: Spider-Man poster fades in place, text in place; Back: same card, panel unchanged, focus returned to the card; first load of Series: skeleton then fade, no shift | PASS |
| M5 | AC-5 | Idle 4 s on a card with a trailer | panel stays still | text geometry unchanged, trailer video plays, no transition re-run | PASS |
| M6 | AC-6, AC-7 | Right from a visited card onto a never visited card, 0.2 s frame sampling | previous text and poster held, then new content lands | old text and poster held for several samples, then new poster and text arrive together; no skeleton text or poster in any sampled frame | PASS |
| M7 | AC-8 | Trailer card (Who's Coming for Christmas), key 22, wait 5 s, 3 repeats; once cold | trailer video visible | 3 of 3 screenshots show trailer video over the full poster area; the cold card also reached its trailer crossfade at about 4.6 s; no outgoing poster visible over video | PASS |
| M8 | AC-9 | Open Details (23) of a catalogue card, wait for hero | hero as at base | hero shows title, badges, plot and the trailer video, stable over 3 s, no shift or fade. No base screenshot was taken, so this is not a pixel comparison. | PASS (no base comparison) |
| M9 | AC-10 | owner | smooth, no right-edge strip | not judged by qa; see the strip note in M2 | NOT CHECKED |
| M-refresh | AC-5 (refresh path) | needs an account change in Details | none | not run: needs account mutation | NOT CHECKED |

Note: an in-app update dialog (1.12.2) appeared on first launch; it was dismissed with Back. Update was not pressed.

## Round 2 (fix commits ba4ee226, 4d9725b9)

- Build: worktree at 4d9725b9, `:app:assembleDevDebug`, `com.kino.puber.stage` 1.12.1-dev, apk sha256 `2f1d59c41a80e47f0aa14fae7b903df993702267ae59bf4965785ff0e045bc99` (matches the on-device `base.apk`). `tools/android-apk-install-preserve` -> `outcome: installed`, `compatible_replace`.
- Device: `emulator-5554`, leased, released. Method as in round 1 (screenrecord, frames by ffmpeg); no frames committed.
- Static: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL; 2127 unit tests, 0 failures, 0 skipped (incl. `PosterTransitionTest`).
- Rail note: a rail item takes effect on Enter (OK); moving focus over it only moves the highlight. Round 1 saw the highlight move without the content switching, which is that behaviour. The diff touches no rail code.

| Check | AC | Steps | Expected | Observed | Result |
|---|---|---|---|---|---|
| R2-1 | AC-1 | Movies catalogue, Right from card 2 to card 3 | new poster and text from the right, old held opaque under it | text fades; the new poster enters from the right over the old poster, which stays visible and opaque until the new one covers it; no skeleton | PASS |
| R2-2 | AC-2 | same, Left from card 3 to card 2 | mirrored | new poster enters from the left; text swaps; no skeleton | PASS |
| R2-3 | AC-10 note | Left press, right screen edge, frames 34-38 | no ghosted band | a strip about 70 px wide at the right edge shows the OLD poster at full strength (red plates) for one or two frames, then shrinks to nothing; no brighter or half-transparent band as in round 1. The seam is still there (option b of ux F1); owner judges on TV. | PASS (seam remains) |
| R2-4 | AC-3 | Rail heart item, Enter: the real Favourites screen (text left, poster right with the left gradient, grid below; confirmed before recording) | side-by-side layout | text column left, poster column right with a dark left gradient, card grid below. First load shows the skeleton, then content. | PASS |
| R2-5 | AC-3 / M3 | Favourites, Right to the next card (content landed after the details request, one request returned 502 first) | new poster and text from the right | text swaps first, then the new poster enters from the right over the old one; no skeleton, no clipping at the 16 dp text edge | PASS |
| R2-6 | AC-3 / M3 | Favourites, Left | mirrored | new poster crossfades in from the left; right-edge strip shows the old poster at full strength for 2-3 frames (frames 6-8), shrinking; no ghost band | PASS (seam remains) |
| R2-7 | AC-7 | all of R2-1, R2-2, R2-5, R2-6 | no skeleton flash over a loaded poster or text | none in any sampled frame; skeleton only on the first load of a tab | PASS |
| R2-8 | AC-8 | Movies, trailer card, Right then wait 9 s, 3 repeats | trailer video visible | 3 of 3 screenshots show trailer video over the poster area; also seen playing after a Left press | PASS |
| R2-9 | held key | Favourites, 6x Right at 120 ms then 6x Left | panel follows the settled card, no skeleton, no stuck state | panel held on the card before the burst, moved once to the settled card, then once back; end state matches the focused first card; no skeleton | PASS |
| R2-10 | AC-10 | owner | smooth on TV | not judged by qa (encoder 4-7 frames per transition) | NOT CHECKED |

Observation: in Favourites the text lands before the poster when the image loads later than the details, so for a moment the new text sits next to the old poster.
Observation: while a trailer plays, a key press was handled several seconds late on the emulator (frames show video until the transition). Not part of this change; seen only during trailer playback.

## Round 3 (fix commit b27800f6)

- Build: worktree at b27800f6, `:app:assembleDevDebug`, `com.kino.puber.stage` 1.12.1-dev, apk sha256 `278e16fa601eabf682212d9feab00c68f5f68f037b3bd343210cee3e02f670c7` (matches the on-device `base.apk`). `tools/android-apk-install-preserve` -> `outcome: installed`, `compatible_replace`, data preserved.
- Device: `emulator-5554`, leased, released. Method as before (screenrecord, native frames by ffmpeg); two key presses sent from one adb shell call about 80-90 ms apart. No frames committed.
- Static: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL; 2128 unit tests, 0 failures, 0 skipped.
- Dismissed the in-app update dialog with Back; Update was not pressed.

| Check | AC | Steps | Expected | Observed | Result |
|---|---|---|---|---|---|
| R3-1 | AC-1, AC-7 | Movies catalogue (4K row), double Right at about 90 ms with the new pictures not yet loaded | old poster held at full strength and unshifted while the text follows; new posters land; no skeleton | the old poster stayed opaque and in place across both focus changes; the first card's poster landed, then the second's; settled on the second card; no dimmed or shifted old poster, no bare strip | PASS |
| R3-2 | AC-2 | Movies, double Left at about 90 ms, warm cards | second motion interrupts the first; settles clean | first poster entered from the left, the second replaced it mid-motion; after the motion the final poster is full strength and in place; no old see-through or shifted layer left over, no bare strip | PASS |
| R3-3 | AC-1 | Movies, double Right at about 80 ms, warm cards | as R3-2, mirrored | same: the interrupted poster is replaced, the last poster settles opaque and in place over the 4+ frames that follow; no strip | PASS |
| R3-4 | AC-3 | Real Favourites screen (text left, poster right with the gradient, grid below), double Right at about 80 ms, warm cards | clean settle | intermediate poster passes, final poster full strength and unshifted for the rest of the recording; no bare strip | PASS |
| R3-5 | AC-3 | Favourites, double Left at about 90 ms | clean settle | same; settled on the first card, poster opaque, in place | PASS |
| R3-6 | AC-7 | all of R3-1..R3-5 | no skeleton over a loaded poster or text | none in any sampled frame | PASS |
| R3-7 | AC-8 | Series catalogue (4K row, cards with trailers), Right/Left then 10 s wait, 5 repeats (Scrubs twice, American Horror Story, Game of Thrones; Movies catalogue trailer video also seen in R3-2 frames) | trailer video visible | video visible over the poster area in every screenshot; some key presses were handled late while the trailer played (known, not this change) | PASS |
| R3-8 | AC-10 | owner | smooth on TV | not judged by qa | NOT CHECKED |

Note: R3-7 used the Series catalogue because the Movies row positions were not re-found; the poster and trailer code is shared. A triple press and a held key were not re-run this round (round 2 R2-9 covered the held key).

## Round 4 (fix commit 484e5adf)

- Build: worktree at 484e5adf, `:app:assembleDevDebug`, `com.kino.puber.stage` 1.12.1-dev, apk sha256 `1191f0593fa93b9779b5224a3898854e7ddf4a95933855523a01249c55355e41` (matches the on-device `base.apk`). `tools/android-apk-install-preserve` -> `outcome: installed`, `compatible_replace`, data preserved.
- Device: `emulator-5554`, leased, released. Method as before (screenrecord at 12 Mbit/s, native frames by ffmpeg); key presses sent from one adb shell call 80-90 ms apart, onto cards already seen (warm). No frames committed.
- Static: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL; 2134 unit tests, 0 failures, 0 skipped.
- Observation: while a trailer plays the emulator handles keys late (known, not this change), so the recordings were 6-11 s long and the presses landed a few seconds in; a triple Left in the catalogue was handled spread out rather than packed.

| Check | AC | Steps | Expected | Observed | Result |
|---|---|---|---|---|---|
| R4-1 | AC-1 | Movies catalogue (4K row), warm cards, double Right at about 90 ms, then again at about 80 ms | old poster held, interrupted poster replaced mid-motion, final poster settles opaque and in place | the outgoing poster stayed full strength under the entering one; the intermediate poster was replaced mid-motion; the last poster settled at full strength with no dimmed or shifted layer and no bare strip over the following frames | PASS |
| R4-2 | AC-2 | same, double Left at about 90 ms (card 3 to 1) | mirrored, clean settle | intermediate poster entered from the left and was replaced; the final poster settled opaque, nothing left over at the right edge | PASS |
| R4-3 | AC-1, AC-2 | Movies, triple Left at about 90 ms (card 4 to 1) | each interrupted layer finishes, final clean | three successive posters entered; the last settled full strength and in place; no ghost band | PASS |
| R4-4 | AC-3 | Real Favourites screen (text left, poster right with the gradient, grid below, confirmed from a screenshot), double Right at about 90 ms | clean settle | intermediate poster passed, final poster full strength and in place | PASS |
| R4-5 | AC-3 | Favourites, double Left at about 90 ms | clean settle | same, settled on the first card, poster opaque | PASS |
| R4-6 | AC-3 | Favourites, triple Right at about 90 ms (card 1 to 4, all warm) | clean settle | three posters passed, the last settled opaque and in place | PASS |
| R4-7 | AC-7 | all of R4-1..R4-6 and R4-9 | no skeleton over a loaded poster or text | none in any sampled frame | PASS |
| R4-8 | AC-8 | Movies catalogue, after double Right, after double Left, after a single Left (3 screenshots; trailer video also seen at the end of the triple Left recording) | trailer video visible | video visible over the poster area in 3 of 3 screenshots | PASS |
| R4-9 | AC-5 | Movies, one Down from the first row to the next row | crossfade with no horizontal shift | poster and text crossfaded in place to the new card; the new row's cards loaded in the grid below; no shift | PASS |
| R4-10 | AC-10 | owner | smooth on TV | not judged by qa | NOT CHECKED |

Note: the unit suite now includes the seeding tests added with 484e5adf (`PosterTransitionTest`); they run green in the static gate above.
