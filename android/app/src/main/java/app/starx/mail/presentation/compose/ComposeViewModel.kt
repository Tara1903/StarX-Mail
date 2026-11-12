package app.starx.mail.presentation.compose

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Draft
import app.starx.mail.core.model.EmailAttachment
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.repository.AccountRepository
import app.starx.mail.domain.repository.DraftRepository
import app.starx.mail.domain.usecase.GetEmailDetailsUseCase
import app.starx.mail.domain.usecase.SaveDraftUseCase
import app.starx.mail.domain.usecase.SendEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

sealed class ComposeUiState {
    object Idle : ComposeUiState()
    object Sending : ComposeUiState()
    object Sent : ComposeUiState()
    data class Error(val message: String) : ComposeUiState()
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendEmailUseCase: SendEmailUseCase,
    private val saveDraftUseCase: SaveDraftUseCase,
    private val draftRepository: DraftRepository,
    private val getEmailDetailsUseCase: GetEmailDetailsUseCase,
    private val accountRepository: AccountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val draftId: String? = savedStateHandle["draftId"]
    private val replyToId: String? = savedStateHandle["replyToId"]
    private val replyAll: Boolean = savedStateHandle["replyAll"] ?: false
    private val forwardId: String? = savedStateHandle["forwardId"]

    private val _uiState = MutableStateFlow<ComposeUiState>(ComposeUiState.Idle)
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    private val _fromAddress = MutableStateFlow("")
    val fromAddress: StateFlow<String> = _fromAddress.asStateFlow()

    private val _availableFromAddresses = MutableStateFlow<List<String>>(emptyList())
    val availableFromAddresses: StateFlow<List<String>> = _availableFromAddresses.asStateFlow()

    private val _to = MutableStateFlow("")
    val to: StateFlow<String> = _to.asStateFlow()

    private val _cc = MutableStateFlow("")
    val cc: StateFlow<String> = _cc.asStateFlow()

    private val _bcc = MutableStateFlow("")
    val bcc: StateFlow<String> = _bcc.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _attachments = MutableStateFlow<List<EmailAttachment>>(emptyList())
    val attachments: StateFlow<List<EmailAttachment>> = _attachments.asStateFlow()

    private var activeDraftId: String = draftId ?: "draft_${UUID.randomUUID()}"
    private var inReplyToThreadId: String? = null

    init {
        loadAccountAndInboxes()
        loadInitialData()
    }

