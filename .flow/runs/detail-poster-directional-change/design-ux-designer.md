# Design delta: detail-poster-directional-change

ux-designer, 2026-11-20, worktree at 01d1d305. Code paths are relative to `app/src/main/java/com/kino/puber/`; the panel is `core/ui/uikit/component/details/VideoItemGridDetails.kt` (cited as `VIGD`).

## Governing stand

- Geometry: `docs/design/catalogue/` governs the catalogue panel. Its "Agreed sizes" table (panel 67 %, description 60 % of the width, 15 dp side padding, scrim edge 10 % at 90 %, 50 dp bottom fade) applies unchanged. This run changes no size, so it does not need a designing-a-screen session.
- Favourites (side-by-side layout, `DescriptionLayout.Default`, 48 dp left gradient `VIGD:532-547`) has no stand. Its geometry stays as at base.
- Motion: no stand states motion (`catalogue/README.md`, `content/README.md`). The numbers below are the agreed numbers once the owner accepts them on the emulator (brief.md#AC-10). Then add a short "Motion" section to `docs/design/catalogue/README.md` with the final values in this same run, so the stand does not drift from the code.

## Motion numbers

Values are in dp at 1080p (1 dp = 2 px). "Forward" means the direction from brief.md#AC-4: the new content enters from that side.

| Part | Shift | Translation | Alpha | Draw order |
|---|---|---|---|---|
| Poster, incoming (the still) | 48 dp from the forward side to 0 | 250 ms, `FastOutSlowInEasing` | 0 to 1 over 150 ms, `LinearOutSlowInEasing`, no delay | above the outgoing layer |
| Poster, outgoing (transient copy) | 0 to 48 dp toward the opposite side | 250 ms, `FastOutSlowInEasing` | 1 to 0 over 250 ms, `LinearEasing` | below the still, above the player |
| Text, incoming | 40 dp from the forward side to 0 | 250 ms, `FastOutSlowInEasing` | 0 to 1 from 90 to 250 ms (`delayMillis = 90`, 160 ms, `LinearOutSlowInEasing`) | above |
| Text, outgoing | 0 to 40 dp toward the opposite side | 250 ms, `FastOutSlowInEasing` | 1 to 0 over 0-90 ms, `FastOutLinearInEasing` | below |

- The poster crossfades with overlap: two pictures blend cleanly. The text fades through: the old column is gone before the new one shows, so two titles and two plots are never readable on top of each other. Both move on the same 250 ms translation, so they read as one motion when they start together (warm card).
- Poster shift 48 dp sits in the owner's 40-60 dp range and equals the Favourites left gradient width, which hides the bared strip there. Text 40 dp is the floor of the range: text is smaller than the picture and the same distance reads larger on it.
- No stagger between text blocks. Title, ratings, facts, duration and plot move as one layer: the stand pins them in one column ("pinned, not flowed"), and a stagger would stretch the change past 250 ms.
- Direction `None` (row change Up/Down, first focus, Back from Details, tab re-entry; brief.md#AC-5): same durations and alpha curves, shift 0. Same `id`: no transition at all.

## Edges and clipping

| Edge | What is bared | Treatment |
|---|---|---|
| Catalogue poster, left | up to 48 dp at the media's left edge | lies under the scrim's 0-10 % band (67.5 dp of a 675 dp frame at alpha 1 to 0.9, drawn after the still `VIGD:515-531`). Nothing to add |
| Catalogue poster, right (screen edge) | a strip as wide as the outgoing layer has moved, covered only by the fading-in still | kept small by the still's 150 ms alpha: the still is opaque by the time the outgoing layer has moved about 40 dp. The owner judges it under brief.md#AC-10 |
| Favourites poster, left | up to 48 dp | under the existing 48 dp gradient |
| Text, start side | up to 40 dp past the column start (15 dp padding in the catalogue, 16 dp in Favourites) | clip the animated text to the description's start edge only. Do not clip the bottom: a catalogue plot has no `maxLines` and deliberately runs below the panel (`catalogue/README.md`, "Agreed sizes"). Without the clip the text draws into the 60 dp closed rail (`ui/feature/main/component/MainScreenComponent.kt:291-298`, no clip there) |
| Text, end side | none: in the catalogue it lies over the picture, in Favourites over the poster's gradient | no clip |

The poster box keeps its `clipToBounds()` (`VIGD:457`); the vertical overflow of the 16:9 catalogue frame is unchanged because the shift is horizontal only.

## Rapid focus changes

| Case | Behaviour |
|---|---|
| A new card lands during a transition | Retarget. The in-flight incoming becomes the outgoing and continues from its current offset and alpha toward the side opposite the new direction; the older outgoing leaves composition at once. Never more than two poster layers and two text columns. |
| Direction reverses mid-flight (Right, then Left) | Same rule: the in-flight content leaves toward the end side, the new one enters from the start side. |
| Held key in the catalogue | No transitions while the key repeats: the 150 ms debounce (`ui/feature/contentlist/vm/ContentListVM.kt:61-95`) publishes only the final card, and the direction is shown card to final card (brief.md#AC-4). |
| Held key in Favourites | No debounce (`FavoriteVM.kt:106-118`); warm details can land faster than 250 ms apart and chain retargets. Accepted under the retarget rule; the owner judges it under AC-10. |
| Text and poster start at different moments (cold image) | Each runs its own 250 ms when its content is ready (brief Q3 default). |

## Held text and skeleton

- During `Loading` the previous text stays at full alpha, with no dimming, until the new details land or 3 s pass (brief.md#AC-6, same cap as `POSTER_HOLD_MS` `VIGD:74`).
- Text to skeleton after the cap, and skeleton to the first text: direction `None` (fade only, 250 ms).
- First load of the screen: skeleton as at base, then fade only.

## What stays static

Static: the panel ground, the full-bleed scrim and the bottom fade (`VIGD:515-565`), the Favourites left gradient, the trailer player and the still's trailer fade (`posterStillAlpha`, 300 ms `POSTER_FADE_OUT_MS`, unchanged), the section heading and the rows below.

Moves with the text: everything inside `VideoDetailsDescription`, including the rating badges. The panel has no buttons or labels of its own.

## Focus

N/A: the change adds no focusable element and does not move focus. The panel is not focusable. Focus stays on the card in the row throughout the transition.

## Shared constants

| Constant | Rule |
|---|---|
| Motion values above | New private constants in `VIGD` next to `POSTER_HOLD_MS`, grouped and named once (for example `PanelMotion`: `DURATION_MS = 250`, `POSTER_SHIFT = 48.dp`, `TEXT_SHIFT = 40.dp`, `POSTER_FADE_IN_MS = 150`, `TEXT_FADE_OUT_MS = 90`), so the AC-10 tuning is a one-line change. |
| `POSTER_CROSSFADE_MS = 200`, `PosterCrossfade` (`VIGD:72`, `:681-688`) | Not retuned to 250. A card change no longer goes through it (brief plan step 4). It stays for the same-card cases (first image, fallback URL) that the `detail-poster-fallback-empty-frame` run owns. |
| `POSTER_FADE_OUT_MS = 300` | Unchanged: the trailer fade is not a card change. |
| `DescriptionLayout`, `MediaScrim` | Unchanged. Shift distances do not go into them: the motion is the same on both screens. |
| `direction` parameter on `VideoItemGridDetails` / `VideoDetailsMedia` | Default `None`. The details hero (`ui/feature/details/component/DetailsScreenContent.kt:189-231`) passes nothing and keeps its base look (brief.md#AC-9). |

## Open

| ID | Question | Recommended |
|---|---|---|
| D1 | Poster 48 dp / text 40 dp / 250 ms, or another point in the 40-60 dp range | As above; the owner confirms on the emulator under AC-10 |
| D2 | The right screen-edge strip on the catalogue poster during the first ~150 ms | Accept if the owner sees no strip. Otherwise move the still's alpha to 100 ms rather than adding an overscan |
