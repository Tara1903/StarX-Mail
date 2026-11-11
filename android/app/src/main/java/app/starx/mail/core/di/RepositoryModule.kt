package app.starx.mail.core.di

import app.starx.mail.data.repository.AccountRepositoryImpl
import app.starx.mail.data.repository.AuthRepositoryImpl
import app.starx.mail.data.repository.DraftRepositoryImpl
import app.starx.mail.data.repository.EmailRepositoryImpl
import app.starx.mail.domain.repository.AccountRepository
import app.starx.mail.domain.repository.AuthRepository
import app.starx.mail.domain.repository.DraftRepository
import app.starx.mail.domain.repository.EmailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEmailRepository(impl: EmailRepositoryImpl): EmailRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
}
