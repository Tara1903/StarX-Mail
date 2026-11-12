package app.starx.mail.data.remote

import app.starx.mail.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface StarXApiService {

    @POST("setup/connect")
    suspend fun connect(
        @Body request: SetupConnectRequestDto
    ): Response<SetupConnectResponseDto>

    @GET("setup/status")
    suspend fun getStatus(): Response<SetupStatusResponseDto>

    @POST("setup/disconnect")
    suspend fun disconnect(): Response<SimpleMessageResponseDto>

    @POST("setup/domains")
    suspend fun addDomain(
        @Body request: AddDomainRequestDto
    ): Response<DomainActionResponseDto>

    @POST("setup/domains/{domainId}/verify")
    suspend fun verifyDomain(
        @Path("domainId") domainId: String
    ): Response<DomainActionResponseDto>

    @PATCH("setup/default-sender")
    suspend fun updateDefaultSender(
        @Body request: UpdateDefaultSenderRequestDto
    ): Response<UpdateDefaultSenderResponseDto>

    @GET("emails")
    suspend fun getEmails(
        @Query("folder") folder: String? = null,
        @Query("q") search: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<EmailListResponseDto>

    @GET("emails/{id}")
    suspend fun getEmail(
        @Path("id") id: String
    ): Response<EmailDetailResponseDto>

    @POST("emails/send")
    suspend fun sendEmail(
        @Body request: SendEmailRequestDto
    ): Response<SendEmailResponseDto>

    @POST("emails/{id}/reply")
    suspend fun replyEmail(
        @Path("id") id: String,
        @Body request: ReplyEmailRequestDto
    ): Response<SendEmailResponseDto>

    @POST("emails/{id}/forward")
    suspend fun forwardEmail(
        @Path("id") id: String,
        @Body request: ForwardEmailRequestDto
    ): Response<SendEmailResponseDto>

    @PATCH("emails/{id}")
    suspend fun updateEmail(
        @Path("id") id: String,
        @Body request: UpdateEmailRequestDto
    ): Response<EmailDetailResponseDto>

    @DELETE("emails/{id}")
    suspend fun deleteEmail(
        @Path("id") id: String
    ): Response<SimpleMessageResponseDto>

    @GET("emails/{id}/attachments/{attachmentId}")
    @Streaming
    suspend fun downloadAttachment(
        @Path("id") id: String,
        @Path("attachmentId") attachmentId: String
    ): Response<ResponseBody>

    @GET("drafts")
    suspend fun getDrafts(): Response<DraftListResponseDto>

    @POST("drafts")
    suspend fun saveDraft(
        @Body draft: DraftDto
    ): Response<DraftResponseDto>

    @DELETE("drafts/{id}")
    suspend fun deleteDraft(
        @Path("id") id: String
    ): Response<SimpleMessageResponseDto>

    @GET("inboxes")
    suspend fun getInboxes(): Response<InboxListResponseDto>

    @POST("inboxes")
    suspend fun createInbox(
        @Body request: CreateInboxRequestDto
    ): Response<CreateInboxResponseDto>

    @GET("sync")
    suspend fun sync(
        @Query("since") since: String? = null
    ): Response<SyncResponseDto>
}
