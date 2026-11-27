# Review blind, round 1

LENS: blind  ROUND: 1  BASE: 08f50b81

### F1 [major] Regenerated profile no longer covers the details screen's bookmark-folder lookup, which ordinary movies use
- Where: baseline/item.json:1 (new "bookmarks":[{...}], "in_watchlist":true), item_serial.json:1 ("in_watchlist":true), vs DetailsInteractor.kt:50-59
- Problem: isInWatchLaterFolder returns early for a series with inWatchlist or a movie with non-empty bookmarks; only otherwise it calls getMovieBookmarkFolders (/v1/bookmarks/get-item-folders). Every details payload now takes an early return. Profile: KinoPubApiClient.getItemBookmarkFolders 8 -> 0, BookmarkFoldersResponse 21 -> 0; BookmarkFolder$$serializer and DetailsInteractor$getMovieBookmarkFolders$1 gone. The route is not required, so nothing flagged it.
- Failure scenario: details of a movie in no folder (the common case) run the folder lookup, its apiCall and serializers without AOT rules.
- Fix: keep at least one details payload on the fallback path (e.g. "bookmarks":[] for ids other than the watched one); mark /v1/bookmarks/get-item-folders required = browse || playback.

### F2 [minor] Chunked encoding is chosen in json(...) separately from the route's server
- Where: BaselineMockBackend.kt:156-157, used at :190-195
- Failure scenario: route("/x", server = H2, respond = json(...)) sends Transfer-Encoding: chunked over HTTP/2.
- Fix: decide chunking from the dispatching server.

### F3 [minor] The new profile-content test asserts a class the base profile already had
- Where: BaselineProfileContentTest.kt:71-81 (okhttp3/internal/http2/Http2Connection: 40 base, 38 head)
- Fix: mark it a guard, or assert something only the HTTP/2 client path loads.

### Unproven
- baseline-prof.txt player coverage fell (AudioTrackPreferenceResolver 57 -> 48, SubtitleTrackMerger 16 -> 12, PlayerTextTracks 12 -> 9, media3/exoplayer 3490 -> 3312, exoplayer/hls 387 -> 349); the Playback movie is now finished:true, watched:1. Real if a regeneration on base fixtures shows the same counts.
- BaselineMockBackend.kt:67-72 only an IOException from h2Server.start closes h1Server.
