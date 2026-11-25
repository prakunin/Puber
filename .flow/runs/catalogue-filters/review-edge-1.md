LENS: edge  ROUND: 1  BASE: b7a7d99e
### F1 [minor] A page from the old query can still reach the grid after the generation check (ContentListPagingVM.kt:206-208, :293-295)
- check and publish are not atomic against changeQuery; old replace can land after Restart. Fix: lock shared with changeQuery, or tag replace/setNextPage with the generation and drop mismatches.
### Unproven
- ShowAllVM.kt:176-180 Anime filter applied, then show-anime turned off: refresh walks ~80 pages empty.
- ContentListPagingVM.kt:122-126, 132-138 refreshFirstPage between changeQuery and the new first page rebuilds the old depth.
- KinoPubApiClient.kt:632-637 items null cached as an empty genre list for 5 minutes.
