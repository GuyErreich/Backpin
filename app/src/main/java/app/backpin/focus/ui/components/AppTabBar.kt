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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
                    icon = Icons.Outlined.Email,
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
                    icon = Icons.Outlined.Star,
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
                        .background(FocusColors.Live.Mint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.toString(),
                        style = TabBadgeStyle,
                        color = FocusColors.Live.OnMint,
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
