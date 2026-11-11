package app.starx.mail.data.repository

import app.starx.mail.core.model.Draft
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.data.local.dao.DraftDao
import app.starx.mail.data.remote.StarXApiService
import app.starx.mail.data.remote.dto.AttachmentDto
import app.starx.mail.data.remote.dto.DraftDto
import app.starx.mail.domain.repository.DraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepositoryImpl @Inject constructor(
    private val apiService: StarXApiService,
    private val draftDao: DraftDao
) : DraftRepository {

    override fun getDrafts(): Flow<List<Draft>> {
        return draftDao.getAllDrafts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDraftById(id: String): Draft? {
        return draftDao.getDraftById(id)?.toDomain()
    }

    override suspend fun saveDraft(draft: Draft): NetworkResult<Draft> {
        val draftId = if (draft.id.isBlank()) "draft_${UUID.randomUUID()}" else draft.id
        val updatedDraft = draft.copy(
            id = draftId,
            updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
        )

        // Save locally first
        draftDao.insertDraft(updatedDraft.toEntity())

        // Sync with backend
        return try {
            val dto = DraftDto(
                id = updatedDraft.id,
                inboxId = updatedDraft.inboxId,
                to = updatedDraft.to,
                cc = updatedDraft.cc,
                bcc = updatedDraft.bcc,
                subject = updatedDraft.subject,
                textBody = updatedDraft.textBody,
                htmlBody = updatedDraft.htmlBody,
                attachments = updatedDraft.attachments.map {
                    AttachmentDto(
                        id = it.id,
                        filename = it.filename,
                        contentType = it.contentType,
                        size = it.size,
                        contentBase64 = it.contentBase64
                    )
                },
                updatedAt = updatedDraft.updatedAt
            )
            apiService.saveDraft(dto)
            NetworkResult.Success(updatedDraft)
        } catch (e: Exception) {
            // Still successful locally
            NetworkResult.Success(updatedDraft)
        }
    }

    override suspend fun deleteDraft(id: String): NetworkResult<Unit> {
        draftDao.deleteDraftById(id)
        return try {
            apiService.deleteDraft(id)
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Success(Unit)
        }
    }
}
