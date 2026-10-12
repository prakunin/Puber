# Research: tmdb-result-handling

## 1. Request

"`runCatchingCancellable` instead of `apiResult`" — refactor from the code-quality audit, finding SOLID-13 (size S proposed, kind refactor, principle SOLID). Board note: "Device: smoke (episode schedule, skip segments)".

SOLID-13, quoted in full from `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:99` (line numbers in it refer to commit `36cda915`; `apiResult` is now at `:70`):

> | SOLID-13 | `TmdbApiClient.kt` (on `36cda915`) | **Duplicated error wrapper, two failure policies for one endpoint.** (1) The private `apiResult` re-implements `runCatchingCancellable` (rethrow `CancellationException`, wrap the rest in `Result`). The only difference is that it catches `Exception` where the shared helper catches `Throwable`, so an `Error` (for example a `NoClassDefFoundError` from an API missing on an old TV box) escapes TMDB calls, while every other caller of the helper gets it wrapped in a failed `Result`. The helper has 12 call sites in `app/src/main`. (2) `findTvByImdbId` and `findByImdbId` call the same `find/{imdb_id}` endpoint with the same parameters. The first turns a non-2xx response into a failed `Result` (`TmdbApiException`), which `EpisodeScheduleRepository` surfaces with `getOrThrow()`. The second turns it into `Result.success(null)`, which reads as "no TMDB match", and it also falls back from TV to movie results, for `SkipSegmentService`. An expired TMDB token or a 429 is therefore an error on the schedule screen and a silent "no skip segments" in the player. No miss is cached (only a hit is saved), so the impact is diagnostics, not stale data. | [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:127] [Source: app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:37] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:50] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:73] [Source: app/src/main/java/com/kino/puber/data/repository/EpisodeScheduleRepository.kt:55] [Source: app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt:62] | low | S | Replace `apiResult` with `runCatchingCancellable`. Keep one private `find(imdbId)` that returns the raw `TmdbFindResponse` with one failure policy (fail on non-2xx), and two thin projections (TV only; TV then movie). `SkipSegmentService` only calls `.getOrNull()` today and logs nothing, so the follow-up must add a log of the failure there (for example `onFailure { log(it, ...) }` before `getOrNull()`). A failure then still means "no segments", but it is recorded as a failure, not lost as a miss. Pin both behaviours in `TmdbApiClientTest` first. | follow-up `tmdb-result-handling` |

Follow-up row (`report.md:267`): "`runCatchingCancellable` instead of `apiResult`; one `find` call with one failure policy behind the TV-only and TV-then-movie lookups; log the lookup failure in `SkipSegmentService`".

## 2. Understanding

`TmdbApiClient` has its own private try/catch wrapper (`apiResult`) that duplicates the project helper `runCatchingCancellable`, except that it catches `Exception` instead of `Throwable`. It also has two near-identical lookups of the same TMDB `find` endpoint that react differently to an HTTP error: the episode-schedule one fails, the skip-segment one pretends "no match". The audit asks to use the shared helper, merge the two lookups behind one fetch with one failure policy, and make a skip-segment lookup failure visible (logged) rather than silent. The core swap is tiny; the "log it" part collides with an existing privacy guard test (see Current state and Risks), which the audit did not account for.

## 3. Current state in code

