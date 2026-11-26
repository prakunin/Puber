# Review ux, round 1 (03265fbc)

No findings. Code-only check; the look on the TV is NOT CHECKED (box shows Unauthorized: user_banned in Settings, no device used).

Compared diff 4c398e07..03265fbc with design-ux.md:
- Style: NetworkDiagnosticsContent.kt:148-156 plain Text, bodyMedium, onSurfaceVariant, same as the subtitle; no weight, tint or icon.
- Spacing: 4 dp below the subtitle, 12 dp gap to the results row unchanged, same 48 dp left edge.
- transport == null: spacer and Text inside state.transport?.let, header unchanged until a value is read.
- Focus: no clickable/focusable/Surface; Compose test checks shown, not focused, no request-focus action; first focus on the Amsterdam card.
- Shared values unchanged (ScreenHorizontalPadding, ScreenVerticalPadding, GaugeSize 300 dp, StatusAreaHeight 64 dp, 12 dp spacer); no theme change.
- Vertical space: results row 344 -> 320 dp by calculation, still holds the 300 dp gauge; needs a measurement on the box.
- Text: every value uses onSurfaceVariant incl. "Нет подключения"; longest label about 200 dp vs 864 dp content width.
Accepted but unseen: the results row moves down once by 24 dp when the value arrives on open.
Still needed on the box: measure the results row (gauge top and lower card edge must not touch neighbours), check for a visible jump on open; owner judges AC-8.

VISUAL: not measured: no device used (Unauthorized: user_banned); checked from code against the design delta only, look on the TV NOT CHECKED
