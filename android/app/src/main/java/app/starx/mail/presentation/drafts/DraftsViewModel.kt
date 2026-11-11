package app.starx.mail.presentation.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Draft
import app.starx.mail.domain.repository.DraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftsViewModel @Inject constructor(
    private val draftRepository: DraftRepository
) : ViewModel() {

    val drafts: StateFlow<List<Draft>> = draftRepository.getDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDraft(draft: Draft) {
        viewModelScope.launch {
            draftRepository.deleteDraft(draft.id)
        }
    }
}
