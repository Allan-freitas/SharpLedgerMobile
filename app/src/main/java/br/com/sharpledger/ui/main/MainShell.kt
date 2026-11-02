package br.com.sharpledger.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.data.Permissions
import br.com.sharpledger.data.UserProfile
import br.com.sharpledger.ui.categories.CategoriesScreen
import br.com.sharpledger.ui.dashboard.DashboardScreen
import br.com.sharpledger.ui.investments.InvestmentsScreen
import br.com.sharpledger.ui.more.MoreScreen
import br.com.sharpledger.ui.transactions.TransactionsScreen

enum class MainTab(val permission: String?) {
    DASHBOARD(Permissions.DASHBOARD_VIEW),
    TRANSACTIONS(Permissions.TRANSACTIONS_MANAGE),
    CATEGORIES(Permissions.CATEGORIES_MANAGE),
    INVESTMENTS(Permissions.INVESTMENTS_MANAGE),
    MORE(null),
}

private data class TabInfo(
    val tab: MainTab,
    val labelRes: Int,
    val shortLabelRes: Int,
    val icon: ImageVector,
)

@Composable
fun MainShell(container: AppContainer, user: UserProfile) {
    val session = container.session
    val permissions = user.permissions

    val tabs = buildList {
        add(
            TabInfo(
                MainTab.DASHBOARD,
                R.string.tab_dashboard,
                R.string.tab_dashboard,
                Icons.Default.Dashboard,
            ),
        )
        if (permissions.contains(Permissions.TRANSACTIONS_MANAGE)) {
            add(
                TabInfo(
                    MainTab.TRANSACTIONS,
                    R.string.tab_transactions,
                    R.string.tab_transactions_short,
                    Icons.Default.ReceiptLong,
                ),
            )
        }
        if (permissions.contains(Permissions.CATEGORIES_MANAGE)) {
            add(
                TabInfo(
                    MainTab.CATEGORIES,
                    R.string.tab_categories,
                    R.string.tab_categories,
                    Icons.Default.Category,
                ),
            )
        }
        if (permissions.contains(Permissions.INVESTMENTS_MANAGE)) {
            add(
                TabInfo(
                    MainTab.INVESTMENTS,
                    R.string.tab_investments,
                    R.string.tab_investments_short,
                    Icons.Default.ShowChart,
                ),
            )
        }
        add(
            TabInfo(
                MainTab.MORE,
                R.string.tab_more,
                R.string.tab_more,
                Icons.Default.MoreHoriz,
            ),
        )
    }.filter { it.tab.permission == null || permissions.contains(it.tab.permission) }

    var selected by rememberSaveable { mutableStateOf(MainTab.DASHBOARD.name) }

    LaunchedEffect(tabs) {
        if (tabs.none { it.tab.name == selected }) {
            selected = tabs.first().tab.name
        }
    }

    val selectedTab = MainTab.valueOf(selected)

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { info ->
                    val isSelected = selectedTab == info.tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selected = info.tab.name },
                        alwaysShowLabel = isSelected,
                        icon = {
                            Icon(
                                info.icon,
                                contentDescription = stringResource(info.labelRes),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(info.shortLabelRes),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        val contentPadding = if (selectedTab == MainTab.DASHBOARD) {
            PaddingValues(bottom = padding.calculateBottomPadding())
        } else {
            padding
        }

        Box(modifier = Modifier.padding(contentPadding)) {
            when (selectedTab) {
                MainTab.DASHBOARD -> DashboardScreen(
                    container = container,
                    user = user,
                )
                MainTab.TRANSACTIONS -> TransactionsScreen(
                    container = container,
                    user = user,
                )
                MainTab.CATEGORIES -> CategoriesScreen(container = container)
                MainTab.INVESTMENTS -> InvestmentsScreen(container = container)
                MainTab.MORE -> MoreScreen(
                    container = container,
                    user = user,
                    onLoggedOut = { session.logout() },
                )
            }
        }
    }
}
