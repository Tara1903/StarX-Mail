package app.starx.mail.presentation.starred

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.starx.mail.presentation.components.EmailListItem
import app.starx.mail.presentation.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    onEmailClick: (emailId: String, threadId: String) -> Unit,
    viewModel: StarredViewModel = hiltViewModel()
) {
    val emails by viewModel.starredEmails.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starred", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (emails.isEmpty()) {
                EmptyStateView(
                    title = "No starred emails",
                    description = "Star important emails in your inbox or sent items to easily find them here.",
                    icon = Icons.Default.Star
                )
            } else {
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
