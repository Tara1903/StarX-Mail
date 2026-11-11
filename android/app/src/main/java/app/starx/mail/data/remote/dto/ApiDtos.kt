package app.starx.mail.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SetupConnectRequestDto(
    @SerializedName("resendApiKey") val resendApiKey: String,
    @SerializedName("backendUrl") val backendUrl: String? = null,
    @SerializedName("preferredEmailPrefix") val preferredEmailPrefix: String? = null
)

data class SetupConnectResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String,
    @SerializedName("account") val account: AccountDto,
    @SerializedName("message") val message: String
)

data class SetupStatusResponseDto(
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("account") val account: AccountDto? = null,
    @SerializedName("setupRequirements") val setupRequirements: SetupRequirementsDto? = null
)

data class SetupRequirementsDto(
    @SerializedName("domainVerified") val domainVerified: Boolean,
    @SerializedName("missingDnsRecords") val missingDnsRecords: List<DomainRecordDto> = emptyList(),
    @SerializedName("inboxReady") val inboxReady: Boolean,
    @SerializedName("webhookReady") val webhookReady: Boolean
)

data class DomainRecordDto(
    @SerializedName("record") val record: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: String,
    @SerializedName("ttl") val ttl: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("priority") val priority: Int? = null
)

data class DomainDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("region") val region: String? = null,
    @SerializedName("records") val records: List<DomainRecordDto> = emptyList(),
    @SerializedName("createdAt") val createdAt: String
)

data class InboxDto(
    @SerializedName("id") val id: String,
    @SerializedName("emailAddress") val emailAddress: String,
    @SerializedName("name") val name: String,
    @SerializedName("fromName") val fromName: String? = null,
    @SerializedName("forwarding") val forwarding: Boolean = false,
    @SerializedName("status") val status: String = "active",
    @SerializedName("createdAt") val createdAt: String
)

data class AccountDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("resendKeyMasked") val resendKeyMasked: String,
    @SerializedName("defaultFromAddress") val defaultFromAddress: String,
    @SerializedName("domains") val domains: List<DomainDto> = emptyList(),
    @SerializedName("inboxes") val inboxes: List<InboxDto> = emptyList(),
    @SerializedName("webhookConfigured") val webhookConfigured: Boolean,
    @SerializedName("webhookUrl") val webhookUrl: String? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("lastSyncedAt") val lastSyncedAt: String? = null
)

data class AttachmentDto(
    @SerializedName("id") val id: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("contentType") val contentType: String,
    @SerializedName("size") val size: Long,
    @SerializedName("contentBase64") val contentBase64: String? = null,
    @SerializedName("downloadUrl") val downloadUrl: String? = null
)

data class EmailDto(
    @SerializedName("id") val id: String,
    @SerializedName("inboxId") val inboxId: String? = null,
    @SerializedName("threadId") val threadId: String,
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: List<String>,
    @SerializedName("cc") val cc: List<String> = emptyList(),
    @SerializedName("bcc") val bcc: List<String> = emptyList(),
    @SerializedName("replyTo") val replyTo: String? = null,
    @SerializedName("subject") val subject: String,
    @SerializedName("textBody") val textBody: String,
    @SerializedName("htmlBody") val htmlBody: String,
    @SerializedName("snippet") val snippet: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("folder") val folder: String,
    @SerializedName("isRead") val isRead: Boolean,
    @SerializedName("isStarred") val isStarred: Boolean,
    @SerializedName("hasAttachments") val hasAttachments: Boolean,
    @SerializedName("attachments") val attachments: List<AttachmentDto> = emptyList(),
    @SerializedName("deliveryStatus") val deliveryStatus: String,
    @SerializedName("headers") val headers: Map<String, String>? = null
)

data class EmailListResponseDto(
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("hasMore") val hasMore: Boolean,
    @SerializedName("emails") val emails: List<EmailDto>
)

