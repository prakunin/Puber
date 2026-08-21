# Network Diagnostics Implementation Plan (superseded)

> Superseded on 2026-08-21 by the KinoPub-compatible Amsterdam/Moscow media-server speed test.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a Settings → Сеть → «Диагностика сети» screen that runs five bounded network measurements with a D-pad, reports each one's state in plain words, and offers exactly one explained setting change — switching the API mirror — that is applied only on confirmation.

**Architecture:** a domain interactor emits a snapshot of the whole run as a `Flow` after every step transition; a pure `advise()` function turns the finished snapshot into a verdict and an optional proposal; a screen of its own collects the flow in a view-model job whose cancellation is the cancel button. Nothing in the run writes a preference, a domain override or a cache, so an abandoned run leaves nothing behind.

**Tech Stack:** Kotlin, Coroutines/Flow, OkHttp 5 + Okio, Ktor 3 (existing `KinoPubApiClient`), Koin, Compose TV Material3 (`androidx.tv.material3`), Voyager (`PuberScreen`), JUnit 5 + MockK.

**Spec:** `docs/superpowers/specs/2026-08-21-network-diagnostics-design.md`

## Global Constraints

- Build tasks in a worktree use `./tools/agentw <task>`, never `./gradlew`. Unit tests: `./tools/agentw testDevDebugUnitTest`. Lint: `./tools/agentw :app:detektAll`.
- Set `AGENT_GRADLE_SERIAL=1` whenever another worktree may build at the same time.
- Detekt: `MaxLineLength` 120, `LongMethod` 60 lines (production only), `MagicNumber` active in production sources — every literal other than `-1`, `0`, `1`, `2` needs a named constant.
- Unit tests are JUnit 5 (`org.junit.jupiter`), never `org.junit.Test`. Use `@JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()` in a `companion object` for anything touching `Dispatchers.Main`.
- `FakeResourceProvider` (`app/src/test/kotlin/com/kino/puber/util/FakeResourceProvider.kt`) returns `"string_$resId"` — never assert on real resource text.
- Test names read `method_expectedResult_whenCondition`.
- `KinoPubConfig.DEFAULT_API_DOMAIN` decodes Base64 through `android.util.Base64`, which is not on the JVM unit-test classpath. Any unit test that touches `KinoPubConfig` must `mockkObject(KinoPubConfig)` in `@BeforeEach` and `unmockkObject(KinoPubConfig)` in `@AfterEach`.
- Do not add dependencies. There is no MockWebServer and no Turbine on this classpath.
- User-visible strings live in `app/src/main/res/values/strings.xml` (**Russian** — this is the default locale) and `app/src/main/res/values-en/strings.xml` (English). Non-composable code reads them through `ResourceProvider`.
- Source, comments and commit messages are English.
- Never render, log or persist a media URL, a bearer token, or a resolved IP address. The only host allowed on screen is the API mirror domain.
- Screens resolve their view model with `puberViewModel<VM>()` inside `DIScope`, never bare `koinViewModel()`.
- Content composables are pure: `state` plus `onAction: (UIAction) -> Unit`.

---

## File Structure

**Create**

| File | Responsibility |
|---|---|
| `app/src/main/java/com/kino/puber/data/api/network/diagnostics/BoundedDownload.kt` | `ThroughputSample`, `BoundedDownloader`, `OkHttpBoundedDownloader`, `readAtMost` |
| `app/src/main/java/com/kino/puber/data/api/network/diagnostics/DiagnosticsSources.kt` | `HostResolver` + `DnsHostResolver`, `DiagnosticsApi` + `KinoPubDiagnosticsApi` |
| `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsModels.kt` | `DiagnosticStep`, `StepState`, `SkipReason`, `FailureReason`, `NetworkDiagnosticsRun` |
| `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsAdvice.kt` | pure `advise()`, `qualityCeiling()`, `QualityCeiling`, `DiagnosticsAdvice` |
| `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractor.kt` | the runner: `run(): Flow<NetworkDiagnosticsRun>` |
| `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/model/NetworkDiagnosticsViewState.kt` | view state + UI step model |
| `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/model/NetworkDiagnosticsActions.kt` | UI actions |
| `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt` | run job ownership, cancellation, proposal confirmation |
| `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt` | pure composable |
| `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt` | `PuberScreen` + `buildModule` |
| `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsTestTags.kt` | test tags |

**Modify**

| File | Change |
|---|---|
| `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt` | add `switchToBuiltInDomain(domain)` |
| `app/src/main/java/com/kino/puber/core/ui/navigation/Screens.kt` | add `networkDiagnostics()` |
| `app/src/main/java/com/kino/puber/ui/ScreensImpl.kt` | implement it |
| `app/src/main/java/com/kino/puber/domain/di/modules.kt` | register the interactor and its sources |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsActions.kt` | add `OpenNetworkDiagnostics` |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt` | handle it |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt` | row in `networkItems` |
| `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` | new strings |

---

### Task 1: Bounded download primitive

The one piece of new network machinery. It downloads at most N bytes, times it, checks cancellation between chunks, and closes the response on every path.

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/api/network/diagnostics/BoundedDownload.kt`
- Test: `app/src/test/kotlin/com/kino/puber/data/api/network/diagnostics/BoundedDownloadTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `ThroughputSample(bytes: Long, elapsedMillis: Long)` with `val bitsPerSecond: Double`; `fun interface BoundedDownloader { suspend fun measure(url: String, maxBytes: Long): ThroughputSample }`; `class OkHttpBoundedDownloader(okHttpClient: OkHttpClient, timeout: Duration, clock: () -> Long)`; `internal fun BufferedSource.readAtMost(maxBytes: Long, isActive: () -> Boolean): Long`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/data/api/network/diagnostics/BoundedDownloadTest.kt`:

```kotlin
package com.kino.puber.data.api.network.diagnostics

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BoundedDownloadTest {

    @Test
    fun bitsPerSecond_convertsBytesAndMillisToBits() {
        val sample = ThroughputSample(bytes = 1_250_000, elapsedMillis = 1_000)

        assertEquals(10_000_000.0, sample.bitsPerSecond)
    }

    /** A sample taken faster than the clock can see is not an infinite link. */
    @Test
    fun bitsPerSecond_isZero_whenNoTimePassed() {
        val sample = ThroughputSample(bytes = 4_096, elapsedMillis = 0)

        assertEquals(0.0, sample.bitsPerSecond)
    }

    @Test
    fun readAtMost_stopsAtTheCap_whenTheSourceHasMore() {
        val source = Buffer().write(ByteArray(10_000))

        val read = source.readAtMost(maxBytes = 4_096) { true }

        assertEquals(4_096L, read)
    }

    @Test
    fun readAtMost_returnsWhatArrived_whenTheSourceEndsEarly() {
        val source = Buffer().write(ByteArray(1_500))

        val read = source.readAtMost(maxBytes = 4_096) { true }

        assertEquals(1_500L, read)
    }

    /**
     * Cancelling mid-download has to free the socket rather than read on to the cap, so the loop
     * asks before every chunk and stops the moment the answer is no.
     */
    @Test
    fun readAtMost_stopsEarly_whenTheCallerIsCancelled() {
        val source = Buffer().write(ByteArray(300_000))
        var chunks = 0

        val read = source.readAtMost(maxBytes = 300_000) { chunks++ < 2 }

        assertEquals(2 * DOWNLOAD_CHUNK_BYTES, read)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*BoundedDownloadTest*'`
Expected: FAIL — compilation error, `ThroughputSample` / `readAtMost` / `DOWNLOAD_CHUNK_BYTES` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/kino/puber/data/api/network/diagnostics/BoundedDownload.kt`:

```kotlin
package com.kino.puber.data.api.network.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How much of a bounded download arrived, and how long it took. */
data class ThroughputSample(val bytes: Long, val elapsedMillis: Long) {

    /**
     * Zero rather than infinity when no time passed. A download the clock could not separate from
     * its own start is a measurement that failed, and reporting it as an unbounded link would put
     * the largest number on the screen for the least evidence.
     */
    val bitsPerSecond: Double
        get() = if (elapsedMillis <= 0L) {
            0.0
        } else {
            bytes.toDouble() * BITS_PER_BYTE * MILLIS_PER_SECOND / elapsedMillis
        }
}

/** Downloads a bounded prefix of a URL and reports nothing but its size and its duration. */
fun interface BoundedDownloader {
    suspend fun measure(url: String, maxBytes: Long): ThroughputSample
}

/**
 * Measures against the shared client, so the download travels the same DNS-over-HTTPS path every
 * real request takes. Only the call timeout is per-call, through `newBuilder()`, the way
 * [com.kino.puber.data.api.network.HttpEndpointProbe] already does it — the singleton's own
 * configuration is never touched, because a diagnostic must not be able to change how the app talks
 * to the network.
 */
