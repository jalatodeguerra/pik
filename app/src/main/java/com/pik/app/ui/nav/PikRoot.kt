package com.pik.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pik.app.MainViewModel
import com.pik.app.ui.screens.activity.ActivityScreen
import com.pik.app.ui.screens.add.AddTransactionScreen
import com.pik.app.ui.screens.firstrun.FirstRunScreen
import com.pik.app.ui.screens.home.HomeScreen
import com.pik.app.ui.screens.placeholder.DebtsScreen
import com.pik.app.ui.screens.placeholder.PlanScreen
import com.pik.app.ui.screens.settings.CategoriesScreen
import com.pik.app.ui.screens.settings.SettingsScreen
import com.pik.app.ui.theme.PikColors

@Composable
fun PikRoot(quickAddToken: Int = 0, mainViewModel: MainViewModel = hiltViewModel()) {
    val start by mainViewModel.startDestination.collectAsStateWithLifecycle()
    val startDestination = start
    if (startDestination == null) {
        Box(Modifier.fillMaxSize().background(PikColors.Background))
        return
    }
    PikNavigation(startDestination, quickAddToken)
}

@Composable
private fun PikNavigation(startDestination: String, quickAddToken: Int) {
    val navController = rememberNavController()

    // App shortcut "Add expense": open the Add screen, but only once onboarding is complete.
    LaunchedEffect(quickAddToken) {
        if (quickAddToken > 0 && startDestination != Routes.FIRST_RUN) {
            navController.navigate(Routes.add()) { launchSingleTop = true }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = PikColors.Background,
        contentColor = PikColors.TextPrimary,
        bottomBar = {
            if (currentRoute in Routes.tabs) {
                PikBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { route -> navController.navigateToTab(route) },
                    onAddClick = { navController.navigate(Routes.add()) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.FIRST_RUN) {
                FirstRunScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onAddClick = { navController.navigate(Routes.add()) },
                    onSeeAll = { navController.navigateToTab(Routes.ACTIVITY) },
                    onTransactionClick = { id -> navController.navigate(Routes.edit(id)) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.edit(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAN) { PlanScreen() }
            composable(Routes.DEBTS) { DebtsScreen() }
            composable(
                route = Routes.ADD,
                arguments = listOf(
                    navArgument(Routes.ARG_TRANSACTION_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                AddTransactionScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
