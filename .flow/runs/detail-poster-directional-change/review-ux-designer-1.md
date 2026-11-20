### F1 [major] A 47 dp hard-edged strip at the right screen edge on a Left press in the catalogue, and my D2 remedy would not remove it
- Where: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:638-642` (still `translationX`), `:885-912` (`PosterTransition.run`), `:965-966` (sign and shift)
- Problem: expected no visible strip (the D2 condition). Measured on emulator-5554 with screenrecord, Series catalogue, Altruists to Catedrales:
  - In the first transition frame, a vertical seam sits at x = 1825 px. That is a 95 px (about 47 dp) strip at the right edge, running the full media height into the bottom fade.
  - The strip shows the outgoing still at high alpha with no incoming picture over it. Next to it, the incoming still is about half transparent over the same picture, so the strip is brighter: mean luma 200 against 149.
  - The next frame leaves 8 px. It is gone by the third.
  - On a Right press there is no strip (the bared left edge lies under the scrim's 0-10 % band, as designed).
  - Cause: on a Start change the incoming still is offset by `-POSTER_SHIFT`. That leaves a strip `|stillOffset|` wide at its end edge. Only the outgoing layer covers it, and that layer fades linearly. The full-bleed media is anchored to the screen edge (`Alignment.CenterEnd`), and that side has no scrim.
  - Its width follows the translation, not the still's alpha. So the D2 remedy in my design delta (shorten the still's alpha to 100 ms) would not remove it; I am withdrawing it.
- Failure scenario: Movies or Series catalogue, focus on card N, press D-pad Left. For about one frame a light or dark band about 47 dp wide flashes at the right screen edge. It is most visible on light posters such as Altruists.
- Fix: the owner decides under AC-10 before gate 2. These are the options, smallest first:
  - (a) Accept it.
  - (b) Keep the outgoing layer opaque under the still (`outgoingAlpha` stays 1 until `DURATION_MS`, then it is removed). The strip then shows the old picture at full strength, which reads like a "push" rather than a ghosted band, but the seam is still there.
  - (c) The only option that removes the strip: overscan the media by `POSTER_SHIFT` on the screen-edge side in the full-bleed layout. The picture gets about 3 % larger at rest, which changes the stand's geometry and needs a designing-a-screen session.

  My recommendation is (b) now, and (c) only if the owner still sees the seam on the TV. Record the outcome in the stand's Motion section.

### F2 [major] The Favourites side-by-side layout was not verified on the device: QA's M3 measured the catalogue
- Where: `app/src/main/java/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContent.kt:103-109`; evidence row M3 in `docs/superpowers/evidence/2026-11-20-detail-poster-directional-change-device.md`
- Problem: expected M3 to show the side-by-side layout (`DescriptionLayout.Default`, poster at weight 5 with the 48 dp left gradient). That is what `TabType.Favourites -> screens.favorites()` -> `FavoriteScreenContent` builds (`VideoItemGridDetails` without `fullBleedMedia`). What I saw on the device:
  - Selecting the heart (Watching) item in the rail moves the rail highlight, but the content stays the Series catalogue ("New releases", full-bleed). The same happened when selecting Movies.
  - This exactly matches QA's note that the Watching tab "is full-bleed with a poster row below".
  - So none of these were measured on the device: the 48 dp poster shift under the 48 dp gradient, the text clip at the 16 dp padding, the strip at the poster's right edge (that column also ends at the screen edge, so F1 applies there too), and the unbounced held-key retarget.
- Failure scenario: Favourites, D-pad Right or Left across the grid. Any clipping defect or edge strip in the side-by-side layout reaches the owner unseen.
- Fix: qa re-runs M3 on the real Favourites screen and confirms it shows the side-by-side layout before recording frames. Separately, the conductor should have someone check that the rail not switching content also happens on the base build. That is behaviour, outside this review. The log at the time showed HTTP 502 from the API.

### F3 [minor] The stand's Motion section reads as agreed before the owner has accepted the values, and leaves D2 out
- Where: `docs/design/catalogue/README.md:166-177`
- Problem: the design delta has the values become the agreed numbers once the owner accepts them on the emulator under AC-10. The section instead says "the owner confirms them on the TV after release". It also says nothing about the edge strip or the retarget rule.
- Failure scenario: a later run treats 48/40/250 as settled and retunes around an artefact nobody signed off.
- Fix: mark the values as pending AC-10, and add one line for the edge strip (F1, with the chosen option) and one for the retarget rule. Update it after the owner's decision in this same run.

### What I checked and found matching
- `PanelMotion` (`VideoItemGridDetails.kt:104-115`) carries the agreed numbers: 250 ms with `FastOutSlowInEasing`, poster 48 dp, text 40 dp, poster fade-in 150 ms, text fade-out 90 ms, text fade-in 160 ms after 90 ms. The easings and draw order match the design delta (`:449-459`, `:890-906`; outgoing poster under the still, above the player).
- On the device, text at rest starts at x = 151 px (60 dp rail + 15 dp padding). Incoming text on a Left press measured 145 px; outgoing text on a Right press measured 130 px and dimmed. Nothing was drawn past the 120 px clip edge (`startEdgeClip`, `:470-481`), and the bottom stays open.
- The scrim, the bottom fade, the section heading and the rows stayed still in every frame. The Favourites gradient is outside the moving layers in the code. `POSTER_CROSSFADE_MS` and `POSTER_FADE_OUT_MS` were not retuned. The details hero defaults to `direction = None`.

VISUAL: deviates (F1, measured on emulator-5554, 1080p; Favourites side-by-side not measured, F2)
