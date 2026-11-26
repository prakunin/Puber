LENS: blind  ROUND: 1  BASE: 90e69aff

No findings.

### Unproven
- /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:648 A 200 reply to `/countries` with no `items` becomes an empty list through `items.orEmpty()`. `CountryInteractor` then caches it for 30 minutes, and the Country page shows only «Все страны», with no error row and no Retry. This becomes real if the server is shown to send `status` or `error` without `items` on a 200.
- /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllFilterPanel.kt (CountryPage `items(..., key = { it.id })`) If the API returns the same country id twice, the LazyColumn throws "Key was already used" and the panel crashes. This becomes real if a `/countries` response is found with a repeated id.
- /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/app/src/main/java/com/kino/puber/ui/feature/showall/model/ShowAllFilterState.kt (`YEAR_FLOOR = 1900`) The «до 1980» preset sends `1900-1979`, so titles from before 1900 never match any year preset. This becomes real if the catalogue is shown to hold items with a year before 1900.
- /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllFilterPanel.kt (YearPage) A single-year filter saved last year, such as `2025..2025` once the year is 2027, is no longer in `yearOptions`. The page then opens with focus on «Любой год», no row is marked selected, and the door still shows the old year. This becomes real if a stored filter like that turns up after a year change and is shown to confuse users.
