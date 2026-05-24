package com.aionyxe.filebridge.data.credentials

interface CredentialsRepository {
    suspend fun getPassword(): String?

    suspend fun setPassword(value: String)

    suspend fun clear()
}
