# Network diagnostics and server guidance — design

**Goal:** a user whose playback stutters can press one thing in Settings and be told, in words they
can act on, whether the API is reachable, whether names resolve, and how fast video actually
arrives — and be offered exactly one setting change, explained, never applied behind their back.

Issue: prakunin/Puber#10.

## What is wrong now

Puber lets a user pick an API mirror, a streaming type and a server location, and gives them no way
to find out which of the three is hurting them. The mirror dialog has a «Определить» button that
walks the built-in endpoints, but it answers one question — does this domain serve the API — and
says nothing about how fast anything downloads. Everything else is guesswork: change a setting,
play something, see whether it still buffers.

Meanwhile the pieces to answer it properly are already here. `HttpEndpointProbe` knows how to tell a
live API from a captive portal. `EndpointReachability` is the shared verdict on whether a domain
answers. The shared `OkHttpClient` already resolves through DNS-over-HTTPS. What is missing is
something that runs them in order, times them, adds a real download, and turns the numbers into a
sentence.

## The screen

A screen of its own, reached from Settings → Сеть, above the mirror row. Not a dialog: the run has
a list of steps, a verdict and a confirmation to show, and it must survive the focus taking a walk
through all of them. `DeviceSettingsVM` is 535 lines and holds ten dependencies; the run does not
belong in it.

```
┌────────────────────────────────────────────────────────────────┐
│ Диагностика сети                                               │
│                                                                │
│ ✓ Доступность API              api.example.org  ·  142 мс      │
│ ✓ Определение имён (DoH)                        38 мс          │
│ ✓ Отклик API                                    340 мс         │
│ ◌ Скорость видео                        качаем…                │
│ ‒ Запасные зеркала                      не нужны               │
│                                                                │
│ ───────────────────────────────────────────────────────────    │
│ API отвечает. Скорость видео — 18 Мбит/с: этого хватает для     │
│ 1080p, для 4K маловато.                                        │
│                                                                │
│                                       [ Отмена ]               │
└────────────────────────────────────────────────────────────────┘
```

Only one number on the screen is a rate — the media one — and everything else is a duration in
milliseconds, so there are never two figures a user could mistake for each other's units. The only
host ever rendered is the API mirror's domain, which is the user's own setting and already on the
settings screen next door; no media URL, and no resolved address, appears anywhere.

The run starts by itself when the screen opens. While it runs, the button says «Отмена»; when it
ends, «Повторить». When a mirror is proposed, «Переключить» joins «Повторить» and takes focus first.
Back cancels and leaves. Every one of those is a D-pad press on a focusable row — there is no
pointer affordance anywhere on the screen.

Each step carries one of five states, and the screen draws all five distinctly: pending, running,
success, failure, skipped. Skipped is not failure and must not read as one: a step is skipped when
there is nothing for it to do — no media link to be had, or a preceding step whose outcome already
settles it — and the row says why. A step settles the moment its outcome is known rather than when
the run reaches it, which is why the sketch above can show the mirror sweep as unnecessary while the
video step is still downloading: step 1 succeeded, and that is the whole question step 5 asks.

## The steps

Five, in order, each with an explicit ceiling on time and bytes. Nothing here is derived at runtime
from a server response; the endpoints and the caps are constants in the source and are meant to be
read in review.

**1. Доступность API.** `HttpEndpointProbe.isReachable(KinoPubConfig.CURRENT_ENDPOINT)`, timed. The
probe's existing 5 s call timeout stands. A success also calls `EndpointReachability.markReachable`,
so a run does not merely observe the network — it refreshes the verdict the rest of the app reads,
which is the reuse the issue asks for. A failure marks nothing: `markUnreachable` is the client's
job, and a diagnostic must not retire a verdict earned by traffic it cannot see.

**2. Определение имён.** `okHttpClient.dns.lookup(CURRENT_API_HOST)` on the shared client, so the
step exercises the same DNS-over-HTTPS path every real request takes rather than a fresh resolver
built for the occasion. Timed, 5 s ceiling. The result is «resolved» or «failed» plus the elapsed
time. The addresses themselves are never shown, logged or persisted.

**3. Отклик API.** One `items?type=movie&page=1` through the authenticated client, timed end to end
under an 8 s ceiling of its own — the client's own request timeout is 120 s, which is a sane figure
for a screen that is loading content and an absurd one for a step a user is watching. What the step
reports is milliseconds.

An earlier draft of this design had it report a rate over a 512 KB cap. Counting bytes off an
authenticated response means reaching past the parsed model into the raw channel, which is new
surface on a 900-line client for a number that would have been misleading anyway: a catalogue page
is a couple of hundred kilobytes, so on any link worth having, the figure measures how long the
server thinks rather than how fast the link is. Round-trip time is what the step actually observes,
so round-trip time is what it says. It still earns its place — it separates "the API answers an
unauthenticated probe" from "the API serves this account's real requests promptly" — and it leaves
the media rate as the only rate on the screen.

The response goes through the existing call path and is dropped; nothing extra is stored.

**4. Скорость видео.** The step the feature exists for, because it is the only one that travels the
path video travels. `getItems(type = movie, sort = fresh)` for an id, `getItemFiles(id)` for its
files, then the first `VideoUrl.http` — the progressive candidate `PlayerInteractor` itself
prefers — fetched with `Range: bytes=0-4194303`, capped at 4 MB and 10 s.

