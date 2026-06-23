package com.aionyxe.filebridge.data.server

import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.server.ServerEvent
import org.apache.ftpserver.ftplet.Authentication
import org.apache.ftpserver.ftplet.AuthenticationFailedException
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.usermanager.AnonymousAuthentication
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.security.MessageDigest

/**
 * In-memory [UserManager] built from the active [ServerConfig].  Password is passed in as a
 * plain string resolved before [FtpServerControllerImpl.start] suspends, avoiding `runBlocking`
 * in the FTPServer I/O thread.
 *
 * Authentication uses [MessageDigest.isEqual] on UTF-8 byte arrays to avoid early-exit timing
 * leaks.
 */
internal class AppUserManager(
    private val config: ServerConfig,
    /** Pre-resolved password; null means anonymous-only. */
    private val resolvedPassword: String?,
    private val eventBus: ServerEventBus,
) : UserManager {

    // ---- UserManager implementation ----

    override fun authenticate(authentication: Authentication): User {
        val ip = SessionContext.currentIp.get() ?: "unknown"
        return when {
            authentication is AnonymousAuthentication -> {
                if (config.authMode == AuthMode.ANONYMOUS) {
                    buildUser(name = "anonymous")
                } else {
                    eventBus.tryEmit(ServerEvent.AuthFailure(ip = ip, username = "anonymous"))
                    throw AuthenticationFailedException("Anonymous access is disabled")
                }
            }

            authentication is UsernamePasswordAuthentication -> {
                // resolvedPassword == null means credentials were not available at server-start
                // time; always reject rather than falling back to empty-string comparison which
                // would accept any client that sends an empty password.
                val ok = config.authMode == AuthMode.SINGLE_USER &&
                    resolvedPassword != null &&
                    constantTimeEquals(authentication.username, config.username) &&
                    constantTimeEquals(authentication.password, resolvedPassword)
                if (ok) {
                    buildUser(name = authentication.username)
                } else {
                    eventBus.tryEmit(
                        ServerEvent.AuthFailure(ip = ip, username = authentication.username),
                    )
                    throw AuthenticationFailedException("Bad credentials")
                }
            }

            else -> throw AuthenticationFailedException("Unsupported auth type")
        }
    }

    override fun getUserByName(userName: String): User = when {
        config.authMode == AuthMode.ANONYMOUS && userName == "anonymous" ->
            buildUser("anonymous")
        config.authMode == AuthMode.SINGLE_USER && userName == config.username ->
            buildUser(config.username)
        else -> throw FtpException("User not found: $userName")
    }

    override fun getAllUserNames(): Array<String> = when (config.authMode) {
        AuthMode.ANONYMOUS -> arrayOf("anonymous")
        AuthMode.SINGLE_USER -> arrayOf(config.username)
    }

    override fun doesExist(userName: String): Boolean = when (config.authMode) {
        AuthMode.ANONYMOUS -> userName == "anonymous"
        AuthMode.SINGLE_USER -> userName == config.username
    }

    override fun getAdminName(): String = config.username

    override fun isAdmin(userName: String): Boolean = false

    // No persistent backing store — these are no-ops.
    override fun delete(userName: String) = Unit
    override fun save(user: User) = Unit

    // ---- Helpers ----

    private fun buildUser(name: String): User {
        val authorities = mutableListOf<Authority>()
        if (config.accessMode == AccessMode.READ_WRITE) {
            authorities.add(WritePermission())
        }
        // Required for login to succeed at all: on every login Apache issues a
        // ConcurrentLoginRequest, and BaseUser.authorize() returns null (-> "421 Maximum login
        // limit has been reached") unless some authority can handle it. (0, 0) = unlimited
        // concurrent logins / per-IP, which suits a personal server and clients like Windows
        // Explorer that open many parallel connections.
        authorities.add(ConcurrentLoginPermission(0, 0))
        return BaseUser().apply {
            this.name = name
            homeDirectory = config.rootDir.absolutePath
            this.authorities = authorities
            setEnabled(true)
            maxIdleTime = 0 // no forced logout
        }
    }

    private companion object {
        /** Constant-time string comparison via [MessageDigest.isEqual]. */
        fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}
