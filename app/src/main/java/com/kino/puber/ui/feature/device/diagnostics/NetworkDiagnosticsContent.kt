package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalDrawerState
import com.kino.puber.core.ui.uikit.component.modifier.FOCUS_ON_LAUNCH_DELAY_MILLIS
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.data.api.network.diagnostics.LatencySample
import com.kino.puber.domain.interactor.diagnostics.ServerTestState
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import com.kino.puber.ui.feature.device.diagnostics.model.ServerSpeedUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

private val ScreenHorizontalPadding = 48.dp
private val ScreenVerticalPadding = 28.dp
private val ResultCardShape = RoundedCornerShape(12.dp)
private val GaugeSize = 300.dp
private val StatusAreaHeight = 64.dp
private const val BITS_PER_MEGABIT = 1_000_000.0
private const val GAUGE_MAX_MBIT = 100f
private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP_ANGLE = 240f
private const val GAUGE_WEIGHT = 1.2f
private const val RESULTS_WEIGHT = 0.8f
private const val GAUGE_TICK_COUNT = 41
private const val GAUGE_TICK_DIVISOR = 40f
private const val GAUGE_MAJOR_TICK_INTERVAL = 10
private const val HD_MIN_MBIT = 5f
private const val FULL_HD_MIN_MBIT = 10f
private const val UHD_MIN_MBIT = 25f
private val GaugeRed = Color(0xFFE5534B)
private val GaugeYellow = Color(0xFFF2C94C)
private val GaugeGreen = Color(0xFF43A66F)

@Composable
private fun rememberSpeedTestFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    val drawerState = LocalDrawerState.current
    val autoFocusEnabled = LocalAutoFocusOnLaunchEnabled.current
    var initialClaimPending by remember(drawerState) {
        mutableStateOf(drawerState?.currentValue != DrawerValue.Open)
    }

    LaunchedEffect(focus, autoFocusEnabled) {
        if (!autoFocusEnabled) return@LaunchedEffect
        if (!initialClaimPending && drawerState?.currentValue == DrawerValue.Open) {
            snapshotFlow { drawerState.currentValue }.first { it != DrawerValue.Open }
        }
        delay(FOCUS_ON_LAUNCH_DELAY_MILLIS)
        focus.requestFocus()
        initialClaimPending = false
    }
    return focus
}

@Composable
internal fun NetworkDiagnosticsContent(
    state: NetworkDiagnosticsViewState,
    onAction: (UIAction) -> Unit = {},
) {
    val initialFocusRequester = rememberSpeedTestFocus()
    val bestServer = state.bestServer()
    val runningRow = state.servers.firstOrNull { it.state is ServerTestState.Running }
    val dialRow = runningRow
        ?: bestServer?.let { server -> state.servers.firstOrNull { it.server == server } }
        ?: state.currentServer?.let { server -> state.servers.firstOrNull { it.server == server } }
    val dialSample = dialRow?.state?.sampleOrNull()

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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.diagnostics_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerResults(
                    state = state,
                    bestServer = bestServer,
                    initialFocusRequester = initialFocusRequester,
                    onServerSelected = { server ->
                        onAction(NetworkDiagnosticsActions.Start(server))
                    },
                    modifier = Modifier
                        .weight(RESULTS_WEIGHT)
                        .fillMaxHeight(),
                )
                Speedometer(
                    sample = dialSample,
                    server = dialRow?.server,
                    running = runningRow != null,
                    modifier = Modifier
                        .weight(GAUGE_WEIGHT)
                        .fillMaxHeight(),
                )
            }

            Column(modifier = Modifier.height(StatusAreaHeight)) {
                if (state.finished) {
                    Text(
                        text = resultSummary(state),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(NetworkDiagnosticsTestTags.Summary),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

            }
        }
    }
}

