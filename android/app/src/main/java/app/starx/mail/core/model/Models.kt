package app.starx.mail.core.model

enum class EmailFolder(val id: String, val displayName: String) {
    INBOX("inbox", "Inbox"),
    SENT("sent", "Sent"),
    DRAFTS("drafts", "Drafts"),
    STARRED("starred", "Starred"),
    ARCHIVE("archive", "Archive"),
    TRASH("trash", "Trash");

    companion object {
        fun fromId(id: String): EmailFolder = values().find { it.id == id } ?: INBOX
    }
}

enum class DeliveryStatus(val displayName: String) {
    PENDING("Pending"),
    SENT("Sent"),
    DELIVERED("Delivered"),
    BOUNCED("Bounced"),
    COMPLAINED("Complained"),
    RECEIVED("Received");

    companion object {
        fun fromString(status: String?): DeliveryStatus =
            values().find { it.name.equals(status, ignoreCase = true) } ?: SENT
    }
}

data class EmailAttachment(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val contentBase64: String? = null,
    val localUri: String? = null
)

data class Email(
    val id: String,
    val inboxId: String? = null,
    val threadId: String,
    val from: String,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val replyTo: String? = null,
    val subject: String,
    val textBody: String,
    val htmlBody: String,
    val snippet: String,
    val createdAt: String,
    val folder: EmailFolder = EmailFolder.INBOX,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val hasAttachments: Boolean = false,
    val attachments: List<EmailAttachment> = emptyList(),
    val deliveryStatus: DeliveryStatus = DeliveryStatus.RECEIVED,
    val headers: Map<String, String>? = null
)

data class ConversationThread(
    val threadId: String,
    val subject: String,
    val emails: List<Email>
)

data class DomainDnsRecord(
    val record: String,
    val name: String,
    val type: String,
    val value: String,
    val ttl: String? = null,
    val status: String,
    val priority: Int? = null
)

data class Domain(
    val id: String,
    val name: String,
    val status: String, // "verified", "pending", "not_started"
    val region: String? = null,
    val records: List<DomainDnsRecord> = emptyList(),
    val createdAt: String
)

data class Inbox(
    val id: String,
    val emailAddress: String,
    val name: String,
    val fromName: String? = null,
    val forwarding: Boolean = false,
    val status: String = "active",
    val createdAt: String
)

data class Account(
    val id: String,
    val email: String? = null,
    val connected: Boolean = false,
    val resendKeyMasked: String = "",
    val defaultFromAddress: String = "",
    val domains: List<Domain> = emptyList(),
    val inboxes: List<Inbox> = emptyList(),
    val webhookConfigured: Boolean = false,
    val webhookUrl: String? = null,
    val createdAt: String = "",
    val lastSyncedAt: String? = null
)

data class Draft(
    val id: String,
    val inboxId: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val textBody: String = "",
    val htmlBody: String = "",
    val attachments: List<EmailAttachment> = emptyList(),
    val updatedAt: String = ""
)
