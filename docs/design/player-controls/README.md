# Player control overlay

The title, the seek bar and the button row drawn over playback. Designed 2026-08-26 against
frames taken off KM6 (1920 × 1080, density 320, so 1 dp = 2 px).

Published copy: <https://claude.ai/code/artifact/d5220e80-c653-482b-ab1b-61913b838dda>

## What the stand carries

- The frames in `assets/` are real: `letterbox.jpg` is a 2.39∶1 title as it arrives (the bar
  sits on pure black), `fill.jpg` is the same frame cropped to 16∶9 so the bar can be judged
  over picture. Both matter — legibility differs completely between them.
- Three arrangements of the button row: as it ships (three weighted sections), one group at the
  left edge, one group centred.
- Three shapes for the right-hand group. The middle one — settings + about — is what shipped;
  the other two are kept so the choice can be re-argued against the same frames.
- Arrow keys walk the focus, so the remote's path is visible before it is written.

## Agreed sizes

Dialled on the stand and carried by `PlayerControlsMetrics.kt`. Measured back off the television
after the port; every one matched.

| | было | стало |
|---|---|---|
| высота пилюли | 44 | **25** dp |
| ширина пилюли | 58 | **30** dp |
| зазор между кнопками | 12 | **5** dp |
| иконка | 20 | **15** dp |
| иконка play/pause | 24 | **20** dp |
| фон кнопки | 4 | **3** % alpha |
| фон в фокусе | 32 | **20** % alpha |
| высота трека | 4 | **3** dp |
| бегунок | 12 | **10** dp |
| кегль времени | 14 | **10** sp |
| время ↔ трек | 12 | **5** dp |
| поле слева и справа | 48 | **20** dp |
| поле снизу | 16 | **10** dp |
| центр трека → кнопки | 24 | **20** dp |
| кегль названия | 28 | **20** sp |
| кегль подзаголовка | 16 | **10** sp |
| поле сверху | 28 | **10** dp |

20 dp at the sides is deliberately inside Google's 48 dp TV safe zone, the same edge the content
screen uses. Not a mistake to be quietly reverted.

The gap from the track centre to the button row is not a padding anyone typed: it is
`TrackCentreToButtons − ProgressRowHeight / 2`, so it survives a change to either. That is also
why the progress row has a height of its own rather than taking one from the time labels — a row
that grew with whatever the clock reads would drag the button row about. It is a floor, not a
cap: scale the system font up and the row grows rather than clipping the labels.

## Grouping by meaning

Ported. The row reads left to right as content · transport · playback:

| | было | стало |
|---|---|---|
| слева | серии | серии, о фильме, просмотрено |
| по центру | предыдущая, пауза, следующая | без изменений |
| справа | звук и субтитры, видео, о потоке, просмотрено | одна шестерёнка |

Three buttons onto the same panel only ever differed in which door took the focus, so they are
one now: `ActivePanel.AudioSubtitles` and `ActivePanel.VideoSettings` became `ActivePanel.Settings`,
and the panel opens on its first door every time rather than on a door the pressed button chose.
Stream diagnostics did not go anywhere — it is `SettingsDoor.Stream`, the «Поток» door in that
same root, and it is still what keeps the live readings flowing while the panel is up.

`ActivePanel.Info` became `ActivePanel.About` and now carries the item's own plot, which the
player had no way to show before: `PlayerAboutPanel` scrolls the whole text, four lines a press,
and nothing is trimmed. The button is absent when the API gave us no plot — a door onto an empty
page is worse than no door. The scrolling-focusable column both it and the stream page use is
`PlayerPanelScrollBox` in `PlayerSidePanel.kt`.

## Still open

- **`PlayerProgressBar` and `PlayerTitle` are shared.** The trailer overlay on the content screen
  (`TrailerOverlay.kt`) and the buffering bar (`PlayerOverlayLayers.kt`) draw them too, and
  inherited the new sizes — but kept their own bottom paddings of 32 dp and 24 dp against the
  player's 10 dp.
- **Focus on the seek bar is shown by nothing at all.** `SeekTrack` is a bare `focusable()`, and
  a 3 dp track makes that harder to live with than a 4 dp one did. The stand has two sliders for
  it, both parked at the current no-op values.
- **The About panel has not been measured on the television.** Its 13 sp / 19 sp prose and the
  panel's 420 dp cap were taken from the settings pages, not dialled against a frame.
