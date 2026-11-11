package app.starx.mail.domain.usecase

import app.starx.mail.core.model.Account
import app.starx.mail.core.model.Draft
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailAttachment
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.repository.AccountRepository
import app.starx.mail.domain.repository.AuthRepository
import app.starx.mail.domain.repository.DraftRepository
import app.starx.mail.domain.repository.EmailRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEmailsUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    operator fun invoke(folder: EmailFolder): Flow<List<Email>> =
        if (folder == EmailFolder.STARRED) emailRepository.getStarredEmails()
        else emailRepository.getEmails(folder)
}

class GetEmailDetailsUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    operator fun invoke(id: String): Flow<Email?> = emailRepository.getEmailById(id)
    fun getThread(threadId: String): Flow<List<Email>> = emailRepository.getConversationThread(threadId)
}

class SendEmailUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    suspend operator fun invoke(
        from: String?,
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        text: String?,
        html: String?,
        attachments: List<EmailAttachment> = emptyList(),
        threadId: String? = null
    ): NetworkResult<Email> = emailRepository.sendEmail(
        from, to, cc, bcc, subject, text, html, attachments, threadId
    )
}

class ReplyEmailUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    suspend operator fun invoke(
        emailId: String,
        text: String,
        html: String?,
        replyAll: Boolean,
        attachments: List<EmailAttachment> = emptyList()
    ): NetworkResult<Email> = emailRepository.replyEmail(emailId, text, html, replyAll, attachments)
}

class ForwardEmailUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    suspend operator fun invoke(
        emailId: String,
        to: List<String>,
        text: String
    ): NetworkResult<Unit> = emailRepository.forwardEmail(emailId, to, text)
}

class SaveDraftUseCase @Inject constructor(
    private val draftRepository: DraftRepository
) {
    suspend operator fun invoke(draft: Draft): NetworkResult<Draft> = draftRepository.saveDraft(draft)
}

class ToggleStarUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    suspend operator fun invoke(id: String, isStarred: Boolean): NetworkResult<Unit> =
        emailRepository.toggleStar(id, isStarred)
}

class MarkReadUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    suspend operator fun invoke(id: String, isRead: Boolean): NetworkResult<Unit> =
        emailRepository.markAsRead(id, isRead)
}

class SyncEmailsUseCase @Inject constructor(
    private val emailRepository: EmailRepository
) {
    suspend operator fun invoke(): NetworkResult<Int> = emailRepository.syncEmails()
}

class ConnectAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        resendApiKey: String,
        backendUrl: String? = null,
        preferredEmailPrefix: String? = null
    ): NetworkResult<Account> = authRepository.connect(resendApiKey, backendUrl, preferredEmailPrefix)
}
