package io.github.aldrinadira.jalanz.core.searchclient

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides
    @Singleton
    fun placeSearch(): PlaceSearch = PhotonPlaceSearch()
}