TMDB client (`app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`):
- `:36-42` `findTvByImdbId` — `GET find/tt…?external_source=imdb_id`, `requireSuccess()` (non-2xx throws `TmdbApiException`), returns first `tvResults` id or null.
- `:44-48` `getTvDetails`, `:50-57` `getTvSeasonDetails` — same `requireSuccess()` policy.
- `:59-68` `findByImdbId` — same request; non-2xx returns `null` via `return@apiResult null`; TV id, else movie id.
- `:70-76` `apiResult` — non-inline `suspend` fun, rethrows `kotlinx.coroutines.CancellationException`, catches `Exception` into `Result.failure`. `Error` subclasses escape.
- `:81-85` `requireSuccess`; `:111` `TmdbApiException(statusCode) : IOException` (message contains only the status code).
- `:87-90` `forTesting(httpClient)` factory used by tests. `:19` imports `kotlinx.coroutines.CancellationException` (on JVM this and `kotlin.coroutines.cancellation.CancellationException` are both `java.util.concurrent.CancellationException`, so catch semantics are identical).
- No detekt baseline/suppression for `apiResult` was found under `config/detekt/` (it catches `Exception`, which detekt's `TooGenericExceptionCaught` normally flags; it evidently passes today — exact detekt config not checked, unverified).

Shared helper (`app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt`):
- `:37-45` `suspend inline fun <T> runCatchingCancellable(crossinline call: suspend () -> T): Result<T>` — rethrows `CancellationException`, wraps any `Throwable`. `@Suppress("TooGenericExceptionCaught")`.
- `:20-29` `tryOrNull` — same shape but logs and returns null (not suitable here: it logs, see privacy guard).
- Because it is `inline` with `crossinline`, a labelled early return inside the block becomes `return@runCatchingCancellable` (pattern already used at `IntroDbAppApiClient.kt:51`, `PlayerVM.kt:1503`).

`runCatchingCancellable` call sites in `app/src/main` (12, matching the review's correction C9):
- `domain/interactor/bookmarks/BookmarkFoldersInteractor.kt:29`
- `domain/interactor/genre/GenreInteractor.kt:17`
- `domain/interactor/prefetch/DetailsPrefetcher.kt:138`
- `domain/interactor/trailer/TrailerLinkInteractor.kt:29`
- `data/repository/KinoPubRepository.kt:79`
- `data/api/ApiFailures.kt:36`
- `data/api/TheIntroDbApiClient.kt:42`
- `data/api/IntroDbAppApiClient.kt:43`
- `data/api/KinoPubApiClient.kt:1156` (inside `apiCall`)
- `ui/feature/main/vm/MainVM.kt:86`
- `ui/feature/details/vm/DetailsVM.kt:137`
- `ui/feature/player/vm/PlayerVM.kt:1501`
- `apiResult` is used only in `TmdbApiClient.kt` (4 call sites, `:36, :44, :53, :59`).

Callers of the TMDB client:
- `data/di/modules.kt:72` `singleOf(::TmdbApiClient)`.
- `data/repository/EpisodeScheduleRepository.kt:55` `findTvByImdbId(...).getOrThrow()`; `:65` `getTvDetails(...).getOrThrow()`; `:72` `getTvSeasonDetails(...).getOrThrow()`. Wrapped in `cache.getOrPut` + `withContext(workerDispatcher)` (`:45-48`); failures are not cached (pinned by test).
- `ui/feature/episodeschedule/vm/EpisodeScheduleVM.kt:43-53` catches `CancellationException` (rethrow) then `Throwable` -> `mapper.mapError(error)`. So the schedule screen already handles any `Throwable`, including an `Error` that today escapes `apiResult`.
- `data/repository/SkipSegmentService.kt:56-65` `resolveTmdbId`: cached TMDB id first (`TmdbIdRepository`), else `findByImdbId(imdbId).getOrNull()` and save only a hit. `:39-42` a null TMDB id means the primary source (TheIntroDB) is skipped; `:32-34` IntroDB.app fallback still runs on the IMDb id.
- `data/repository/SkipSegmentRepository.kt:19-27` caches the merged segment list 24 h, an **empty** result for 1 h. So a TMDB failure that leaves both sources empty is cached as "no segments" for an hour — the audit's "no miss is cached" is true for the TMDB id, not for the segment list.

Sibling clients with the same "silent miss" policy (out of the finding's scope, noted for consistency):
- `TheIntroDbApiClient.kt:48-55` every non-200 -> `emptyList()`, including 429.
- `IntroDbAppApiClient.kt:50-52` non-2xx -> `emptyList()`.

Privacy guards that constrain the change (not mentioned in the audit):
- `app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentPrivacyTest.kt:67-84,146-156` reads `SkipSegmentService.kt`, `TmdbApiClient.kt`, both IntroDB clients, `PlayerVM.kt`, etc. and **fails if the source imports `com.kino.puber.core.logger.log` or matches `\blog\s*\(`**. `:27-64` also asserts that a full skip-segment lookup emits no Timber message at all (success path only).
- `app/src/test/kotlin/com/kino/puber/data/repository/EpisodeSchedulePrivacyTest.kt:12-23,36-43` forbids `Timber.`, `android.util.Log`, `println(` and `response.bodyAsText` in `TmdbApiClient.kt`, `EpisodeScheduleRepository.kt` and the schedule interactor/VM/mapper.
- `core/logger/Logger.kt` `log(throwable, …)` sanitises via `SensitiveRequestLogPolicy.sanitizeThrowable` (`core/logger/SensitiveRequestLogPolicy.kt:116-124`); the sanitiser's patterns target KinoPub `/items/<id>`, credentials and bearer tokens (`:240-253`). There is no pattern for a TMDB `find/tt…` path, so an exception message carrying the request URL would log the IMDb id (whether Ktor 3.5.2 exception messages include the URL for this path is unverified).
- So the audit's suggested `onFailure { log(it, ...) }` in `SkipSegmentService` would break `transitiveSkipSegmentSourcesContainNoApplicationLogging` as written, and the guard exists to keep media identity (what the user watches) out of logs.

Tests:
- `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt` (Ktor `MockEngine`): `:26` find TV with normalised id; `:38` TV-only, no movie fallback; `:47` tv endpoints paths/decoding; `:102` 429 -> `TmdbApiException`; `:114` **pins `findByImdbId` non-2xx (502) -> `Result.success(null)`** — this test changes if the failure policy is unified; `:135` cancellation rethrown from `getTvDetails`. No test for `findTvByImdbId` non-2xx, none for `Error` wrapping.
- `app/src/test/kotlin/com/kino/puber/data/repository/EpisodeScheduleRepositoryTest.kt:165` failures not cached; `:129` typed outcomes incl. no match.
- `app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentServiceTest.kt:50,62,75` merge behaviour only; TMDB id always comes from the mocked `TmdbIdRepository` cache; no failure case.
- `app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentPrivacyTest.kt` (above); `EpisodeScheduleRepositoryModuleTest.kt` (DI only).
- No direct unit test of `runCatchingCancellable` itself (grep of `app/src/test` finds none).

Docs:
- `docs/architecture/api.md:29-41` describes `apiCall` (KinoPub) as the convention: rethrow cancellation, `Result.failure` for anything else; `:7` lists `TmdbApiClient.kt` as a separate small client. `:64-67` "Never wrap a `Result` in another `Result`."

## 4. Related work

- `code-quality-audit` (run, done): source of SOLID-13. Review `docs/superpowers/reviews/2026-10-12-code-quality-audit.md:77` (C9) corrected the call-site count to 12 and added "the row must say the log has to be added" (fixed in `2f246f31`). Spec `docs/superpowers/specs/2026-10-12-code-quality-audit-design.md:97` deferred it because "SOLID-13 changes how a TMDB failure is reported".
- Audit plan F2 / commit `2342ea57` "Delete the unused TMDB cast and configuration endpoints (BP-2)" removed the other TMDB endpoints and kept `apiResult`, both find calls, `TmdbApiException` (`docs/superpowers/plans/2026-10-12-code-quality-audit.md:183-208`).
- History of `TmdbApiClient.kt`: `35dea703` "Add episode release schedule" (added `findTvByImdbId`, tv endpoints, `requireSuccess`, `TmdbApiException`, alongside the older `findByImdbId`); `a1a51cca` (shared OkHttp client); `e3101ddc` Release 1.6.0 (also where `SkipSegmentPrivacyTest` appears in history); `01bbd62f`, `88cdf071`.
- Other open runs: none touch `TmdbApiClient.kt`, `SkipSegmentService.kt`, `CoroutinesErrorHandler.kt` or the privacy test (checked `git diff master...<branch>` for every local branch: empty). `api-client-split` (idea, M) is about `KinoPubApiClient`, not TMDB. `dead-code-sweep` (BP-1, BP-7, STR-4) does not list TMDB. `network-diagnostics` is done and unrelated.
- **Duplicate: none.**

## 5. External findings

External findings: off (local research only). `.flow/config.yaml` has no `research.web` key and the change is internal. Versions in use, from `gradle/libs.versions.toml`: Kotlin 2.4.10, Ktor 3.5.2, kotlinx-coroutines 1.11.0, Timber 5.0.1.

## 6. Options

**A. Swap only.** Replace `apiResult` with `runCatchingCancellable` in `TmdbApiClient` (`return@apiResult` -> `return@runCatchingCancellable`, drop the private fun and the `CancellationException` import). Add one test that an `Error` thrown by the engine comes back as a failed `Result`. Behaviour change: `Error`s are wrapped instead of propagating (the schedule VM already catches `Throwable`, the skip path turns it into "no segments"). Two failure policies stay.
- + Smallest diff, zero privacy-test impact, pure SOLID dedup.
- - Leaves half of SOLID-13 (the two policies) open.
- Size S (1 main file, 1 test file).

**B. Swap + one `find` with one failure policy, no logging (recommended).** A, plus a private `find(imdbId): TmdbFindResponse` that `requireSuccess()`es, and two thin projections: `findTvByImdbId` (TV only) and `findByImdbId` (TV then movie). `findByImdbId` now returns `Result.failure(TmdbApiException)` on non-2xx; `SkipSegmentService` keeps `.getOrNull()`, so the player outcome is unchanged (still "no TMDB id" -> IntroDB.app fallback), but the client contract is honest and a future caller can tell a failure from a miss. Update `TmdbApiClientTest:114` (502 -> failure) and add a non-2xx test for `findTvByImdbId`; optionally a `SkipSegmentServiceTest` case "TMDB failure -> fallback still asked, nothing saved".
- + Closes both parts of the finding without touching the privacy guard; still S.
- - The failure is still not recorded anywhere at runtime (the audit's diagnostic goal is not met).
- Size S (2 main files at most, 2-3 test files).

**C. Audit text as written: B + log the failure in `SkipSegmentService`.** Requires changing `SkipSegmentPrivacyTest` (allow one log call in that file) and guaranteeing the logged throwable carries no media identity — e.g. log only `TmdbApiException.statusCode`, or a fixed message without the throwable, since `sanitizeThrowable` has no rule for TMDB `find/tt…` URLs.
- + Meets the audit's diagnostics goal.
- - Weakens a deliberate privacy guard; touches a risk area (logging of watch identity); needs an owner decision. Arguably M by the size rule (risk area).
- Size S-M.

Recommendation: **B**. It removes the duplicate wrapper and the split policy; the logging part conflicts with an existing, deliberate privacy rule and should only happen if the owner explicitly accepts an identity-free log (then C, limited to status code).

## 7. Risks

- Privacy: any `log(` in `SkipSegmentService.kt` or `TmdbApiClient.kt` fails `SkipSegmentPrivacyTest` / `EpisodeSchedulePrivacyTest`; loosening them risks logging IMDb ids (watch history) because the log sanitiser does not cover TMDB URLs.
- Behaviour: wrapping `Error` (OOM, `NoClassDefFoundError`, `StackOverflowError`) into `Result` hides fatal errors the same way the other 12 callers already do; consistent with the project helper, but it is a change for TMDB.
- Unifying the policy flips the pinned test `TmdbApiClientTest.existingFindByImdbId_preservesTvFirstMovieFallbackAndNonSuccessBehavior`; it must be rewritten deliberately, not "fixed".
- A TMDB failure leading to an empty merged segment list is cached for 1 h (`SkipSegmentRepository.kt:21-23`); unchanged by A/B, but worth knowing for the smoke test (a 429 during the smoke can hide segments for an hour).
- `inline`/`crossinline` change: `runCatchingCancellable` inlines into each method; no functional risk, just labels change.
- Device smoke (board note): touches networking result handling on the schedule screen and in the player; AGENTS.md rules say on-device verification is needed (episode schedule opens; skip intro/credits prompt appears on a known series). Worktree builds have an empty TMDB token, so `isConfigured` is false there: the schedule screen shows MissingCredentials and `findByImdbId` gets 401 -> TheIntroDB is skipped. A meaningful smoke of the TMDB path from a worktree build is therefore limited (unverified whether a cached TMDB id on the device masks this).

## 8. Open questions

1. Should a TMDB lookup failure in the skip-segment path be logged, given `SkipSegmentPrivacyTest` forbids application logging in that path? Recommended: **no** — take option B (one policy, `getOrNull()` stays, no log); keep the guard intact. If yes, log only the HTTP status code with a fixed message and amend the guard narrowly.
2. Scope: include the `find` unification (B) or only the wrapper swap (A)? Recommended: **B**; it is the substance of SOLID-13 and stays S.
3. Device verification: the worktree APK has no TMDB token, so the smoke cannot exercise a real TMDB call. Accept a smoke of "schedule screen shows its MissingCredentials state, player still offers IntroDB.app segments" plus unit tests as sufficient? Recommended: **yes**, recording the limitation in evidence.

## 9. Sources

Files:
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/data/repository/SkipSegmentRepository.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/data/repository/EpisodeScheduleRepository.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/ui/feature/episodeschedule/vm/EpisodeScheduleVM.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/data/api/TheIntroDbApiClient.kt`, `IntroDbAppApiClient.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/main/java/com/kino/puber/core/logger/Logger.kt`, `SensitiveRequestLogPolicy.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt`
- `/mnt/data/projects/PetProjets/Puber/app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentPrivacyTest.kt`, `EpisodeSchedulePrivacyTest.kt`, `SkipSegmentServiceTest.kt`, `EpisodeScheduleRepositoryTest.kt`
- `/mnt/data/projects/PetProjets/Puber/docs/architecture/api.md`
- `/mnt/data/projects/PetProjets/Puber/docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md` (`:11-15`, `:99`, `:219`, `:267`)
- `/mnt/data/projects/PetProjets/Puber/docs/superpowers/reviews/2026-10-12-code-quality-audit.md:77`
- `/mnt/data/projects/PetProjets/Puber/docs/superpowers/specs/2026-10-12-code-quality-audit-design.md:97`
- `/mnt/data/projects/PetProjets/Puber/docs/superpowers/plans/2026-10-12-code-quality-audit.md:183-208`
- `/mnt/data/projects/PetProjets/Puber/gradle/libs.versions.toml`, `/mnt/data/projects/PetProjets/Puber/.flow/config.yaml`

Commits: `2342ea57`, `35dea703`, `a1a51cca`, `e3101ddc`, `2f246f31` (review fix C9), audited `36cda915`.

Runs: `code-quality-audit`, `api-client-split`, `dead-code-sweep`, `layer-deps-cleanup`, `screen-error-states`, `network-diagnostics` (state.md read; none overlapping).

URLs: none (web off).
