// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.ui.theme.ButtonMinHeight

/**
 * Home v2 geometry from the design-v2 redline (412 dp, 1 px = 1 dp).
 * Touch targets stay at least [HomeTouchTarget].
 */
internal object HomeMetrics {
    val PageHorizontal = 20.dp
    val PageVertical = 8.dp
    val EyebrowTop = 4.dp
    val EyebrowBottom = 8.dp
    val TitleBottom = 12.dp

    val NowRadius = 22.dp
    val NowBorder = 2.dp
    val NowPaddingVertical = 14.dp
    val NowPaddingHorizontal = 20.dp
    val NowLabelBottom = 4.dp
    val NowTitleBottom = 2.dp

    val FlatHeaderTop = 14.dp

    val SlotRadius = 20.dp
    val SlotPaddingVertical = 13.dp
    val SlotPaddingHorizontal = 20.dp
    val SlotGap = 8.dp

    val TaskRadius = 18.dp
    val TaskPaddingVertical = 12.dp
    val TaskPaddingHorizontal = 18.dp
    val TaskGap = 7.dp

    val RowVisualHeight = 43.dp
    val RepeatIconSize = 20.dp
    val RepeatGlyphGap = 6.dp
    val RepeatTagGap = 8.dp

    val CountsTop = 6.dp
    val HomeTouchTarget = 48.dp
    val SnackbarHeight = 48.dp

    /** Scroll padding so the last row and footer clear the FAB, a snackbar, and 16dp. */
    val ListBottom = ButtonMinHeight + SnackbarHeight + 16.dp
    val TabBarHeight = 64.dp

    val Eyebrow =
        TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.5.sp,
            lineHeight = 14.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val ScreenTitle =
        TextStyle(
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            lineHeight = 35.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val NowLabel =
        TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            lineHeight = 14.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val NowTitle =
        TextStyle(
            fontSize = 21.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 25.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val NowTime =
        TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 16.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val SlotTitle =
        TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 22.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val TaskTitle =
        TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val SlotChevron =
        TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 16.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val RowTag =
        TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.2.sp,
            lineHeight = 14.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val RepeatTitle =
        TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val RepeatCadence =
        TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 16.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
}

internal fun HomeLayout.taskRadius() = if (this == HomeLayout.BANDS) HomeMetrics.TaskRadius else HomeMetrics.SlotRadius

internal fun HomeLayout.taskPaddingVertical() = if (this == HomeLayout.BANDS) HomeMetrics.TaskPaddingVertical else HomeMetrics.SlotPaddingVertical

internal fun HomeLayout.taskPaddingHorizontal() = if (this == HomeLayout.BANDS) HomeMetrics.TaskPaddingHorizontal else HomeMetrics.SlotPaddingHorizontal

internal fun HomeLayout.taskGap() = if (this == HomeLayout.BANDS) HomeMetrics.TaskGap else HomeMetrics.SlotGap

internal fun HomeLayout.taskTitleStyle() = if (this == HomeLayout.BANDS) HomeMetrics.TaskTitle else HomeMetrics.SlotTitle
