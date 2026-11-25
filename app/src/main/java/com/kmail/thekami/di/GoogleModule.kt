package com.kmail.thekami.di

import com.kmail.thekami.auth.google.GoogleAuthorizer
import com.kmail.thekami.auth.google.PlayServicesAuthorizer
import com.kmail.thekami.mail.gmail.AccessTokenSource
import com.kmail.thekami.mail.gmail.GmailHttp
import com.kmail.thekami.mail.gmail.UrlConnectionGmailHttp
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleModule {
    @Binds
    abstract fun bindGoogleAuthorizer(impl: PlayServicesAuthorizer): GoogleAuthorizer

    @Binds
    abstract fun bindAccessTokenSource(impl: PlayServicesAuthorizer): AccessTokenSource

    @Binds
    abstract fun bindGmailHttp(impl: UrlConnectionGmailHttp): GmailHttp
}
