package app.starx.mail.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Account
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.usecase.ConnectAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingUiState {
    object Idle : OnboardingUiState()
    object Loading : OnboardingUiState()
    data class Success(val account: Account) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val connectAccountUseCase: ConnectAccountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _backendUrl = MutableStateFlow("")
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _emailPrefix = MutableStateFlow("")
    val emailPrefix: StateFlow<String> = _emailPrefix.asStateFlow()

    fun onApiKeyChanged(key: String) {
        _apiKey.value = key
    }

    fun onBackendUrlChanged(url: String) {
        _backendUrl.value = url
    }

    fun onEmailPrefixChanged(prefix: String) {
        _emailPrefix.value = prefix
    }

    fun connect() {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            _uiState.value = OnboardingUiState.Error("Please enter your Resend API key.")
            return
        }

        if (!key.startsWith("re_")) {
            _uiState.value = OnboardingUiState.Error("Resend API key must start with 're_'.")
            return
        }

        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading

            val url = _backendUrl.value.trim().ifBlank { null }
            val prefix = _emailPrefix.value.trim().ifBlank { null }

            when (val result = connectAccountUseCase(key, url, prefix)) {
                is NetworkResult.Success -> {
                    _uiState.value = OnboardingUiState.Success(result.data)
                }
                is NetworkResult.Error -> {
                    _uiState.value = OnboardingUiState.Error(result.message)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = OnboardingUiState.Idle
    }
}
