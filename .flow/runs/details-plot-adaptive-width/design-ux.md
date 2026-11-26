# Design delta: details-plot-adaptive-width (ux-designer)

## Governing stand

`docs/design/content/` governs this change (`README.md` "Agreed sizes", `index.src.html`,
built with `python3 docs/design/build.py content`). A stand exists, so the conductor does not
need to start `designing-a-screen` from scratch. One value is still open, though: the steps and
the floor. Those need the owner on the stand's sliders, so the stand has to gain the controls below
before the owner dials them (AC-5).

## Visual delta

- The plot box is narrowed only when the plot fits at a narrower step without growing taller than
  it is at 590 dp today (option A). The box height does not change, so nothing else on the hero
  moves: title, ratings, chips, fact lines, action row and rail stay where they are.
- In practice this narrows plots of up to 6 lines at 590 dp, because those sit on the
  `PLOT_MIN_HEIGHT` 110 dp floor (6 x 18 + 2 x 1 dp border). A plot of 7 lines or more at 590
  narrows only if a narrower step wraps it into the same number of lines, which is rare. Long
  plots that scroll stay at 590 dp. The owner should know this before dialling: medium plots barely
  change, short ones change the most.
- The still starts at x 331.2 dp, and the box spans x 11 to 11 + W. `MediaScrim.Details` is
  alpha 1.0 at 331.2, 0.50 at 394.1 (10 % of the 628.8 dp frame) and clear at 645.6 (50 %).

| Plot width W | Right edge | Under the still | Scrim alpha at the right edge |
|---|---|---|---|
| 590 (today) | 601 dp | 269.8 dp | 0.09 |
| 520 | 531 dp | 199.8 dp | 0.23 |
| 460 | 471 dp | 139.8 dp | 0.35 |
| 420 | 431 dp | 99.8 dp | 0.40 |
| 320 | 331 dp | 0 | 1.0 (fully clear of the still) |

  Each step narrower moves the end of the line onto denser scrim. That is the readability gain
  the owner asked for. Rough density at 13 sp is 6 to 7 dp per Cyrillic character: about 85 to 95
  characters a line at 590 and 65 to 75 at 460. The stand has to confirm this, because the TV
  typography guide gives no floor.
- The skeleton's plot bars stay at `PLOT_WIDTH` 590 (Q3).

## What the stand must gain

All of these go in `docs/design/content/index.src.html`, followed by a rebuild of
`index.html`:

1. **A switch** in `SWITCHES`: `{k:"plotFit", n:"ширина описания", opts:[["fixed","фиксированная"],["steps","по шагам"]]}`.
   It opens on `fixed` (what ships today). After "Зафиксируй" the default becomes `steps`.
2. **Two sliders** in `CONTROLS`, group "Описание", added to `SHIPPED` and used by `geom()`:
   - `plotStep`, "шаг ширины": range 10 to 200, step 5 dp, starting at 70.
   - `plotFloor`, "минимум ширины": range 320 to 590, step 5 dp, starting at 460, with the
     note "кадр начинается на 331 dp".

   The candidate widths run `plotW`, `plotW - plotStep`, ... while each stays above
   `plotFloor`, and `plotFloor` itself is always the last one. The starting values give
   590 / 520 / 460 (Q1). The existing `plotW` slider is still the widest step. `BEFORE` has
   no value for either new slider, so no grey "было" appears.
3. **The same rule as the code**, so the stand cannot disagree with Compose:
   - base height = `max(plotMin, lines590 x plotLine + 2)`, capped by the share snapped down to
     whole lines (the Compose `layout {}` does this; the stand currently skips the snapping);
   - lines at each step = `measureLines(text, step - 2, plotSp, plotLine)`;
   - pick the narrowest step where `lines x plotLine + 2 <= base height`;
   - fall back to `plotW` when no step fits, or when the plot scrolls at `plotW`.

   The block keeps the base height, and only its width changes.
