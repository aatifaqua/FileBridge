package com.aionyxe.filebridge.data.server

import android.util.Log
import com.aionyxe.filebridge.BuildConfig
import com.aionyxe.filebridge.domain.server.ServerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult

/**
 * FTPServer Ftplet that bridges server-side callbacks into [ServerEventBus] and keeps
 * [connectedClientCount] up to date.  Constructed fresh per [FtpServerControllerImpl.start] call
 * so that a stopped server leaves the count at 0.
 */
internal class EventBridgeFtplet(
    private val eventBus: ServerEventBus,
    private val connectedClientCount: MutableStateFlow<Int>,
) : DefaultFtplet() {

    override fun onConnect(session: FtpSession): FtpletResult {
        val ip = session.clientAddress.address.hostAddress ?: "unknown"
        // Store IP so AppUserManager can attach it to AuthFailure events.
        SessionContext.currentIp.set(ip)
        connectedClientCount.update { it + 1 }
        eventBus.tryEmit(ServerEvent.ClientConnected(ip))
        if (BuildConfig.DEBUG) Log.i(TAG, "[$ip] === CONNECTED ===")
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        val ip = session.clientAddress.address.hostAddress ?: "unknown"
        connectedClientCount.update { maxOf(0, it - 1) }
        SessionContext.currentIp.remove()
        eventBus.tryEmit(ServerEvent.ClientDisconnected(ip))
        if (BuildConfig.DEBUG) Log.i(TAG, "[$ip] === DISCONNECTED ===")
        return FtpletResult.DEFAULT
    }

    override fun onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
        val ip = session.clientAddress.address.hostAddress ?: "unknown"
        eventBus.tryEmit(ServerEvent.FileUploaded(ip = ip, path = request.argument ?: ""))
        return FtpletResult.DEFAULT
    }

    override fun onDownloadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
        val ip = session.clientAddress.address.hostAddress ?: "unknown"
        eventBus.tryEmit(ServerEvent.FileDownloaded(ip = ip, path = request.argument ?: ""))
        return FtpletResult.DEFAULT
    }

    // ---- Diagnostic command/response tracing (debug builds only) ----
    //
    // Apache FtpServer logs the protocol exchange via SLF4J, which has no binding on Android, so it
    // is otherwise invisible. These hooks mirror the exchange to Logcat (tag "FtpTrace") so a
    // connect-then-disconnect can be traced to the exact command. Passwords are masked.

    override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult {
        if (BuildConfig.DEBUG) {
            val cmd = request.command ?: ""
            val arg = if (cmd.equals("PASS", ignoreCase = true)) "****" else request.argument.orEmpty()
            Log.i(TAG, "[${ip(session)}] --> $cmd $arg".trimEnd())
        }
        // Must return DEFAULT explicitly: DefaultFtplet returns null, which Apache treats as a
        // signal to close the session, killing every connection after the first command.
        return FtpletResult.DEFAULT
    }

    override fun afterCommand(
        session: FtpSession,
        request: FtpRequest,
        reply: FtpReply?,
    ): FtpletResult {
        if (BuildConfig.DEBUG && reply != null) {
            val firstLine = reply.message?.lineSequence()?.firstOrNull().orEmpty()
            Log.i(TAG, "[${ip(session)}] <-- ${reply.code} $firstLine".trimEnd())
        }
        return FtpletResult.DEFAULT
    }

    private fun ip(session: FtpSession): String =
        session.clientAddress?.address?.hostAddress ?: "unknown"

    private companion object {
        const val TAG = "FtpTrace"
    }
}
