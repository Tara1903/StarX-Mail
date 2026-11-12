package app.starx.mail.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.starx.mail.presentation.theme.StarXSuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val account by viewModel.account.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showCreateInboxDialog by remember { mutableStateOf(false) }
    var showAddDomainDialog by remember { mutableStateOf(false) }
    var showEditSenderDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = viewModel::refreshStatus) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Connected Account Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resend Infrastructure",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val isAccountConnected = account?.connected == true
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAccountConnected) StarXSuccessGreen.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (isAccountConnected) "Connected" else "Reconnect Needed",
                                color = if (isAccountConnected) StarXSuccessGreen else MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (account?.connected != true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.disconnect(onDisconnected) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Session Expired — Reconnect Resend Key", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "API Key: ${account?.resendKeyMasked ?: "Configured"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Default Sender: ${account?.defaultFromAddress ?: "onboarding@resend.dev"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showEditSenderDialog = true }) {
                            Text("Edit")
                        }
                    }

                    val webhookUrl = account?.webhookUrl
                    if (!webhookUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(webhookUrl))
                                    viewModel.notifyMessage("Webhook URL copied to clipboard")
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (account?.webhookConfigured == true) "Inbound Webhook: Active" else "Inbound Webhook URL (Tap to copy)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (account?.webhookConfigured == true) StarXSuccessGreen else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = webhookUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy webhook URL",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Custom Domains & DNS Records
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Domains & DNS (${account?.domains?.size ?: 0})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(onClick = { showAddDomainDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Domain", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val domains = account?.domains ?: emptyList()
                    if (domains.isEmpty()) {
                        Text(
                            text = "No custom domains connected yet. Add your own domain or verify it in Resend to send from your domain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        domains.forEach { domain ->
                            val isVerified = domain.status == "verified"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = domain.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!isVerified) {
                                        TextButton(
                                            onClick = { viewModel.verifyDomain(domain.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Verify DNS", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isVerified) StarXSuccessGreen.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = domain.status.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isVerified) StarXSuccessGreen else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (!isVerified && domain.records.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "DNS Records (Tap a record to copy value):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                domain.records.forEach { rec ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                clipboardManager.setText(AnnotatedString(rec.value))
                                                viewModel.notifyMessage("Copied ${rec.type} value")
                                            }
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "• ${rec.type} ${rec.name}: ${rec.value} (${rec.status})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy DNS value",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            // Inboxes Management
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inboxes (${account?.inboxes?.size ?: 0})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(onClick = { showCreateInboxDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Inbox")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val inboxes = account?.inboxes ?: emptyList()
                    if (inboxes.isEmpty()) {
                        Text(
                            text = "No Resend inboxes provisioned. You can still send from any alias on your verified custom domain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        inboxes.forEach { inbox ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = inbox.emailAddress,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = inbox.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = inbox.status.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sync and Security Settings
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sync & Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Background Sync Interval", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (syncIntervalMinutes > 0) "Every $syncIntervalMinutes minutes" else "Manual sync only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(onClick = viewModel::syncNow) {
                            Text("Sync Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (viewModel.canUseBiometrics) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Lock", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Require biometric authentication to open StarX Mail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = viewModel::setBiometricEnabled
                            )
                        }
                    }
                }
            }

            // Disconnect Account Button
            OutlinedButton(
                onClick = { showDisconnectDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect Account", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Add Custom Domain Dialog
    if (showAddDomainDialog) {
        var domainName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDomainDialog = false },
            title = { Text("Add Custom Domain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Add any domain you own. Resend will generate DKIM and SPF DNS records for verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = domainName,
                        onValueChange = { domainName = it },
                        label = { Text("Domain Name") },
                        placeholder = { Text("yourdomain.com") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clean = domainName.trim()
                        .lowercase()
                        .substringAfter('@')
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore('/')
                        .trimEnd('.')
                    if (clean.isNotBlank()) {
                        viewModel.addDomain(clean)
                        showAddDomainDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDomainDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Default Sender Dialog
    if (showEditSenderDialog) {
        var senderEmail by remember { mutableStateOf(account?.defaultFromAddress ?: "") }

        AlertDialog(
            onDismissRequest = { showEditSenderDialog = false },
            title = { Text("Default Sender Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose any email address on one of your verified custom domains.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = senderEmail,
                        onValueChange = { senderEmail = it },
                        label = { Text("From Email Address") },
                        placeholder = { Text("hello@yourdomain.com") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clean = senderEmail.trim()
                    if (clean.contains("@")) {
                        viewModel.updateDefaultSender(clean)
                        showEditSenderDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSenderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Inbox Dialog
    if (showCreateInboxDialog) {
        var emailAddress by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var fromName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateInboxDialog = false },
            title = { Text("Create New Inbox") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emailAddress,
                        onValueChange = { emailAddress = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("support@yourdomain.com") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Inbox Name") },
                        placeholder = { Text("Customer Support") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fromName,
                        onValueChange = { fromName = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("Support Team") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (emailAddress.isNotBlank() && name.isNotBlank()) {
                        viewModel.createInbox(emailAddress, name, fromName.ifBlank { null })
                        showCreateInboxDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInboxDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Resend Account?") },
            text = {
                Text("This will remove your local cached messages and security tokens from this device. You will need to re-enter your API key to connect again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnect(onDisconnected)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
