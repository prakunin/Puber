VISUAL: deviates

### F1 [major] Kinopoisk and IMDb pills show the API's unrounded rating ("5.978", "8.011")
- Where: VideoItemUIMapper.kt:141-142 buildPillRatings passes kinopoiskRating / imdbRating raw
- Measured on the device frame (sha256 ed5524c7…, movies-grid-year-only-4k-hd.png): Корчак "8.011", Всё закончится "5.978"; pills ~5 dp wider.
- Fix: in buildPillRatings only, one decimal with a dot, trailing ".0" dropped like PUB ("5.978" -> "6", "8.011" -> "8", "7.25" -> "7.3"); mapper tests; buildRatings untouched.

### F2 [minor] Pill text gets lost on bright or text-heavy posters at the agreed 62 % scrim
- Where: Color.kt:34 PosterChipScrim = Black 0.62; VideoItemPoster.kt:266
- Contrast ~4.7:1 over white; poster lettering competes with 8 sp digits (Concerts: Rammstein, Funky Times, Oddisee, ...).
- Fix: owner sets it on the stand (start 0.75-0.80), then change PosterChipScrim only.

### F3 [nit] A cut original title leaves a wider gap before " · year"
- Where: VideoItemPoster.kt:333-353; 14 px before "·" on ellipsized lines vs <7 px on uncut ones.
- Fix: place the year after getLineRight(0) with a custom Layout, or accept.

Everything else matches the agreed numbers (grid pitch 147 dp, poster 250x375 px, tile 237.5 dp, badge 11 dp, pills 13 dp, focus frame 2 dp #938F99 at 1.05). Box 192.168.1.110:5555, lease released.
