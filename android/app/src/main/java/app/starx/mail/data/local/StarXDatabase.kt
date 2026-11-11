package app.starx.mail.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.starx.mail.data.local.dao.*
import app.starx.mail.data.local.entity.*

@Database(
    entities = [
        EmailEntity::class,
        AttachmentEntity::class,
        DraftEntity::class,
        AccountEntity::class,
        InboxEntity::class,
        SyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StarXDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao
    abstract fun draftDao(): DraftDao
    abstract fun accountDao(): AccountDao
    abstract fun inboxDao(): InboxDao
    abstract fun syncStateDao(): SyncStateDao
}
