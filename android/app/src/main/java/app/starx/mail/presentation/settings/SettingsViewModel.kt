package app.starx.mail.presentation.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.starx.mail.core.model.Account
import app.starx.mail.core.model.Inbox
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.core.security.BiometricAuthManager
import app.starx.mail.core.util.Constants
import app.starx.mail.data.repository.dataStore
import app.starx.mail.domain.repository.AccountRepository
import app.starx.mail.domain.repository.AuthRepository
import app.starx.mail.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    private val biometricAuthManager: BiometricAuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val account: StateFlow<Account?> = accountRepository.getAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val biometricKey = androidx.datastore.preferences.core.booleanPreferencesKey(Constants.KEY_BIOMETRIC_ENABLED)
    val isBiometricEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { it[biometricKey] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val syncIntervalKey = androidx.datastore.preferences.core.longPreferencesKey(Constants.KEY_SYNC_INTERVAL_MINUTES)
    val syncIntervalMinutes: StateFlow<Long> = context.dataStore.data
        .map { it[syncIntervalKey] ?: 15L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15L)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val canUseBiometrics: Boolean = biometricAuthManager.canAuthenticate()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = accountRepository.refreshStatus()) {
                is NetworkResult.Error -> _statusMessage.value = result.message
                else -> {}
            }
            _isRefreshing.value = false
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[biometricKey] = enabled
            }
        }
    }

    fun setSyncInterval(minutes: Long) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[syncIntervalKey] = minutes
            }
            if (minutes > 0) {
                syncScheduler.schedulePeriodicSync(minutes)
                _statusMessage.value = "Sync interval set to $minutes minutes"
            } else {
                syncScheduler.cancelAllSync()
                _statusMessage.value = "Background sync set to Manual"
            }
        }
    }

    fun syncNow() {
        syncScheduler.triggerImmediateSync()
        _statusMessage.value = "Immediate sync initiated"
    }

    fun createInbox(emailAddress: String, name: String, fromName: String?) {
        viewModelScope.launch {
            when (val result = accountRepository.createInbox(emailAddress, name, fromName)) {
                is NetworkResult.Success -> {
                    _statusMessage.value = "Created inbox: ${result.data.emailAddress}"
                    refreshStatus()
                }
                is NetworkResult.Error -> {
                    _statusMessage.value = "Failed: ${result.message}"
                }
                else -> {}
            }
        }
    }

    fun addDomain(domainName: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = accountRepository.addDomain(domainName)) {
                is NetworkResult.Success -> {
                    _statusMessage.value = "Domain added: ${result.data.name}. Add the DNS records and verify."
                    refreshStatus()
                }
                is NetworkResult.Error -> {
                    _statusMessage.value = "Add domain failed: ${result.message}"
                }
                else -> {}
            }
            _isRefreshing.value = false
        }
    }

    fun verifyDomain(domainId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = accountRepository.verifyDomain(domainId)) {
                is NetworkResult.Success -> {
                    _statusMessage.value = "DNS verification check triggered."
                    refreshStatus()
                }
                is NetworkResult.Error -> {
                    _statusMessage.value = "Verify failed: ${result.message}"
                }
                else -> {}
            }
            _isRefreshing.value = false
        }
    }

    fun updateDefaultSender(emailAddress: String) {
        viewModelScope.launch {
            when (val result = accountRepository.updateDefaultSender(emailAddress)) {
                is NetworkResult.Success -> {
                    _statusMessage.value = "Default sender set to ${result.data.defaultFromAddress}"
                }
                is NetworkResult.Error -> {
                    _statusMessage.value = "Failed to update sender: ${result.message}"
                }
                else -> {}
            }
        }
    }

    fun notifyMessage(message: String) {
        _statusMessage.value = message
    }

    fun disconnect(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.disconnect()
            onComplete()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