class OkHttpBoundedDownloader(
    okHttpClient: OkHttpClient,
    timeout: Duration = DEFAULT_TIMEOUT,
    private val clock: () -> Long = System::currentTimeMillis,
) : BoundedDownloader {

    private val client = okHttpClient.newBuilder()
        .callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun measure(url: String, maxBytes: Long): ThroughputSample =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${maxBytes - 1}")
                .get()
                .build()

            val startedAt = clock()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Bounded download refused")
                // A server that ignores Range answers 200 with the whole file; the cap is what
                // stops us either way, so the status is not treated as a failure.
                response.body.source().readAtMost(maxBytes) { isActive }
            }
            ThroughputSample(bytes = bytes, elapsedMillis = clock() - startedAt)
        }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = 10.seconds
    }
}

/** How much is pulled off the socket between two cancellation checks. */
internal const val DOWNLOAD_CHUNK_BYTES = 64L * 1024L

/**
 * Reads at most [maxBytes], discarding everything it reads.
 *
 * Nothing is kept because nothing may be: the media probe's URL is authenticated, and its body is
 * somebody's film. Only the count leaves this function.
 */
internal fun BufferedSource.readAtMost(maxBytes: Long, isActive: () -> Boolean): Long {
    val sink = Buffer()
    var total = 0L
    while (total < maxBytes && isActive()) {
        val read = read(sink, minOf(DOWNLOAD_CHUNK_BYTES, maxBytes - total))
        if (read == -1L) break
        total += read
        sink.clear()
    }
    return total
}

private const val BITS_PER_BYTE = 8
private const val MILLIS_PER_SECOND = 1_000
```

- [ ] **Step 4: Run tests and detekt**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*BoundedDownloadTest*' :app:detektAll`
Expected: PASS, no detekt findings.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/api/network/diagnostics/BoundedDownload.kt \
        app/src/test/kotlin/com/kino/puber/data/api/network/diagnostics/BoundedDownloadTest.kt
git commit -m "Measure a bounded download without keeping any of it"
```

---

### Task 2: The run snapshot and the advice

Everything a run can say, and the pure function that turns a finished run into a verdict. No coroutines, no Android, no resources — which is why this is where the interesting assertions live.

**Files:**
- Create: `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsModels.kt`
- Create: `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsAdvice.kt`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsAdviceTest.kt`

