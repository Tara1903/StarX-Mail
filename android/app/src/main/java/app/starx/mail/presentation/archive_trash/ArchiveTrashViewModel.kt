package app.starx.mail.presentation.archive_trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.domain.usecase.GetEmailsUseCase
import app.starx.mail.domain.usecase.ToggleStarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveTrashViewModel @Inject constructor(
    getEmailsUseCase: GetEmailsUseCase,
    private val toggleStarUseCase: ToggleStarUseCase,
    private val emailRepository: EmailRepository
) : ViewModel() {

    val archiveEmails: StateFlow<List<Email>> = getEmailsUseCase(EmailFolder.ARCHIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashEmails: StateFlow<List<Email>> = getEmailsUseCase(EmailFolder.TRASH)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleStar(email: Email) {
        viewModelScope.launch {
            toggleStarUseCase(email.id, !email.isStarred)
        }
    }

    fun restoreToInbox(email: Email) {
        viewModelScope.launch {
            emailRepository.moveToFolder(email.id, EmailFolder.INBOX)
        }
    }

    fun permanentlyDelete(email: Email) {
        viewModelScope.launch {
            emailRepository.deleteEmail(email.id)
        }
    }
}
