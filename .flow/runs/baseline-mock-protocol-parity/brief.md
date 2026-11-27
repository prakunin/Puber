# Brief: baseline-mock-protocol-parity

Kind: improvement. Size: M. Tracks: perf. Device verification: yes (emulator host.docker.internal:5555, generation only).
Research: research.md (sections 3, 6, 8); option A taken. Checked on master b27d8e40.

## Request

The hermetic regeneration of `baseline-prof.txt` lost rules against the real-account profile 5cd3e37e (`docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-r2-device.md`). Restore them by making the loopback mock serve HTTP/1.1 as well as HTTP/2 and carry richer fixtures, and guard the restored paths in the content test.

| Package | 5cd3e37e | b27d8e40 |
|---|---|---|
| `okhttp3/internal/http1` | 51 | 0 |
| `kotlinx/serialization/internal` | 197 | 140 |
| `kotlinx/serialization/descriptors` | 73 | 51 |
| `com/adamglin/phosphoricons` | 69 | 51 |

## Why M, not S

No `app/src/main` change, but the baseline profile is a named risk area, the change crosses `:baselineprofile`, `app/src/instrumentation`, Gradle properties and both profile files, and the proof is a full generation on the emulator.

## Cause (code, confirmed)

| Loss | Where | Cause |
|---|---|---|
| `http1` | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:27`, `:43-49` | One `MockWebServer` with default `protocols` `[HTTP_2, HTTP_1_1]`; the app client offers the same default (`app/src/main/java/com/kino/puber/data/api/network/HttpStack.kt:33`), so ALPN picks h2 on every connection |
| `http` date/headers | `BaselineMockBackend.kt:213-225` | Responses carry only `Content-Type` and a fixed-length body: no chunked body, no `Date`/`Cache-Control` |
| serialization primitives | `baselineprofile/src/main/assets/baseline/items.json`, `item.json` | Fixtures omit the boolean, float/double and nullable fields of `app/src/main/java/com/kino/puber/data/api/models/Models.kt:62-95`, `:210`, `:537`, `:380-384` |
| filled icons | same fixtures, `empty_bookmark_folders.json` | No watched item (`VideoItemUIState.kt:373`, `DetailsScreenContent.kt:539`, `PlayerButtonRow.kt:145`) and no `in_watchlist: true` (`DetailsScreenContent.kt:519`) |

Which real origin speaks which protocol is unverified (research.md: 3, "What ordinary use recorded"); the base profile proves both codecs ran.

## Files

| Area | Files |
|---|---|
| Mock | `BaselineMockBackend.kt` (second server, protocols, chunked bodies, headers), `BaselineProfileTestControl.kt:19-28` (second port argument), `BaselineMockSelfTest.kt:39-52` |
| Ports | `gradle.properties:5`, `app/build.gradle.kts:246`, `baselineprofile/build.gradle.kts:20-26` |
| App side | `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt:17-46` (DoH origin, `allowedOrigins()`), `BaselineProfileApp.kt:26` (GitHub origin) |
| Fixtures | `baselineprofile/src/main/assets/baseline/*.json` |
| Tests | `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:61-67`; port literals in `app/src/testInstrumentation/.../BaselineInstrumentationEnvironmentTest.kt:53-56`, `BaselineNetworkBlockerTest.kt:18-20`, `BaselineFixtureContentTest.kt:25` (fixture assertions, AC-4) |
| Output | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` |

## Decisions taken (owner standing rule: recommended answers)

- Two mock servers: HTTP/1.1-only (`protocols = [HTTP_1_1]`) for `api.baseline.test` and `127.0.0.1` (API, images, stream); the existing h2 server on a second port for `dns.baseline.test` and `/github`. The second port is its own Gradle property next to `puber.baselineMockPort`.
- The protocol split is accepted as inferred; no per-origin confirmation against the live API (Q1).
- Fixtures stay free of `imdb` and `trailer` (Q2).
- Port literals 18765 in the instrumentation tests are folded in where the second port changes them (Q3).

## Acceptance criteria

- AC-1. Given the mock is started, when `BaselineMockSelfTest` runs on the emulator, then a request to `https://api.baseline.test:<port>` reports `Response.protocol == HTTP_1_1` and a request to `https://dns.baseline.test:<h2 port>/dns-query` reports `HTTP_2`.
- AC-2. Given the mock, when a JSON API route answers, then at least one route sends a chunked body and every response carries a `Date` header (self-test assertion).
- AC-3. Given the instrumentation build, when `allowedOrigins()`, the DoH URL and the GitHub origin are built, then each names the server that serves it and `BaselineInstrumentationEnvironmentTest` and `BaselineNetworkBlockerTest` pass with both ports.
- AC-4. Given the enriched fixtures, when `BaselineFixtureContentTest` runs, then they decode with the app's models; `item.json` is the plain movie (unwatched, empty `bookmarks`, not in watchlist), so details take the `get-item-folders` lookup, which answers with one watch-later folder (`bookmark_folders.json`); a watched item is `items.json` id 3, and `in_watchlist: true` comes from the series fixture and the populated folder lookup; at least one boolean, one float, one double and one explicit `null` field are present; and no `imdb`, `trailer` or non-loopback URL.
- AC-5. Given a full `make baseline-profile` on the emulator against the loopback mock, when it ends, then it exits 0, every route journal shows `unknown=[]`, and the egress journal names no real host.
- AC-6. Given the regenerated `baseline-prof.txt`, when `BaselineProfileContentTest` runs, then it requires and finds rules for `okhttp3/internal/http1/Http1ExchangeCodec`, `okhttp3/internal/http2/Http2Connection`, `kotlinx/serialization/internal/BooleanSerializer`, `FloatSerializer`, `DoubleSerializer`, `com/adamglin/phosphoricons/fill/EyeKt` and `fill/BookmarksimpleKt`; it also requires rules for the bookmark-folder lookup (`BaselineProfileContentTest.baselineProfileCoversBookmarkFolderLookup`: `getItemBookmarkFolders` and `BookmarkFoldersResponse`); all existing tests in that class still pass.
- AC-7. Given the regenerated profile against 5cd3e37e, when rule lines are counted per package of the table above, then `http1` is at least 40 and the other three are each within 20 % of 5cd3e37e or above it; the counts go into the evidence file.
- AC-8. Given the change, when `git diff --stat master` is read, then no file under `app/src/main/java` or `app/src/main/res` changed.

## Risks

- Rebase: baseline-generation-hardening (worktree `/home/dev/orca/workspaces/Puber/baseline-generation-hardening`) changes `Makefile`, `tools/generate-baseline-profile.sh`, `BaselineProfileGenerator.kt` (rail check), `.gitignore`, `docs/release.md` and rewrites both profile files. This run branches from, or rebases onto, the master commit that merges hardening, and regenerates the profiles after that rebase; its own diff does not touch those three code files unless a journey must show the watched item.
- Splitting ports ends OkHttp coalescing of `api.` and `dns.baseline.test` onto one h2 connection; check `unknown=[]` per journey (AC-5).
- Guards on library internals can break on a dependency bump; they name stable top-level classes only.
- Device rules: generation and evidence only on the emulator (`ssh mac-emu start`, lease, stop after if no other lease); generation reaches only the loopback mock; no script touches the live KinoPub API.

## Open questions

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | Accept the inferred split (HTTP/1.1 API, images, stream; HTTP/2 DoH, GitHub) without confirming per origin? | Accept; both codecs are served either way, no API probe | no |
| Q2 | Put `imdb` or `trailer` in fixtures to warm the schedule and trailer icons? | No; they start external lookups or a preview | no |
| Q3 | Fold in hermetic-baseline-generation minor #3 (hardcoded port 18765 in tests)? | Yes, where the second port touches it; #1 only if the late check is touched | no |
