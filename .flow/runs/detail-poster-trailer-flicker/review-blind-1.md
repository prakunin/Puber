LENS: blind  ROUND: 1  BASE: 76a8bc2a

### F1 [minor] A failed details load now leaves the previous card's poster on screen indefinitely
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:668 (`heldPosterUrls`), used at :455
- Problem: the held URL list is released only when `isLoading` turns false. In `ContentListVM.onItemFocused` (app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt, around line 71) and in `FavoriteVM.onItemFocused`, `selectedItem` is set to `VideoDetailsUIState.Loading` (`isLoading = true`, `imageUrl = ""`) before `interactor.getItemDetails` runs. If that call throws, `PuberVM`'s `DefaultExceptionHandler` swallows the error and `selectedItem` stays `Loading`. The hold therefore never releases. Before this change, the panel showed a skeleton in that case.
- Failure scenario: the user focuses card A, which loads with its poster. They move to card B and `getItemDetails(B)` fails (network error or timeout). The panel then shows card A's poster next to card B's skeleton text, and nothing marks it as stale. This lasts until focus moves again.
- Fix: limit how long the hold can last, or release it on failure. Two options: have the VMs publish a non-loading error/empty state when the details call fails, or pass the item id into `VideoDetailsPoster` and release the hold when the loading state belongs to an item other than the one whose URLs are held.

### F2 [nit] The KDoc for `heldPosterUrls` is attached to nothing
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:650-659
- Problem: the comment explaining the held list ("While the next card's details load...") is followed directly by `PosterCrossfade`'s own KDoc. That leaves the first block attached to no declaration, while `heldPosterUrls` / `rememberHeldUrls` further down have no documentation.
- Failure scenario: KDoc and the IDE show the wrong documentation, and the reason for the identity-preserving hold is lost from the function it describes.
- Fix: move that KDoc block so it sits directly above `heldPosterUrls` (or `rememberHeldUrls`).

### Unproven
- app/src/main/java/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImage.kt:30 `hasShownImage` is kept across model changes. After an earlier success, a primary URL that fails sets the AsyncImage to alpha 0 and suppresses the skeleton while the fallback URL loads, so the frame is blank instead of showing a skeleton. This becomes real only if detail posters with a broken primary URL and a working fallback actually occur in production data.
- app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:658 `PosterCrossfade` relies on Coil 3.6.3 passing a memory-cache hit through `State.Loading` with the existing image as placeholder. If Coil resolves memory hits synchronously instead, the crossfade's start painter is null and the poster fades in from an empty frame. Checking Coil's `AsyncImagePainter` source, or a device capture of a warm card change, would settle it.
