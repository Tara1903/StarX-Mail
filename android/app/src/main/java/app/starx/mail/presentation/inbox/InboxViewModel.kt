package app.starx.mail.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.domain.usecase.GetEmailsUseCase
import app.starx.mail.domain.usecase.ToggleStarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val getEmailsUseCase: GetEmailsUseCase,
    private val toggleStarUseCase: ToggleStarUseCase,
    private val emailRepository: EmailRepository
) : ViewModel() {

    val emails: StateFlow<List<Email>> = getEmailsUseCase(EmailFolder.INBOX)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = emailRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = emailRepository.refreshEmails(EmailFolder.INBOX)) {
                is NetworkResult.Error -> _errorMessage.value = result.message
                else -> _errorMessage.value = null
            }
            _isRefreshing.value = false
        }
    }

    fun toggleStar(email: Email) {
        viewModelScope.launch {
            toggleStarUseCase(email.id, !email.isStarred)
        }
    }

    fun archiveEmail(email: Email) {
        viewModelScope.launch {
            emailRepository.moveToFolder(email.id, EmailFolder.ARCHIVE)
        }
    }

    fun deleteEmail(email: Email) {
        viewModelScope.launch {
            emailRepository.deleteEmail(email.id)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
