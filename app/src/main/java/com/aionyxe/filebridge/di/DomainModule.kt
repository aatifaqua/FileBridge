package com.aionyxe.filebridge.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings for the domain layer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule
