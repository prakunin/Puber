# Design (architect): player-callback-gate

Base `cec5f7fc`. Paths are relative to the worktree; `PC` = `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Signal that an error belongs to the previous media (brief: Design direction) | A switch session whose own stream URL has not started loading: `AnalyticsListener.onLoadStarted` (`PC:274`) with `loadEventInfo.dataSpec.uri.toString() == session.streamUrl` marks the session loaded | In the defect B never loads (masked `prepare()` is a no-op), so "B's URL never started loading" holds for every stale error, including one created on the playback thread after the switch call. Load events of A carry A's URL and cannot mark B. `MediaItemSpec.uri` is `stream.url` verbatim (`PlaybackMediaItemFactory.kt:42,60`) |
| Answer to a stale error | Drop it from every callback and call `player.prepare()` once per session; checked before the BehindLiveWindow and AC3 branches (`PC:258-259`) | The player is IDLE with B's item and saved position already set by `setMediaItem(item, savedPosition)` (`PC:506`), so a forwarded prepare loads B there; a stale BehindLiveWindow would otherwise seek B to the live edge |
| Bound on misclassification | At most one re-prepare per session; the next error is reported against the session URL | A real B error before any load of B costs one extra prepare (brief AC-3) |
| Sessions that may re-prepare | Only sessions begun by `switchStream`; `prepare` sessions never | `prepare` releases the old player first (`PC:385`), so no previous media can queue an error into it |
| Gate shape (Q3) | New `PlaybackCallbackGate` with nested `Session(generation, streamUrl, fromSwitch)`; `beginSession`, `invalidate`, `dispatch(session) { streamUrl -> }`, `onLoadStarted(requestedUri)`, `routeError(session, reprepare, report)` | Ports upstream `fe6224fb` `PlaybackCallbackGate` and keeps the stale decision JVM-testable without ExoPlayer (AC-2, AC-3) |
| Callback ownership | The gate holds no `Callback`; `dispatch` runs a lambda that uses the controller's `callback` field (`PC:169`) | Upstream's `setCallback` on the gate would duplicate `PC:374-376`; `notifyPlaybackState`/`notifyTracksUpdated` (`PC:821,861`) keep their current call |
| Locking | None; all gate calls run on the application looper | ExoPlayer is built and called on main (`PC:419`, harness `onMain`); upstream's `synchronized` guards nothing here |
| Listener lifetime | One `Player.Listener` per session: the current `playerListener` (`PC:214-271`) becomes an inner class bound to a `Session`; bound in `prepare` and `switchStream` before `setMediaItem`, removed on the next bind and in `release` | Matches spec D26; a listener removed during a nested switch (VM `onStreamFailure` -> `switchStream` inside dispatch, `PlayerVM.kt:275,476`) receives no more events, and the generation check covers what remains |
| `currentStreamUrl` (`PC:175`) | Stays for `SeekStallRecovery` (`PC:187`) and debug info (`PC:790`); listener callbacks read `session.streamUrl` | Smallest change; those readers want the live URL |
| Switch KDoc | Move the "Deliberately no `stop()`" KDoc from `createMediaSourceFactory` (`PC:473-483`, attached to `PC:484`) to `switchStream` and add one sentence on the stale-error re-prepare | It documents `switchStream` (AC-5) but sits on the wrong function |

## Changes by layer

API / repository, screen and DI module, Compose content, resources: N/A, the change is inside the player controller and its tests.

### ViewModel state and actions (player controller)

| File | Change |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGate.kt` (new) | `internal class PlaybackCallbackGate`: `generation: Long`, `current: Session?`. `beginSession(streamUrl, fromSwitch)` bumps the generation and returns the new current session; `invalidate()` bumps it and clears `current`; `dispatch` runs the action with `session.streamUrl` only when `session` is current; `onLoadStarted(uri)` sets `streamLoadStarted` on the current session when `uri == streamUrl`; `routeError`: not current -> nothing, `fromSwitch && !streamLoadStarted && !staleErrorRetried` -> set `staleErrorRetried`, `reprepare()`, else `report(streamUrl)` |
| `PC:214-271` | `playerListener` -> `private inner class SessionListener(val session: PlaybackCallbackGate.Session) : Player.Listener`. ENDED (`:235`) and READY (`:241`) go through `gate.dispatch(session)`, READY with the dispatched URL; `onPlayerError` (`:253-270`) becomes `gate.routeError(session, reprepare = { exoPlayer?.prepare() }, report = { url -> <existing when, with url at :263> })`. Other overrides wrap their bodies in `dispatch` |
| `PC` new private `bindSession(player, streamUrl, fromSwitch)` | Removes the previous `SessionListener`, begins a gate session, adds a new listener; field `sessionListener: SessionListener?` |
| `PC:274-281` `onLoadStarted` | Adds `gate.onLoadStarted(loadEventInfo.dataSpec.uri.toString())` before the existing line |
| `PC:427-430`, `:434-436` `prepare` | `addListener(playerListener)` removed from the builder `apply`; `bindSession(player, stream.url, fromSwitch = false)` before `setMediaItem` at `:435` |
| `PC:499-519` `switchStream` | `bindSession(player, stream.url, fromSwitch = true)` before `setMediaItem` at `:506`; no `stop()` (AC-5); KDoc moved here (Decisions) |
| `PC:581-598` `release` | `removeListener(sessionListener)` replaces `:586`; `gate.invalidate()`; `sessionListener = null` |

