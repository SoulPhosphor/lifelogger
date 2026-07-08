package com.datadragon.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datadragon.app.ui.screens.ChecklistScreen
import com.datadragon.app.ui.screens.CreateLogScreen
import com.datadragon.app.ui.screens.EditFormScreen
import com.datadragon.app.ui.screens.FollowUpNoteScreen
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
                onCreateForm = { navController.navigate(Routes.CREATE_LOG) },
                onOpenLog = { logId -> navController.navigate(Routes.log(logId.toString())) },
                onAddEntry = { logId -> navController.navigate(Routes.newEntry(logId.toString())) },
                onCreateChecklist = { navController.navigate(Routes.CREATE_CHECKLIST) },
                onOpenChecklist = { checklistId -> navController.navigate(Routes.checklist(checklistId)) },
            )
        }

        composable(Routes.CREATE_LOG) {
            CreateLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
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
                onEditForm = { navController.navigate(Routes.editForm(logId.orEmpty())) },
                onOpenFollowUp = { entryId, noteId ->
                    navController.navigate(Routes.followUp(logId.orEmpty(), entryId, noteId))
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

        composable(
            route = Routes.EDIT_FORM,
            arguments = listOf(navArgument(Routes.LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val logId = backStackEntry.arguments?.getString(Routes.LOG_ARG)
            EditFormScreen(
                logId = logId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_CHECKLIST) {
            // A brand-new list (no id yet) — it's a draft until Save.
            ChecklistScreen(
                checklistId = null,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CHECKLIST,
            arguments = listOf(navArgument(Routes.CHECKLIST_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            ChecklistScreen(
                checklistId = backStackEntry.arguments?.getString(Routes.CHECKLIST_ARG),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.FOLLOW_UP,
            arguments = listOf(
                navArgument(Routes.LOG_ARG) { type = NavType.StringType },
                navArgument(Routes.ENTRY_ARG) { type = NavType.StringType },
                navArgument(Routes.NOTE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            FollowUpNoteScreen(
                logId = backStackEntry.arguments?.getString(Routes.LOG_ARG),
                entryId = backStackEntry.arguments?.getString(Routes.ENTRY_ARG),
                noteId = backStackEntry.arguments?.getString(Routes.NOTE_ARG),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
