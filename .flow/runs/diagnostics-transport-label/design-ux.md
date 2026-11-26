# Design delta: diagnostics-transport-label (ux-designer)

Checked against the worktree at 4c398e07. Paths are under
`app/src/main/java/com/kino/puber/`.

## Stand

No `docs/design/` stand governs the network diagnostics screen (stands exist for catalogue,
content, player-controls, showall). The conductor does not need to run `designing-a-screen`
before planning: the skill rules itself out for "a one-line style fix", and this change adds one
non-focusable line that copies the style of the subtitle already above it. It sets no new size,
spacing or colour that a slider would have to settle. If the owner rejects the look at gate 2
(AC-8), that would be the moment to start a stand, not now.

## Agreed numbers that apply

With no stand, the numbers come from the screen's own code
(`ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt:133-148`) and the theme
(`core/ui/uikit/theme/Type.kt`, which does not override `bodyMedium` or `headlineLarge`, so
the tv-material3 defaults apply):

| Element | Style | Size | Colour | Gap before |
|---|---|---|---|---|
| Title | `headlineLarge` | 32 sp / 40 sp line | default `onSurface` | top padding 28 dp |
| Subtitle | `bodyMedium` | 14 sp / 20 sp line | `onSurfaceVariant` | 4 dp |
| **Transport line (new)** | **`bodyMedium`** | **14 sp / 20 sp line** | **`onSurfaceVariant`** | **4 dp** |
| Results row | - | `weight(1f)` | - | 12 dp (unchanged, after the new line) |

The new line matches the subtitle exactly: same style, same colour, same 4 dp step as the
title-to-subtitle gap, left edge on the same 48 dp `ScreenHorizontalPadding`. One line, no
`maxLines` cut (the longest value, `Подключение: Нет подключения`, is about 200 dp at 14 sp, far
under the 864 dp of content width). It must not be bolder, larger, primary-tinted or an icon
chip: next to the title it would compete with the server cards, which carry the screen's
colour. The ported code on `upstream-sync-triage` (f037c052,
`NetworkDiagnosticsContent.kt:148-155`) already does exactly this; take it as is.

The `Spacer(4.dp)` and the `Text` go inside `state.transport?.let { }`, so a null transport
leaves the header byte-for-byte as today (AC-6) and nothing shifts when the value arrives except
the one 24 dp step down of the row below, once, on open.

### Vertical budget (on a 1080p box, 540 dp tall)

The line takes 24 dp (4 gap + 20 line) out of the `weight(1f)` results row:

| | Today | With the line |
|---|---|---|
| Fixed: 2 x 28 padding, title 40, 4, subtitle 20, 12, status area 64 | 196 dp | 220 dp |
| Results row height | 344 dp | 320 dp |
| Gauge box (`GaugeSize`) | 300 dp | 300 dp, 20 dp spare |

The gauge still fits without clipping; the server cards column is centred and two cards (about
130 dp each plus 12 dp) fit in 320 dp. The visual review must measure the row on the box: if the
gauge canvas top or the lower card edge touches its neighbour, that is a blocker, and the fix is
to fold the line into the subtitle row, not to shrink `GaugeSize`.

### Still open (owner, AC-8 / Q2)

- Whether `Нет подключения` should stand out (for example `colorScheme.error`). Recommended:
  no, keep `onSurfaceVariant` for every value; the server cards already turn to an error state
  when a test fails, and a red header line would be the only colour above them.
- Wording (brief Q2); not a visual question.

## Focus

The transport line is not focusable: a plain `Text` with no `clickable`, `focusable` or
`Surface` around it. Focus order is unchanged: first focus still lands on the current (or first)
server card through `initialFocusRequester` (`NetworkDiagnosticsContent.kt:389-393`), D-pad up
from the top card goes nowhere (or to the drawer), never to the header. No new focus visibility
is needed. `NetworkDiagnosticsContentFocusTest` must keep passing unchanged (AC-7).

## Shared constants

None are touched. `ScreenHorizontalPadding`, `ScreenVerticalPadding`, `GaugeSize`,
`StatusAreaHeight` and the 12 dp header spacer are private to this file and stay as they are;
`bodyMedium` and `onSurfaceVariant` are theme values read, not retuned. Do not add a
`Typography` override in `Type.kt` for this line.
