# Brief: card-progress-watching-duration

Base c506d0bd. Analyst, 2026-11-22. Builds on `research.md` (cited as research.md: section); the lines relied on below were re-read at c506d0bd and hold.

kind: bug. size: M. tracks: none. Device verification: **yes** (persistence of the watch-state index; the card bar after a Details visit only shows at runtime). Device: the owner's TV box `192.168.1.110:5555`, package `com.kino.puber.stage`; no emulator.

Size reason: one goal, but it changes what the API-model readers write into the persisted watch-state index (risk areas "API models" and "persistence", `.flow/roles/analyst.md`).

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Request
research.md: Request.

## Root cause
Status: **plausible**. The mechanism is confirmed from code; whether any real payload triggers it is not observed (no captured `/v1/items/<id>` body; research.md: External findings).

| Step | Where | Status |
|---|---|---|
| Movie readers take the length only from `watching.duration` and ignore `videos[0].watching`, `videos[0].duration`, `duration.total` | `data/repository/WatchStateRepository.kt:496-509`, duplicate in `core/ui/model/VideoItemUIMapper.kt:116-126` | confirmed (code) |
| `WatchingInfo.duration` defaults to 0 and is documented absent from item payloads | `data/api/models/Models.kt:548-553` | confirmed (code, kinoapi docs) |
| A movie with no usable length gets `progressPercent = null` | `WatchStateRepository.kt:418-430` | confirmed (code) |
| `resolve()` prefers the item's own state over the index | `WatchStateRepository.kt:227` | confirmed (code) |
| Each fresh Details emission records the item; `upsertFromServer` overwrites `progress_duration` unconditionally, so a 0 length replaces the history length | `data/repository/ItemDetailsRepository.kt:17-28`, `data/db/WatchStateDao.kt:66-83,132-169` | confirmed (code) |
| Item-level `watching`/`watched` present on a real movie payload | `Models.kt:89-94,150-161` KDocs claim it; kinoapi docs do not show it | unverified |
| When only `videos[0].watching` is sent (documented shape), `toWatchStateOrNull` returns null and the index is used: the card bar survives, but Details never adds movie progress to the index | `WatchStateRepository.kt:499` | confirmed (code) |

Series cards use episode counts and are unaffected (`WatchStateRepository.kt:421-424`).

## Scope
In: research option A, with the watched verdict from `videos[0]` (open question 2). One shared movie rule (position `videos[0].watching ?: watching`; length `watching.duration > 0 ?: videos[0].duration ?: duration.total`; watched `videos[0].watched > 0` or status 1 on either `watching`) used by `toWatchStateOrNull`, `toLocalWatchState` and `DetailsScreenUIMapper.kt:386-405`. Tests at `WatchStateRepositoryTest.kt:596-615` and `VideoItemUIMapperTest.kt:111-135` move to the documented shape.
Out: research option B (DAO length guard, open question 3); the `resolve()` order (`collection-watched-mark-lag`).

## Acceptance criteria
- AC-1. Given a movie `Item` with `videos[0] = Video(duration = 120, watching = WatchingInfo(time = 30))` and no item-level `watching`/`watched`, When `recordFromServer` runs, Then the row holds `progressTime = 30`, `progressDuration = 120`, `isFullyWatched = false` (unit test, red on base).
- AC-2. Given the AC-1 item, When `mapShortItem` runs with and without a `WatchStateRepository`, Then `progressPercent` is 0.25 in both (unit test, red on base).
- AC-3. Given a movie with item-level `watching = WatchingInfo(time = 30)` (duration 0), When mapped or recorded, Then the length comes from `videos[0].duration`, else `duration.total`; with neither, `progressPercent` is null (unit test).
- AC-4. Given a movie whose only verdict is `videos[0].watched = 1` or `videos[0].watching.status = 1`, When mapped, Then `isWatched` is true and `progressPercent` is null (unit test).
- AC-5. Given a movie `Item` with no watch fields at item or video level, When `resolve` runs, Then the index row is returned unchanged; a series `Item` maps as on base (unit test, regression).
- AC-6. Given the existing `DetailsScreenUIMapper` tests, When the Details mapper calls the shared rule, Then they stay green with no expectation changed.
- AC-7. Given the stage build on the box and a movie started and left past 5 min, When its card is read on Home, its Details opened and closed with Back, and the app is restarted, Then the card bar is shown each time at the same position (box observation, D-pad driven; evidence records whether `/v1/items/<id>` carries item-level `watching`/`watched`, field names only, no body).
- AC-8. Given the full check, When `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then it passes.

## Risks
| Risk | Guard |
|---|---|
| Preferring `videos[0].watching` hides a newer item-level position if both exist (research.md: Risks 1) | Same trade-off as the shipped Details fix; AC-7 records which fields exist |
| Movies now resolve from the Details payload instead of the index, so a fresh local write can lag behind cached Details (research.md: Risks 2) | Shared with `collection-watched-mark-lag`; AC-7 checks the bar after a play |
| Extending the watched verdict changes `Item.isFullyWatched()` (`Models.kt:163-166`) for every caller, including catalogue hiding | Spec lists the callers; AC-4, AC-5 |
| Index rows already overwritten with no length stay wrong until a replay (research.md: Risks 4) | Unverified; open question 3 |

## Open questions
1. Fix without first capturing the real `/v1/items/<id>` shape? Recommended yes: the rule is correct for either shape; AC-7 records the field names as verify evidence. Blocks: no.
2. Read the movie watched verdict from `videos[0]` too? Recommended yes, in the same rule (AC-4): a 98% bar on a finished movie is worse than today. Blocks: yes.
3. Add the DAO length guard (option B) now? Recommended no; open an idea run if AC-7 shows a lost length after the fix. Blocks: no.
