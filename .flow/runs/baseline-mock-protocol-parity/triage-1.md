# Triage round 1: baseline-mock-protocol-parity

Reviewed head: f04f6e36 (base 08f50b81). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (clean). qa round 1: qa-report.md (AC-1..AC-8 PASS).

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Every details payload takes an early return, so the bookmark-folder lookup (get-item-folders) left the profile (8 -> 0, BookmarkFoldersResponse 21 -> 0) | blind F1 | major | fix | Keep at least one details payload on the fallback path (movie with "bookmarks":[] and not in watchlist); mark /v1/bookmarks/get-item-folders required in Browse; add a content-test guard for KinoPubApiClient.getItemBookmarkFolders / BookmarkFoldersResponse |
| 2 | Player coverage fell (media3/exoplayer 3490 -> 3312, hls 387 -> 349, AudioTrackPreferenceResolver 57 -> 48); the Playback movie is now finished/watched | blind (unproven) | major | fix | Conductor judgement: a likely regression from the fixture change, cheap to rule out. Keep the watched/bookmarked fields off the item the Playback journey plays; after regeneration report media3/exoplayer, hls and player-class counts vs base 08f50b81 in the evidence |
| 3 | No evidence file for AC-5/AC-7 | tests F1 (major), edge F1 | major | fix | qa-device writes docs/superpowers/evidence/2026-11-26-baseline-mock-protocol-parity-device.md after the regeneration (Perf table, journeys, unknown=[], player counts), audited |
| 4 | Date header not asserted on binary responses (media, DoH) | codex, tests F2 | minor | fix | Add /media/poster.jpg and one DoH response to the Date self-test |
| 5 | Chunked framing decided in json() separately from the route's server | blind F2 | minor | fix | Decide chunking from the dispatching server |
| 6 | Http2Connection entry in the new content test passes on base | blind F3 | minor | fix | Mark it as a guard (name/comment) |
| 7 | P0 red self-tests have no recorded red run on their mutation | tests F3 | minor | fix | Record the red-by-construction reasoning per test in the Implementation record (base lacks h2Port/boundH2Port or bans seasons / returns the movie); test-designer re-tags rows accordingly |
| 8 | Wrong hostname on the right port; close() lock; h2 bind failure branch; missing runner arg; equal ports via -P; subtitle.vtt route; phosphoricons margin | codex rejected, edge/tests unproven | - | reject | Unproven or not reachable in the current journeys; margin recorded in evidence |

Deferred: none. Accepted: none.
