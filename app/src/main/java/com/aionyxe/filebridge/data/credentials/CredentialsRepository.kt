package com.aionyxe.filebridge.data.credentials

import kotlinx.coroutines.flow.Flow

interface CredentialsRepository {
    /** Hot flow that re-emits whenever the stored password changes. */
    val passwordFlow: Flow<String?>

    suspend fun getPassword(): String?

    suspend fun setPassword(value: String)

    suspend fun clear()
}