@Composable
private fun Speedometer(
    sample: ThroughputSample?,
    server: SpeedTestServer?,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val speed = ((sample?.bitsPerSecond ?: 0.0) / BITS_PER_MEGABIT).toFloat()
    val animatedValue by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 120f),
        label = "speedometer-result",
    )
    val animatedNeedle by animateFloatAsState(
        targetValue = if (running) speed else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 120f),
        label = "speedometer-needle",
    )
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val qualityLabels = listOf(
        stringResource(R.string.diagnostics_quality_sd),
        stringResource(R.string.diagnostics_quality_hd),
        stringResource(R.string.diagnostics_quality_full_hd),
        stringResource(R.string.diagnostics_quality_4k),
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(GaugeSize), contentAlignment = Alignment.Center) {
            GaugeCanvas(
                speedMbit = animatedNeedle,
                qualityLabels = qualityLabels,
                primary = primary,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.padding(top = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = server?.title()
                        ?: stringResource(R.string.diagnostics_gauge_ready),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (running) primary else onSurfaceVariant,
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", animatedValue),
                    style = TextStyle(
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = stringResource(R.string.diagnostics_rate_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                )
                if (sample != null) {
                    Text(
                        text = qualityForSpeed(animatedValue),
                        style = MaterialTheme.typography.titleMedium,
                        color = primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GaugeCanvas(
    speedMbit: Float,
    qualityLabels: List<String>,
    primary: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    val needleFraction = speedFraction(speedMbit)

    Box(
        modifier = modifier.drawWithCache {
            val center = Offset(size.width / 2f, size.height * 0.62f)
            val radius = min(size.width * 0.39f, size.height * 0.48f)
            val arcRect = Rect(center = center, radius = radius)
            val trackWidth = 10.dp.toPx()
            val zoneSpeeds = listOf(0f, HD_MIN_MBIT, FULL_HD_MIN_MBIT, UHD_MIN_MBIT, GAUGE_MAX_MBIT)
            val zoneColors = listOf(
                GaugeRed,
                GaugeYellow,
                GaugeGreen.copy(alpha = 0.72f),
                GaugeGreen,
            )
            val measuredLabels = qualityLabels.map { textMeasurer.measure(it, labelStyle) }

            onDrawBehind {
                drawArc(
                    color = onSurfaceVariant.copy(alpha = 0.14f),
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = GAUGE_SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(width = trackWidth, cap = StrokeCap.Round),
                )

                zoneColors.forEachIndexed { index, color ->
                    val start = speedFraction(zoneSpeeds[index])
                    val end = speedFraction(zoneSpeeds[index + 1])
                    val gap = 1.5f
                    drawArc(
                        color = color,
                        startAngle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * start + gap,
                        sweepAngle = GAUGE_SWEEP_ANGLE * (end - start) - gap * 2f,
                        useCenter = false,
                        topLeft = arcRect.topLeft,
                        size = arcRect.size,
                        style = Stroke(width = trackWidth, cap = StrokeCap.Butt),
                    )

                    val middle = (start + end) / 2f
                    val angle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * middle
                    val position = pointOnCircle(center, radius - 28.dp.toPx(), angle)
                    val label = measuredLabels[index]
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            position.x - label.size.width / 2f,
                            position.y - label.size.height / 2f,
                        ),
                    )
                }

                repeat(GAUGE_TICK_COUNT) { tick ->
                    val fraction = tick / GAUGE_TICK_DIVISOR
                    val angle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * fraction
                    val major = tick % GAUGE_MAJOR_TICK_INTERVAL == 0
                    drawLine(
                        color = if (major) onSurface else onSurfaceVariant.copy(alpha = 0.55f),
                        start = pointOnCircle(center, radius + 8.dp.toPx(), angle),
                        end = pointOnCircle(
                            center,
                            radius + if (major) 20.dp.toPx() else 14.dp.toPx(),
                            angle,
                        ),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                val needleAngle = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * needleFraction
                rotate(degrees = needleAngle + 90f, pivot = center) {
                    val needle = Path().apply {
                        moveTo(center.x, center.y - radius + 6.dp.toPx())
                        lineTo(center.x - 5.dp.toPx(), center.y + 12.dp.toPx())
                        lineTo(center.x + 5.dp.toPx(), center.y + 12.dp.toPx())
                        close()
                    }
                    drawPath(needle, primary)
                }
                drawCircle(onSurface, radius = 8.dp.toPx(), center = center)
                drawCircle(primary, radius = 4.dp.toPx(), center = center)
            }
        }
    )
}

@Composable
private fun ServerResults(
    state: NetworkDiagnosticsViewState,
    bestServer: SpeedTestServer?,
    initialFocusRequester: FocusRequester,
    onServerSelected: (SpeedTestServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(NetworkDiagnosticsTestTags.Steps),
        verticalArrangement = Arrangement.Center,
    ) {
        state.servers.forEachIndexed { index, row ->
            val initialServer = state.currentServer ?: state.servers.firstOrNull()?.server
            ServerResultCard(
                row = row,
                current = row.server == state.currentServer,
                best = state.finished && row.server == bestServer,
                enabled = !state.running,
                onClick = { onServerSelected(row.server) },
                modifier = if (row.server == initialServer) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                },
            )
            if (index != state.servers.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ServerResultCard(
    row: ServerSpeedUi,
    current: Boolean,
    best: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (current) 0.12f else 0.07f)
    var focused by remember(row.server) { mutableStateOf(false) }
    val contentColor = if (focused) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .testTag(NetworkDiagnosticsTestTags.server(row.server.name))
            .semantics {
                selected = current
                if (!enabled) disabled()
            },
        shape = ClickableSurfaceDefaults.shape(shape = ResultCardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = cardColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            pressedContentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        val stateColor = if (row.state == ServerTestState.Failure) {
            MaterialTheme.colorScheme.error
        } else {
            contentColor
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.server.title(),
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
                if (current) {
                    Text(
                        text = stringResource(R.string.diagnostics_current_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                } else if (best) {
                    Text(
                        text = stringResource(R.string.diagnostics_best_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stateText(row.state),
                style = MaterialTheme.typography.bodyLarge,
                color = stateColor,
            )
            row.state.latencyOrNull()?.let { latency ->
                Spacer(Modifier.height(6.dp))
                LatencyResult(latency, contentColor)
            }
            if (row.state is ServerTestState.Running) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
    }
}

@Composable
private fun LatencyResult(latency: LatencySample, contentColor: Color) {
    val secondaryContentColor = contentColor.copy(alpha = 0.72f)
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(
                R.string.diagnostics_ping_value,
                latency.pingMillis.toString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryContentColor,
        )
        Text(
            text = stringResource(
                R.string.diagnostics_jitter_value,
                latency.jitterMillis.toString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryContentColor,
        )
    }
}

private fun ServerTestState.sampleOrNull(): ThroughputSample? = when (this) {
    is ServerTestState.Running -> sample
    is ServerTestState.Success -> sample
    else -> null
}

private fun ServerTestState.latencyOrNull(): LatencySample? = when (this) {
    is ServerTestState.Running -> latency
    is ServerTestState.Success -> latency
    else -> null
}

private fun NetworkDiagnosticsViewState.bestServer(): SpeedTestServer? = servers
    .mapNotNull { row -> row.state.sampleOrNull()?.let { row.server to it.bitsPerSecond } }
    .maxByOrNull { it.second }
    ?.first

private fun speedFraction(speedMbit: Float): Float =
    (ln(1.0 + speedMbit.coerceAtLeast(0f)) / ln(1.0 + GAUGE_MAX_MBIT))
        .toFloat()
        .coerceIn(0f, 1f)

private fun pointOnCircle(center: Offset, radius: Float, angleDegrees: Float): Offset {
    val radians = angleDegrees * PI.toFloat() / 180f
    return Offset(
        x = center.x + cos(radians) * radius,
        y = center.y + sin(radians) * radius,
    )
}

@Composable
private fun stateText(state: ServerTestState): String = when (state) {
    ServerTestState.Pending -> stringResource(R.string.diagnostics_state_pending)
    is ServerTestState.Running -> state.sample?.rateText()
        ?: stringResource(R.string.diagnostics_state_running)
    is ServerTestState.Success -> state.sample.rateText()
    ServerTestState.Failure -> stringResource(R.string.diagnostics_failure_request)
    ServerTestState.Cancelled -> stringResource(R.string.diagnostics_skipped_cancelled)
}

@Composable
private fun ThroughputSample.rateText(): String = stringResource(
    R.string.diagnostics_rate_mbits,
    String.format(Locale.getDefault(), "%.1f", bitsPerSecond / BITS_PER_MEGABIT),
)

@Composable
private fun qualityForSpeed(speedMbit: Float): String = stringResource(
    when {
        speedMbit >= UHD_MIN_MBIT -> R.string.diagnostics_quality_4k
        speedMbit >= FULL_HD_MIN_MBIT -> R.string.diagnostics_quality_full_hd
        speedMbit >= HD_MIN_MBIT -> R.string.diagnostics_quality_hd
        else -> R.string.diagnostics_quality_sd
    }
)

@Composable
private fun SpeedTestServer.title(): String = stringResource(
    when (this) {
        SpeedTestServer.Amsterdam -> R.string.diagnostics_server_amsterdam
        SpeedTestServer.Moscow -> R.string.diagnostics_server_moscow
    }
)


@Composable
private fun resultSummary(state: NetworkDiagnosticsViewState): String {
    val successful = state.servers.count { it.state is ServerTestState.Success }
    return when {
        successful == 0 -> stringResource(R.string.diagnostics_summary_all_failed)
        successful == 1 -> stringResource(R.string.diagnostics_summary_partial)
        state.currentServer == null -> stringResource(R.string.diagnostics_summary_complete)
        state.recommendedServer == null -> stringResource(R.string.diagnostics_summary_current_fastest)
        else -> stringResource(
            R.string.diagnostics_summary_faster_server,
            state.recommendedServer.title(),
        )
    }
}
