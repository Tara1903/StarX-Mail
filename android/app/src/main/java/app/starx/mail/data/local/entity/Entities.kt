package app.starx.mail.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.starx.mail.core.model.DeliveryStatus
import app.starx.mail.core.model.EmailFolder

@Entity(
    tableName = "emails",
    indices = [
        Index(value = ["folder"]),
        Index(value = ["threadId"]),
        Index(value = ["createdAt"]),
        Index(value = ["isStarred"]),
        Index(value = ["isRead"])
    ]
)
data class EmailEntity(
    @PrimaryKey val id: String,
    val inboxId: String? = null,
    val threadId: String,
    val fromAddress: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String> = emptyList(),
    val bccAddresses: List<String> = emptyList(),
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
    val deliveryStatus: DeliveryStatus = DeliveryStatus.RECEIVED
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = EmailEntity::class,
            parentColumns = ["id"],
            childColumns = ["emailId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["emailId"])]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val emailId: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val contentBase64: String? = null,
    val localUri: String? = null
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val inboxId: String? = null,
    val toAddresses: List<String> = emptyList(),
    val ccAddresses: List<String> = emptyList(),
    val bccAddresses: List<String> = emptyList(),
    val subject: String = "",
    val textBody: String = "",
    val htmlBody: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String? = null,
    val connected: Boolean = false,
    val resendKeyMasked: String = "",
    val defaultFromAddress: String = "",
    val webhookConfigured: Boolean = false,
    val webhookUrl: String? = null,
    val createdAt: String = "",
    val lastSyncedAt: String? = null
)

@Entity(tableName = "inboxes")
data class InboxEntity(
    @PrimaryKey val id: String,
    val emailAddress: String,
    val name: String,
    val fromName: String? = null,
    val forwarding: Boolean = false,
    val status: String = "active",
    val createdAt: String
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val lastSyncTimestamp: String
)