### Tests and docs

| File | Change |
|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGateTest.kt` (new, JUnit 5) | AC-2 and AC-3 against the gate; rows come from `test-design.md` |
| `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:84`, `:12` | Remove `@Ignore` and its import; body unchanged (AC-1). KDoc `:21-23` reworded from "finding goes to the run" to a regression statement |
| `docs/testing/player-playback.md:11,57,63` | Queued-failure case described as a passing regression test (AC-7); `:57` names the run too |

## Alternatives rejected

| Alternative | Reason |
|---|---|
| `PlaybackException.timestampMs` earlier than the switch time | Misses an A error created on the playback thread after the switch call but before it processes `setMediaItems(B)`; B then never loads |
| Any `onLoadStarted` in the session, URL unchecked | A load start of A already queued at the switch would mark B as loaded and let the stale error through |
| `stop()` before `setMediaItem` (option B) | Owner chose A (Q1): allocator reset on every switch and the test precondition at `PlaybackControllerStaleCallbackTest.kt:106-110` fails |
| Gate only, single shared listener (option C) | The stale error is delivered after the switch to whatever listener is current; neither defect is fixed |
| Gate owns the callback (upstream `setCallback`) | Two holders of one callback |

## Risks

| Risk | Area | Code | Mitigation |
|---|---|---|---|
| A real B error arrives on main before B's `onLoadStarted` (ordering of analytics vs playback-info posts is unverified) and is retried once | correctness | `PC:274`, `routeError` | One retry per session, then reported (AC-3) |
| Switch to the same URL as the failing stream (links refresh returning identical URLs, `PlayerVM.kt:510-528`): A's queued load start matches B's URL, the stale error is reported against that URL and B is not re-prepared | correctness | `gate.onLoadStarted` | VM still advances (`PlayerVM.kt:275-277`); same behaviour as today, narrower |
| A re-entrant switch inside a callback leaves events of the old batch for the removed listener | lifecycle | `bindSession`, `PlayerVM.kt:476` | Removed listener is not invoked; `dispatch` generation check |
| Re-prepare after `release` or on a replaced player | lifecycle | `routeError` reprepare lambda | Lambda uses `exoPlayer?`; `routeError` returns for a non-current session, and `release` invalidates |
| Stale error still flips `isPlaying` to false for one turn; `notifyPlaybackState` reports it | correctness (UI) | `PC:215-228` | Transient; B's READY follows. AC-6 on the box |
| Device test passes without reaching the window | rollout | `PlaybackControllerStaleCallbackTest.kt:106-110` | Precondition unchanged; three runs, XML case count (AC-1) |
| A media3 bump changes `prepare()` masking or listener removal semantics | rollout | `gradle/libs.versions.toml:27` | AC-1 is the regression test |
| Per-session listener add/remove on each switch | performance | `bindSession` | One allocation per switch; negligible next to the source swap |

## Open questions

none
