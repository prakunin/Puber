VISUAL: match
### F1 [minor] Panel glass lets poster artwork and lettering show through the filter rows (TvSidePanel.kt:75-79, PanelGlass.Strong over 62 % scrim)
- 13-genre-page.png: «EL DESHIEL…» readable behind «Все жанры»; 07-panel-main.png, 09-sort-page.png similar.
- Fix: owner rules at AC-13; if plain surface, add a material parameter to TvSidePanelSurface used by ShowAllFilterPanel; do not change PanelGlass.Strong.
All measured numbers match the stand (header, buttons, gaps, grid rows, panel sizes, empty state). Player panel not checked on device.
