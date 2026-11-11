package app.starx.mail.data.repository

import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailAttachment
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.core.notification.NotificationHelper
import app.starx.mail.data.local.dao.EmailDao
import app.starx.mail.data.local.dao.SyncStateDao
import app.starx.mail.data.local.entity.AttachmentEntity
import app.starx.mail.data.local.entity.SyncStateEntity
import app.starx.mail.data.remote.StarXApiService
import app.starx.mail.data.remote.dto.*
import app.starx.mail.domain.repository.EmailRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailRepositoryImpl @Inject constructor(
    private val apiService: StarXApiService,
    private val emailDao: EmailDao,
    private val syncStateDao: SyncStateDao,
    private val notificationHelper: NotificationHelper
) : EmailRepository {

    override fun getEmails(folder: EmailFolder): Flow<List<Email>> {
        return emailDao.getEmailsByFolder(folder).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStarredEmails(): Flow<List<Email>> {
        return emailDao.getStarredEmails().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEmailById(id: String): Flow<Email?> {
        return combine(
            emailDao.getEmailByIdFlow(id),
            emailDao.getAttachmentsForEmailFlow(id)
        ) { emailEntity, attachmentEntities ->
            val attachments = attachmentEntities.map { it.toDomain() }
            emailEntity?.toDomain(attachments)
        }
    }

    override fun getConversationThread(threadId: String): Flow<List<Email>> {
        return emailDao.getEmailsByThreadId(threadId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchEmails(query: String): Flow<List<Email>> {
        return emailDao.searchEmails(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnreadCount(): Flow<Int> {
        return emailDao.getUnreadCount()
    }

    override suspend fun refreshEmails(folder: EmailFolder): NetworkResult<Unit> {
        return try {
            val response = apiService.getEmails(folder = folder.id)
            if (response.isSuccessful && response.body() != null) {
                val emailsDto = response.body()!!.emails
                val entities = emailsDto.map { it.toEntity() }
                emailDao.insertEmails(entities)

                val attachments = emailsDto.flatMap { dto ->
                    dto.attachments.map { att ->
                        AttachmentEntity(
                            id = att.id,
                            emailId = dto.id,
                            filename = att.filename,
                            contentType = att.contentType,
                            size = att.size,
                            contentBase64 = att.contentBase64
                        )
                    }
                }
                if (attachments.isNotEmpty()) {
                    emailDao.insertAttachments(attachments)
                }

                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to fetch emails: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun syncEmails(): NetworkResult<Int> {
        return try {
            val lastSync = syncStateDao.getLastSyncTimestamp("emails")
            val response = apiService.sync(since = lastSync)

            if (response.isSuccessful && response.body() != null) {
                val syncData = response.body()!!
                val newEmails = syncData.emails

                val entities = newEmails.map { it.toEntity() }
                emailDao.insertEmails(entities)

                val attachments = newEmails.flatMap { dto ->
                    dto.attachments.map { att ->
                        AttachmentEntity(
                            id = att.id,
                            emailId = dto.id,
                            filename = att.filename,
                            contentType = att.contentType,
                            size = att.size,
                            contentBase64 = att.contentBase64
                        )
                    }
                }
                if (attachments.isNotEmpty()) {
                    emailDao.insertAttachments(attachments)
                }

                // Process deleted emails if any
                for (delId in syncData.deletedEmailIds) {
                    emailDao.deleteEmailById(delId)
                }

                // Trigger notifications for new unread incoming emails
                val incomingUnread = newEmails.filter { it.folder == "inbox" && !it.isRead }
                for (dto in incomingUnread) {
                    notificationHelper.showNewEmailNotification(dto.toDomain())
                }

                // Update sync timestamp
                syncStateDao.setLastSyncTimestamp(
                    SyncStateEntity(key = "emails", lastSyncTimestamp = syncData.syncTimestamp)
                )

                NetworkResult.Success(newEmails.size)
            } else {
                NetworkResult.Error("Sync failed: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Sync error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun sendEmail(
        from: String?,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        text: String?,
        html: String?,
        attachments: List<EmailAttachment>,
        threadId: String?
    ): NetworkResult<Email> {
        return try {
            val requestDto = SendEmailRequestDto(
                from = from,
                to = to,
                cc = cc.ifEmpty { null },
                bcc = bcc.ifEmpty { null },
                subject = subject,
                text = text,
                html = html,
                attachments = attachments.map {
                    SendAttachmentDto(
                        filename = it.filename,
                        content = it.contentBase64 ?: "",
                        contentType = it.contentType
                    )
                },
                threadId = threadId
            )

            val response = apiService.sendEmail(requestDto)
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val sentEmail = result.email?.toDomain() ?: Email(
                    id = result.emailId,
                    threadId = threadId ?: "thread_${result.emailId}",
                    from = from ?: "user@domain.com",
                    to = to,
                    cc = cc,
                    bcc = bcc,
                    subject = subject,
                    textBody = text ?: "",
                    htmlBody = html ?: "",
                    snippet = (text ?: subject).take(160),
                    createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    folder = EmailFolder.SENT,
                    isRead = true,
                    isStarred = false,
                    hasAttachments = attachments.isNotEmpty(),
                    attachments = attachments
                )

                emailDao.insertEmail(sentEmail.toEntity())
                NetworkResult.Success(sentEmail)
            } else {
                NetworkResult.Error("Failed to send email: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Failed to send email: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun replyEmail(
        emailId: String,
        text: String,
        html: String?,
        replyAll: Boolean,
        attachments: List<EmailAttachment>
    ): NetworkResult<Email> {
        return try {
            val requestDto = ReplyEmailRequestDto(
                text = text,
                html = html,
                replyAll = replyAll,
                attachments = attachments.map {
                    SendAttachmentDto(
                        filename = it.filename,
                        content = it.contentBase64 ?: "",
                        contentType = it.contentType
                    )
                }
            )

            val response = apiService.replyEmail(emailId, requestDto)
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val reply = result.email?.toDomain() ?: Email(
                    id = result.emailId,
                    threadId = "thread_${result.emailId}",
                    from = "me",
                    to = emptyList(),
                    subject = "Re: Email",
                    textBody = text,
                    htmlBody = html ?: text,
                    snippet = text.take(160),
                    createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    folder = EmailFolder.SENT,
                    isRead = true
                )
                emailDao.insertEmail(reply.toEntity())
                NetworkResult.Success(reply)
            } else {
                NetworkResult.Error("Failed to send reply: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Reply error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun forwardEmail(
        emailId: String,
        to: List<String>,
        text: String
    ): NetworkResult<Unit> {
        return try {
            val response = apiService.forwardEmail(
                emailId,
                ForwardEmailRequestDto(to = to, text = text)
            )
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to forward: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Forward error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun markAsRead(id: String, isRead: Boolean): NetworkResult<Unit> {
        emailDao.markAsRead(id, isRead)
        return try {
            apiService.updateEmail(id, UpdateEmailRequestDto(isRead = isRead))
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Success(Unit) // Offline updated
        }
    }

    override suspend fun toggleStar(id: String, isStarred: Boolean): NetworkResult<Unit> {
        emailDao.setStarred(id, isStarred)
        return try {
            apiService.updateEmail(id, UpdateEmailRequestDto(isStarred = isStarred))
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Success(Unit)
        }
    }

    override suspend fun moveToFolder(id: String, folder: EmailFolder): NetworkResult<Unit> {
        emailDao.updateFolder(id, folder)
        return try {
            apiService.updateEmail(id, UpdateEmailRequestDto(folder = folder.id))
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Success(Unit)
        }
    }

    override suspend fun deleteEmail(id: String): NetworkResult<Unit> {
        emailDao.deleteEmailById(id)
        return try {
            apiService.deleteEmail(id)
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Success(Unit)
        }
    }

    override suspend fun downloadAttachment(
        emailId: String,
        attachmentId: String,
        destinationFile: File
    ): NetworkResult<File> {
        return try {
            val response = apiService.downloadAttachment(emailId, attachmentId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val parent = destinationFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                FileOutputStream(destinationFile).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                NetworkResult.Success(destinationFile)
            } else {
                NetworkResult.Error("Failed to download attachment: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Download error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
