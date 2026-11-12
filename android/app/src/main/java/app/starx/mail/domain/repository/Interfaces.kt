package app.starx.mail.domain.repository

import app.starx.mail.core.model.*
import app.starx.mail.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface EmailRepository {
    fun getEmails(folder: EmailFolder): Flow<List<Email>>
    fun getStarredEmails(): Flow<List<Email>>
    fun getEmailById(id: String): Flow<Email?>
    fun getConversationThread(threadId: String): Flow<List<Email>>
    fun searchEmails(query: String): Flow<List<Email>>
    fun getUnreadCount(): Flow<Int>

    suspend fun refreshEmails(folder: EmailFolder): NetworkResult<Unit>
    suspend fun syncEmails(): NetworkResult<Int> // returns number of new emails
    suspend fun sendEmail(
        from: String?,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        text: String?,
        html: String?,
        attachments: List<EmailAttachment>,
        threadId: String? = null
    ): NetworkResult<Email>

    suspend fun replyEmail(
        emailId: String,
        text: String,
        html: String?,
        replyAll: Boolean,
        attachments: List<EmailAttachment> = emptyList()
    ): NetworkResult<Email>

    suspend fun forwardEmail(
        emailId: String,
        to: List<String>,
        text: String
    ): NetworkResult<Unit>

    suspend fun markAsRead(id: String, isRead: Boolean): NetworkResult<Unit>
    suspend fun toggleStar(id: String, isStarred: Boolean): NetworkResult<Unit>
    suspend fun moveToFolder(id: String, folder: EmailFolder): NetworkResult<Unit>
    suspend fun deleteEmail(id: String): NetworkResult<Unit>
    suspend fun downloadAttachment(emailId: String, attachmentId: String, destinationFile: File): NetworkResult<File>
}

interface AuthRepository {
    fun isConnected(): Flow<Boolean>
    suspend fun connect(
        resendApiKey: String,
        backendUrl: String? = null,
        preferredEmailPrefix: String? = null
    ): NetworkResult<Account>
    suspend fun disconnect(): NetworkResult<Unit>
    suspend fun getStoredToken(): String?
}

interface DraftRepository {
    fun getDrafts(): Flow<List<Draft>>
    suspend fun getDraftById(id: String): Draft?
    suspend fun saveDraft(draft: Draft): NetworkResult<Draft>
    suspend fun deleteDraft(id: String): NetworkResult<Unit>
}

interface AccountRepository {
    fun getAccount(): Flow<Account?>
    suspend fun refreshStatus(): NetworkResult<Account>
    suspend fun getInboxes(): NetworkResult<List<Inbox>>
    suspend fun createInbox(emailAddress: String, name: String, fromName: String?): NetworkResult<Inbox>
    suspend fun addDomain(domainName: String, region: String = "us-east-1"): NetworkResult<Domain>
    suspend fun verifyDomain(domainId: String): NetworkResult<Domain?>
    suspend fun updateDefaultSender(emailAddress: String): NetworkResult<Account>
}
