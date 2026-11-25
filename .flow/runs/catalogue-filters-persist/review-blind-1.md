LENS: blind  ROUND: 1  BASE: ef5987ec
### F1 [minor] Stored filter is read from disk on the main thread when the grid opens
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:90 (restoreFilter call in the secondary constructor) -> app/src/main/java/com/kino/puber/data/preferences/CatalogueFilterRepository.kt:24
- Problem: The secondary constructor calls `restoreFilter` before the super call, and that runs `repository.read` -> `prefs.getString`. The ShowAllScreen Koin factory builds the ViewModel during composition, on the main thread. The first `getString` on the `catalogue_filters` file blocks until SharedPreferences has loaded it from disk.
- Failure scenario: Cold start, then open a browse grid for the first time. The main thread waits for the prefs file to load while the screen is composing. StrictMode reports a disk read, and a slow TV flash can drop frames on the screen transition.
- Fix: Read the prefs earlier and off the main thread. For example, have the repository load its prefs (or a memory snapshot) on a background dispatcher when it is created, or warm it from `PuberApp`, so the constructor only reads memory.

### F2 [minor] Stored «Аниме» genre that was set aside is overwritten by the next filter change
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:192-195 (closeFilterPanel) together with restoreFilter at :368-380
- Problem: `restoreFilter` drops the anime genre while anime is hidden. Its comment says "The entry itself stays", and `applied` becomes the filter without the genre. Any later change in the panel calls `filterRepository.write(base.id, applied)`, and `applied` no longer has the anime genre. So the stored entry stays only until the first change, not until anime is shown again.
- Failure scenario: The grid has «Аниме» + YEAR ASC stored and showAnime is off. The user opens the panel, flips the direction to DESC and closes it. The store now holds YEAR DESC with no genre. The user turns showAnime on, reopens the grid, and the «Аниме» genre is gone, although the comment promises it is kept.
- Fix: Either keep the skipped genre and write it back when the user has not picked a genre (`write(base.id, if (draft.genre == null && skippedGenre != null) draft.copy(genre = skippedGenre) else draft)`), or change the comment to say the entry is kept only until the next change, if that is what you intend.

### F3 [nit] shelfGrid_neverReadsOrWritesTheStore cannot detect a read
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt (shelfGrid_neverReadsOrWritesTheStore)
- Problem: The test only checks `preferences.transactions.isEmpty()` and the first-page call. A read creates no transaction, and the filter state would stay `Unavailable` either way. If the `isFilterable` guard in `restoreFilter` were removed, the stored «Драма» filter would page the shelf with a genre. The test then fails only through the `FirstPageCall(popular, cacheable = true)` assertion, not through the "never reads" claim in its name.
- Fix: Rename the test to match what it checks (the shelf ignores a stored entry), or check the repository read itself (a spy, or a read counter on FakeSharedPreferences).

### Unproven
- ShowAllVM.kt:97 The first filter state does not set `draft` to the restored filter. If `FilterPanelClosed` ever arrived without `FilterPanelOpened` first, it would apply an empty filter and clear the stored one. Today it is sent only from the panel's `onDismiss` (ShowAllFilterPanel.kt:74).
- CatalogueFilterRepository.kt:29 A stored genre that the server later removes or renames keeps paging an empty or renamed grid until the user taps Reset.
