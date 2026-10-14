# Design delta: subtitle-upstream-port (ux-designer)

## Which stand governs

**No stand on master governs the subtitle picker.** It is a page of the player settings side
panel (`PlayerSettingsPanel.kt` `SubtitlesPage`, rows drawn by `PlayerPanelItem` in
`PlayerSidePanel.kt`).

- `docs/design/player-controls/` (master) covers the title, the seek bar and the button row.
  It does not cover the side panel. This change leaves every number in it alone.
- `docs/design/player-overlays/` exists only on the unmerged `player-overlays-port` branch
  (commit 92a9441d). It has a side-panel scene, but its README lists the panel as **"Not yet
  agreed"**, and its "as shipped" numbers come from source, not from the television. The resume
  prompt scene there *is* agreed (`ResumeDialog.kt`, bottom-left at 20 / 48 dp), but master still
  ships the old dialog.

This does **not** block planning. The change adds no layout, size, colour or material. It only
changes the text that existing rows show, plus when the controls are visible. The panel geometry
stays the shipped code values listed below. Nobody has dialled them, and this run must not retune
them. When the side-panel scene of `player-overlays` is dialled, it takes the new labels as
content (see "Content for the stand").

## Visual delta

| Where | Before (old base / master) | After |
|---|---|---|
| Subtitles page rows | raw API code (`rus`, `eng`, `ai`), `rus · вариант 2` | `Русский` / `Russian`, `Созданные ИИ` / `AI-generated`, `ENG` for an unresolved code, `Субтитры N` / `Subtitles N` for a blank one |
| Forced row | no marker | `<label> · частичные` / `· forced` |
| Rows that would read the same | numbered per raw language | `<label> · вариант N` / `· variant N`, numbered per final label |
| Root "Субтитры" door, trailing value | selected raw code, or `Выкл.` | selected label (same strings), `Выкл.` / `Off` by default |
| Fresh media with no resume prompt | controls up, first Back left the player | controls up on the buttons, first Back hides them and video keeps playing |
| Fresh media with a resume prompt | controls could show under the prompt | controls hidden, the prompt alone on screen |
| Subtitles on fresh media | could appear unasked (HLS DEFAULT/FORCED) | none until chosen |

Labels get longer: the worst case is `Русский · частичные · вариант 2`, about 31 characters.
The panel is content-sized (`.width(IntrinsicSize.Max)` between `PanelMinWidth` 240 dp and
`PanelMaxWidth` 420 dp), so a long label **widens the whole panel** on the Subtitles page.
`animateContentSize` animates the change between pages. This is the shipped behaviour, not a
regression, but it is the one place where the new strings show up as geometry.

## Numbers that apply (shipped in code, not agreed on a stand)

From `PlayerSidePanel.kt` / `PlayerSettingsPanel.kt` on master:

- Panel: min 240 dp, max 420 dp, padding 20 / 20 / 20 / 16 dp, corner 20 dp (left only),
  flush to the right edge. Title 14 sp / 18 sp, 12 dp below it.
- Row (`PlayerPanelItem`): min height 40 dp, padding 12 × 6 dp, corner 12 dp; label 14 sp /
  18 sp Medium, **maxLines 2, ellipsis**; trailing value 13 sp at 76 % alpha, **maxLines 1,
  ellipsis**, 16 dp before it; check icon 18 dp, 12 dp before it. Selected bed: primary at 16 %;
  focused bed: onSurface at 30 %.
- Page row spacing `PageSpacing` 2 dp; root door spacing `RootSpacing` 4 dp.

Checked against these: inside 420 dp minus padding (about 356 dp of row content), a 31-character
14 sp label fits on one line, and the 13 sp trailing value on the root door fits beside
`Субтитры`. Neither should wrap or ellipsize. This is calculated, not measured, so the device
pass must confirm it.

Still open (belongs to the player-overlays stand, not to this run): every side-panel number above.
The stand recommends 20 dp off the edge, all four corners 12 dp, a flat bed instead of glass,
34 dp / 13 sp rows and a 360 dp max. If that is agreed, the longest label must be re-checked
against 360 dp.

## Focus order and visibility

The change adds **no new focusable element**.

- Subtitles page: initial focus is `selectedSubtitleIndex`. With Off as the default, that is the
  first row, `Выкл.`, focused and selected (focused bed plus check). Order: Off, API rows in API
  order, `Аа Размер`. Unchanged.
- Fresh media without a resume prompt: focus target `Buttons`, so the play/pause pill must show
  the focused 20 % bed as soon as the controls appear. After the first Back, no control is visible
  or focused, and the next key shows the controls again.
- Fresh media with a resume prompt: focus target `null`, controls hidden. The prompt's own button
  holds the visible focus. No control pill may sit under the prompt.

## Shared constants: parameterise, do not retune

- `PlayerPanelItem`, `PlayerSidePanel`, `PanelMinWidth` / `PanelMaxWidth`: shared by every
  settings page, the About panel and the stream page. Do not shorten labels by tightening these,
  and do not drop `maxLines` to 1 for subtitle rows ("never hide data to protect a layout").
- Separator: `QUALIFIER_SEPARATOR = " · "` in `SubtitleLabels.kt` must stay identical to the
  ` · ` inside `player_subtitle_variant_label` (both locales), so that `Русский · частичные ·
  вариант 2` reads as one rhythm. A unit assertion on the composed string covers this.
- `PlayerPreview.kt:37-41` still shows the old content (`Русские`, `Узбекские · Созданы
  нейросетью`). Update it to the new shapes (`Русский`, `Созданные ИИ`, a forced row, a variant
  row) so the preview shows the strings that actually ship. This is a nit, not a blocker.

## Content for the stand

When the side-panel scene of `player-overlays` is dialled, its Subtitles page should carry
`Выкл.`, `Русский`, `Русский · частичные`, `English · вариант 1`, `English · вариант 2`,
`Созданные ИИ`, so that panel width is judged on the longest real label.

## What to check on device (rebased build, 1080p, dp = px / 2)

Note: the old evidence frames are 960 × 540, half scale, so 1 px = 1 dp there. They were taken
with the device in English. Retake them at full resolution.

1. Subtitles page with labels: the panel's right edge stays flush, every label is on one line with
   no ellipsis, and the check sits at the same x on every row. Measure the panel width and compare
   it with the content width (≤ 420 dp).
2. Root settings page: the `Субтитры` door shows `Выкл.` on fresh media, and the full selected
   label after a choice, with no ellipsis.
3. App in Russian at least once: `Русский`, `Созданные ИИ` titlecased. Android locale data names
   fewer languages than the JVM, so record any row that falls back to an uppercase code.
4. If the owner names a title with forced or duplicate-language subtitles: `· частичные` and
   `· вариант N` rendered, with the panel width measured. Otherwise unit coverage stands, and the
   review says `not measured` for those rows.
5. Fresh movie, no resume prompt: frame with the controls up and play/pause focused, then Back,
   then a frame with no controls and video still running.
6. Fresh media with a resume prompt: frame with the prompt alone, no control overlay under it,
   and focus visible on the prompt button.
7. Fresh media: no subtitle text in the first frame after start (Off by default).
