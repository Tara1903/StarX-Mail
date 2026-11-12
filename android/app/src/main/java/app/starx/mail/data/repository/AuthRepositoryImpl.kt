package app.starx.mail.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.starx.mail.core.model.Account
import app.starx.mail.core.network.AuthInterceptor
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.core.util.Constants
import app.starx.mail.data.local.dao.AccountDao
import app.starx.mail.data.local.dao.DraftDao
import app.starx.mail.data.local.dao.EmailDao
import app.starx.mail.data.local.dao.InboxDao
import app.starx.mail.data.remote.StarXApiService
import app.starx.mail.data.remote.dto.SetupConnectRequestDto
import app.starx.mail.domain.repository.AuthRepository
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFERENCES_NAME)

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: StarXApiService,
    private val authInterceptor: AuthInterceptor,
    private val accountDao: AccountDao,
    private val emailDao: EmailDao,
    private val draftDao: DraftDao,
    private val inboxDao: InboxDao
) : AuthRepository {

    private val tokenKey = stringPreferencesKey(Constants.KEY_AUTH_TOKEN)
    private val connectedKey = booleanPreferencesKey(Constants.KEY_ACCOUNT_CONNECTED)
    private val backendUrlKey = stringPreferencesKey(Constants.KEY_BACKEND_URL)

    override fun isConnected(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[connectedKey] ?: false
        }
    }

    override suspend fun getStoredToken(): String? {
        val prefs = context.dataStore.data.first()
        val token = prefs[tokenKey]
        val savedBackendUrl = prefs[backendUrlKey]
        if (!savedBackendUrl.isNullOrBlank()) {
            authInterceptor.setCustomBaseUrl(savedBackendUrl)
        }
        if (!token.isNullOrBlank()) {
            authInterceptor.setToken(token)
        }
        return token
    }

    override suspend fun connect(
        resendApiKey: String,
        backendUrl: String?,
        preferredEmailPrefix: String?
    ): NetworkResult<Account> {
        val cleanUrl = backendUrl?.trim()?.ifBlank { null }
        authInterceptor.setCustomBaseUrl(cleanUrl)

        return try {
            val response = apiService.connect(
                SetupConnectRequestDto(
                    resendApiKey = resendApiKey,
                    backendUrl = cleanUrl,
                    preferredEmailPrefix = preferredEmailPrefix
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val token = body.token
                val accountDto = body.account

                authInterceptor.setToken(token)

                context.dataStore.edit { prefs ->
                    prefs[tokenKey] = token
                    prefs[connectedKey] = true
                    if (cleanUrl != null) {
                        prefs[backendUrlKey] = cleanUrl
                    } else {
                        prefs.remove(backendUrlKey)
                    }
                }

                val account = accountDto.toDomain()
                accountDao.insertAccount(account.toEntity())
                inboxDao.insertInboxes(account.inboxes.map { it.toEntity() })

                NetworkResult.Success(account)
            } else {
                val raw = response.errorBody()?.string()
                val msg = if (!raw.isNullOrBlank()) {
                    try {
                        val json = JsonParser.parseString(raw).asJsonObject
                        json.get("message")?.asString ?: json.get("error")?.asString ?: raw
                    } catch (_: Exception) {
                        raw
                    }
                } else {
                    response.message().ifBlank { "HTTP ${response.code()}" }
                }
                NetworkResult.Error("Connection failed: $msg", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun disconnect(): NetworkResult<Unit> {
        return try {
            try {
                apiService.disconnect()
            } catch (_: Exception) {
                // Ignore backend failure on disconnect
            }

            authInterceptor.setToken(null)
            authInterceptor.setCustomBaseUrl(null)

            context.dataStore.edit { prefs ->
                prefs.remove(tokenKey)
                prefs.remove(backendUrlKey)
                prefs[connectedKey] = false
            }

            accountDao.clearAccount()
            inboxDao.clearInboxes()
            emailDao.clearAll()
            draftDao.clearAll()

            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Disconnect error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
