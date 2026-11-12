package app.starx.mail.data.repository

import app.starx.mail.core.model.Account
import app.starx.mail.core.model.Domain
import app.starx.mail.core.model.Inbox
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.data.local.dao.AccountDao
import app.starx.mail.data.local.dao.InboxDao
import app.starx.mail.data.remote.StarXApiService
import app.starx.mail.data.remote.dto.AddDomainRequestDto
import app.starx.mail.data.remote.dto.CreateInboxRequestDto
import app.starx.mail.data.remote.dto.UpdateDefaultSenderRequestDto
import app.starx.mail.domain.repository.AccountRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val apiService: StarXApiService,
    private val accountDao: AccountDao,
    private val inboxDao: InboxDao
) : AccountRepository {

    override fun getAccount(): Flow<Account?> {
        return combine(
            accountDao.getAccount(),
            inboxDao.getAllInboxes()
        ) { accountEntity, inboxEntities ->
            accountEntity?.toDomain(inboxes = inboxEntities.map { it.toDomain() })
        }
    }

    override suspend fun refreshStatus(): NetworkResult<Account> {
        return try {
            val response = apiService.getStatus()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val accountDto = body.account
                if (accountDto != null) {
                    val domainAccount = accountDto.toDomain()
                    accountDao.insertAccount(domainAccount.toEntity())
                    inboxDao.insertInboxes(domainAccount.inboxes.map { it.toEntity() })
                    NetworkResult.Success(domainAccount)
                } else {
                    NetworkResult.Error("Session expired or account not found", 401)
                }
            } else {
                NetworkResult.Error("Failed to fetch status: ${parseError(response)}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Status error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun getInboxes(): NetworkResult<List<Inbox>> {
        return try {
            val response = apiService.getInboxes()
            if (response.isSuccessful && response.body() != null) {
                val inboxes = response.body()!!.inboxes.map { it.toDomain() }
                inboxDao.insertInboxes(inboxes.map { it.toEntity() })
                NetworkResult.Success(inboxes)
            } else {
                NetworkResult.Error("Failed to get inboxes: ${parseError(response)}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Inboxes error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun createInbox(
        emailAddress: String,
        name: String,
        fromName: String?
    ): NetworkResult<Inbox> {
        return try {
            val response = apiService.createInbox(
                CreateInboxRequestDto(
                    emailAddress = emailAddress,
                    name = name,
                    fromName = fromName
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val inbox = response.body()!!.inbox.toDomain()
                inboxDao.insertInboxes(listOf(inbox.toEntity()))
                NetworkResult.Success(inbox)
            } else {
                NetworkResult.Error("Failed to create inbox: ${parseError(response)}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Creation error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun addDomain(domainName: String, region: String): NetworkResult<Domain> {
        return try {
            val response = apiService.addDomain(AddDomainRequestDto(name = domainName, region = region))
            if (response.isSuccessful && response.body()?.domain != null) {
                val body = response.body()!!
                val newDomain = body.domain!!.toDomain()
                val existingAccount = accountDao.getAccountSync()
                if (existingAccount != null && body.domains.isNotEmpty()) {
                    accountDao.insertAccount(existingAccount.copy(domains = body.domains.map { it.toDomain() }))
                } else {
                    refreshStatus()
                }
                NetworkResult.Success(newDomain)
            } else {
                NetworkResult.Error(parseError(response), response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Failed to add domain: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun verifyDomain(domainId: String): NetworkResult<Domain?> {
        return try {
            val response = apiService.verifyDomain(domainId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val existingAccount = accountDao.getAccountSync()
                if (existingAccount != null && body.domains.isNotEmpty()) {
                    accountDao.insertAccount(existingAccount.copy(domains = body.domains.map { it.toDomain() }))
                } else {
                    refreshStatus()
                }
                NetworkResult.Success(body.domain?.toDomain())
            } else {
                NetworkResult.Error(parseError(response), response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Verification failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun updateDefaultSender(emailAddress: String): NetworkResult<Account> {
        return try {
            val response = apiService.updateDefaultSender(UpdateDefaultSenderRequestDto(emailAddress))
            if (response.isSuccessful && response.body() != null) {
                val updatedAccount = response.body()!!.account.toDomain()
                val currentLocal = accountDao.getAccountSync()
                val merged = updatedAccount.copy(
                    domains = if (updatedAccount.domains.isNotEmpty()) updatedAccount.domains else (currentLocal?.domains ?: emptyList())
                )
                accountDao.insertAccount(merged.toEntity())
                NetworkResult.Success(merged)
            } else {
                NetworkResult.Error(parseError(response), response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Update failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun parseError(response: Response<*>): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (!raw.isNullOrBlank()) {
            try {
                val json = JsonParser.parseString(raw).asJsonObject
                if (json.has("message")) return json.get("message").asString
                if (json.has("error")) return json.get("error").asString
            } catch (_: Exception) {
                return raw
            }
        }
        val msg = response.message()
        return if (!msg.isNullOrBlank()) msg else "HTTP ${response.code()}"
    }
}
