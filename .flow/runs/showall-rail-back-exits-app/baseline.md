# Baseline: showall-rail-back-exits-app (mode baseline, defect 3 step 1 and Back red evidence)

Round: baseline. Date: 2026-11-25. Base f88f3079, worktree clean.

## Build and device

| Item | Value |
|---|---|
| Device | TV box 192.168.1.110:5555 (lease taken and released) |
| APK | worktree `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, built with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug` (BUILD SUCCESSFUL) |
| sha256 | b4338ce59d24669dc0bbee86d7a94db4a1aaa2d6852345bc1d0118a4604f95e3 (host and on-device base.apk equal) |
| Install | install-preserve, outcome `installed`, classification `compatible_replace`, destructive_action false, signer unchanged |
| Session | `make DEVICE=192.168.1.110:5555 auth-save` before install; app stayed logged in after install |
| Screenshots | /tmp/claude-1000/-home-dev-Puber/358d7f50-346b-457c-808a-96f6304f7f16/scratchpad/shots/ (outside Git, not audited, not committed) |

## Step 1: entry into Show all («Все фильмы», Center from the tab root)

Focus read with a uiautomator focused-node dump, kept in memory only. Bounds [8,344][520,440] are the Movies item of the expanded rail (rail width 520); [152,84][422,558] is the first grid card; [152,654][333,730] is «Все фильмы» at the tab root.

| # | Entry path | 1 s | 3 s | Focused node at 3 s | Rail expanded |
|---|---|---|---|---|---|
| P1 | Cold start, Movies via rail, Center on «Все фильмы» | Loading spinner, header «Фильтры» dim, rail collapsed with Movies item highlighted (focus already on the rail) | rail expanded (labels shown), grid loaded and dimmed, Movies item focused | rail Movies item | yes |
| P2 | Second entry, same session | rail already expanded, grid already loaded (cached page) | same | rail Movies item | yes |
| P3 | After Details round trip from the Новинки row | rail expanded, grid loaded | same | rail Movies item | yes |
| P5 | After tab switch (Home, then Movies via rail) | rail expanded, grid loaded | same | rail Movies item | yes |
| P4 | Through a shelf «Показать все» tile | NOT REACHED | | | |
| P6 | Fifth entry in the session, rail closed by an earlier Right (incidental) | grid with first card focused, rail collapsed | | first grid card | no |

Result: defect 3 reproduces on base on every entry I measured (P1, P2, P3, P5). Focus is on the rail item and the rail is open before the grid is usable; on the cold first entry (P1) the rail is collapsed at 1 s with the item already highlighted, and expands by 3 s. The node that takes focus is the rail Movies item (the whole rail sheet, per the dump); I could not see which component moves focus there, only the result. P6 was an accident of my own key sequence, one entry where focus stayed on the grid with the rail collapsed; it was not repeated, so "every entry" is not established.

P4: the tile is the trailing item of each content row (SectionRow.kt:191) and appears only after the row has loaded to its end. The Movies «Новинки» row keeps paging (40 Right presses ×8 left the focus on the second card of the viewport each time) and the Home «Популярные фильмы» row ended at «Восставший / The Uprising» with no tile within 10 bursts of 40. Not reached; needs a tab row with a short list, or the owner.

## Back red evidence on base (rail expanded over Show all)

| History | Steps | Observed | AC |
|---|---|---|---|
| Cold start (P1) | entry, rail open at 3 s, Back | Show all pops to the tab root, «Все фильмы» focused, rail collapsed, no toast in the screenshot 1 s after | AC-1 red (rail did not just close; the grid did not stay) |
| After Details (P3) | Details round trip, entry, rail open, Back | rail closes, grid stays with first card focused, toast «Нажми «Назад» ещё раз, чтобы выйти» shown; next Back popped Show all | AC-2 red (toast appears) |
| After tab switch (P5) | Home, Movies, entry, rail open, Back | Show all pops to the tab root, «Все фильмы» focused, no toast | AC-3 red |
| After tab switch, rail opened by Left from a focused card | entry, Right (focus on first card, rail collapsed), Left (rail expanded, focus on rail item), Back | Show all pops to the tab root, «Все фильмы» focused | AC-1 literal steps, red |
| Second entry (P2) | entry, rail open, Back | Show all pops, «Все фильмы» focused | red |

This matches the code analysis in research.md (TabBack wins on cold start and after tab switch; RailOpen wins after a Details round trip). Rail closed, Back from the grid pops to the tab root in every history (observed after the P6 entry and the first-card-focused states), no toast: AC-4 holds on base.

Not probed: the three-Back app exit (defect 2), AC-5 to AC-7, AC-9, AC-10. I did not press Back repeatedly on purpose to avoid leaving the app.

## Deviation

While locating a Home-row «Показать все» tile my key sequence drifted: a Center press opened a Spider-Man Details page and a Center on its content started a trailer (Sony logo, about 10 s). Back closed it. The Details page showed «Не начат», so no watch state changed; no resume prompt was confirmed. No other playback or account action was done.

## State left

App on Movies tab root after a Back from Show all, toast not armed; lease released (``).
