**No findings in scope.** Visual check on device (emulator-5554, 1920x1080, build sha256 d10a2dfd…ce3a = worktree app-dev-debug.apk at 0bf26ee5) matches design-ux-designer.md. One pre-existing nit for the player-overlays stand.

Measured on device:
- Root settings panel, English (Outlander S2E3): 690 px = 345 dp wide, under 420 dp; flush right; door `AI-generated`, one line, no ellipsis.
- Root settings panel, Russian (Psyren ep 1, 2): door `Выкл.`; rows `Аудио`, `Субтитры`, `Качество`, `Скорость`, `Дополнительно`, `Поток`, one line each.
- Subtitles page, English: `Off`, `AI-generated` (checked), `Aa Size Medium`; 479 px ≈ 240 dp (minimum).
- Subtitles page, Russian, 9 languages: `Выкл.` (checked), `Китайский`, `Русский`, `Португальский`, `Польский`, `Итальянский`, `Французский`, `Испанский`, `Немецкий`, `Английский`, `Аа Размер Средний`; title case, no uppercase-code fallback; longest label fits one line at 240 dp; one check per page.
- Focus on Subtitles page: opens on the selected row, focused bed and check visible.
- Fresh media without resume prompt (Psyren ep 2): controls up, play/pause focused; Right moves focus to gear; Back at ~0:02 hides controls, playback continues, stays in player; no subtitle text.
- Resume prompt (Outlander S2E3): prompt alone, `Continue` focused, no control pills.
- HW-overlay toggle refused on API 36 ("Operation not permitted"); screencap still valid. Screenshots in the session scratchpad only.

### F1 [nit] Subtitles page with many rows reaches the top and bottom screen edges
- Where: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSidePanel.kt:112-124 (`Alignment.CenterEnd`, no vertical inset; not in this diff)
- Problem: with 11 rows the panel border runs y=2..1077 px (538 of 540 dp), about 1 dp from each edge, outside the ~27 dp TV safe area. Same geometry as master; this change only renames rows.
- Failure scenario: Settings > Subtitles on a title with 9 API subtitle languages; on an overscanning TV the title or last row could be cropped.
- Fix: none in this run; record in the player-overlays stand when the side-panel scene is dialled (vertical inset or max height with scrolling, checked against this 11-row page).

Not measured: `· частичные` / `· вариант N` rows, uppercase-code fallback, root door with a long selected label.

VISUAL: match
