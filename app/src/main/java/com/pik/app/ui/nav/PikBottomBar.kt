package com.pik.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.ui.theme.PikColors

private data class TabItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabsLeft = listOf(
    TabItem(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
    TabItem(Routes.ACTIVITY, R.string.tab_activity, Icons.AutoMirrored.Filled.List),
)
private val tabsRight = listOf(
    TabItem(Routes.PLAN, R.string.tab_plan, Icons.Filled.CalendarMonth),
    TabItem(Routes.DEBTS, R.string.tab_debts, Icons.Filled.AccountBalanceWallet),
)

/** Floating pill bar with 4 labelled tabs and a raised lime "+" button in the centre. */
@Composable
fun PikBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = PikColors.Surface,
            contentColor = PikColors.TextPrimary,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                tabsLeft.forEach { tab ->
                    TabButton(tab, tab.route == currentRoute, onTabSelected, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.weight(1f))
                tabsRight.forEach { tab ->
                    TabButton(tab, tab.route == currentRoute, onTabSelected, Modifier.weight(1f))
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(PikColors.Lime)
                .clickable(role = Role.Button, onClick = onAddClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_transaction),
                tint = PikColors.OnLime,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun TabButton(
    tab: TabItem,
    selected: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) PikColors.Lime else PikColors.TextSecondary
    val label = stringResource(tab.labelRes)
    Column(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Tab) { onSelected(tab.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
