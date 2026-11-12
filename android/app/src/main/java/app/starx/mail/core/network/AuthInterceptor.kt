package app.starx.mail.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    @Volatile
    private var token: String? = null

    @Volatile
    private var customBaseUrl: String? = null

    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

    fun setToken(newToken: String?) {
        this.token = newToken
    }

    fun getToken(): String? = token

    fun setCustomBaseUrl(url: String?) {
        this.customBaseUrl = url?.trim()?.ifBlank { null }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        val currentToken = token
        if (!currentToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $currentToken")
        }

        requestBuilder.header("Accept", "application/json")

        val currentUrl = customBaseUrl
        if (!currentUrl.isNullOrBlank()) {
            val parsed = currentUrl.toHttpUrlOrNull()
            if (parsed != null) {
                val newUrl = original.url.newBuilder()
                    .scheme(parsed.scheme)
                    .host(parsed.host)
                    .port(parsed.port)
                    .build()
                requestBuilder.url(newUrl)
            }
        }

        val response = chain.proceed(requestBuilder.build())
        val path = original.url.encodedPath
        if (response.code == 401 && !currentToken.isNullOrBlank() && !path.endsWith("setup/connect")) {
            _sessionExpiredEvents.tryEmit(Unit)
        }
        return response
    }
}
