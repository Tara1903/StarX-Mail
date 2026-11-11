package app.starx.mail.core.di

import android.content.Context
import androidx.room.Room
import app.starx.mail.core.util.Constants
import app.starx.mail.data.local.StarXDatabase
import app.starx.mail.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StarXDatabase {
        return Room.databaseBuilder(
            context,
            StarXDatabase::class.java,
            Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideEmailDao(db: StarXDatabase): EmailDao = db.emailDao()

    @Provides
    fun provideDraftDao(db: StarXDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideAccountDao(db: StarXDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideInboxDao(db: StarXDatabase): InboxDao = db.inboxDao()

    @Provides
    fun provideSyncStateDao(db: StarXDatabase): SyncStateDao = db.syncStateDao()
}
