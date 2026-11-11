package app.starx.mail.data.local.dao

import androidx.room.*
import app.starx.mail.core.model.EmailFolder
import app.starx.mail.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE folder = :folder ORDER BY createdAt DESC")
    fun getEmailsByFolder(folder: EmailFolder): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE isStarred = 1 AND folder != 'TRASH' ORDER BY createdAt DESC")
    fun getStarredEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id")
    suspend fun getEmailById(id: String): EmailEntity?

    @Query("SELECT * FROM emails WHERE id = :id")
    fun getEmailByIdFlow(id: String): Flow<EmailEntity?>

    @Query("SELECT * FROM emails WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun getEmailsByThreadId(threadId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE subject LIKE '%' || :query || '%' OR fromAddress LIKE '%' || :query || '%' OR snippet LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchEmails(query: String): Flow<List<EmailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity)

    @Update
    suspend fun updateEmail(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :id")
    suspend fun markAsRead(id: String, isRead: Boolean)

    @Query("UPDATE emails SET isStarred = :isStarred WHERE id = :id")
    suspend fun setStarred(id: String, isStarred: Boolean)

    @Query("UPDATE emails SET folder = :folder WHERE id = :id")
    suspend fun updateFolder(id: String, folder: EmailFolder)

    @Query("DELETE FROM emails WHERE id = :id")
    suspend fun deleteEmailById(id: String)

    @Query("DELETE FROM emails")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM emails WHERE folder = 'INBOX' AND isRead = 0")
    fun getUnreadCount(): Flow<Int>

    // Attachments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE emailId = :emailId")
    suspend fun getAttachmentsForEmail(emailId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE emailId = :emailId")
    fun getAttachmentsForEmailFlow(emailId: String): Flow<List<AttachmentEntity>>
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun getAllDrafts(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun getDraftById(id: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteDraftById(id: String)

    @Query("DELETE FROM drafts")
    suspend fun clearAll()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM account LIMIT 1")
    fun getAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM account LIMIT 1")
    suspend fun getAccountSync(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Query("DELETE FROM account")
    suspend fun clearAccount()
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inboxes ORDER BY createdAt ASC")
    fun getAllInboxes(): Flow<List<InboxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInboxes(inboxes: List<InboxEntity>)

    @Query("DELETE FROM inboxes")
    suspend fun clearInboxes()
}

@Dao
interface SyncStateDao {
    @Query("SELECT lastSyncTimestamp FROM sync_state WHERE `key` = :key")
    suspend fun getLastSyncTimestamp(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLastSyncTimestamp(state: SyncStateEntity)
}
