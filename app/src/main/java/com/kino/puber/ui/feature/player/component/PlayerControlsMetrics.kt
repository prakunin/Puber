package com.kino.puber.ui.feature.player.component

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The geometry of the control overlay, dialled on the full-size 1920 × 1080 stand against frames
 * taken off the television and agreed on 2026-08-26.
 *
 * The title, the seek bar and the button row all measure from here rather than each carrying its
 * own copy: they share edges, and a value that drifts in one of the three shows up as a crooked
 * overlay. Values are dp/sp; the television runs at density 2.0, so 1 dp is 2 px there.
 */
internal object PlayerControlsMetrics {

    /** Deliberately inside Google's 48 dp TV safe zone — the same edge the content screen uses. */
    val SideMargin = 20.dp
    val TopMargin = 10.dp
    val BottomMargin = 10.dp

    /**
     * From the centre of the seek track down to the top of the button row. The button row's own
     * top padding is derived from this and [ProgressRowHeight], so the relation survives a change
     * to either.
     */
    val TrackCentreToButtons = 20.dp

    val ButtonWidth = 30.dp
    val ButtonHeight = 25.dp
    val ButtonGap = 5.dp
    val ButtonIcon = 15.dp
    val PlayPauseIcon = 20.dp
    val ButtonProgressStroke = 1.5.dp

    const val ButtonContainerAlpha = 0.03f
    const val ButtonContentAlpha = 0.90f
    const val ButtonFocusedContainerAlpha = 0.20f
    const val ButtonPressedContainerAlpha = 0.16f
    const val ButtonSelectedContainerAlpha = 0.12f

    /**
     * A floor, not a cap. [TrackCentreToButtons] is stated from the centre of the track, so the
     * row must not shrink or grow with whatever the clock happens to read; at the television's
     * font scale the labels fit inside this exactly. Scale the system font up and the row grows
     * rather than clipping them, and the gap above the buttons grows with it.
     */
    val ProgressRowHeight = 14.dp
    val TrackHeight = 3.dp
    val TrackCornerRadius = 1.5.dp
    val ThumbSize = 10.dp
    val TimeToTrackGap = 5.dp
    val TimeTextSize = 10.sp
    val TimeLineHeight = 13.sp

    val TitleTextSize = 20.sp
    val TitleLineHeight = 24.sp
    val SubtitleTextSize = 10.sp
    val SubtitleLineHeight = 13.sp
    val TitleToSubtitleGap = 4.dp

    /** The gap the button row leaves above itself so the track centre lands where it should. */
    val ButtonRowTopPadding = TrackCentreToButtons - ProgressRowHeight / 2
}
