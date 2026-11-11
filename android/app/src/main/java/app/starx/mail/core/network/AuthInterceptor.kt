package app.starx.mail.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    @Volatile
    private var token: String? = null

    fun setToken(newToken: String?) {
        this.token = newToken
    }

    fun getToken(): String? = token

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        val currentToken = token
        if (!currentToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $currentToken")
        }

        requestBuilder.header("Accept", "application/json")
        return chain.proceed(requestBuilder.build())
    }
}
