VISUAL: not measured: box session rejected (HTTP 403 user_banned), the filter panel can't be reached on 192.168.1.110:5555. Compose code checked against docs/design/showall/README.md starting values and matches apart from F1. The look on the TV (AC-11) needs a working session and the owner's check.

What matches:
- Root page: height is 266 dp with five doors and 226 dp with four. The doors come in the agreed order, «Статус» comes last and shows only on the series grids, and the trailing values read «Все страны» / «Любой год» / «Любой статус».
- «Страна» page: the width is fixed at 320 dp through the new optional `fixedWidth` parameter on `TvSidePanelSurface`. The other panels keep their own sizing because the parameter defaults to off. The page is its own `LazyColumn` under the title, not inside the outer scroll, with `PanelMaxHeight` 500 unchanged. That gives the agreed ten whole rows and a cut eleventh.
- Rows and states: all rows use the shared row component. The loading spinner and the failure text plus «Повторить» use the same sizes as the genre page.
- «Год» page: «Любой год» plus 8 presets, 426 dp, no scroll.
- Header summary: the order is genre, country, year, status, then sort.
- Strings: the new Russian and English strings are all there.
- Stand: the README section «catalogue-filters-country-year-status starting values» matches the code. The source page and the built `index.html` were changed in step.

### F1 [minor] The «Год» page shows no checked row for a stored year that is no longer a preset, while the root door shows it
- Where: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllFilterPanel.kt:244-262
- Problem: expected the value on the root door and the checked row on its page to agree, as they do for «Сортировка» and «Жанр». Measured in code: the year presets move with the current year. A stored «2026» is off the preset list from 2028. The root door still reads «Год 2026», but on the page «Любой год» is unchecked, no other row is checked, and focus lands on «Любой год».
- Failure scenario: the viewer opens «Фильтры», sees «Год 2026», presses OK on the door, and gets a page with no check mark and focus on «Любой год». It looks as if the filter had been reset.
- Fix: when `chosen` is not null and not in `filter.yearOptions`, add one selected row labelled `yearLabel(chosen)` after «Любой год» and put first focus on it. Or else map the stored range to the nearest preset when the filter is restored.

Open for the owner on the stand: «Статус» door vs one root row; «Страна» 320 dp vs longest name; ~103 presses to the end of the country list («Популярные» later).