If there is no `http` URL to be had, the step is **skipped**, not failed: an item that offers only
HLS is a fact about the item, not about the network. This is what "when a safe probe is available"
in the issue means in practice.

The URL carries a token. It is never rendered, never logged, never persisted, and never included in
evidence. Only the byte count and the elapsed time leave the step.

**5. Запасные зеркала.** `HttpEndpointProbe` against the remaining `BUILT_IN_ENDPOINTS` — and only
when step 1 failed. When the current mirror answers, there is nothing to choose between, so the step
is skipped rather than run: probing live mirrors to rank them by tens of milliseconds would invite a
recommendation that means nothing.

## The verdict

`advise(run): DiagnosticsAdvice` is a pure function of the run's snapshot — no coroutines, no
Android, no resources. Everything user-visible comes out of it as an identifier plus its numbers,
and the screen turns that into a string. That is what makes the interesting half of this feature
testable without a device, a dispatcher or a fake `Context`.

It says two separate things, because the acceptance criterion is that API reachability and media
throughput are distinguishable, and in practice they fail independently — a mirror can be blocked
while video streams fine, and a CDN can crawl while the API is instant.

Throughput becomes a quality ceiling through explicit constants: 5 Mbit/s for 720p, 10 for 1080p,
25 for 4K. The wording reports what was measured and what it covers — «этого хватает для 1080p, для
4K маловато» — and promises nothing about what will play.

## The one change it may propose

Switching the API mirror, and only when step 1 failed and step 5 found one that answers. The screen
states both halves — this domain did not answer, that one did — and does nothing until the user
selects «Переключить». Nothing is applied while the run is in flight, and nothing is applied on the
way out.

Applying it goes through `ApiDomainInteractor`, which today exposes only "detect a working domain
for me" and "save this arbitrary string". It needs a third entry point that switches to a named
built-in preset, so the screen can apply the mirror it actually probed instead of re-running a walk
that might land somewhere else. That method reuses the existing `applyEndpoint`, and so inherits its
cache clearing unchanged.

Server location and streaming type get an explanation and no button. `ServerLocation` from the API
is `id`, `title` and `location` with no host, so a location that is not currently selected cannot be
measured without switching the account's setting to it — which is precisely the silent change the
issue puts out of scope. Sweeping locations stays a separate, later question.

## Cancellation, failure, and the promise not to break anything

The run lives in a job owned by the view model. «Отмена» cancels it; so does Back, by way of the
screen's scope going away. The interactor writes nothing — not a preference, not a domain override,
not a cache invalidation — so a run abandoned at any point leaves nothing behind. "A diagnostic
failure must not alter normal networking configuration" is therefore structural here rather than a
thing to remember to check.

`measureBoundedDownload` checks `ensureActive()` between chunks and closes its `Response` on every
path, so cancelling mid-download frees the socket instead of reading to the cap first.

A step that throws does not end the run. `CancellationException` is rethrown; anything else turns
that one step into a failure carrying a user-facing string from `ResourceProvider`, and the run
continues to the next step. Exception text never reaches the screen.

## Shape

```
data/api/network/diagnostics/BoundedDownload.kt   measureBoundedDownload(), ThroughputSample
domain/interactor/diagnostics/
  NetworkDiagnosticsModels.kt                     DiagnosticStep, StepState, NetworkDiagnosticsRun
  NetworkDiagnosticsInteractor.kt                 run(): Flow<NetworkDiagnosticsRun>
  NetworkDiagnosticsAdvice.kt                     advise() — pure
ui/feature/device/diagnostics/
  NetworkDiagnosticsScreen.kt                     PuberScreen + buildModule
  NetworkDiagnosticsContent.kt                    state + onAction, no view model
  vm/NetworkDiagnosticsVM.kt
  model/…                                         view state, actions
  NetworkDiagnosticsTestTags.kt
```

`measureBoundedDownload` takes the shared `OkHttpClient` and derives a per-call timeout with
`newBuilder()`, the way `HttpEndpointProbe` already does — the singleton's own configuration, DNS
included, is never touched.

Wiring: `Screens.networkDiagnostics()` and its implementation; a row in `networkItems`; the
interactor in `domain/di/modules.kt`; strings in `values` and `values-ru`.

## Tests

JUnit 5 throughout, `@RegisterExtension MainDispatcherExtension`, `FakeResourceProvider` for
anything that maps a string.

`NetworkDiagnosticsAdviceTest` is the centre of gravity, and it needs no fakes at all: the quality
thresholds at and either side of each boundary; API failure with healthy media and the reverse, each
producing its own half of the verdict; a skipped step never counted as a failure; the mirror
proposal appearing only when the current mirror failed and another answered.

`NetworkDiagnosticsInteractorTest`, with a fake probe and a fake downloader: the sequence of
emissions and their states; the media step skipping when no `http` URL exists; a failing step
leaving the following ones to run; cancellation mid-run producing no further emissions and touching
`ApiDomainInteractor` not at all.

`NetworkDiagnosticsVMTest`: start, cancel, re-run; the proposed mirror applying only after the
confirm action and never before it.

D-pad reachability — start and cancel both driven by the remote — is covered by an instrumented test
against the screen's test tags, in the existing settings instrumentation.

## Out of scope

Background benchmarking. Any automatic or silent change of server, mirror or streaming type. The
location sweep. Any measurement of a server location that is not the selected one.
