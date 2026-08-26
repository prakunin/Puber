# Content screen — a chosen film or series

The details screen: the hero on top — still, title, facts, actions — and one rail below it, the
chosen season's episodes on a series or the similar items on a film. Stood up 2026-08-26 against
`DetailsScreenContent.kt` as it landed in `0192929`, then dialled and fixed the same day.

Published copy: <https://claude.ai/code/artifact/cb46056c-9d47-47b9-9c32-c4e217ae4374>

## What the stand carries

- **Four items, all from the device's own cache** (`databases/puber.db`, `cached_payload`), run
  through a replica of `DetailsScreenUIMapper` so every string on the stand is the string the
  screen would print — chips, facts line, resume line, season summaries, button labels:
  - **Укрытие** (89797) — three seasons, 24 of 28 watched, resumes on 3×5. The ordinary case.
  - **Чужестранка** (13337) — eight season chips, 101 episodes, watched to the end, so the play
    button falls back to «Смотреть сериал» and the resume line reads «Просмотрено».
  - **Аполлон 13** (30) — a film, and the longest facts line in the cache.
  - **Хищник: Убийца убийц** (111985) — a second film, short plot, for the buttons-float case.
- **Real artwork.** Wide posters and episode stills from `m.staticpop.net`, downscaled to what
  the screen actually shows (1080 px for the hero, 260 px for an 80 dp card).
- **Every size in `region Sizes` on a slider**, plus the episode card's title type, which lives
  in the theme rather than the screen. Each opens on the agreed value. Amber means you have
  moved off it; the grey «было» is the first edition of the screen, before this pass.
- **Arrow keys walk the real focus graph** — plot, buttons, season chips, cards — the one wired
  through `onPreviewKeyEvent` rather than the geometric search, which walks past the chip row.

## Agreed sizes

Dialled on the stand and carried by `DetailsScreenContent.kt`, except the scrim, which lives in
`MediaScrim.Details` — the details screen is its only caller, so it was retuned in place rather
than parameterised again.

| | было | стало |
|---|---|---|
| поле сверху и снизу | 16 | **0** dp |
| поле слева | 8 | **11** dp |
| текст героя от поля | 8 | **0** dp |
| кнопки → низ героя | 16 | **0** dp |
| высота нижнего ряда | 116 | **122** dp |
| поле над сезонами | 8 | **0** dp |
| ширина кадра | 56.25 | **65.5** % |
| край подложки | 15 | **10** % кадра |
| подложка на краю | 72 | **50** % |
| подложка растворена | 36 | **50** % кадра |
| кегль названия | 24 | **28** sp |
| кегль оригинала | 14 | **15** sp |
| интерлиньяж названия | 28 | **25** sp |
| оригинал от базовой | 2 | **4** dp |
| название → рейтинги | 7 | **4** dp |
| между рейтингами | 12 | **15** dp |
| рейтинги → фишки | 8 | **3** dp |
| фишки → описание | 10 | **3** dp |
| ширина описания | 450 | **590** dp |
| кегль описания | 13 | **13** sp |
| интерлиньяж описания | 19 | **18** sp |
| минимум высоты описания | 57 | **110** dp |
| описание → строки | 9 | **13** dp |
| кегль строк фактов | 11 | **9** sp |
| интерлиньяж строк фактов | — | **13** sp |
| ширина строки фактов | 550 | **940** dp |
| непрозрачность строк | 72 | **70** % |
| высота кнопки | 24 | **30** dp |
| поле внутри кнопки | 17 | **15** dp |
| иконка кнопки | 17 | **15** dp |
| кегль кнопки | — | **11** sp |
| кегль подписи | 11 | **10** sp |
| кнопки → подпись | 14 | **5** dp |
| высота чипа сезона | 22 | **20** dp |
| поле внутри чипа | — | **15** dp |
| кегль чипа сезона | 12 | **10** sp |
| между чипами сезонов | 8 | **5** dp |
| чипы → подпись | 6 | **12** dp |
| чипы → карточки | 8 | **4** dp |
| высота карточки | 70 | **92** dp |
| между карточками | 16 | **5** dp |
| кегль названия карточки | 14 | **8** sp |
| интерлиньяж в карточке | 20 | **14** sp |
| место строки «Дорожек» | 0 | **95** % свободного |

Zero page edges and 11 dp at the left put the screen nearer the edge than Google's 48 dp TV
safe zone allows — the same call the player's overlay makes, and deliberate. Not a mistake to be
quietly reverted.

The measured result, taken off the stand's own DOM in dp: hero 418 tall from y 0, media
628.8 × 418 at x 331.2, plot 590 wide and 110 tall, facts 940 wide, the rail 122 tall from y 418
to the bottom edge at 540, cards 163.56 × 92.

Widening the plot to 590 dp puts it 270 dp under the picture; the scrim was opened up in the same
breath — 10 % of the frame at half alpha, clear by half — so the text sits on a gradient rather
than on a black panel with a seam. The fact lines then went to 940 dp, which is past the scrim
entirely: a full line ends at 951 dp, 98 % across the frame, on bare picture. That buys the whole
cast instead of the first four names, and it is the one place on the screen where legibility
depends on the still.

