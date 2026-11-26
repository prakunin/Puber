LENS: state-path  ROUND: 2  BASE: 90e69aff

No findings.

Incremental diff 1f6b03b0..e286d189 checked against the branch from 90e69aff:
- Cache key: country, year and finished are added only when one is set. With no filter the key matches the old one exactly; a filtered key has three more parts so the two never collide; an active filter skips the cache on first load anyway, Reset loads with cacheable = true; the key is built in one place (line 82) and nothing searches by prefix.
- Year page with a stored off-list year: one checked row with first focus. Picking any row only changes the draft; picking a preset or «Любой год» removes the extra row (own `if` block in a plain Column, so preset rows keep identity and the picked row keeps focus); picking the extra row changes nothing; at most one row holds first focus.
- Tests only in ShowAllVMTest and ShowAllFilterFocusTest.

Files read: ContentListInteractor.kt, ShowAllFilterPanel.kt (worktree), triage-1.md.