4. **Two sample plots**, chosen with a switch `{k:"plotText", n:"текст описания", opts:[["item","из карточки"],["short","короткое"],["long","длинное"]]}`.
   The switch replaces only the plot of the current item, so the artwork, facts and buttons stay
   real.
   - Short: one or two sentences, about 120 to 200 characters, 1 to 2 lines at 590.
   - Long: about 1,500 or more characters, enough to overflow the share at 590 so the plot scrolls
     and takes focus.

   The four cached items (300 to 560 characters) cover the medium case. An edited text is enough
   for both samples.
5. **Readout and guides.** The spec block reports the width that was applied, its line count, the
   line count at every candidate step, the overlap with the still in dp and the scrim alpha at the
   right edge. The "описание" guide line follows the applied width, and a faint second line stays
   at `plotW` so the gain is visible.

## Numbers the screen must keep (README "Agreed sizes", `DetailsScreenContent.kt`)

| Item | Value | Constant |
|---|---|---|
| plot width, widest step (long plots, skeleton bars) | **590** dp | `PLOT_WIDTH` |
| plot font size | **13** sp | `PLOT_TEXT_SIZE` |
| plot line height | **18** sp | `PLOT_LINE` |
| plot minimum height | **110** dp | `PLOT_MIN_HEIGHT` |
| focus border, corner radius | **1** dp, **4** dp | `PLOT_FOCUS_BORDER`, `PLOT_FOCUS_RADIUS` |
| chips to plot | **3** dp | `GAP_CHIPS_TO_PLOT` |
| plot to fact lines | **13** dp | `GAP_PLOT_TO_FACTS` |
| position of the "Дорожек" line | **95** % of the free height | `HERO_FACTS_ABOVE` 0.95 |
| fact line width | **940** dp | `HERO_LINE_WIDTH` (does not depend on the plot width) |
| still width | **65.5** % | `MEDIA_WIDTH_FRACTION` |
| scrim | **10** % / **50** % / **50** % | `MediaScrim.Details` |
| left margin | **11** dp | `SIDE_EDGE` |

Still open, to be set by the owner on the stand: the step (start 70 dp, which gives 590 / 520 /
460) and the floor (start 460 dp). The new rows go into the README "Agreed sizes" table and into
the constants in the same change. Suggested rows: "шаг ширины описания" (было —) and
"минимум ширины описания" (было —, since today's width is fixed at 590).

## Focus

- The change adds no focusable element.
- The plot still takes focus only when it scrolls (`focusable(enabled = scrollable)`). Under
  option A, a plot narrower than 590 never scrolls by construction, so it never takes focus, and
  UP from the actions stays on the actions (AC-4). The 1 dp primary ring, radius 4 dp, therefore
  only ever appears around a 590 dp block, exactly as today (AC-3).
- Visual check: a narrowed plot must not end mid-line or clip a descender at its bottom edge. The
  base height already holds whole lines plus the 2 dp border padding, so a correct line count
  guarantees this. A count off by one would show as a plot that scrolls and takes a focus ring.
  That is the visible failure to look for on the TV.

## Shared constants: parameterise, do not retune

- `PLOT_WIDTH` stays 590 and becomes the widest step. Add the new constants (for example
  `PLOT_WIDTH_STEP` and `PLOT_MIN_WIDTH`, or an explicit list of steps) next to it in
  `region Sizes`, and do not change 590 in place. The skeleton reads `PLOT_WIDTH`.
- Leave `MediaScrim.Details` and `MEDIA_WIDTH_FRACTION` alone (`VideoItemGridDetails.kt` is
  shared with the catalogue). Narrowing does not call for a different scrim.
- Keep `HERO_LINE_WIDTH` independent of the plot width.
- Measure at the same inner width and style that `Text` uses: the step minus 2 x
  `PLOT_FOCUS_BORDER`, with `bodyLarge` merged with 13 sp / 18 sp. The stand measures at
  `step - 2` to match.

## README follow-ups in the same change

- Add the rows to "Agreed sizes" and a short paragraph on the rule (option A: narrowest step no
  taller than at 590; the plot box keeps its height).
- Leave "The picture behind the long lines has not been judged" open, but note that narrowed plots
  now end on denser scrim. The fact lines at 940 dp are unchanged, so the bright-still question
  still applies to them.
- Republishing the claude.ai copy is optional. The repo copy is the one that counts.
