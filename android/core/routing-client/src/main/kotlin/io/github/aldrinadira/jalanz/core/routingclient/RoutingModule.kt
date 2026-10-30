package io.github.aldrinadira.jalanz.core.routingclient

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {
    @Provides
    @Singleton
    fun routingClient(): RoutingClient = ValhallaRoutingClient()
}
