package app.starx.mail.presentation.archive_trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.starx.mail.presentation.components.EmailListItem
import app.starx.mail.presentation.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveTrashScreen(
    onEmailClick: (emailId: String, threadId: String) -> Unit,
    viewModel: ArchiveTrashViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val archiveEmails by viewModel.archiveEmails.collectAsState()
    val trashEmails by viewModel.trashEmails.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Archive" else "Trash", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Archive (${archiveEmails.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Trash (${trashEmails.size})") }
                )
            }

            val list = if (selectedTab == 0) archiveEmails else trashEmails

            if (list.isEmpty()) {
                EmptyStateView(
                    title = if (selectedTab == 0) "Archive is empty" else "Trash is empty",
                    description = if (selectedTab == 0)
                        "Archived conversations are stored locally and will appear here."
                    else "Deleted emails remain in trash until permanently removed.",
                    icon = if (selectedTab == 0) Icons.Default.Archive else Icons.Default.Delete
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { email ->
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
