package com.fatlosstrack.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the application-wide [CoroutineScope] managed by Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * Provides a singleton [CoroutineScope] tied to the application lifecycle.
     * Uses [SupervisorJob] so one child failure doesn't cancel siblings,
     * and [Dispatchers.IO] since summary generation is IO-bound.
     *
     * Hilt destroys SingletonComponent when the process dies, which cancels
     * the SupervisorJob and all children — no more orphaned coroutines.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