**`RAIL_HEIGHT` is sized off what the rail holds, not off a round number.** The card went
80 → 92 dp, and the row inside the rail then measured 26 (season chip) + 4 (gap) + 92 = 122,
against the 117 the rail had. That would not have clipped: the rail is a fixed-height `Column`,
and a card's `Modifier.height` is bounded by the constraints it is handed, so a 92 dp card in an
87 dp remainder comes out 87 dp — the dial above 87 would simply have done nothing, silently. The
rail went to 122 and the hero gave up the 5 dp, which it has. The stand models the same clamp, so
the card slider reports what it can actually reach rather than what was asked for.

The season chip then came down to 20 dp, which does *not* take the rail with it: the summary
beside the chips names only a font size, inherits `bodyLarge`'s 24 sp line, and floors that row at
24. The sum is 24 + 4 + 92 = 120 and `RAIL_HEIGHT` keeps 2 dp over. Dialling the chip below 24
makes the chips smaller and the row no shorter.

**Where the fact lines sit is now a share, not a place in the column.** The single
`Spacer(weight(1F))` under them became two — `HERO_FACTS_ABOVE` = 0.84 above the lines and the
remaining 0.16 below. Both shares are cut from the spacer's half of the remainder, which does not
move with the length of the description, so the lines hang the same fixed gap above the action row
on every item instead of sitting straight under the plot. The two still weigh 1F between them,
which is what keeps `PlotBlock`'s half intact: a third weighted child at full weight would have
cut the plot to a third of the remainder.

It places the lines; it does not pin them. A short description still lifts the lines and the
action row together, because `weight(1F, fill = false)` hands back nothing of the half the plot
did not use — the open item below, unchanged by this. The stand
carries a slider per line (**место: дорожки / режиссёр / в ролях**), each a share of what the
line above it did not take, so the three can be spread; the code needs only the first, the other
two being zero.

**The episode caption is passed in, not themed.** `VideoItemHorizontal` gained a `titleStyle`
parameter the way it already had `titleMaxLines`, and the details rail hands it `titleSmall` at
8 sp on a 14 sp line. Both halves matter: `titleSmall` names a 20 sp line, so the font size alone
would have left the caption in a box two thirds taller than its letters. Retuning `Typography`
was not an option — `CollectionCard` and the vertical card read the same style.

## Measured on the television

`assets/km6-hero.jpg` and `assets/km6-focused-card.jpg` are the ported screen rendered on KM6 at
1920 × 1080 — not the stand, the real thing. The pairing was gone, so they were taken by pointing
an instrumented run at `DetailsScreenContent` with a made-up state and writing
`onRoot().captureToImage()` out to external storage, then `make itest TESTS=…`. One trap if this is
done again: the screen paints no ground of its own, so the harness must wrap it in the same
`Surface(shape = RectangleShape)` that `App()` does. A plain `Box` with a background gives the
right ground but no `LocalContentColor`, and the title and the plot come out black on black.

What the frames settle, in dp off the pixels:

| | |
|---|---|
| title ink | 4.5 → 32, inside the 33 dp line box with 1 dp to spare |
| chips | 40 → 56 |
| plot text | 62 → 85 (two lines of a short synthetic plot) |
| fact lines | 202 → 229.5 |
| action row | 376.5 → 402.5 |
| season chips | 423 → 449 |
| cards | 453 → 533 |
| focused card's ring | 451.5 → 534.5 outer, 3 dp stroke straddling the card's edge; left edge at 9.5 |
| lowest ink anywhere | 534.5, so 5.5 dp clear of the physical bottom |

- **`TITLE_LINE` at the 25 sp first dialled would have cut the descenders.** Roboto-Medium on the
  device reports ascent 1900 and descent −500 on a 2048 unit em — 1.1719 em, so 32.8 sp under a
  28 sp title. The ink measures 27.5 dp tall and reaches 32 dp down. At 33 sp it fits.
- **Nothing reaches the screen edge, but the bottom is tighter than it looks.** The focused card's
  ring stops 5.5 dp above the bottom and its left stroke sits at 9.5 dp, and no button that scales
  on focus sits against an edge. Whether a panel overscans past that is a question no framebuffer
  capture can answer.
- **The hero is mostly empty.** On that frame the plot text ends at 85 dp and the fact lines do not
  start until 202 — 117 dp of nothing, held open by `PLOT_MIN_HEIGHT`. Then another 147 dp between
  the fact lines and the buttons, out of the unused half-share. That content is short: two plot
  lines and no ratings. With Укрытие's five lines and three ratings the two voids shrink, but they
  do not close.

## Still open

Four things the stand and the review turned up. None of them is a size, and none was settled by
this pass.

