package app.starx.mail.core.network

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()

    fun toResult(): Result<T> = when (this) {
        is Success -> Result.success(data)
        is Error -> Result.failure(Exception(message))
        is Loading -> Result.failure(IllegalStateException("Still loading"))
    }
}
