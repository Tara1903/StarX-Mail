package app.starx.mail.presentation.inbox

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.starx.mail.core.model.Email
import app.starx.mail.presentation.components.EmailListItem
import app.starx.mail.presentation.components.EmptyStateView
import app.starx.mail.presentation.components.LoadingSkeletonList
import app.starx.mail.presentation.theme.StarXPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onEmailClick: (emailId: String, threadId: String) -> Unit,
    onComposeClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val emails by viewModel.emails.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Inbox", style = MaterialTheme.typography.titleLarge)
                        if (unreadCount > 0) {
                            Text(
                                "$unreadCount unread",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search emails")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh inbox")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onComposeClick,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("Compose") },
                containerColor = StarXPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isRefreshing && emails.isEmpty() -> {
                    LoadingSkeletonList()
                }
                emails.isEmpty() -> {
                    EmptyStateView(
                        title = "Your inbox is empty",
                        description = "When you receive emails via your Resend inboxes, they will appear here automatically.",
                        icon = Icons.Default.AllInbox
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(emails, key = { it.id }) { email ->
                            EmailListItem(
                                email = email,
                                onClick = { onEmailClick(email.id, email.threadId) },
                                onToggleStar = { viewModel.toggleStar(email) }
                            )
                        }
                    }
                }
            }
        }
    }
}
