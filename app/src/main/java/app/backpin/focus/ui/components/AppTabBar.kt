// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.R
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.FocusColors

internal enum class AppTab {
    HOME,
    INBOX,
    COMING_UP,
    FOCUS,
    SETTINGS,
}

/**
 * Five-tab bar: Home, Inbox, Coming up, Focus, Settings.
 * The selected tab keeps a pill behind the outline icon and a bold label.
 */
@Composable
internal fun AppTabBar(
    selected: AppTab,
    inboxCount: Int,
    comingUpCount: Int,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = FocusColors.Live.Surface,
    ) {
        Column {
            HorizontalDivider(color = FocusColors.Live.Outline)
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TabBarHeight)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTabItem(
                    label = stringResource(R.string.tab_home),
                    icon = Icons.Outlined.Home,
                    selected = selected == AppTab.HOME,
                    onClick = { onSelect(AppTab.HOME) },
                )
                AppTabItem(
                    label = stringResource(R.string.tab_inbox),
                    icon = InboxTrayIcon,
                    selected = selected == AppTab.INBOX,
                    badge = inboxCount,
                    onClick = { onSelect(AppTab.INBOX) },
                )
                AppTabItem(
                    label = stringResource(R.string.tab_coming_up),
                    icon = Icons.Outlined.DateRange,
                    selected = selected == AppTab.COMING_UP,
                    badge = comingUpCount,
                    onClick = { onSelect(AppTab.COMING_UP) },
                )
                AppTabItem(
                    label = stringResource(R.string.tab_focus),
                    icon = TargetRingIcon,
                    selected = selected == AppTab.FOCUS,
                    onClick = { onSelect(AppTab.FOCUS) },
                )
                AppTabItem(
                    label = stringResource(R.string.tab_settings),
                    icon = Icons.Outlined.Settings,
                    selected = selected == AppTab.SETTINGS,
                    onClick = { onSelect(AppTab.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun AppTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    val interaction = rememberPressSource()
    val color = if (selected) FocusColors.Live.Text else FocusColors.Live.Muted
    Column(
        modifier =
        Modifier
            .pressScale(interaction)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    modifier =
                    Modifier
                        .size(TabIconPillWidth, TabIconPillHeight)
                        .background(FocusColors.Live.SurfaceHigh, RoundedCornerShape(14.dp)),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            if (badge > 0) {
                Box(
                    modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .size(TabBadgeSize)
                        .background(FocusColors.Live.Muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.toString(),
                        style = TabBadgeStyle,
                        color = FocusColors.Live.Text,
                    )
                }
            }
        }
        Text(
            text = label,
            style = if (selected) TabLabelSelected else TabLabel,
            color = color,
            maxLines = 1,
        )
    }
}

private fun materialIcon(
    name: String,
    pathData: String,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()

/** Material Inbox outlined — tray, not the envelope Email icon. */
private val InboxTrayIcon =
    materialIcon(
        name = "InboxTray",
        pathData =
        "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5v-3h3.56c.69 1.19 1.97 2 3.45 2s2.75-.81 3.45-2H19v3zm0-5h-4.99c0 1.1-.9 2-2 2s-2-.9-2-2H5V5h14v9z",
    )

/** Material GpsFixed — target ring for Focus. */
private val TargetRingIcon =
    materialIcon(
        name = "TargetRing",
        pathData =
        "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
    )

private val TabBarHeight = 64.dp
private val TabIconPillWidth = 48.dp
private val TabIconPillHeight = 28.dp
private val TabBadgeSize = 16.dp
private val TabLabel =
    TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 13.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
private val TabLabelSelected =
    TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 13.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
private val TabBadgeStyle =
    TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 10.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
