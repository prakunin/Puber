# Research: details-plot-adaptive-width

## 1. Request

"On the film/series details screen the description nicely overlaps the poster, but the overlap should shrink when there is enough room below - make the description narrower then." (owner, 2026-11-26, recorded in `state.md` Log, with the conductor's proposal: narrowest of a few width steps, e.g. 590 / 520 / 460 dp, at which the whole plot fits without scrolling; long plots keep 590 dp and scroll; floor about 420-460 dp; width fixed per title; fact lines and action row must not move; dial on the stand first.)

## 2. Understanding

The plot block on the details hero is a fixed 590 dp wide, so it runs about 270 dp under the still, where the scrim is already thin. When a plot is short enough that a narrower block would still hold it inside the height the block already occupies, draw it narrower; the rest of the hero must not shift. Long plots stay as they are (590 dp, remote scroll).

## 3. Current state in code

All in `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt` unless stated.

| Item | Where | What it does now |
|---|---|---|
| Hero column | :183-235 | `VideoDetailsMedia` (`widthFraction = MEDIA_WIDTH_FRACTION`, `scrim = MediaScrim.Details`), then a `Column` at `SIDE_EDGE` 11 dp: title, ratings, chips, `PlotBlock(weight(1F, fill = false))`, `GAP_PLOT_TO_FACTS`, `Spacer(weight(HERO_FACTS_ABOVE))`, four `HeroLine`s, `Spacer(weight(1F - HERO_FACTS_ABOVE))`, `ActionRow`. |
| Height budget | :214-224, KDoc :879-893 | The plot and the two spacers share the hero's leftover height 1 : 1, so the plot gets at most half of it. `fill = false` hands back nothing it does not use: a shorter plot lifts the fact lines and the action row (README "Still open", "The action row is not pinned"). |
| `PlotBlock` | :297-366 | `width(PLOT_WIDTH)`, `heightIn(min = PLOT_MIN_HEIGHT)`, a `layout {}` that snaps the max height down to whole `PLOT_LINE`s, `onPreviewKeyEvent` UP/DOWN scrolling by one line, `focusable(enabled = scrollable)` (focus only when the text overflows), 1 dp primary border when focused, inner `Column(verticalScroll)` with `Text(fontSize = PLOT_TEXT_SIZE, lineHeight = PLOT_LINE)`. |
| Text style | :358-363; `core/ui/uikit/theme/Type.kt:11` | Only size and line height are named; everything else comes from `LocalTextStyle` = `bodyLarge`. A measurer must use the same merged style or its line count will differ. |
| Constants | :861-866 | `PLOT_WIDTH` 590 dp, `PLOT_TEXT_SIZE` **13 sp**, `PLOT_LINE` 18 sp (line height, not font size), `PLOT_MIN_HEIGHT` 110 dp (6 lines plus 2 dp), focus border 1 dp. |
| Media start | :831 | `MEDIA_WIDTH_FRACTION` 0.655 of 960 dp, so the still begins at x 331.2 dp. The plot box spans 11 to 601 dp. |
| Scrim | `core/ui/uikit/component/details/VideoItemGridDetails.kt:175-180` | `MediaScrim.Details`: alpha 1.0 at the frame's left edge, 0.50 at 10 % of the frame (about 394 dp on screen), clear at 50 % (about 646 dp). The details screen is its only caller. |
| Skeleton | :724-760 | `SkeletonBar(width = PLOT_WIDTH)` x3; stays at 590 unless changed. |
| Hero lines | :868-877 | `HERO_LINE_WIDTH` 940 dp, independent of the plot width. |
| Plot text source | `state.details.description` (:215) | Comes with `DetailsScreenState.Content`; the VM rebuilds Content from cache, then from the network (`DetailsVM.kt:272`). Whether the description can differ between the two is unverified. |
| Text measuring elsewhere | `ui/feature/player/component/PlayerTimedActionOverlay.kt:467`, `ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt:285` | Both use `rememberTextMeasurer()` with `measure(text, style)` inside `remember`. The overlay is a pattern you can reuse. `EpisodesPanel.kt:148` uses `BoxWithConstraints`. |

Overlap of the plot box with the still: about 272 dp at 590, 202 at 520, 142 at 460, 102 at 420. A plot clear of the still would need a box of 320 dp or less.

Tests around it:

| Test | Kind | Relevance |
|---|---|---|
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/details/component/DetailsScreenContentTest.kt` | instrumented (`make DEVICE=<serial> itest`) | `aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions` (:171) with `LONG_DESCRIPTION` (60 repeats); `heroShowsTitle...WithoutScrolling` (:54) with a one-line plot; focus hand-off tests (:88-170). |
| `app/src/test/.../details/component/DetailsScreenKeyTest.kt`, `details/vm/*`, `details/model/DetailsScreenUIMapperTest.kt` | JVM unit | Do not touch plot layout. |
| `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:22` | JVM unit | Checks that the profile covers the `details/` classes; a new composable/lambda in the same file stays covered by the prefix rule. |
| Screenshot tests | none | No Roborazzi/Paparazzi in `gradle/libs.versions.toml`, no Robolectric. |

## 4. Related work

| Item | Relation |
|---|---|
| `docs/design/content/` (`README.md`, `index.src.html`, `build.py` at `docs/design/build.py`) | The stand that holds the agreed sizes; `plotW` slider 240-640 step 5 (`index.src.html:691`), note "кадр начинается на 331 dp". |
| Commits `60e8c9a9` (set `PLOT_WIDTH = 590`), `834ee233` (`HERO_FACTS_ABOVE = 0.95`), `d0a3fea7` (stand), `0192929e` (hero/rail redesign), `d62c3f14` (genres line) | History of the hero layout. |
| README "Still open": action row not pinned; facts lines one line each; picture behind long lines not judged on a bright still | Same area. Changing the plot width bears on the first and third. |
| Runs `detail-poster-directional-change` (done), `trailer-video-hidden-regression` (done) | Touch the same media/text area; no overlap with plot width. |
| `docs/superpowers/specs/2026-08-20-details-single-screen-design.md` | Original single-screen hero spec. |

Duplicate: none.

### Stand: how it works and how a slider is added

- `SHIPPED` (`index.src.html:626`) holds the shipped values (`plotW:590, plotSp:13, plotLine:18, plotMin:110`); `BEFORE` (:648) holds the first edition, shown as grey "было".
- `CONTROLS` (:670-) is one entry per slider: `{g: group, k: key, n: label, min, max, step, u: unit, note?}`. To add a slider, add the key to `SHIPPED` (and `S` picks it up through `...SHIPPED`, :767), add a `CONTROLS` entry, and read `g.<key>` in `geom()` (:780) / `heroHTML`. Sliders show amber when moved off the shipped value.
- `SWITCHES` (:736) is a list of option groups (`item`, `media`, `fit`, `facts`, `pin`, `rail`, `guides`). An "adaptive width: off / steps" switch fits here.
- `measureLines()` (:843) counts wrapped lines with the real font in a hidden ruler; `heroHTML` (:891-933) already models the 1 : 1 share, snapping, `plotMin` and scrollability. An adaptive width needs only a loop over the candidate widths calling `measureLines`.
- Items: four from the device cache (Укрытие, Чужестранка, Аполлон 13, Хищник). All four plots are medium length (roughly 300-560 characters). There is no very short and no very long plot. Add one of each (an edited text is enough) to judge the steps.
- After editing, run `python3 docs/design/build.py content` to rebuild `index.html`; the README's "Agreed sizes" table and the published artifact get updated in the same change (`docs/design/README.md`, the `designing-a-screen` skill section 3).

## 5. External findings

| Finding | Source |
|---|---|
| `TextMeasurer` (added in compose-ui 1.3.0) measures text outside composition; `measure(text, style, overflow, softWrap, maxLines, constraints, ...)` returns a `TextLayoutResult` (`lineCount`, `size`). It keeps an LRU cache keyed on layout inputs; color/shadow changes reuse the layout. Get one with `rememberTextMeasurer` to inherit density, font resolver and layout direction. The overload that takes `defaultLocaleList` is listed as added in 1.13.0. Which compose-ui version BOM `2026.09.00` resolves to is unverified, so call with named `text`/`style`/`constraints` only. | https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextMeasurer (read 2026-11-26) |
| `rememberTextMeasurer(cacheSize = ...)` exists; `cacheSize = 0` disables the implicit cache. The default value could not be read off the page (unverified). | https://developer.android.com/reference/kotlin/androidx/compose/ui/text/package-summary (read 2026-11-26) |
| Compose forbids measuring a child twice in one pass. Intrinsic queries (`maxIntrinsicHeight(width)`) run before measuring and do not count as measuring. So a `layout {}` can try several widths through intrinsics or a `TextMeasurer` and then measure the child once. | https://developer.android.com/develop/ui/compose/layouts/custom ; https://developer.android.com/develop/ui/compose/layouts/intrinsic-measurements (read 2026-11-26) |
| The TV typography guide recommends larger type and Roboto for body text, and gives no line-length or minimum-width figure. The readability floor has to come from the stand and the TV. | https://developer.android.com/design/ui/tv/guides/styles/typography (read 2026-11-26) |

Readability estimate (unverified, to be measured on the stand): at 13 sp Roboto, Cyrillic averages about 6-7 dp a character, so 590 dp is about 85-95 characters a line, 460 dp about 65-75, and 420 dp about 60-70. The usual comfortable range for body text is 45-75 characters, so narrowing to the proposed floor stays within it and does not hurt reading.

## 6. Options

| | Option | How | Trade-offs | Size |
|---|---|---|---|---|
| A (recommended) | Step widths, "no taller than today" | Inside `PlotBlock`'s existing `layout {}`: compute the height the block takes at 590 (`max(PLOT_MIN_HEIGHT, lines590 x PLOT_LINE)`, capped by the snapped budget). Then pick the narrowest step (e.g. 590 / 520 / 460) whose line count x `PLOT_LINE` fits that height, and measure the child once at that width. Line counts come from `rememberTextMeasurer` with the merged `LocalTextStyle` (or `maxIntrinsicHeight`). The box keeps its height, so the facts and action row cannot move. | Pure layout: no state, no recomposition, nothing changes in the VM. The width is a function of (text, budget), so it is stable for a title unless the budget changes. Short plots (6 lines or fewer at 590) gain the most; plots between 6 lines and the budget barely narrow. | S |
| B | Step widths against the full budget | Same as A, but a step fits if its lines fit the half-share budget, not today's height. | Narrows more plots, but a plot that grows from, say, 6 to 7 lines makes the block 18 dp taller and pushes the facts and buttons down. That breaks the "must not move" guard. | S |
| C | Continuous width | Binary-search the smallest width (to a 5-10 dp grain) that keeps today's line count or height. | Tightest overlap, but the width differs on every title, so the right edge looks ragged from title to title. More measuring per layout. | S |

Recommended: A. It meets every guard in the proposal and changes nothing outside `PlotBlock` and its constants, plus the skeleton if wanted. The stand gets the same rule first.

## 7. Risks

- **Style mismatch.** If the measurer's `TextStyle` differs from what `Text` uses (`bodyLarge` merged with 13/18 sp, minus 2 x `PLOT_FOCUS_BORDER` of width), the line count is off by one. The plot then scrolls where it should fit, or wastes width.
- **Focusability.** `scrollable` is read from `scrollState` after layout. A narrower plot must still never overflow the box, or it becomes focusable and catches UP from the buttons (covered by `DetailsScreenContentTest.kt:171`; add a short-plot case that asserts UP does not leave the buttons).
- **Budget changes after first layout.** Content is rebuilt from cache and then from the network (`DetailsVM.kt:272`). If ratings, chips or fact lines appear later, the budget changes and option A's width can step once. Pinning the width per description (`remember(description)`) avoids the step, but can then overflow and scroll.
- **Existing constraint edge (unverified).** `heightIn(min = 110 dp)` sits before the snapping `layout {}`. If the budget is 110-125 dp, the snapped max (108) is below the min, and `constraints.copy(maxHeight = …)` could throw. Any rewrite of this modifier chain should clamp `snapped` to `constraints.minHeight` and check that case.
- **Bright stills.** Narrowing moves text off the thinnest part of the scrim. It lowers this risk; it does not settle README's open item.
- **Device check.** This is a TV UI change, so it needs an on-device check on the TV box (`com.kino.puber.stage`) at user pace. No API probing.

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Which steps, and what floor? | Dial them on the stand with a short, a medium and a long plot. Start from 590 / 520 / 460 with a 460 floor (about 70 characters a line at 13 sp). |
| Q2 | Fit criterion: today's block height (A) or the full half-share budget (B)? | Today's height (A): nothing else on the hero moves. |
| Q3 | Should the skeleton's plot bars follow? | No. Keep 590; the skeleton has no text to measure. |
| Q4 | Freeze the width per description, or recompute when the budget changes? | Recompute in layout (no state). A one-time step when cached content is replaced is acceptable. Revisit only if seen on the TV. |
| Q5 | Should the stand gain a switch "ширина описания: фиксированная / по шагам" plus step and floor sliders? | Yes. Add a `SWITCHES` entry and `CONTROLS` for the steps and floor, and add a short and a long test plot. |

## 9. Sources

- Files: `DetailsScreenContent.kt` (:120-366, :720-947), `VideoItemGridDetails.kt:140-200`, `PlayerTimedActionOverlay.kt:460-490`, `DetailsScreenContentTest.kt`, `BaselineProfileContentTest.kt`, `DetailsVM.kt`, `docs/design/README.md`, `docs/design/build.py`, `docs/design/content/README.md`, `docs/design/content/index.src.html` (:620-1220), `.claude/skills/designing-a-screen/SKILL.md`, `gradle/libs.versions.toml` (composeBom 2026.09.00, tvMaterial 1.1.0, kotlin 2.4.21), `AGENTS.md`, `docs/architecture/`.
- Commits: `60e8c9a9`, `834ee233`, `d0a3fea7`, `0192929e`, `d62c3f14`, `eac8c56d`.
- Runs: `detail-poster-directional-change`, `trailer-video-hidden-regression`.
- URLs: listed in section 5.
