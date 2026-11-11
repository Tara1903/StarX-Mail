package app.starx.mail.data.repository

import app.starx.mail.core.model.Account
import app.starx.mail.core.model.Inbox
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.data.local.dao.AccountDao
import app.starx.mail.data.local.dao.InboxDao
import app.starx.mail.data.remote.StarXApiService
import app.starx.mail.data.remote.dto.CreateInboxRequestDto
import app.starx.mail.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
                    NetworkResult.Error("No account data returned")
                }
            } else {
                NetworkResult.Error("Failed to fetch status: ${response.message()}", response.code())
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
                NetworkResult.Error("Failed to get inboxes: ${response.message()}", response.code())
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
                NetworkResult.Error("Failed to create inbox: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error("Creation error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
