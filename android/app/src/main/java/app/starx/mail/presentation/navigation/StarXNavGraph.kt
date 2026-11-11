package app.starx.mail.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.presentation.archive_trash.ArchiveTrashScreen
import app.starx.mail.presentation.compose.ComposeScreen
import app.starx.mail.presentation.details.EmailDetailsScreen
import app.starx.mail.presentation.drafts.DraftsScreen
import app.starx.mail.presentation.inbox.InboxScreen
import app.starx.mail.presentation.onboarding.OnboardingScreen
import app.starx.mail.presentation.search.SearchScreen
import app.starx.mail.presentation.sent.SentScreen
import app.starx.mail.presentation.settings.SettingsScreen
import app.starx.mail.presentation.starred.StarredScreen

@Composable
fun StarXNavGraph(
    navController: NavHostController,
    startDestination: String,
    emailRepository: EmailRepository
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val unreadCount by emailRepository.getUnreadCount().collectAsState(initial = 0)

    val showBottomBar = currentRoute in listOf(
        Screen.Inbox.route,
        Screen.Sent.route,
        Screen.Drafts.route,
        Screen.Starred.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                StarXBottomNavBar(
                    currentRoute = currentRoute,
                    unreadCount = unreadCount,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Inbox.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Onboarding
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onConnectSuccess = {
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // Inbox
            composable(Screen.Inbox.route) {
                InboxScreen(
                    onEmailClick = { emailId, threadId ->
                        navController.navigate(Screen.EmailDetails.createRoute(emailId, threadId))
                    },
                    onComposeClick = {
                        navController.navigate(Screen.Compose.createRoute())
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            // Sent
            composable(Screen.Sent.route) {
                SentScreen(
                    onEmailClick = { emailId, threadId ->
                        navController.navigate(Screen.EmailDetails.createRoute(emailId, threadId))
                    }
                )
            }

            // Drafts
            composable(Screen.Drafts.route) {
                DraftsScreen(
                    onDraftClick = { draftId ->
                        navController.navigate(Screen.Compose.createRoute(draftId = draftId))
                    }
                )
            }

            // Starred
            composable(Screen.Starred.route) {
                StarredScreen(
                    onEmailClick = { emailId, threadId ->
                        navController.navigate(Screen.EmailDetails.createRoute(emailId, threadId))
                    }
                )
            }

            // Archive & Trash
            composable(Screen.ArchiveTrash.route) {
                ArchiveTrashScreen(
                    onEmailClick = { emailId, threadId ->
                        navController.navigate(Screen.EmailDetails.createRoute(emailId, threadId))
                    }
                )
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onDisconnected = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // Search
            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onEmailClick = { emailId, threadId ->
                        navController.navigate(Screen.EmailDetails.createRoute(emailId, threadId))
                    }
                )
            }

            // Email Details
            composable(
                route = Screen.EmailDetails.route,
                arguments = listOf(
                    navArgument("emailId") { type = NavType.StringType },
                    navArgument("threadId") { type = NavType.StringType }
                )
            ) {
                EmailDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onReply = { emailId, replyAll ->
                        navController.navigate(Screen.Compose.createRoute(replyToId = emailId, replyAll = replyAll))
                    },
                    onForward = { emailId ->
                        navController.navigate(Screen.Compose.createRoute(forwardId = emailId))
                    }
                )
            }

            // Compose
            composable(
                route = Screen.Compose.route,
                arguments = listOf(
                    navArgument("draftId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("replyToId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("replyAll") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("forwardId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                ComposeScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
