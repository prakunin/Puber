LENS: tests  ROUND: 1  BASE: ad2c7f0c
### F1 [major] The ellipsis test cannot catch the year being cut off (R1, AC-3/AC-7)
- Where: VideoItemPosterTest.kt:57-72 videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole; VideoItemPoster.kt OriginalTitleLine, year Text
- Problem: the year Text uses TextOverflow.Clip so isLineEllipsized is always false; the bounds check passes a 0-width year.
- Failure scenario: move weight(1f) to the year or drop it from the original; the year gets 0 dp and both assertions pass.
- Fix: assert !didOverflowWidth and width > 0 (>= getLineRight(0)); show it red once against a weighted-year card.

### F2 [major] P0 "red" tests with no red run on unchanged code and no guard mark
- Where: plan Implementation record; test-design Traceability
- Problem: four unit P0 red tests were green on base (qualityBadgeNull_whenQualityIs719, qualityBadgeNull_whenQualityNull, yearOnly_whenTitleHasNoOriginal, noOriginalAndNoYear_whenBothMissing); four instrumented P0 red tests in VideoItemPosterTest were never run red.
- Fix: mark the four unit tests guard; record one red run per instrumented test against a stub or mutated card.

### F3 [major] Manual check M3 failed its stated outcome but was recorded as PASS; no test covers focus after the menu closes
- Where: evidence M3 / Observation; spec Manual checks M3
- Problem: on the box focus went to the expanded rail after the context menu closed; recorded as PASS for opening. No base comparison.
- Fix: record M3 failed; repeat on a base build; add an instrumented test in ShowAllScreenContentFocusTest: open the menu on a row-2 card, dismiss, assert that card is focused.

### F4 [minor] AC-12 device check M6 only half done
- Problem: no before screenshots; Episode schedule and player episode panel not opened; AC-12 instrumented guards (VideoGridInitialFocusTest, FavoriteScreenContentFocusTest, HomeFocusTraversalTest, SectionRowFocusTraversalTest, EpisodeScheduleScreenContentTest) not run.
- Fix: run those five via make itest; open the two skipped screens.

Suite state: check.full green at f68f3524; device itest 17/17.

### Unproven
- PosterImage fallback walk to the next URL is untested.
