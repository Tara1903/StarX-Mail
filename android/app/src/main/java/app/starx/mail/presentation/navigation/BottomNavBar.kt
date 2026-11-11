package app.starx.mail.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Inbox : BottomNavItem(Screen.Inbox.route, "Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox)
    object Sent : BottomNavItem(Screen.Sent.route, "Sent", Icons.Filled.Send, Icons.Outlined.Send)
    object Drafts : BottomNavItem(Screen.Drafts.route, "Drafts", Icons.Filled.Drafts, Icons.Outlined.Drafts)
    object Starred : BottomNavItem(Screen.Starred.route, "Starred", Icons.Filled.Star, Icons.Outlined.StarBorder)
    object Settings : BottomNavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun StarXBottomNavBar(
    currentRoute: String?,
    unreadCount: Int,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem.Inbox,
        BottomNavItem.Sent,
        BottomNavItem.Drafts,
        BottomNavItem.Starred,
        BottomNavItem.Settings
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (item is BottomNavItem.Inbox && unreadCount > 0) {
                                Badge {
                                    Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title
                        )
                    }
                },
                label = { Text(item.title, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