**The buttons carry a 24 sp line box they never asked for.** `MaterialTheme` provides
`LocalTextStyle` from `typography.bodyLarge`, which `PuberTheme` sets to 16 sp on a 24 sp line, and
every `Text` here that names only a `fontSize` inherits that line height — the resume caption, the
season summary, the «Похожее» label, the fact chips. Not the button label or the season chip's
number: `Button` wraps its content in `ProvideTextStyle(typography.labelLarge)`, so those two take
`labelLarge`'s line box instead. It costs nothing where the box is fixed and taller, and the
action row is no longer one of those places — at `BUTTON_HEIGHT` 30 dp the button sets the row's
height again, where at 23 it was the resume caption's inherited 24 that did. The season chip row
is where it still bites: `max(SEASON_CHIP_HEIGHT 20, 24) = 24`. The stand models both. Naming a
line height on those texts would settle it.

**The action row is not pinned to the bottom.** `PlotBlock` takes `weight(1F, fill = false)` and
the spacer under the facts takes `weight(1F)`. Compose splits the remaining height in half and a
non-filling child does not hand back what it does not use, so the buttons rise by whatever the
plot left over — up to one text line. With `HERO_BOTTOM` now 0 dp, Укрытие's row still ends 8 dp
short of the hero's own bottom, and the amount moves with the length of the plot. That is exactly
what «pinned, not flowed» is meant to prevent. The `PLOT_MIN_HEIGHT` of 110 dp holds most items
still, which is why it is there at all; it is a floor, not a fix. The switch **низ героя → прижать
кнопки к низу** shows the alternative.

**The facts, director and cast lines are still one line each with an ellipsis.**
`HERO_LINE_WIDTH` went 590 → 940 dp, which is most of what the screen has left of `SIDE_EDGE`, so
Укрытие's 17-name cast and Аполлон 13's four dub credits fit where they did not. But `maxLines`
is still 1, and a longer list still ends in an ellipsis with nowhere left to widen to. The switch
**строки фактов** offers two lines, or the whole thing wrapped; both take their height out of the
plot's share.

**The player's episodes panel now carries a visibly bigger caption than the details rail.**
`EpisodesPanel` measures its card off the width it has — (960 − 18 × 2 − 18 × 4) / 5 × 9/16 =
95.85 dp — so it is within 4 dp of the details rail's 92 dp card, and it takes `titleStyle` as it
comes: 14 sp on a 20 sp line against the rail's 8 on 14. Nothing here changed it; the two simply
never agreed, and shrinking one of them made the disagreement visible. The panel was never dialled
on a stand, so it was left alone.

**A film's rail is 122 dp of nothing.** `similarItems` was empty for every film in the device's
cache — the API returned none — so `DetailsRail` falls to its `else -> Box(modifier)` branch and
the screen ends in an empty band, five dp wider than before. The **похожее** option fills the
rail with real cards from the same cache so the row can be judged; the **пусто** option is what
the television shows today.

Also open:

- **`MediaWidthFraction` is shared with the catalogue** (`VideoItemGridDetails.kt`). The details
  screen passes its own `widthFraction`, so 65.5 % went into `MEDIA_WIDTH_FRACTION` on the
  details side and the catalogue's own default was left alone.
- **The still is fitted to width and centred**, so a 16∶9 frame in a 423 dp hero leaves 34.65 dp
  of black above and below it. The **вписать** switch offers filling the height with a crop, or
  pinning the frame to the top.
- **The picture behind the long lines has not been judged.** The frames in `assets/` were taken
  with a placeholder, not a bright still, and their cast line is short. A 15-name cast on a sunlit
  poster is the case that would show whether the softened scrim still holds. That one needs the
  KinoPub pairing back and `puber://content/items/89797`.
- **The skeleton's rail sits about 7 dp below the loaded one.** `DetailsContentSkeleton` lays its
  own bottom group out rather than reserving `RAIL_HEIGHT`, so the two disagree at the moment the
  content arrives. The gap was about 8 dp before this pass and is about 7 dp after it — not
  introduced here, but `Spacer(HERO_BOTTOM + RAIL_TOP)` is now literally zero and no longer models
  anything.
- **The focused card's ring straddles the card's edge.** `CardDefaults.border` builds its focused
  border as `Border(BorderStroke(3.dp, colorScheme.border), inset = 0.dp,
  shape = RoundedCornerShape(8.dp))` — read off `CardDefaults.border` in `tv-material-1.1.0.aar`,
  where the stroke width is the `3` and the inset is defaulted to zero. The frame settles where a
  zero inset actually puts it: the stroke is centred on the outline, 1.5 dp outside the card and
  1.5 dp inside it. Measured on `km6-focused-card.jpg`, the top stroke runs 451.5 → 454.5 and the
  bottom 531.5 → 534.5 around an 80 dp card at 453 → 533. Two earlier readings of this were wrong:
  a 6 dp outward inset that would have reached into the season chips, and then a ring wholly inside
  the card eating 3 dp of the still. It eats 1.5 dp, and it is the 1.5 dp on the outside that
  spends the bottom clearance.
- Button icons are Phosphor Duotone in the app and plain silhouettes of the same size on the
  stand: what is being judged is the 15 dp, not the drawing.