data class ThreadDto(
    @SerializedName("threadId") val threadId: String,
    @SerializedName("subject") val subject: String,
    @SerializedName("emails") val emails: List<EmailDto>
)

data class EmailDetailResponseDto(
    @SerializedName("email") val email: EmailDto,
    @SerializedName("thread") val thread: ThreadDto
)

data class SendAttachmentDto(
    @SerializedName("filename") val filename: String,
    @SerializedName("content") val content: String, // Base64
    @SerializedName("contentType") val contentType: String? = null
)

data class SendEmailRequestDto(
    @SerializedName("from") val from: String? = null,
    @SerializedName("to") val to: List<String>,
    @SerializedName("cc") val cc: List<String>? = null,
    @SerializedName("bcc") val bcc: List<String>? = null,
    @SerializedName("replyTo") val replyTo: String? = null,
    @SerializedName("subject") val subject: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("html") val html: String? = null,
    @SerializedName("attachments") val attachments: List<SendAttachmentDto>? = null,
    @SerializedName("threadId") val threadId: String? = null,
    @SerializedName("inReplyTo") val inReplyTo: String? = null
)

data class SendEmailResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("emailId") val emailId: String,
    @SerializedName("email") val email: EmailDto? = null,
    @SerializedName("message") val message: String
)

data class ReplyEmailRequestDto(
    @SerializedName("text") val text: String? = null,
    @SerializedName("html") val html: String? = null,
    @SerializedName("replyAll") val replyAll: Boolean = false,
    @SerializedName("attachments") val attachments: List<SendAttachmentDto>? = null
)

data class ForwardEmailRequestDto(
    @SerializedName("to") val to: List<String>,
    @SerializedName("text") val text: String? = null,
    @SerializedName("html") val html: String? = null
)

data class UpdateEmailRequestDto(
    @SerializedName("isRead") val isRead: Boolean? = null,
    @SerializedName("isStarred") val isStarred: Boolean? = null,
    @SerializedName("folder") val folder: String? = null
)

data class DraftDto(
    @SerializedName("id") val id: String,
    @SerializedName("inboxId") val inboxId: String? = null,
    @SerializedName("to") val to: List<String> = emptyList(),
    @SerializedName("cc") val cc: List<String> = emptyList(),
    @SerializedName("bcc") val bcc: List<String> = emptyList(),
    @SerializedName("subject") val subject: String = "",
    @SerializedName("textBody") val textBody: String = "",
    @SerializedName("htmlBody") val htmlBody: String = "",
    @SerializedName("attachments") val attachments: List<AttachmentDto> = emptyList(),
    @SerializedName("updatedAt") val updatedAt: String = ""
)

data class DraftListResponseDto(
    @SerializedName("drafts") val drafts: List<DraftDto>
)

data class DraftResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("draft") val draft: DraftDto
)

data class SyncStatsDto(
    @SerializedName("inboxCount") val inboxCount: Int,
    @SerializedName("unreadCount") val unreadCount: Int,
    @SerializedName("starredCount") val starredCount: Int,
    @SerializedName("sentCount") val sentCount: Int,
    @SerializedName("draftsCount") val draftsCount: Int
)

data class SyncResponseDto(
    @SerializedName("syncTimestamp") val syncTimestamp: String,
    @SerializedName("hasMore") val hasMore: Boolean,
    @SerializedName("emails") val emails: List<EmailDto>,
    @SerializedName("deletedEmailIds") val deletedEmailIds: List<String> = emptyList(),
    @SerializedName("stats") val stats: SyncStatsDto
)

data class InboxListResponseDto(
    @SerializedName("inboxes") val inboxes: List<InboxDto>
)

data class CreateInboxRequestDto(
    @SerializedName("emailAddress") val emailAddress: String,
    @SerializedName("name") val name: String,
    @SerializedName("fromName") val fromName: String? = null,
    @SerializedName("forwarding") val forwarding: Boolean = false
)

data class CreateInboxResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("inbox") val inbox: InboxDto
)

data class SimpleMessageResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)
