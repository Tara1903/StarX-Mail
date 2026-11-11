package app.starx.mail

import app.starx.mail.core.model.Draft
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.repository.DraftRepository
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.domain.usecase.GetEmailsUseCase
import app.starx.mail.domain.usecase.SaveDraftUseCase
import app.starx.mail.domain.usecase.SendEmailUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UseCasesTest {

    private val emailRepository: EmailRepository = mock()
    private val draftRepository: DraftRepository = mock()

    @Test
    fun `GetEmailsUseCase retrieves emails from repository for folder`() = runTest {
        val testEmails = listOf(
            Email(
                id = "email_1",
                threadId = "thread_1",
                from = "test@example.com",
                to = listOf("me@starx.app"),
                subject = "Test Email",
                textBody = "Hello world",
                htmlBody = "<p>Hello world</p>",
                snippet = "Hello world",
                createdAt = "2026-11-11T12:00:00Z",
                folder = EmailFolder.INBOX
            )
        )

        whenever(emailRepository.getEmails(EmailFolder.INBOX)).thenReturn(flowOf(testEmails))

        val useCase = GetEmailsUseCase(emailRepository)
        val result = useCase(EmailFolder.INBOX).first()

        assertEquals(1, result.size)
        assertEquals("Test Email", result[0].subject)
    }

    @Test
    fun `SendEmailUseCase successfully dispatches and returns email`() = runTest {
        val expectedEmail = Email(
            id = "sent_123",
            threadId = "thread_123",
            from = "me@resend.dev",
            to = listOf("client@domain.com"),
            subject = "Sent Subject",
            textBody = "Body",
            htmlBody = "<p>Body</p>",
            snippet = "Body",
            createdAt = "2026-11-11T12:00:00Z",
            folder = EmailFolder.SENT
        )

        whenever(
            emailRepository.sendEmail(
                from = "me@resend.dev",
                to = listOf("client@domain.com"),
                cc = emptyList(),
                bcc = emptyList(),
                subject = "Sent Subject",
                text = "Body",
                html = null,
                attachments = emptyList()
            )
        ).thenReturn(NetworkResult.Success(expectedEmail))

        val useCase = SendEmailUseCase(emailRepository)
        val result = useCase(
            from = "me@resend.dev",
            to = listOf("client@domain.com"),
            subject = "Sent Subject",
            text = "Body",
            html = null
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("sent_123", (result as NetworkResult.Success).data.id)
    }

    @Test
    fun `SaveDraftUseCase delegates draft persistence to repository`() = runTest {
        val draft = Draft(
            id = "draft_1",
            to = listOf("draft@example.com"),
            subject = "Draft",
            textBody = "Body text"
        )

        whenever(draftRepository.saveDraft(draft)).thenReturn(NetworkResult.Success(draft))

        val useCase = SaveDraftUseCase(draftRepository)
        val result = useCase(draft)

        assertTrue(result is NetworkResult.Success)
        assertEquals("draft_1", (result as NetworkResult.Success).data.id)
    }
}
