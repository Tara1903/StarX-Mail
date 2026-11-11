package app.starx.mail.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.core.util.DateTimeFormatter
import app.starx.mail.presentation.components.AttachmentChip
import app.starx.mail.presentation.components.SafeHtmlEmailView
import app.starx.mail.presentation.theme.StarXStarYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailsScreen(
    onBack: () -> Unit,
    onReply: (emailId: String, replyAll: Boolean) -> Unit,
    onForward: (emailId: String) -> Unit,
    viewModel: EmailDetailsViewModel = hiltViewModel()
) {
    val email by viewModel.email.collectAsState()
    val threadEmails by viewModel.threadEmails.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadStatus) {
        downloadStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearDownloadStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    email?.let { currentEmail ->
                        IconButton(onClick = { viewModel.toggleStar(currentEmail) }) {
                            Icon(
                                imageVector = if (currentEmail.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Star",
                                tint = if (currentEmail.isStarred) StarXStarYellow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            viewModel.moveToFolder(EmailFolder.ARCHIVE)
                            onBack()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = {
                            viewModel.deleteEmail()
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            email?.let { currentEmail ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onReply(currentEmail.id, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reply")
                        }

                        if (currentEmail.to.size > 1 || currentEmail.cc.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { onReply(currentEmail.id, true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ReplyAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reply All")
                            }
                        }

                        OutlinedButton(
                            onClick = { onForward(currentEmail.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Forward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Forward")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val displayList = if (threadEmails.isNotEmpty()) threadEmails else listOfNotNull(email)

        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Thread Subject Header
                item {
                    val subject = displayList.first().subject.ifBlank { "(No Subject)" }
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                // Render each email card in thread
                items(displayList, key = { it.id }) { message ->
                    SingleEmailCard(
                        email = message,
                        onDownloadAttachment = viewModel::downloadAttachment
                    )
                }
            }
        }
    }
}

@Composable
fun SingleEmailCard(
    email: Email,
    onDownloadAttachment: (app.starx.mail.core.model.EmailAttachment) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Sender info & avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = email.from.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = email.from,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "to ${email.to.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = DateTimeFormatter.formatRelativeTime(email.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (email.cc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "cc: ${email.cc.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Body
            if (email.htmlBody.isNotBlank()) {
                SafeHtmlEmailView(htmlBody = email.htmlBody)
            } else {
                Text(
                    text = email.textBody,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Attachments
            if (email.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Attachments (${email.attachments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    email.attachments.forEach { att ->
                        AttachmentChip(
                            attachment = att,
                            onDownloadClick = { onDownloadAttachment(att) }
                        )
                    }
                }
            }
        }
    }
}
