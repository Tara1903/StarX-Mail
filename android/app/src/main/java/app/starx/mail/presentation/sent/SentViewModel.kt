package app.starx.mail.presentation.sent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.domain.usecase.GetEmailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SentViewModel @Inject constructor(
    private val getEmailsUseCase: GetEmailsUseCase,
    private val emailRepository: EmailRepository
) : ViewModel() {

    val sentEmails: StateFlow<List<Email>> = getEmailsUseCase(EmailFolder.SENT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            emailRepository.refreshEmails(EmailFolder.SENT)
            _isRefreshing.value = false
        }
    }
}
