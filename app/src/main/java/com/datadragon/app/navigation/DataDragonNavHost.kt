package com.datadragon.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datadragon.app.ui.screens.BackupScreen
import com.datadragon.app.ui.screens.CreateLogScreen
import com.datadragon.app.ui.screens.HomeScreen
import com.datadragon.app.ui.screens.LogScreen
import com.datadragon.app.ui.screens.NewEntryScreen
import com.datadragon.app.ui.screens.SettingsScreen

@Composable
fun DataDragonNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onCreateLog = { navController.navigate(Routes.CREATE_LOG) },
                onOpenLog = { logId -> navController.navigate(Routes.log(logId.toString())) },
                onAddEntry = { logId -> navController.navigate(Routes.newEntry(logId.toString())) },
            )
        }

        composable(Routes.CREATE_LOG) {
            CreateLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.LOG,
            arguments = listOf(navArgument(Routes.LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val logId = backStackEntry.arguments?.getString(Routes.LOG_ARG)
            LogScreen(
                logId = logId,
                onBack = { navController.popBackStack() },
                onAddEntry = { navController.navigate(Routes.newEntry(logId.orEmpty())) },
                onEditEntry = { entryId ->
                    navController.navigate(Routes.editEntry(logId.orEmpty(), entryId))
                },
            )
        }

        composable(
            route = Routes.NEW_ENTRY,
            arguments = listOf(navArgument(Routes.LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val logId = backStackEntry.arguments?.getString(Routes.LOG_ARG)
            NewEntryScreen(
                logId = logId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDIT_ENTRY,
            arguments = listOf(
                navArgument(Routes.LOG_ARG) { type = NavType.StringType },
                navArgument(Routes.ENTRY_ARG) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val logId = backStackEntry.arguments?.getString(Routes.LOG_ARG)
            val entryId = backStackEntry.arguments?.getString(Routes.ENTRY_ARG)
            NewEntryScreen(
                logId = logId,
                entryId = entryId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
