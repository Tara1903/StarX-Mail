package app.starx.mail.presentation.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Email
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.domain.usecase.GetEmailsUseCase
import app.starx.mail.domain.usecase.ToggleStarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StarredViewModel @Inject constructor(
    getEmailsUseCase: GetEmailsUseCase,
    private val toggleStarUseCase: ToggleStarUseCase
) : ViewModel() {

    val starredEmails: StateFlow<List<Email>> = getEmailsUseCase(EmailFolder.STARRED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleStar(email: Email) {
        viewModelScope.launch {
            toggleStarUseCase(email.id, !email.isStarred)
        }
    }
}
