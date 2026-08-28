package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.kino.puber.ui.feature.player.vm.PlaybackControl

@Composable
internal fun DebugOverlay(
    debugInfo: PlaybackControl.DebugInfo?,
    modifier: Modifier = Modifier,
) {
    if (debugInfo == null) return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        DebugLine("Source", debugInfo.streamSource)
        DebugLine("Video", "${debugInfo.videoResolution}  ${debugInfo.videoCodec}  ${debugInfo.videoBitrate}")
        DebugLine("Audio", "${debugInfo.audioCodec}  ${debugInfo.audioChannels}")
        DebugLine("Buffer", "${debugInfo.bufferedDuration}  ${debugInfo.bufferedBytes}")
        DebugLine("Render", "${debugInfo.renderRate}  offset ${debugInfo.frameReleaseOffset}")
        DebugLine("Dropped", debugInfo.frameDrops)
        DebugLine("Switch", debugInfo.videoSwitch)
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        fontSize = 10.sp,
        color = Color.White.copy(alpha = 0.85f),
        lineHeight = 14.sp,
    )
}
