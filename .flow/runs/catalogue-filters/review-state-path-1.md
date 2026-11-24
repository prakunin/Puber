LENS: state-path  ROUND: 1  BASE: b7a7d99e
### F1 [major] Closing the side rail opened from «Фильтры» puts focus on a row-1 card, not back on «Фильтры» (ShowAllScreenContent.kt:395-399 GridFocusMemory.onHandoff; ShowAllHeader.kt:80)
- onHandoff treats "not in grid" as "grid takes focus back". Fix: record when focus left the grid for the header; onHandoff does nothing then; instrumented case Фильтры -> Left -> close rail -> Фильтры focused.
### F2 [minor] «Сброс» can leave focus on «Фильтры» when the base page comes from the cache (ShowAllVM.kt:166; ShowAllScreenContent.kt:144, :201)
- Restart and the cached Replace can land in one frame, so Loading is never composed and no launch focus fires. Fix: expose a query generation and key ShowAllContentBody on it; test Content -> Content without Loading.
### F3 [minor] A refresh signal while a new query loads restores the old query's depth; a failed read leaves the spinner up (ContentListPagingVM.kt:132-137, :124, :189, :260)
- Fix: changeQuery sets currentPage = 0 (and emptyPageChain / resumeRounds = 0) before resetPaging; ShowAllVMTest case.
