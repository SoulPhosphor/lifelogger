package com.datadragon.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datadragon.app.ui.screens.CalendarConfigScreen
import com.datadragon.app.ui.screens.CalendarViewScreen
import com.datadragon.app.ui.screens.ChecklistScreen
import com.datadragon.app.ui.screens.CreateIdeaLogScreen
import com.datadragon.app.ui.screens.CreateLogScreen
import com.datadragon.app.ui.screens.DailyListEditorScreen
import com.datadragon.app.ui.screens.EditIdeaLogScreen
import com.datadragon.app.ui.screens.EditFormScreen
import com.datadragon.app.ui.screens.FollowUpNoteScreen
import com.datadragon.app.ui.screens.HomeScreen
import com.datadragon.app.ui.screens.IdeaDetailScreen
import com.datadragon.app.ui.screens.IdeaLogScreen
import com.datadragon.app.ui.screens.LogScreen
import com.datadragon.app.ui.screens.NewEntryScreen
import com.datadragon.app.ui.screens.NewIdeaScreen
import com.datadragon.app.ui.screens.SettingsScreen
import com.datadragon.app.ui.DailyListViewModel
import com.datadragon.app.ui.HomeViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@Composable
fun DataDragonNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val dailyListViewModel: DailyListViewModel = viewModel()
            val homeViewModel: HomeViewModel = viewModel()

            // "Automatically show current daily list when app is started." — only
            // when Daily List was the remembered mode. Today's existing card
            // opens directly; an unsaved today just opens the Daily List main
            // view. Opening never creates a blank card.
            val autoReopen = homeViewModel.dailyListAutoReopen
            var startupHandled by rememberSaveable { mutableStateOf(!autoReopen) }
            LaunchedEffect(startupHandled) {
                if (!startupHandled && autoReopen) {
                    startupHandled = true
                    navController.navigate(Routes.dailyListEditor(LocalDate.now().toString()))
                }
            }

            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onCreateForm = { navController.navigate(Routes.CREATE_LOG) },
                onOpenLog = { logId -> navController.navigate(Routes.log(logId.toString())) },
                onAddEntry = { logId -> navController.navigate(Routes.newEntry(logId.toString())) },
                onCreateChecklist = { navController.navigate(Routes.CREATE_CHECKLIST) },
                onOpenChecklist = { checklistId -> navController.navigate(Routes.checklist(checklistId)) },
                onCreateIdeaLog = { navController.navigate(Routes.CREATE_IDEA_LOG) },
                onOpenIdeaLog = { ideaLogId -> navController.navigate(Routes.ideaLog(ideaLogId)) },
                onAddIdea = { ideaLogId -> navController.navigate(Routes.newIdea(ideaLogId)) },
                onDailyListToday = {
                    navController.navigate(Routes.dailyListEditor(LocalDate.now().toString()))
                },
                onDailyListPickDate = {
                    // Handled inside HomeScreen's date-picker dialog.
                    dailyListViewModel.requestDatePicker()
                },
                onDailyListDateConfirmed = { picked ->
                    navController.navigate(Routes.dailyListEditor(picked.toString()))
                },
                onOpenDailyListCard = { cardId ->
                    val card = dailyListViewModel.cards.value.firstOrNull { it.id == cardId }
                    navController.navigate(
                        Routes.dailyListEditor(card?.date?.toString() ?: LocalDate.now().toString()),
                    )
                },
                dailyListViewModel = dailyListViewModel,
                viewModel = homeViewModel,
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
                onOpenCalendar = { navController.navigate(Routes.calendarView(logId.orEmpty())) },
                onOpenFollowUp = { entryId, noteId ->
                    navController.navigate(Routes.followUp(logId.orEmpty(), entryId, noteId))
                },
            )
        }

        composable(
            route = Routes.CALENDAR_VIEW,
            arguments = listOf(navArgument(Routes.LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            CalendarViewScreen(
                logId = backStackEntry.arguments?.getString(Routes.LOG_ARG),
                onBack = { navController.popBackStack() },
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
                onEditCalendar = { navController.navigate(Routes.calendarConfig(logId.orEmpty())) },
                onOpenCalendar = { calendarId ->
                    navController.navigate(Routes.calendarConfig(logId.orEmpty(), calendarId))
                },
            )
        }

        composable(
            route = Routes.CALENDAR_CONFIG,
            arguments = listOf(
                navArgument(Routes.LOG_ARG) { type = NavType.StringType },
                navArgument(Routes.CALENDAR_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            CalendarConfigScreen(
                logId = backStackEntry.arguments?.getString(Routes.LOG_ARG),
                calendarId = backStackEntry.arguments?.getString(Routes.CALENDAR_ARG),
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
            route = Routes.DAILY_LIST_EDITOR,
            arguments = listOf(navArgument(Routes.DAILY_LIST_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            DailyListEditorScreen(
                date = backStackEntry.arguments?.getString(Routes.DAILY_LIST_ARG),
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

        composable(Routes.CREATE_IDEA_LOG) {
            CreateIdeaLogScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.IDEA_LOG,
            arguments = listOf(navArgument(Routes.IDEA_LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val ideaLogId = backStackEntry.arguments?.getString(Routes.IDEA_LOG_ARG)
            val id = ideaLogId?.toLongOrNull()
            IdeaLogScreen(
                ideaLogId = ideaLogId,
                onBack = { navController.popBackStack() },
                onAddIdea = { id?.let { navController.navigate(Routes.newIdea(it)) } },
                onOpenIdea = { ideaId -> id?.let { navController.navigate(Routes.ideaDetail(it, ideaId)) } },
                onEditIdea = { ideaId -> id?.let { navController.navigate(Routes.editIdea(it, ideaId)) } },
                onEditIdeaLog = { id?.let { navController.navigate(Routes.editIdeaLog(it)) } },
            )
        }

        composable(
            route = Routes.EDIT_IDEA_LOG,
            arguments = listOf(navArgument(Routes.IDEA_LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            EditIdeaLogScreen(
                ideaLogId = backStackEntry.arguments?.getString(Routes.IDEA_LOG_ARG),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.NEW_IDEA,
            arguments = listOf(navArgument(Routes.IDEA_LOG_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            NewIdeaScreen(
                ideaLogId = backStackEntry.arguments?.getString(Routes.IDEA_LOG_ARG),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDIT_IDEA,
            arguments = listOf(
                navArgument(Routes.IDEA_LOG_ARG) { type = NavType.StringType },
                navArgument(Routes.IDEA_ARG) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            NewIdeaScreen(
                ideaLogId = backStackEntry.arguments?.getString(Routes.IDEA_LOG_ARG),
                ideaId = backStackEntry.arguments?.getString(Routes.IDEA_ARG),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.IDEA_DETAIL,
            arguments = listOf(
                navArgument(Routes.IDEA_LOG_ARG) { type = NavType.StringType },
                navArgument(Routes.IDEA_ARG) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val ideaLogId = backStackEntry.arguments?.getString(Routes.IDEA_LOG_ARG)
            val id = ideaLogId?.toLongOrNull()
            IdeaDetailScreen(
                ideaLogId = ideaLogId,
                ideaId = backStackEntry.arguments?.getString(Routes.IDEA_ARG),
                onBack = { navController.popBackStack() },
                onEditIdea = { ideaId -> id?.let { navController.navigate(Routes.editIdea(it, ideaId)) } },
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
