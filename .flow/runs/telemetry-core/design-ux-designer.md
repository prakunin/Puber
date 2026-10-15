# Design delta: telemetry-core (Settings > Developer)

Author: ux-designer, design mode. Read against `telemetry-core` worktree at base `39903d31`.

## Governing stand

**No `docs/design/<screen>/` stand covers Settings.** The stands that exist are `catalogue`, `content` and
`player-controls`, and none of their numbers applies to the Settings panel. The usual route would be
`designing-a-screen` with the owner before planning. Owner decision Q1 (state.md, 2026-10-15) waives it on
one condition: the change only reuses existing Settings row styles. This delta holds to that condition. Any
step past it (a new colour, a dot or badge, a new row component, new sizes) brings the stand route back.

So the reference here is the Settings code as it is, not a stand. The sizes below are quoted from the source
and still need checking on the device in review:

| What | Value | Source |
|------|-------|--------|
| Screen padding | 48 dp horizontal, 28 dp vertical | `DeviceSettingsContent.kt:73-74` |
| Section list width | 264 dp; rows 16/14 dp padding, `titleMedium`, 6 dp gap, 12 dp corner | `DeviceSettingsContent.kt:75, 290, 308, 330` |
| Panel | `surfaceVariant` @ 0.2, 20 dp corner, top pad 8 dp; list pad 12/8 dp, 4 dp row gap | `DeviceSettingsContent.kt:367-371, 437-438` |
| Row | min height 48 dp, padding 16 h / 8 v dp, 12 dp corner, focused scale 1.0 | `DeviceSettingsItems.kt:71, 121-122, 165-166` |
| Row headline | `titleMedium` (16 sp / 18 sp line) Medium, max 2 lines | `DeviceSettingsItems.kt:172-174`, `Type.kt` |
| Row supporting text | `bodySmall` 12 sp, content colour @ 0.7, top pad 3 dp, max 2 lines | `DeviceSettingsItems.kt:177-183` |
| Trailing text | `bodyMedium`, content colour @ 0.76, 20 dp gap, 1 line | `DeviceSettingsItems.kt:187-195` |
| Switch | Material switch scaled 0.75 (39 x 24 dp), 16 dp gap, not focusable | `DeviceSettingsItems.kt:77, 198, 239-264` |
| Focus | fill `onSurface`, content `surface`; pressed `onSurface` @ 0.82 | `DeviceSettingsItems.kt:130-133` |
| Disabled | whole row alpha 0.46 (`dimWhenDisabled`) | `DeviceSettingsItems.kt:103` |

Text width available in a toggle row on 1920 x 1080 (960 x 540 dp): 960 - 2*48 - 264 - 28 = 572 dp of panel,
minus 2*12 list padding, 2*16 row padding, 16 + 39 switch slot = **about 460 dp**. That is roughly 65-70
characters of 12 sp `bodySmall` in Cyrillic before the supporting text wraps to a second line.

## The delta

### Section list

- `DeviceSettingsContent.kt:185-187`: the section list stops filtering `Developer` out. All seven sections
  show in every build. The order stays as it is (`SettingsSection` entries), with Developer last. The list
  has no new rows or sizes. Seven rows at about 46 dp plus 6 dp gaps come to about 364 dp, inside the roughly
  424 dp under the title, so the list must not scroll. Check this on the device.
- The section title "Для разработчиков" / "For developers" and its description "Диагностическая информация
  для отладки" now ship to prod. They still describe the section, so they stay. A rename (for example to
  "Диагностика") is a wording question for the owner, not a visual one.

### Rows in the Developer panel, top to bottom

1. **Session log toggle**: `SettingsToggleItem`, key `"session-log"`.
   - Label: "Журнал сеанса (для диагностики)" / "Session log (for diagnostics)".
   - `description` always set, so the row keeps one height in both states and does not jump when toggled:
     - off: one line explaining what it does and that it turns off by itself, for example "Записывает
       действия в приложении в локальный файл. Выключится сам через 7 дней."
     - on and recording: **the indicator**, for example "Идёт запись · осталось 6 д 23 ч" / "Recording ·
       6 d 23 h left".
     - on but storage unavailable (AC-3, `getExternalFilesDir` null): the line must not say recording, for
       example "Запись недоступна: нет хранилища". Whether the switch shows off or on in this case is the
       ViewModel's call, not a visual one.
   - Keep each variant to one line, at most about 60 characters in Russian (see the width above), so the
     row is the same height in all three states.
