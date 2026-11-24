LENS: blind  ROUND: 1  BASE: b7a7d99e
No findings.
### Unproven
- ContentListPagingVM.kt:132 changeQuery writes config and cacheable separately; an in-flight first-page load could cache one row under the filtered key (result still dropped by the generation).
- ContentListPagingVM.kt:184 changeQuery does not reset currentPage; a LoadMore between reset and the new page 1 could request old page + 1.
- ShowAllVM.kt:186 items.orEmpty() caches an empty genre list for the TTL if items is missing.
Checked safe: player panel lift; genre override on cartoon/anime (not filterable).
