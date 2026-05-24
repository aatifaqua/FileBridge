package com.aionyxe.filebridge.domain.model

data class ConnectionInfo(
    val url: String,
    val username: String?,
    val password: String?,
    val protocol: Protocol,
)