    private fun loadAccountAndInboxes() {
        viewModelScope.launch {
            accountRepository.getAccount().collect { account ->
                account?.let { acc ->
                    val candidates = linkedSetOf<String>()
                    if (acc.defaultFromAddress.isNotBlank()) {
                        candidates.add(acc.defaultFromAddress)
                    }
                    acc.inboxes.forEach { inbox ->
                        if (inbox.emailAddress.isNotBlank()) {
                            candidates.add(inbox.emailAddress)
                        }
                    }
                    acc.domains
                        .filter { it.status == "verified" && it.name.isNotBlank() }
                        .forEach { domain ->
                            candidates.add("mail@${domain.name}")
                            candidates.add("hello@${domain.name}")
                            candidates.add("support@${domain.name}")
                            candidates.add("noreply@${domain.name}")
                        }
                    if (candidates.isEmpty()) {
                        candidates.add("onboarding@resend.dev")
                    }
                    val list = candidates.toList()
                    _availableFromAddresses.value = list
                    if (_fromAddress.value.isBlank()) {
                        _fromAddress.value = list.first()
                    }
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            if (!draftId.isNullOrBlank()) {
                val draft = draftRepository.getDraftById(draftId)
                draft?.let {
                    _to.value = it.to.joinToString(", ")
                    _cc.value = it.cc.joinToString(", ")
                    _bcc.value = it.bcc.joinToString(", ")
                    _subject.value = it.subject
                    _body.value = it.textBody
                    _attachments.value = it.attachments
                }
            } else if (!replyToId.isNullOrBlank()) {
                val original = getEmailDetailsUseCase(replyToId).firstOrNull()
                original?.let {
                    inReplyToThreadId = it.threadId
                    _to.value = if (replyAll) {
                        (listOf(it.from) + it.to).distinct().joinToString(", ")
                    } else {
                        it.from
                    }
                    if (replyAll && it.cc.isNotEmpty()) {
                        _cc.value = it.cc.joinToString(", ")
                    }
                    _subject.value = if (it.subject.startsWith("Re: ")) it.subject else "Re: ${it.subject}"
                    _body.value = "\n\nOn ${it.createdAt}, ${it.from} wrote:\n> ${it.textBody.replace("\n", "\n> ")}"
                }
            } else if (!forwardId.isNullOrBlank()) {
                val original = getEmailDetailsUseCase(forwardId).firstOrNull()
                original?.let {
                    _subject.value = if (it.subject.startsWith("Fwd: ")) it.subject else "Fwd: ${it.subject}"
                    _body.value = "\n\n---------- Forwarded message ---------\nFrom: ${it.from}\nDate: ${it.createdAt}\nSubject: ${it.subject}\nTo: ${it.to.joinToString(", ")}\n\n${it.textBody}"
                    _attachments.value = it.attachments
                }
            }
        }
    }

    fun onFromAddressChanged(address: String) { _fromAddress.value = address }
    fun onToChanged(value: String) { _to.value = value }
    fun onCcChanged(value: String) { _cc.value = value }
    fun onBccChanged(value: String) { _bcc.value = value }
    fun onSubjectChanged(value: String) { _subject.value = value }
    fun onBodyChanged(value: String) { _body.value = value }

    fun addAttachmentFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                var filename = "attachment_${System.currentTimeMillis()}"
                var size: Long = 0
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) filename = cursor.getString(nameIndex)
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }

                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                if (size == 0L) size = bytes.size.toLong()

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val attachment = EmailAttachment(
                    id = "att_${UUID.randomUUID()}",
                    filename = filename,
                    contentType = mimeType,
                    size = size,
                    contentBase64 = base64,
                    localUri = uri.toString()
                )

                _attachments.value = _attachments.value + attachment
            } catch (e: Exception) {
                _uiState.value = ComposeUiState.Error("Failed to attach file: ${e.message}")
            }
        }
    }

    fun removeAttachment(attachment: EmailAttachment) {
        _attachments.value = _attachments.value.filter { it.id != attachment.id }
    }

    fun autoSaveDraft() {
        if (_to.value.isBlank() && _subject.value.isBlank() && _body.value.isBlank()) return

        val draft = Draft(
            id = activeDraftId,
            to = parseEmailList(_to.value),
            cc = parseEmailList(_cc.value),
            bcc = parseEmailList(_bcc.value),
            subject = _subject.value,
            textBody = _body.value,
            htmlBody = "<p>${_body.value.replace("\n", "<br/>")}</p>",
            attachments = _attachments.value
        )

        viewModelScope.launch {
            saveDraftUseCase(draft)
        }
    }

    fun sendEmail() {
        val recipients = parseEmailList(_to.value)
        if (recipients.isEmpty()) {
            _uiState.value = ComposeUiState.Error("Please enter at least one recipient email address.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ComposeUiState.Sending

            val result = sendEmailUseCase(
                from = _fromAddress.value.ifBlank { null },
                to = recipients,
                cc = parseEmailList(_cc.value),
                bcc = parseEmailList(_bcc.value),
                subject = _subject.value.ifBlank { "(No Subject)" },
                text = _body.value,
                html = "<p>${_body.value.replace("\n", "<br/>")}</p>",
                attachments = _attachments.value,
                threadId = inReplyToThreadId
            )

            when (result) {
                is NetworkResult.Success -> {
                    // Remove saved draft upon successful delivery
                    draftRepository.deleteDraft(activeDraftId)
                    _uiState.value = ComposeUiState.Sent
                }
                is NetworkResult.Error -> {
                    _uiState.value = ComposeUiState.Error(result.message)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    private fun parseEmailList(raw: String): List<String> {
        return raw.split(",", ";", " ")
            .map { it.trim() }
            .filter { it.contains("@") && it.contains(".") }
    }
}
