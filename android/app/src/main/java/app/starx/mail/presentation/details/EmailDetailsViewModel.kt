package app.starx.mail.presentation.details

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailAttachment
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.domain.usecase.GetEmailDetailsUseCase
import app.starx.mail.domain.usecase.MarkReadUseCase
import app.starx.mail.domain.usecase.ToggleStarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EmailDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEmailDetailsUseCase: GetEmailDetailsUseCase,
    private val toggleStarUseCase: ToggleStarUseCase,
    private val markReadUseCase: MarkReadUseCase,
    private val emailRepository: EmailRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val emailId: String = checkNotNull(savedStateHandle["emailId"])
    private val threadId: String = savedStateHandle["threadId"] ?: "thread_$emailId"

    val email: StateFlow<Email?> = getEmailDetailsUseCase(emailId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val threadEmails: StateFlow<List<Email>> = getEmailDetailsUseCase.getThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    init {
        viewModelScope.launch {
            markReadUseCase(emailId, true)
        }
    }

    fun toggleStar(currentEmail: Email) {
        viewModelScope.launch {
            toggleStarUseCase(currentEmail.id, !currentEmail.isStarred)
        }
    }

    fun moveToFolder(folder: EmailFolder) {
        viewModelScope.launch {
            emailRepository.moveToFolder(emailId, folder)
        }
    }

    fun deleteEmail() {
        viewModelScope.launch {
            emailRepository.deleteEmail(emailId)
        }
    }

    fun downloadAttachment(attachment: EmailAttachment) {
        viewModelScope.launch {
            _downloadStatus.value = "Downloading ${attachment.filename}..."
            val dir = File(context.cacheDir, "attachments")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, attachment.filename)

            when (val result = emailRepository.downloadAttachment(emailId, attachment.id, destFile)) {
                is NetworkResult.Success -> {
                    _downloadStatus.value = "Downloaded: ${attachment.filename}"
                }
                is NetworkResult.Error -> {
                    _downloadStatus.value = "Failed: ${result.message}"
                }
                else -> {}
            }
        }
    }

    fun clearDownloadStatus() {
        _downloadStatus.value = null
    }
}