**Interfaces:**
- Consumes: `ThroughputSample` from Task 1.
- Produces: `enum DiagnosticStep { ApiReachability, NameResolution, ApiResponsiveness, MediaThroughput, MirrorSweep }`; `enum SkipReason { NoNetwork, NoMediaLink, CurrentMirrorAnswers }`; `enum FailureReason { Unreachable, ResolutionFailed, RequestFailed }`; `sealed interface StepState { Pending, Running, Success(latencyMillis: Long?, sample: ThroughputSample?), Failure(reason), Skipped(reason) }`; `data class NetworkDiagnosticsRun(steps, apiDomain, workingMirrorDomain, finished)` with `state(step)` and `with(step, state)`; `enum QualityCeiling { TooSlow, Hd720, Hd1080, Uhd4k }`; `data class DiagnosticsAdvice(apiReachable, mediaBitsPerSecond, ceiling, mirrorProposal)`; `fun advise(run): DiagnosticsAdvice`; `fun qualityCeiling(bitsPerSecond: Double): QualityCeiling`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsAdviceTest.kt`:

```kotlin
package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NetworkDiagnosticsAdviceTest {

    @Test
    fun qualityCeiling_isTooSlow_belowThe720pThreshold() {
        assertEquals(QualityCeiling.TooSlow, qualityCeiling(4_999_999.0))
    }

    @Test
    fun qualityCeiling_is720p_atItsThreshold() {
        assertEquals(QualityCeiling.Hd720, qualityCeiling(5_000_000.0))
    }

    @Test
    fun qualityCeiling_is1080p_atItsThreshold() {
        assertEquals(QualityCeiling.Hd1080, qualityCeiling(10_000_000.0))
    }

    @Test
    fun qualityCeiling_is4k_atItsThreshold() {
        assertEquals(QualityCeiling.Uhd4k, qualityCeiling(25_000_000.0))
    }

    @Test
    fun qualityCeiling_is1080p_justBelowThe4kThreshold() {
        assertEquals(QualityCeiling.Hd1080, qualityCeiling(24_999_999.0))
    }

    /** The two halves fail independently, so the verdict has to carry both separately. */
    @Test
    fun advise_reportsFastMedia_whenTheApiIsUnreachable() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Failure(FailureReason.Unreachable),
            DiagnosticStep.MediaThroughput to success(bytes = 4_194_304, millis = 1_000),
        )

        val advice = advise(run)

        assertFalse(advice.apiReachable)
        assertEquals(QualityCeiling.Uhd4k, advice.ceiling)
    }

    @Test
    fun advise_reportsNoRate_whenTheMediaStepWasSkipped() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Success(latencyMillis = 120),
            DiagnosticStep.MediaThroughput to StepState.Skipped(SkipReason.NoMediaLink),
        )

        val advice = advise(run)

        assertTrue(advice.apiReachable)
        assertNull(advice.mediaBitsPerSecond)
        assertNull(advice.ceiling)
    }

    /**
     * A working mirror is only worth proposing when the current one stopped working. Switching
     * between two live mirrors changes which host serves the API and nothing a user can feel.
     */
    @Test
    fun advise_proposesNoMirror_whenTheCurrentOneAnswers() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Success(latencyMillis = 120),
        ).copy(workingMirrorDomain = "api.alador.test")

        assertNull(advise(run).mirrorProposal)
    }

    @Test
    fun advise_proposesTheWorkingMirror_whenTheCurrentOneFailed() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Failure(FailureReason.Unreachable),
        ).copy(workingMirrorDomain = "api.alador.test")

        assertEquals("api.alador.test", advise(run).mirrorProposal)
    }

    @Test
    fun advise_proposesNothing_whenNoMirrorAnswered() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Failure(FailureReason.Unreachable),
        )

        assertNull(advise(run).mirrorProposal)
    }

    private fun success(bytes: Long, millis: Long) =
        StepState.Success(sample = ThroughputSample(bytes = bytes, elapsedMillis = millis))

    private fun runWith(vararg states: Pair<DiagnosticStep, StepState>): NetworkDiagnosticsRun {
        return states.fold(NetworkDiagnosticsRun(apiDomain = "service-kp.test")) { run, (step, state) ->
            run.with(step, state)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*NetworkDiagnosticsAdviceTest*'`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the models**

Create `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsModels.kt`:

```kotlin
package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.network.diagnostics.ThroughputSample

/** The five things a run measures, in the order it measures them. */
internal enum class DiagnosticStep {
    ApiReachability,
    NameResolution,
    ApiResponsiveness,
    MediaThroughput,
    MirrorSweep,
}

/**
 * Why a step had nothing to do.
 *
 * Kept apart from failure on purpose: an item that offers no progressive URL is a fact about the
 * catalogue, and a mirror sweep with a healthy mirror already in hand has no question to ask.
 * Drawn as failures, both would send a user looking for a network problem that is not there.
 */
internal enum class SkipReason {
    NoNetwork,
    NoMediaLink,
    CurrentMirrorAnswers,
}

internal enum class FailureReason {
    Unreachable,
    ResolutionFailed,
    RequestFailed,
}

internal sealed interface StepState {
    data object Pending : StepState
    data object Running : StepState

    data class Success(
        val latencyMillis: Long? = null,
        val sample: ThroughputSample? = null,
    ) : StepState

    data class Failure(val reason: FailureReason) : StepState
    data class Skipped(val reason: SkipReason) : StepState
}

/**
 * Everything a run knows so far.
 *
 * The whole snapshot is re-emitted on every transition rather than the step that changed: a screen
 * that draws five rows needs all five states at once, and a partial update would make the view
 * model responsible for reassembling a run it did not perform.
 */
internal data class NetworkDiagnosticsRun(
    val apiDomain: String,
    val steps: Map<DiagnosticStep, StepState> =
        DiagnosticStep.entries.associateWith { StepState.Pending },
    val workingMirrorDomain: String? = null,
    val finished: Boolean = false,
) {
    fun state(step: DiagnosticStep): StepState = steps.getValue(step)

    fun with(step: DiagnosticStep, state: StepState): NetworkDiagnosticsRun =
        copy(steps = steps + (step to state))
}
```

- [ ] **Step 4: Write the advice**

Create `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsAdvice.kt`:

```kotlin
package com.kino.puber.domain.interactor.diagnostics

/** The highest quality a measured rate can carry, by the thresholds below. */
internal enum class QualityCeiling {
    TooSlow,
    Hd720,
    Hd1080,
    Uhd4k,
}

/**
 * What the run adds up to.
 *
 * API health and media speed are separate fields because they fail separately: a blocked mirror
 * with a fast CDN and a healthy API with a crawling CDN are both ordinary, and a single verdict
 * would have to pick one of them to be wrong about.
 */
internal data class DiagnosticsAdvice(
    val apiReachable: Boolean,
    val mediaBitsPerSecond: Double?,
    val ceiling: QualityCeiling?,
    val mirrorProposal: String?,
)

internal fun advise(run: NetworkDiagnosticsRun): DiagnosticsAdvice {
    val apiReachable = run.state(DiagnosticStep.ApiReachability) is StepState.Success
    val rate = (run.state(DiagnosticStep.MediaThroughput) as? StepState.Success)
        ?.sample
        ?.bitsPerSecond

    return DiagnosticsAdvice(
        apiReachable = apiReachable,
        mediaBitsPerSecond = rate,
        ceiling = rate?.let(::qualityCeiling),
        // Only worth proposing when the mirror in use stopped working: moving between two live
        // mirrors changes which host answers and nothing the user can perceive.
        mirrorProposal = run.workingMirrorDomain.takeUnless { apiReachable },
    )
}

internal fun qualityCeiling(bitsPerSecond: Double): QualityCeiling = when {
    bitsPerSecond >= UHD_4K_BITS_PER_SECOND -> QualityCeiling.Uhd4k
    bitsPerSecond >= HD_1080_BITS_PER_SECOND -> QualityCeiling.Hd1080
    bitsPerSecond >= HD_720_BITS_PER_SECOND -> QualityCeiling.Hd720
    else -> QualityCeiling.TooSlow
}

private const val HD_720_BITS_PER_SECOND = 5_000_000.0
private const val HD_1080_BITS_PER_SECOND = 10_000_000.0
private const val UHD_4K_BITS_PER_SECOND = 25_000_000.0
```

- [ ] **Step 5: Run tests and detekt**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*NetworkDiagnosticsAdviceTest*' :app:detektAll`
Expected: PASS, no detekt findings.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/diagnostics/ \
        app/src/test/kotlin/com/kino/puber/domain/interactor/diagnostics/
git commit -m "Turn a diagnostics run into a verdict a user can act on"
```

---

### Task 3: What the run measures against

Two narrow seams so the runner never touches OkHttp's DNS or the API client directly, and so its test needs no network. Both are deliberately shaped to make the privacy rule structural: the resolver returns a **count**, never addresses, so no `InetAddress` can reach the domain layer at all.

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/api/network/diagnostics/DiagnosticsSources.kt`
- Test: `app/src/test/kotlin/com/kino/puber/data/api/network/diagnostics/KinoPubDiagnosticsApiTest.kt`

**Interfaces:**
- Consumes: `KinoPubApiClient` (existing), `okhttp3.Dns`.
- Produces: `fun interface HostResolver { fun resolve(host: String): Int }`; `class DnsHostResolver(dns: Dns) : HostResolver`; `interface DiagnosticsApi { suspend fun loadCataloguePage(): Boolean; suspend fun findProgressiveMediaUrl(): String? }`; `class KinoPubDiagnosticsApi(api: KinoPubApiClient) : DiagnosticsApi`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/data/api/network/diagnostics/KinoPubDiagnosticsApiTest.kt`:

```kotlin
package com.kino.puber.data.api.network.diagnostics

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ItemFiles
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.VideoUrl
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KinoPubDiagnosticsApiTest {

    private val client = mockk<KinoPubApiClient>()
    private val api = KinoPubDiagnosticsApi(client)

    @Test
    fun findProgressiveMediaUrl_returnsTheProgressiveUrl_whenTheItemOffersOne() = runTest {
        givenCatalogue(itemId = 42)
        coEvery { client.getItemFiles(42) } returns Result.success(
            ItemFiles(id = 42, files = listOf(fileWith(VideoUrl(http = "https://cdn.test/a.mp4"))))
        )

        assertEquals("https://cdn.test/a.mp4", api.findProgressiveMediaUrl())
    }

    /**
     * An item that only offers HLS is a fact about the item, not about the network — the caller
     * turns null into a skipped step rather than a failed one.
     */
    @Test
    fun findProgressiveMediaUrl_returnsNull_whenOnlyHlsIsOnOffer() = runTest {
        givenCatalogue(itemId = 42)
        coEvery { client.getItemFiles(42) } returns Result.success(
            ItemFiles(id = 42, files = listOf(fileWith(VideoUrl(hls4 = "https://cdn.test/a.m3u8"))))
        )

        assertNull(api.findProgressiveMediaUrl())
    }

    @Test
    fun findProgressiveMediaUrl_returnsNull_whenTheCatalogueIsEmpty() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.success(PaginatedResponse(items = emptyList(), pagination = pagination()))

        assertNull(api.findProgressiveMediaUrl())
    }

    @Test
    fun findProgressiveMediaUrl_returnsNull_whenTheCatalogueRequestFails() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.failure(IllegalStateException("offline"))

        assertNull(api.findProgressiveMediaUrl())
    }

    @Test
    fun loadCataloguePage_isTrue_whenThePageArrives() = runTest {
        givenCatalogue(itemId = 42)

        assertTrue(api.loadCataloguePage())
    }

    @Test
    fun loadCataloguePage_isFalse_whenTheRequestFails() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.failure(IllegalStateException("offline"))

        assertFalse(api.loadCataloguePage())
    }

    private fun givenCatalogue(itemId: Int) {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.success(
                PaginatedResponse(items = listOf(item(itemId)), pagination = pagination())
            )
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun pagination() = Pagination(total = 1, current = 1, perpage = 1, totalItems = 1)

    private fun fileWith(url: VideoUrl) = VideoFile(url = url)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*KinoPubDiagnosticsApiTest*'`
Expected: FAIL — `KinoPubDiagnosticsApi` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/kino/puber/data/api/network/diagnostics/DiagnosticsSources.kt`:

```kotlin
package com.kino.puber.data.api.network.diagnostics

import com.kino.puber.data.api.KinoPubApiClient
import okhttp3.Dns

/**
 * Resolves a host and says only how many addresses came back.
 *
 * A count rather than the addresses themselves, because the layer above must not be able to show or
 * store an IP even by accident. Making that impossible is cheaper than remembering it.
 */
fun interface HostResolver {
    fun resolve(host: String): Int
}

/**
 * Resolves through the client's own DNS, which is the DNS-over-HTTPS resolver every real request
 * uses. A resolver built for the occasion would answer a question nobody asked.
 */
class DnsHostResolver(private val dns: Dns) : HostResolver {
    override fun resolve(host: String): Int = dns.lookup(host).size
}

/** The two API errands a diagnostics run needs, and nothing else the client can do. */
interface DiagnosticsApi {

    /** Whether one catalogue page arrives. The page itself is of no interest. */
    suspend fun loadCataloguePage(): Boolean

    /**
     * A progressive media URL served by the currently selected server, or null when the catalogue
     * offers none.
     *
     * The URL carries a token. It goes straight to the downloader and is never returned to a screen,
     * written to a log, or kept on the run.
     */
    suspend fun findProgressiveMediaUrl(): String?
}

class KinoPubDiagnosticsApi(private val api: KinoPubApiClient) : DiagnosticsApi {

    override suspend fun loadCataloguePage(): Boolean =
        api.getItems(type = PROBE_TYPE, sort = PROBE_SORT, page = 1).isSuccess

    override suspend fun findProgressiveMediaUrl(): String? {
        val itemId = api.getItems(type = PROBE_TYPE, sort = PROBE_SORT, page = 1)
            .getOrNull()
            ?.items
            ?.firstOrNull()
            ?.id
            ?: return null

        return api.getItemFiles(itemId)
            .getOrNull()
            ?.files
            ?.firstNotNullOfOrNull { file -> file.url?.http?.takeIf(String::isNotBlank) }
    }

    private companion object {
        const val PROBE_TYPE = "movie"
        const val PROBE_SORT = "-created"
    }
}
```

- [ ] **Step 4: Run tests and detekt**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*KinoPubDiagnosticsApiTest*' :app:detektAll`
Expected: PASS, no detekt findings.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/api/network/diagnostics/DiagnosticsSources.kt \
        app/src/test/kotlin/com/kino/puber/data/api/network/diagnostics/KinoPubDiagnosticsApiTest.kt
git commit -m "Give diagnostics a seam onto DNS and the catalogue"
```

---

### Task 4: The runner

The order, the skipping, the timing, and the promise that a cancelled run leaves nothing behind.

**Files:**
- Create: `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractor.kt`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractorTest.kt`

**Interfaces:**
- Consumes: `EndpointProbe`, `EndpointReachability` (existing, `com.kino.puber.data.api.network`), `HostResolver`, `DiagnosticsApi`, `BoundedDownloader`, `NetworkDiagnosticsRun` and friends.
- Produces: `class NetworkDiagnosticsInteractor(probe, resolver, api, downloader, reachability, clock)` with `fun run(): Flow<NetworkDiagnosticsRun>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractorTest.kt`:

```kotlin
package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.DiagnosticsApi
import com.kino.puber.data.api.network.diagnostics.HostResolver
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

internal class NetworkDiagnosticsInteractorTest {

    private var reachableDomains = setOf("service-kp.test")
    private var resolvedAddresses = 2
    private var cataloguePageArrives = true
    private var progressiveUrl: String? = "https://cdn.test/a.mp4"
    private val downloadedUrls = mutableListOf<String>()
    private var now = 1_000L

    private val reachability = EndpointReachability(clock = { now })

    private val interactor = NetworkDiagnosticsInteractor(
        probe = EndpointProbe { endpoint -> endpoint.domain in reachableDomains },
        resolver = HostResolver { resolvedAddresses },
        api = object : DiagnosticsApi {
            override suspend fun loadCataloguePage(): Boolean = cataloguePageArrives
            override suspend fun findProgressiveMediaUrl(): String? = progressiveUrl
        },
        downloader = BoundedDownloader { url, maxBytes ->
            downloadedUrls += url
            ThroughputSample(bytes = maxBytes, elapsedMillis = 1_000)
        },
        reachability = reachability,
        clock = { now },
    )

    @BeforeEach
    fun setUp() {
        mockkObject(KinoPubConfig)
        every { KinoPubConfig.CURRENT_API_DOMAIN } returns "service-kp.test"
        every { KinoPubConfig.CURRENT_API_HOST } returns "api.service-kp.test"
        every { KinoPubConfig.CURRENT_ENDPOINT } returns endpointFor("service-kp.test")
        every { KinoPubConfig.BUILT_IN_ENDPOINTS } returns listOf(
            endpointFor("service-kp.test"),
            endpointFor("api.alador.test"),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(KinoPubConfig)
    }

    @Test
    fun run_settlesEveryStep_whenEverythingWorks() = runTest {
        val last = interactor.run().toList().last()

        assertTrue(last.finished)
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.ApiReachability))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.NameResolution))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.ApiResponsiveness))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.MediaThroughput))
    }

    /** The sweep has no question to ask while the mirror in use is answering. */
    @Test
    fun run_skipsTheMirrorSweep_whenTheCurrentMirrorAnswers() = runTest {
        val last = interactor.run().toList().last()

        assertEquals(
            StepState.Skipped(SkipReason.CurrentMirrorAnswers),
            last.state(DiagnosticStep.MirrorSweep),
        )
    }

    @Test
    fun run_findsAWorkingMirror_whenTheCurrentOneIsDown() = runTest {
        reachableDomains = setOf("api.alador.test")

        val last = interactor.run().toList().last()

        assertEquals("api.alador.test", last.workingMirrorDomain)
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.MirrorSweep))
    }

    @Test
    fun run_skipsTheMediaStep_whenNoProgressiveUrlIsOnOffer() = runTest {
        progressiveUrl = null

        val last = interactor.run().toList().last()

        assertEquals(
            StepState.Skipped(SkipReason.NoMediaLink),
            last.state(DiagnosticStep.MediaThroughput),
        )
        assertTrue(downloadedUrls.isEmpty())
    }

    /** A failing step is news about that step, not a reason to stop asking the other questions. */
    @Test
    fun run_keepsGoing_whenOneStepFails() = runTest {
        cataloguePageArrives = false

        val last = interactor.run().toList().last()

        assertInstanceOf(StepState.Failure::class.java, last.state(DiagnosticStep.ApiResponsiveness))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.MediaThroughput))
    }

    @Test
    fun run_reportsAResolutionFailure_whenNoAddressComesBack() = runTest {
        resolvedAddresses = 0

        val last = interactor.run().toList().last()

        assertEquals(
            StepState.Failure(FailureReason.ResolutionFailed),
            last.state(DiagnosticStep.NameResolution),
        )
    }

    /**
     * The run refreshes the verdict the rest of the app reads — that is the point of reusing the
     * probe rather than writing a second one.
     */
    @Test
    fun run_marksTheDomainReachable_whenTheProbeAnswers() = runTest {
        interactor.run().toList()

        assertTrue(reachability.answeredWithin("service-kp.test", 15.minutes))
    }

    /** A failure is the client's news to report, not a diagnostic's; a bad run must retire nothing. */
    @Test
    fun run_leavesTheVerdictAlone_whenTheProbeFails() = runTest {
        reachability.markReachable("service-kp.test")
        reachableDomains = emptySet()

        interactor.run().toList()

        assertTrue(reachability.answeredWithin("service-kp.test", 15.minutes))
    }

    @Test
    fun run_emitsRunningBeforeSettling_forEveryStep() = runTest {
        val emissions = interactor.run().toList()

        assertTrue(
            emissions.any { it.state(DiagnosticStep.MediaThroughput) == StepState.Running },
            "the media step must be visible while it is running",
        )
    }

    /**
     * Cancelling is the whole cancellation story: the flow is cold, so abandoning collection stops
     * it where it stands. Nothing downstream of the abandoned point may run — and because the run
     * writes nothing, stopping there leaves nothing behind to undo.
     */
    @Test
    fun run_stopsWhereItStands_whenCollectionIsAbandoned() = runTest {
        val partial = interactor.run().take(2).toList()

        assertEquals(2, partial.size)
        assertFalse(partial.any { it.finished })
        assertTrue(downloadedUrls.isEmpty())
    }

    private fun endpointFor(domain: String) = ApiEndpointPreset(
        domain = domain,
        apiHost = domain,
        mainBaseUrl = "https://$domain/v1/",
        oauthBaseUrl = "https://$domain/oauth2/",
        extraBaseUrl = "https://$domain/",
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*NetworkDiagnosticsInteractorTest*'`
Expected: FAIL — `NetworkDiagnosticsInteractor` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractor.kt`:

```kotlin
package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.DiagnosticsApi
import com.kino.puber.data.api.network.diagnostics.HostResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the five measurements in order and reports the whole run after every transition.
 *
 * Writes nothing. Not a preference, not a domain override, not a cache invalidation — which is what
 * makes "a diagnostic failure must not alter normal networking configuration" a property of the
 * shape rather than a thing to remember. Abandoning collection is therefore a complete cancellation
 * story: the flow is cold, and there is no half-finished write for anyone to undo.
 */
internal class NetworkDiagnosticsInteractor(
    private val probe: EndpointProbe,
    private val resolver: HostResolver,
    private val api: DiagnosticsApi,
    private val downloader: BoundedDownloader,
    private val reachability: EndpointReachability,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun run(): Flow<NetworkDiagnosticsRun> = flow {
        var current = NetworkDiagnosticsRun(apiDomain = KinoPubConfig.CURRENT_API_DOMAIN)
        emit(current)

        current = runStep(current, DiagnosticStep.ApiReachability) { measureApiReachability() }
        val apiReachable = current.state(DiagnosticStep.ApiReachability) is StepState.Success

        current = runStep(current, DiagnosticStep.NameResolution) { resolveApiHost() }
        current = runStep(current, DiagnosticStep.ApiResponsiveness) {
            if (apiReachable) measureApiResponsiveness() else StepState.Skipped(SkipReason.NoNetwork)
        }
        current = runStep(current, DiagnosticStep.MediaThroughput) { measureMediaThroughput() }
        current = sweepMirrors(current, apiReachable)

        current = current.copy(finished = true)
        emit(current)
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<NetworkDiagnosticsRun>.runStep(
        current: NetworkDiagnosticsRun,
        step: DiagnosticStep,
        measure: suspend () -> StepState,
    ): NetworkDiagnosticsRun {
        val running = current.with(step, StepState.Running)
        emit(running)

        val state = try {
            measure()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // One step giving out is news about that step. The others still have answers worth
            // having, and the exception's text is never fit to show a user.
            log(error, "Network diagnostics step $step failed")
            StepState.Failure(FailureReason.RequestFailed)
        }

        val settled = running.with(step, state)
        emit(settled)
        return settled
    }

    private suspend fun FlowCollector<NetworkDiagnosticsRun>.sweepMirrors(
        current: NetworkDiagnosticsRun,
        apiReachable: Boolean,
    ): NetworkDiagnosticsRun {
        if (apiReachable) {
            val skipped = current.with(
                DiagnosticStep.MirrorSweep,
                StepState.Skipped(SkipReason.CurrentMirrorAnswers),
            )
            emit(skipped)
            return skipped
        }

        emit(current.with(DiagnosticStep.MirrorSweep, StepState.Running))
        val working = findWorkingMirror()
        val settled = current
            .with(
                DiagnosticStep.MirrorSweep,
                if (working == null) {
                    StepState.Failure(FailureReason.Unreachable)
                } else {
                    StepState.Success()
                },
            )
            .copy(workingMirrorDomain = working)
        emit(settled)
        return settled
    }

    private fun measureApiReachability(): StepState {
        val endpoint = KinoPubConfig.CURRENT_ENDPOINT
        val startedAt = clock()
        val reachable = probe.isReachable(endpoint)
        val elapsed = clock() - startedAt

        if (!reachable) return StepState.Failure(FailureReason.Unreachable)

        // Marking reachable, never unreachable: retiring a verdict is the client's job, because it
        // is the only thing that sees every request. A probe that failed once must not take down a
        // domain the app is talking to successfully.
        reachability.markReachable(endpoint.domain)
        return StepState.Success(latencyMillis = elapsed)
    }

    private fun resolveApiHost(): StepState {
        val startedAt = clock()
        val addresses = resolver.resolve(KinoPubConfig.CURRENT_API_HOST)
        val elapsed = clock() - startedAt

        return if (addresses > 0) {
            StepState.Success(latencyMillis = elapsed)
        } else {
            StepState.Failure(FailureReason.ResolutionFailed)
        }
    }

    private suspend fun measureApiResponsiveness(): StepState {
        val startedAt = clock()
        // The client's own request timeout is two minutes, which is reasonable for a screen loading
        // content and absurd for a step somebody is watching a spinner for.
        val arrived = withTimeoutOrNull(RESPONSIVENESS_TIMEOUT) { api.loadCataloguePage() }
        val elapsed = clock() - startedAt

        return if (arrived == true) {
            StepState.Success(latencyMillis = elapsed)
        } else {
            StepState.Failure(FailureReason.RequestFailed)
        }
    }

    private suspend fun measureMediaThroughput(): StepState {
        val url = api.findProgressiveMediaUrl() ?: return StepState.Skipped(SkipReason.NoMediaLink)
        val sample = downloader.measure(url, MEDIA_PROBE_MAX_BYTES)

        return if (sample.bytes > 0) {
            StepState.Success(sample = sample)
        } else {
            StepState.Failure(FailureReason.RequestFailed)
        }
    }

    private fun findWorkingMirror(): String? {
        val current = KinoPubConfig.CURRENT_API_DOMAIN
        return KinoPubConfig.BUILT_IN_ENDPOINTS
            .filterNot { it.domain == current }
            .firstOrNull(probe::isReachable)
            ?.domain
    }

    private companion object {
        val RESPONSIVENESS_TIMEOUT: Duration = 8.seconds
        const val MEDIA_PROBE_MAX_BYTES = 4L * 1024L * 1024L
    }
}
```

- [ ] **Step 4: Run tests and detekt**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*NetworkDiagnosticsInteractorTest*' :app:detektAll`
Expected: PASS, no detekt findings.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractor.kt \
        app/src/test/kotlin/com/kino/puber/domain/interactor/diagnostics/NetworkDiagnosticsInteractorTest.kt
git commit -m "Run the five network measurements in order"
```

---

### Task 5: Switching to a mirror the run actually probed

`ApiDomainInteractor` can auto-detect and can save an arbitrary string; it cannot be told "use this built-in one". The screen needs exactly that, so it applies the mirror the sweep proved rather than re-running a walk that might land elsewhere.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorTest.kt`

**Interfaces:**
- Produces: `suspend fun ApiDomainInteractor.switchToBuiltInDomain(domain: String): ApiDomainState?` — the new state, or null when `domain` is not a built-in endpoint.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorTest.kt` (inside the class, reusing its existing `setUp`, `endpointFor` and mocks):

```kotlin
    @Test
    fun switchToBuiltInDomain_appliesTheMirror_whenItIsBuiltIn() = runTest {
        val state = interactor.switchToBuiltInDomain("api.alador.test")

        assertEquals("api.alador.test", state?.domain)
        assertEquals("api.alador.test", state?.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain("api.alador.test") }
    }

    /**
     * The default domain is stored as "no override" rather than as itself, so returning to it has
     * to clear the preference — otherwise a later change to the built-in default would be pinned
     * shut by a value the user never chose.
     */
    @Test
    fun switchToBuiltInDomain_clearsTheOverride_whenTheTargetIsTheDefault() = runTest {
        interactor.switchToBuiltInDomain("api.alador.test")

        val state = interactor.switchToBuiltInDomain("service-kp.test")

        assertEquals("service-kp.test", state?.domain)
        assertNull(state?.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain(null) }
    }

    /** A domain that is not one of ours is not something a diagnostic may switch to. */
    @Test
    fun switchToBuiltInDomain_changesNothing_whenTheDomainIsUnknown() = runTest {
        val state = interactor.switchToBuiltInDomain("evil.test")

        assertNull(state)
        verify(exactly = 0) { preferences.saveApiDomain("evil.test") }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*ApiDomainInteractorTest*'`
Expected: FAIL — `switchToBuiltInDomain` unresolved.

- [ ] **Step 3: Write the implementation**

In `ApiDomainInteractor.kt`, add after `detectAndSaveAlternativeBuiltInDomain`:

```kotlin
    /**
     * Switches to a named built-in endpoint, or does nothing when the name is not one of ours.
     *
     * The diagnostics screen has already probed the mirror it is proposing, so re-running a
     * detection walk would be both wasteful and wrong — the walk could settle on a different
     * endpoint than the one the user was shown and agreed to.
     */
    suspend fun switchToBuiltInDomain(domain: String): ApiDomainState? =
        withContext(Dispatchers.IO) {
            val preset = KinoPubConfig.BUILT_IN_ENDPOINTS.firstOrNull { it.domain == domain }
                ?: return@withContext null

            applyEndpoint(preset)
            getState()
        }
```

- [ ] **Step 4: Run tests and detekt**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*ApiDomainInteractorTest*' :app:detektAll`
Expected: PASS, no detekt findings.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt \
        app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorTest.kt
git commit -m "Let a caller switch to the built-in mirror it probed"
```

---

### Task 6: View state, actions and the view model

The run job lives here, and so does the rule that nothing changes without a confirmation.

**Files:**
- Create: `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/model/NetworkDiagnosticsViewState.kt`
- Create: `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/model/NetworkDiagnosticsActions.kt`
- Create: `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt`

**Interfaces:**
- Consumes: `NetworkDiagnosticsInteractor.run()`, `advise()`, `ApiDomainInteractor.switchToBuiltInDomain()`.
- Produces: `NetworkDiagnosticsViewState(steps, running, finished, advice, applyingMirror, appliedMirror)`; `DiagnosticStepUi(step, state)`; actions `Cancel`, `Restart`, `ConfirmMirrorSwitch`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.FailureReason
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class NetworkDiagnosticsVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val interactor = mockk<NetworkDiagnosticsInteractor>()
    private val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val screens = mockk<Screens>(relaxed = true)
    private val router = mockk<AppRouter>(relaxed = true)

    private fun vm(): NetworkDiagnosticsVM {
        every { router.screens } returns screens
        return NetworkDiagnosticsVM(
            interactor = interactor,
            apiDomainInteractor = apiDomainInteractor,
            errorHandler = errorHandler,
            resources = FakeResourceProvider(),
            router = router,
        )
    }

    private fun finishedRun(mirror: String? = null, apiUp: Boolean = true) =
        NetworkDiagnosticsRun(apiDomain = "service-kp.test")
            .with(
                DiagnosticStep.ApiReachability,
                if (apiUp) {
                    StepState.Success(latencyMillis = 120)
                } else {
                    StepState.Failure(FailureReason.Unreachable)
                },
            )
            .copy(workingMirrorDomain = mirror, finished = true)

    @Test
    fun onStart_runsTheDiagnostics_andPublishesTheFinishedRun() = runTest {
        every { interactor.run() } returns flowOf(finishedRun())

        val viewModel = vm()
        viewModel.testOnStart()

        assertFalse(viewModel.testStateValue.running)
        assertTrue(viewModel.testStateValue.finished)
        assertTrue(viewModel.testStateValue.advice?.apiReachable == true)
    }

    @Test
    fun cancel_stopsTheRun_andLeavesTheStepsWhereTheyStood() = runTest {
        val channel = Channel<NetworkDiagnosticsRun>(Channel.UNLIMITED)
        every { interactor.run() } returns channel.consumeAsFlow()

        val viewModel = vm()
        viewModel.testOnStart()
        channel.send(NetworkDiagnosticsRun(apiDomain = "service-kp.test"))
        viewModel.onAction(NetworkDiagnosticsActions.Cancel)

        assertFalse(viewModel.testStateValue.running)
        assertFalse(viewModel.testStateValue.finished)
    }

    @Test
    fun restart_startsAFreshRun_afterACancelledOne() = runTest {
        every { interactor.run() } returns flowOf(finishedRun())

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.Cancel)
        viewModel.onAction(NetworkDiagnosticsActions.Restart)

        assertTrue(viewModel.testStateValue.finished)
    }

    /** The whole point of the confirmation: the run itself must change nothing. */
    @Test
    fun run_changesNoDomain_whenAMirrorIsMerelyProposed() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))

        val viewModel = vm()
        viewModel.testOnStart()

        assertEquals("api.alador.test", viewModel.testStateValue.advice?.mirrorProposal)
        coVerify(exactly = 0) { apiDomainInteractor.switchToBuiltInDomain(any()) }
    }

    @Test
    fun confirmMirrorSwitch_appliesTheProposedMirror() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") } returns
            ApiDomainState(domain = "api.alador.test", customDomain = "api.alador.test")

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)

        coVerify(exactly = 1) { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") }
        assertEquals("api.alador.test", viewModel.testStateValue.appliedMirror)
        assertNull(viewModel.testStateValue.advice?.mirrorProposal)
    }

    @Test
    fun confirmMirrorSwitch_doesNothing_whenNoMirrorWasProposed() = runTest {
        every { interactor.run() } returns flowOf(finishedRun())

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)

        coVerify(exactly = 0) { apiDomainInteractor.switchToBuiltInDomain(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*NetworkDiagnosticsVMTest*'`
Expected: FAIL — `NetworkDiagnosticsVM` unresolved.

- [ ] **Step 3: Write the actions**

Create `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/model/NetworkDiagnosticsActions.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface NetworkDiagnosticsActions : UIAction {
    data object Cancel : NetworkDiagnosticsActions
    data object Restart : NetworkDiagnosticsActions
    data object ConfirmMirrorSwitch : NetworkDiagnosticsActions
}
```

- [ ] **Step 4: Write the view state**

Create `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/model/NetworkDiagnosticsViewState.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.DiagnosticsAdvice
import com.kino.puber.domain.interactor.diagnostics.StepState

/** One row: which measurement, and where it got to. */
@Immutable
internal data class DiagnosticStepUi(
    val step: DiagnosticStep,
    val state: StepState,
)

/**
 * The run's `Map` becomes an ordered `List` here rather than being handed to Compose as it is: a
 * map is not a stable key source for a list, and the order the steps are drawn in is a decision the
 * screen owns.
 */
@Immutable
internal data class NetworkDiagnosticsViewState(
    val steps: List<DiagnosticStepUi> = DiagnosticStep.entries.map {
        DiagnosticStepUi(it, StepState.Pending)
    },
    val apiDomain: String = "",
    val running: Boolean = false,
    val finished: Boolean = false,
    val advice: DiagnosticsAdvice? = null,
    val applyingMirror: Boolean = false,
    val appliedMirror: String? = null,
)
```

- [ ] **Step 5: Write the view model**

Create `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.advise
import com.kino.puber.ui.feature.device.diagnostics.model.DiagnosticStepUi
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import kotlinx.coroutines.Job

internal class NetworkDiagnosticsVM(
    private val interactor: NetworkDiagnosticsInteractor,
    private val apiDomainInteractor: ApiDomainInteractor,
    override val errorHandler: ErrorHandler,
    private val resources: ResourceProvider,
    router: AppRouter,
) : PuberVM<NetworkDiagnosticsViewState>(router) {

    /**
     * The run, and the only thing cancelling has to reach. The interactor writes nothing, so
     * cancelling the job is the whole story — there is no partial change to undo.
     */
    private var runJob: Job? = null

    override val initialViewState = NetworkDiagnosticsViewState()

    override fun onStart() {
        startRun()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            NetworkDiagnosticsActions.Cancel -> cancelRun()
            NetworkDiagnosticsActions.Restart -> startRun()
            NetworkDiagnosticsActions.ConfirmMirrorSwitch -> applyProposedMirror()
        }
    }

    private fun startRun() {
        runJob?.cancel()
        updateViewState(
            NetworkDiagnosticsViewState(
                running = true,
                // The mirror applied in an earlier run is a fact about the app, not about this run,
                // so it survives a restart while every measurement starts over.
                appliedMirror = stateValue.appliedMirror,
            )
        )
        runJob = launch {
            interactor.run().collect(::publish)
        }
    }

    private fun cancelRun() {
        runJob?.cancel()
        runJob = null
        updateViewState(stateValue.copy(running = false))
    }

    private fun publish(run: NetworkDiagnosticsRun) {
        updateViewState(
            stateValue.copy(
                steps = DiagnosticStep.entries.map { DiagnosticStepUi(it, run.state(it)) },
                apiDomain = run.apiDomain,
                running = !run.finished,
                finished = run.finished,
                advice = if (run.finished) advise(run) else null,
            )
        )
    }

    private fun applyProposedMirror() {
        val proposal = stateValue.advice?.mirrorProposal ?: return
        if (stateValue.applyingMirror) return

        updateViewState(stateValue.copy(applyingMirror = true))
        launch {
            val applied = apiDomainInteractor.switchToBuiltInDomain(proposal)
            updateViewState(
                stateValue.copy(
                    applyingMirror = false,
                    appliedMirror = applied?.domain,
                    // The proposal has been acted on; leaving it on screen would invite a second
                    // press that switches to the mirror already in use.
                    advice = stateValue.advice?.copy(mirrorProposal = null),
                )
            )
            if (applied == null) {
                showMessage(resources.getString(R.string.diagnostics_mirror_switch_failed))
            }
        }
    }
}
```

> `showMessage` needs `R.string.diagnostics_mirror_switch_failed`. **This task owns that one string** — add it to both `app/src/main/res/values/strings.xml` (`Не удалось переключить зеркало.`) and `app/src/main/res/values-en/strings.xml` (`The mirror could not be switched.`) so the task compiles on its own. Task 7 adds the rest and skips this key.

- [ ] **Step 6: Run tests and detekt**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*NetworkDiagnosticsVMTest*' :app:detektAll`
Expected: PASS, no detekt findings.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/ \
        app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/ \
        app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "Own the diagnostics run and gate the mirror switch behind a confirmation"
```

---

### Task 7: The screen, its strings, and the way in

**Files:**
- Create: `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsTestTags.kt`
- Create: `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt`
- Create: `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt`
- Modify: `app/src/main/java/com/kino/puber/core/ui/navigation/Screens.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/ScreensImpl.kt`
- Modify: `app/src/main/java/com/kino/puber/domain/di/modules.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsActions.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: `Screens.networkDiagnostics(): PuberScreen`; `NetworkDiagnosticsTestTags.{Steps, Step, PrimaryAction, MirrorSwitch, Summary}`.

- [ ] **Step 1: Add the strings**

Add to `app/src/main/res/values/strings.xml` (Russian, before `</resources>`). **Skip any key already present** — Task 6 added `diagnostics_mirror_switch_failed`, and a duplicate resource id fails the build:

```xml
    <string name="diagnostics_open_action">Диагностика сети</string>
    <string name="diagnostics_settings_subtitle">Проверить связь с сервисом и скорость видео.</string>
    <string name="diagnostics_screen_title">Диагностика сети</string>
    <string name="diagnostics_step_api_reachability">Доступность API</string>
    <string name="diagnostics_step_name_resolution">Определение имён</string>
    <string name="diagnostics_step_api_responsiveness">Отклик API</string>
    <string name="diagnostics_step_media_throughput">Скорость видео</string>
    <string name="diagnostics_step_mirror_sweep">Запасные зеркала</string>
    <string name="diagnostics_state_pending">Ожидает</string>
    <string name="diagnostics_state_running">Проверяю…</string>
    <string name="diagnostics_latency_millis">%1$d мс</string>
    <string name="diagnostics_rate_mbits">%1$s Мбит/с</string>
    <string name="diagnostics_failure_unreachable">Не отвечает</string>
    <string name="diagnostics_failure_resolution">Имя не определилось</string>
    <string name="diagnostics_failure_request">Запрос не прошёл</string>
    <string name="diagnostics_skipped_no_network">Пропущено: нет связи</string>
    <string name="diagnostics_skipped_no_media_link">Пропущено: нечего измерить</string>
    <string name="diagnostics_skipped_mirror_ok">Не нужны: текущее зеркало отвечает</string>
    <string name="diagnostics_summary_api_ok">API отвечает.</string>
    <string name="diagnostics_summary_api_down">API не отвечает.</string>
    <string name="diagnostics_summary_media_too_slow">Скорость видео — %1$s Мбит/с: этого мало даже для 720p.</string>
    <string name="diagnostics_summary_media_720">Скорость видео — %1$s Мбит/с: этого хватает для 720p.</string>
    <string name="diagnostics_summary_media_1080">Скорость видео — %1$s Мбит/с: этого хватает для 1080p, для 4K маловато.</string>
    <string name="diagnostics_summary_media_4k">Скорость видео — %1$s Мбит/с: этого хватает и для 4K.</string>
    <string name="diagnostics_summary_media_unknown">Скорость видео измерить не удалось.</string>
    <string name="diagnostics_summary_slow_hint">Если видео тормозит, попробуй другую локацию сервера или другой тип потока в этом разделе настроек.</string>
    <string name="diagnostics_mirror_proposal">Зеркало %1$s не отвечает, %2$s отвечает.</string>
    <string name="diagnostics_mirror_switch">Переключить на %1$s</string>
    <string name="diagnostics_mirror_switched">Зеркало переключено на %1$s.</string>
    <string name="diagnostics_mirror_switch_failed">Не удалось переключить зеркало.</string>
    <string name="diagnostics_cancel">Отмена</string>
    <string name="diagnostics_restart">Повторить</string>
```

Add the same keys to `app/src/main/res/values-en/strings.xml` in English:

```xml
    <string name="diagnostics_open_action">Network diagnostics</string>
    <string name="diagnostics_settings_subtitle">Check the connection to the service and the video speed.</string>
    <string name="diagnostics_screen_title">Network diagnostics</string>
    <string name="diagnostics_step_api_reachability">API reachability</string>
    <string name="diagnostics_step_name_resolution">Name resolution</string>
    <string name="diagnostics_step_api_responsiveness">API response time</string>
    <string name="diagnostics_step_media_throughput">Video speed</string>
    <string name="diagnostics_step_mirror_sweep">Backup mirrors</string>
    <string name="diagnostics_state_pending">Waiting</string>
    <string name="diagnostics_state_running">Checking…</string>
    <string name="diagnostics_latency_millis">%1$d ms</string>
    <string name="diagnostics_rate_mbits">%1$s Mbit/s</string>
    <string name="diagnostics_failure_unreachable">No answer</string>
    <string name="diagnostics_failure_resolution">Name did not resolve</string>
    <string name="diagnostics_failure_request">Request failed</string>
    <string name="diagnostics_skipped_no_network">Skipped: no connection</string>
    <string name="diagnostics_skipped_no_media_link">Skipped: nothing to measure</string>
    <string name="diagnostics_skipped_mirror_ok">Not needed: the current mirror answers</string>
    <string name="diagnostics_summary_api_ok">The API answers.</string>
    <string name="diagnostics_summary_api_down">The API does not answer.</string>
    <string name="diagnostics_summary_media_too_slow">Video speed is %1$s Mbit/s, which is short even for 720p.</string>
    <string name="diagnostics_summary_media_720">Video speed is %1$s Mbit/s, enough for 720p.</string>
    <string name="diagnostics_summary_media_1080">Video speed is %1$s Mbit/s, enough for 1080p but short for 4K.</string>
    <string name="diagnostics_summary_media_4k">Video speed is %1$s Mbit/s, enough for 4K as well.</string>
    <string name="diagnostics_summary_media_unknown">Video speed could not be measured.</string>
    <string name="diagnostics_summary_slow_hint">If video stutters, try a different server location or a different streaming type in this settings section.</string>
    <string name="diagnostics_mirror_proposal">Mirror %1$s does not answer; %2$s does.</string>
    <string name="diagnostics_mirror_switch">Switch to %1$s</string>
    <string name="diagnostics_mirror_switched">The mirror was switched to %1$s.</string>
    <string name="diagnostics_mirror_switch_failed">The mirror could not be switched.</string>
    <string name="diagnostics_cancel">Cancel</string>
    <string name="diagnostics_restart">Run again</string>
```

- [ ] **Step 2: Add the test tags**

Create `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsTestTags.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics

internal object NetworkDiagnosticsTestTags {
    const val Steps = "diagnostics-steps"
    const val Summary = "diagnostics-summary"
    const val PrimaryAction = "diagnostics-primary-action"
    const val MirrorSwitch = "diagnostics-mirror-switch"
    const val AppliedMirror = "diagnostics-applied-mirror"

    fun step(name: String) = "diagnostics-step-$name"
}
```

- [ ] **Step 3: Write the content composable**

Create `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.DiagnosticsAdvice
import com.kino.puber.domain.interactor.diagnostics.FailureReason
import com.kino.puber.domain.interactor.diagnostics.QualityCeiling
import com.kino.puber.domain.interactor.diagnostics.SkipReason
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.DiagnosticStepUi
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import java.util.Locale

private val ScreenHorizontalPadding = 48.dp
private val ScreenVerticalPadding = 28.dp
private const val BITS_PER_MEGABIT = 1_000_000.0

@Composable
internal fun NetworkDiagnosticsContent(
    state: NetworkDiagnosticsViewState,
    onAction: (UIAction) -> Unit = {},
) {
    val primaryFocusRequester = rememberFocusRequesterOnLaunch()
    // The proposal is not on screen when the screen opens — it appears when the run ends — so a
    // requester that fired at first composition has already spent itself on the button below.
    // The proposal is a change to the user's settings and has to be what their thumb is on, so it
    // takes focus at the moment it appears instead.
    val mirrorFocusRequester = remember { FocusRequester() }
    val proposal = state.advice?.mirrorProposal
    LaunchedEffect(proposal) {
        if (proposal != null) mirrorFocusRequester.requestFocus()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_screen_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(NetworkDiagnosticsTestTags.Steps),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.steps, key = { it.step.name }) { row ->
                    StepRow(
                        row = row,
                        // The one host the screen is allowed to name, and only on the row it is
                        // the subject of. It is the user's own setting, shown next door already.
                        detail = state.apiDomain.takeIf { row.step == DiagnosticStep.ApiReachability },
                    )
                }
            }

            state.advice?.let { advice ->
                Text(
                    text = summaryText(advice),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.Summary),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // A confirmed settings change earns standing confirmation rather than a message that
            // disappears: this is a television, and the person who pressed the button may well
            // have looked away by the time it lands.
            state.appliedMirror?.let { mirror ->
                Text(
                    text = stringResource(R.string.diagnostics_mirror_switched, mirror),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.AppliedMirror),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (proposal != null) {
                    Button(
                        onClick = { onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch) },
                        enabled = !state.applyingMirror,
                        modifier = Modifier
                            .focusRequester(mirrorFocusRequester)
                            .testTag(NetworkDiagnosticsTestTags.MirrorSwitch),
                    ) {
                        Text(stringResource(R.string.diagnostics_mirror_switch, proposal))
                    }
                }
                Button(
                    onClick = {
                        onAction(
                            if (state.running) {
                                NetworkDiagnosticsActions.Cancel
                            } else {
                                NetworkDiagnosticsActions.Restart
                            }
                        )
                    },
                    modifier = Modifier
                        .focusRequester(primaryFocusRequester)
                        .testTag(NetworkDiagnosticsTestTags.PrimaryAction),
                ) {
                    Text(
                        stringResource(
                            if (state.running) R.string.diagnostics_cancel else R.string.diagnostics_restart
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(row: DiagnosticStepUi, detail: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NetworkDiagnosticsTestTags.step(row.step.name))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(row.step.titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Text(
            text = stateText(row.state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val DiagnosticStep.titleRes: Int
    get() = when (this) {
        DiagnosticStep.ApiReachability -> R.string.diagnostics_step_api_reachability
        DiagnosticStep.NameResolution -> R.string.diagnostics_step_name_resolution
        DiagnosticStep.ApiResponsiveness -> R.string.diagnostics_step_api_responsiveness
        DiagnosticStep.MediaThroughput -> R.string.diagnostics_step_media_throughput
        DiagnosticStep.MirrorSweep -> R.string.diagnostics_step_mirror_sweep
    }

@Composable
private fun stateText(state: StepState): String = when (state) {
    StepState.Pending -> stringResource(R.string.diagnostics_state_pending)
    StepState.Running -> stringResource(R.string.diagnostics_state_running)
    is StepState.Success -> successText(state)
    is StepState.Failure -> stringResource(
        when (state.reason) {
            FailureReason.Unreachable -> R.string.diagnostics_failure_unreachable
            FailureReason.ResolutionFailed -> R.string.diagnostics_failure_resolution
            FailureReason.RequestFailed -> R.string.diagnostics_failure_request
        }
    )
    is StepState.Skipped -> stringResource(
        when (state.reason) {
            SkipReason.NoNetwork -> R.string.diagnostics_skipped_no_network
            SkipReason.NoMediaLink -> R.string.diagnostics_skipped_no_media_link
            SkipReason.CurrentMirrorAnswers -> R.string.diagnostics_skipped_mirror_ok
        }
    )
}

@Composable
private fun successText(state: StepState.Success): String = when {
    state.sample != null -> stringResource(
        R.string.diagnostics_rate_mbits,
        formatMegabits(state.sample.bitsPerSecond),
    )
    state.latencyMillis != null -> stringResource(
        R.string.diagnostics_latency_millis,
        state.latencyMillis,
    )
    else -> ""
}

@Composable
private fun summaryText(advice: DiagnosticsAdvice): String {
    val api = stringResource(
        if (advice.apiReachable) {
            R.string.diagnostics_summary_api_ok
        } else {
            R.string.diagnostics_summary_api_down
        }
    )
    val rate = advice.mediaBitsPerSecond
    val media = if (rate == null || advice.ceiling == null) {
        stringResource(R.string.diagnostics_summary_media_unknown)
    } else {
        stringResource(
            when (advice.ceiling) {
                QualityCeiling.TooSlow -> R.string.diagnostics_summary_media_too_slow
                QualityCeiling.Hd720 -> R.string.diagnostics_summary_media_720
                QualityCeiling.Hd1080 -> R.string.diagnostics_summary_media_1080
                QualityCeiling.Uhd4k -> R.string.diagnostics_summary_media_4k
            },
            formatMegabits(rate),
        )
    }
    // Location and streaming type are the two settings a slow link is worth revisiting, and neither
    // can be measured from here — so they are named as something to try, never offered as a button.
    val hint = when (advice.ceiling) {
        QualityCeiling.TooSlow, QualityCeiling.Hd720 ->
            " " + stringResource(R.string.diagnostics_summary_slow_hint)
        else -> ""
    }
    return "$api $media$hint"
}

private fun formatMegabits(bitsPerSecond: Double): String =
    String.format(Locale.getDefault(), "%.1f", bitsPerSecond / BITS_PER_MEGABIT)
```

- [ ] **Step 4: Write the screen**

Create `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.ui.feature.device.diagnostics.vm.NetworkDiagnosticsVM
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class NetworkDiagnosticsScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::NetworkDiagnosticsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val viewModel = puberViewModel<NetworkDiagnosticsVM>()
        val state by viewModel.collectViewState()
        val message by viewModel.collectMessage()
        val onAction = remember(viewModel) { viewModel::onAction }

        Box {
            NetworkDiagnosticsContent(state = state, onAction = onAction)
            ScaffoldMessage(message = message, onAction = onAction)
        }
    }
}
```

- [ ] **Step 5: Wire navigation**

In `app/src/main/java/com/kino/puber/core/ui/navigation/Screens.kt`, add next to `deviceSettings()`:

```kotlin
    fun networkDiagnostics(): PuberScreen
```

In `app/src/main/java/com/kino/puber/ui/ScreensImpl.kt`, add the import
`import com.kino.puber.ui.feature.device.diagnostics.NetworkDiagnosticsScreen` and:

```kotlin
    override fun networkDiagnostics(): PuberScreen = NetworkDiagnosticsScreen()
```

Check `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/FlowComponentRemoteHotkeyTest.kt:100` — `ProbeScreens` implements `Screens` and needs the new member too.

- [ ] **Step 6: Wire DI**

In `app/src/main/java/com/kino/puber/domain/di/modules.kt`, add the imports and register:

```kotlin
    single {
        NetworkDiagnosticsInteractor(
            probe = HttpEndpointProbe(get()),
            resolver = DnsHostResolver(get<OkHttpClient>().dns),
            api = KinoPubDiagnosticsApi(get()),
            downloader = OkHttpBoundedDownloader(get()),
            reachability = get(),
        )
    }
```

Imports needed: `com.kino.puber.data.api.network.diagnostics.DnsHostResolver`, `KinoPubDiagnosticsApi`, `OkHttpBoundedDownloader`, `com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor`, `okhttp3.OkHttpClient`.

- [ ] **Step 7: Add the settings row**

In `DeviceSettingsActions.kt`:

```kotlin
    data object OpenNetworkDiagnostics : DeviceSettingsActions
```

In `DeviceSettingsVM.kt`'s `onAction` `when`, next to `DeviceSettingsActions.OpenApiDomainDialog`:

```kotlin
            DeviceSettingsActions.OpenNetworkDiagnostics ->
                router.navigateTo(router.screens.networkDiagnostics())
```

In `DeviceSettingsContent.kt`'s `networkItems`, immediately after the `"api-domain"` item:

```kotlin
    item(key = "network-diagnostics") {
        SettingsListItem(
            headline = stringResource(R.string.diagnostics_open_action),
            supportingText = stringResource(R.string.diagnostics_settings_subtitle),
            role = Role.Button,
            onClick = { onAction(DeviceSettingsActions.OpenNetworkDiagnostics) },
        )
    }
```

- [ ] **Step 8: Compile, test and lint**

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin testDevDebugUnitTest :app:detektAll`
Expected: BUILD SUCCESSFUL, all tests pass, no detekt findings.

- [ ] **Step 9: Commit**

```bash
git add app/src/main app/src/androidTest
git commit -m "Put network diagnostics behind a row in the network settings"
```

---

### Task 8: D-pad reachability and on-device verification

This change touches TV UI, focus, navigation, DI and networking, so per `AGENTS.md` it needs verification on a device. **Do not skip this; if no device is free, say so and ask rather than declaring the task done.**

**Files:**
- Create: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt`

- [ ] **Step 1: Write the focus test**

Instrumented tests here are JUnit 4 (`org.junit.Test`, `@get:Rule`) — unlike the unit tests. Create
`app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt`:

```kotlin
package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.DiagnosticsAdvice
import com.kino.puber.domain.interactor.diagnostics.QualityCeiling
import com.kino.puber.domain.interactor.diagnostics.SkipReason
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.DiagnosticStepUi
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val FocusTimeoutMillis = 3_000L

internal class NetworkDiagnosticsContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Starting and stopping the test are both one press on the row that already holds focus. */
    @Test
    fun runningStateOffersCancelOnTheFocusedButton() {
        val actions = mutableListOf<UIAction>()
        setContent(state = runningState(), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.PrimaryAction)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.diagnostics_cancel)).assertExists()

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.Cancel), actions)
    }

    @Test
    fun finishedStateOffersRestartOnTheFocusedButton() {
        val actions = mutableListOf<UIAction>()
        setContent(state = finishedState(), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.PrimaryAction)
        composeRule.onNodeWithText(context.getString(R.string.diagnostics_restart)).assertExists()

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.Restart), actions)
    }

    /**
     * The proposal is a change to the user's settings, so it must be the thing under their thumb
     * when the run ends — and reachable by nothing but the remote.
     */
    @Test
    fun mirrorProposalTakesFocusAndConfirmsOnPress() {
        val actions = mutableListOf<UIAction>()
        setContent(state = proposalState(), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).assertIsFocused()

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.ConfirmMirrorSwitch), actions)
    }

    @Test
    fun restartStaysReachableFromTheProposalWithTheDPad() {
        setContent(state = proposalState())

        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).press(Key.DirectionRight)

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).assertIsFocused()
    }

    /** A skipped step must read as skipped, not as a failure. */
    @Test
    fun skippedStepsAreDrawnWithTheirOwnReason() {
        setContent(state = finishedState())

        composeRule
            .onNodeWithText(context.getString(R.string.diagnostics_skipped_mirror_ok))
            .assertExists()
    }

    private fun setContent(
        state: NetworkDiagnosticsViewState,
        onAction: (UIAction) -> Unit = {},
    ) {
        composeRule.setContent {
            PuberTheme {
                NetworkDiagnosticsContent(state = state, onAction = onAction)
            }
        }
    }

    private fun awaitFocus(tag: String) {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(
                    androidx.compose.ui.semantics.SemanticsProperties.TestTag
                ) == tag
            }
        }
    }

    private fun SemanticsNodeInteraction.press(key: Key): SemanticsNodeInteraction {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
        return this
    }

    private fun runningState() = NetworkDiagnosticsViewState(
        steps = listOf(
            DiagnosticStepUi(DiagnosticStep.ApiReachability, StepState.Success(latencyMillis = 142)),
            DiagnosticStepUi(DiagnosticStep.NameResolution, StepState.Success(latencyMillis = 38)),
            DiagnosticStepUi(DiagnosticStep.ApiResponsiveness, StepState.Success(latencyMillis = 340)),
            DiagnosticStepUi(DiagnosticStep.MediaThroughput, StepState.Running),
            DiagnosticStepUi(
                DiagnosticStep.MirrorSweep,
                StepState.Skipped(SkipReason.CurrentMirrorAnswers),
            ),
        ),
        apiDomain = "api.example.test",
        running = true,
    )

    private fun finishedState() = runningState().copy(
        steps = runningState().steps.map { row ->
            if (row.step == DiagnosticStep.MediaThroughput) {
                row.copy(state = StepState.Success(latencyMillis = null))
            } else {
                row
            }
        },
        running = false,
        finished = true,
        advice = DiagnosticsAdvice(
            apiReachable = true,
            mediaBitsPerSecond = 18_000_000.0,
            ceiling = QualityCeiling.Hd1080,
            mirrorProposal = null,
        ),
    )

    private fun proposalState() = finishedState().copy(
        advice = DiagnosticsAdvice(
            apiReachable = false,
            mediaBitsPerSecond = null,
            ceiling = null,
            mirrorProposal = "api.alador.test",
        ),
    )
}
```

Add `import androidx.compose.ui.test.assertExists` if the compiler asks for it — it is an extension
on `SemanticsNodeInteraction` and is sometimes already in scope.

- [ ] **Step 2: Acquire a device lease and run it**

```bash
./tools/emulator-resource-lock.sh acquire
```

Confirm exactly one device is attached (`adb devices`) — `connectedAndroidTest` runs on every attached device regardless of the serial it is given. Do not detach a device whose lease you do not hold; ask for authorization instead.

Note before running: instrumented tests wipe the dev app's login. Do any on-device visual checks **before** this step, not after.

Run: `AGENT_GRADLE_SERIAL=1 ./tools/agentw connectedDevDebugAndroidTest --tests '*NetworkDiagnosticsContentFocusTest*'`
Expected: PASS.

- [ ] **Step 3: Verify the real screen on the device**

Build and install the worktree's own APK — never substitute a main-checkout build — preserving app data:

```bash
AGENT_GRADLE_SERIAL=1 ./tools/agentw assembleDevDebug
./tools/android-apk-install-preserve <apk-path> <serial>
```

With the remote only: Settings → Сеть → Диагностика сети. Confirm that the run starts by itself, that each row moves through running into a settled state, that «Отмена» stops it and the button becomes «Повторить», that Back leaves the screen, and that no URL, token or IP appears anywhere on it.

- [ ] **Step 4: Release the lease**

```bash
./tools/emulator-resource-lock.sh release
```

- [ ] **Step 5: Audit any evidence before sharing it**

```bash
./tools/mobile-evidence-audit.sh <evidence-dir> com.kino.puber
```

Keep credentials, raw logcat and authenticated responses out of Git. Screenshots of this screen are safe only because the screen renders no URL — check before adding one.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/
git commit -m "Prove the diagnostics screen is reachable with a D-pad"
```

---

## Review

- [ ] Self-review the whole diff.
- [ ] Send it to Codex for review and adjudicate the findings — Codex repeats false positives, so argue with them rather than implementing them blindly.