2. **Share log**: `SettingsListItem` with `role = Role.Button`, key `"share-log"`. This is the same pattern
   as "Проверить обновления сейчас" (`DeviceSettingsContent.kt:499-506`) and "Проверка скорости"
   (`:762-770`), the existing row-shaped actions in Settings.
   - Headline: "Поделиться журналом" / "Share log".
   - Supporting text: "Упаковать закрытые файлы в архив и отправить" when there is something to share.
     Optionally the archive size goes in `trailingText` (for example "1,2 МБ"), the same slot the API
     domain row uses for its value (`:755`).
   - **Nothing to share**: `enabled = false`, `focusableWhenDisabled = true`, default `dimWhenDisabled`
     (row alpha 0.46), supporting text "Пока нечего отправлять" / "Nothing to share yet". It stays
     focusable so the D-pad does not skip it and the reason can still be read. This matches the disabled
     "Rebuild the index" button in Data (`DeviceSettingsInformationItems.kt:70-76`, `focusableWhenDisabled =
     true`). Do not hide the row.
   - **Zip in progress**: the same disabled, focusable look, with the supporting text "Готовлю архив…". If
     the work takes long enough to need a progress bar, reuse the `SettingSwitchItem` pattern: a `Box` with
     a `LinearProgressIndicator` along the bottom edge (`DeviceSettingsItems.kt:282-295`). No spinner and
     no new component.
3. **Debug overlay** ("Показывать информацию о потоке"): unchanged, `item(key = "debug-overlay")`, emitted
   only under `BuildConfig.DEBUG` and moved to the end of the list. No heading separates it.

There is no separate indicator row. A non-clickable `SettingsListItem` between the toggle and Share would be
a line of text with no focus stop. It reads as a broken row, and the D-pad passes over it. Putting the
indicator in the toggle's own supporting line keeps it next to the switch that controls it. It is visible
whether or not the row has focus, and it inverts correctly with the focused fill because it uses
`LocalContentColor`.

### Not to add

- No coloured recording dot, red text or `primary`/`error` tint on the indicator. A fixed colour does not
  invert with the focused row (white fill, dark content) and would turn into a hole on focus. It would also
  be a new visual element, which Q1 excludes.
- No `TvSafeButton` for Share. In this panel the pill button only appears under an information block in
  Data. In the Developer section it would set a second action style beside the list rows.
- No `SectionHeading` above the three rows. The panel title and description already say what the section
  is, and one more heading would only push the rows down.

## Focus order and visibility

- Right from the "Для разработчиков" section row goes to the panel's top item, the **Session log toggle**.
  This is the existing `panelFocusRequester` and `onExit` route (`DeviceSettingsContent.kt:282-286`), and
  the list scrolls to item 0 on the way out (`:427-429`).
- Down goes to **Share log** (a focus stop in all states, including disabled), then in debug builds Down
  goes to **Debug overlay**. Up reverses this. Down from the last row stays put.
- Left from any of these rows goes to the "Для разработчиков" row in the section list. This comes for free
  through `LocalSettingsLeftFocusRequester` and `returnFocusLeft` in `SettingsListItem`, as long as all
  three rows are built on `SettingsListItem` or `SettingsToggleItem`.
- Visibility: the focused row has the standard inverted fill (`onSurface` with `surface` content, 12 dp
  corner, no scale). On a disabled, focusable Share row the whole row, fill included, is at alpha 0.46. That
  is the existing disabled-focused look and must still read as a grey slab on the panel. Review checks this
  on the device.
- Toggling keeps focus on the toggle row. The item key must not change with the state, and the description
  swap must not change the row height.
- **Return from the share sheet**: when the system chooser closes and Settings resumes, focus must be back on
  the Share row, not on the navigation rail or the section list. The precedent is the network-diagnostics
  return (`restoreNetworkDiagnosticsFocus`, `DeviceSettingsContent.kt:198-204`): a requester on the row
  and a request after resume. Review checks it visually with OK on Share, then Back out of the chooser,
  then a screenshot.

## Shared constants: reuse, do not retune

These are shared by every Settings row. The change must not touch them, and must not copy them into new
values: `ItemMinHeight` 48 dp, `DenseItemMinHeight`, `TrailingSlotSize`, `SwitchScale` 0.75, the 0.46
disabled alpha, the 0.7 and 0.76 supporting and trailing alphas, the 12 dp row corner, and the 16/8 dp row
padding in `SettingsListItemBody`. The same applies to the panel and navigation constants in
`DeviceSettingsContent.kt:73-75`. If a new row seems to need different spacing, raise that as a stand
question. Do not pass a one-off modifier.

The debug-only rule moves from the section filter to the single row. It belongs inside `developerItems`, next
to the overlay item, and the list filter is not to be reworded into a different condition.

## Open items for the owner (wording, not geometry)

- The indicator and description text (both locales), including the format of the time left. Recommendation:
  always "N д H ч", and "меньше часа" in the last hour.
- Whether to rename the section for a prod audience ("Для разработчиков" or "Диагностика").
- Whether Share shows the archive size in the trailing slot.

## What review will measure on device (1080p, dp = px / 2)

The section list with seven rows and no scroll. Toggle row height equal in the off and on states (48 dp
minimum, so 96 px or more, and the same in both). Supporting text on one line in both states. The Share row
disabled at alpha 0.46 and still visibly focused. Left edges of all three rows aligned with the other
sections' rows. No overlay row in deploy and release builds. Focus on Share after the share sheet is
dismissed.
