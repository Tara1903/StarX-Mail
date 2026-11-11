package app.starx.mail.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Email
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.domain.usecase.ToggleStarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val toggleStarUseCase: ToggleStarUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Email>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else emailRepository.searchEmails(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun clearQuery() {
        _searchQuery.value = ""
    }

    fun toggleStar(email: Email) {
        viewModelScope.launch {
            toggleStarUseCase(email.id, !email.isStarred)
        }
    }
}
