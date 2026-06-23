package com.aionyxe.filebridge.data.server

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
 *
 * Per-session transfer stats (file count, bytes, failures) are accumulated in [FtpSession]
 * attributes and summarized on disconnect.
 *
 * Note: Apache MINA FtpServer never invokes the Ftplet onUpload/onDownload callbacks, so transfers
 * are detected in [afterCommand] from the STOR/RETR reply code instead.
 */
internal class EventBridgeFtplet(
    private val eventBus: ServerEventBus,
    private val connectedClientCount: MutableStateFlow<Int>,
) : DefaultFtplet() {

    override fun onConnect(session: FtpSession): FtpletResult {
        val ip = ip(session)
        // Store IP so AppUserManager can attach it to AuthFailure events.
        SessionContext.currentIp.set(ip)
        connectedClientCount.update { it + 1 }
        eventBus.tryEmit(ServerEvent.ClientConnected(ip))
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        val ip = ip(session)
        connectedClientCount.update { maxOf(0, it - 1) }
        SessionContext.currentIp.remove()
        eventBus.tryEmit(
            ServerEvent.ClientDisconnected(
                ip = ip,
                filesTransferred = getInt(session, KEY_FILES),
                totalBytes = getLong(session, KEY_BYTES),
                failedCount = getInt(session, KEY_FAILED),
            ),
        )
        return FtpletResult.DEFAULT
    }

    /**
     * Detects transfer outcomes from the final reply of a STOR/RETR command:
     * 2xx = success (emit uploaded/downloaded + accumulate size), 4xx/5xx = failure.
     */
    override fun afterCommand(session: FtpSession, request: FtpRequest, reply: FtpReply?): FtpletResult {
        val command = request.command?.uppercase()
        val upload = command == "STOR" || command == "STOU" || command == "APPE"
        val download = command == "RETR"
        if ((upload || download) && reply != null) {
            val ip = ip(session)
            val path = request.argument ?: ""
            when {
                reply.code in 200..299 -> {
                    val size = fileSize(session, request.argument)
                    addInt(session, KEY_FILES, 1)
                    addLong(session, KEY_BYTES, size)
                    eventBus.tryEmit(
                        if (upload) ServerEvent.FileUploaded(ip, path, size)
                        else ServerEvent.FileDownloaded(ip, path, size),
                    )
                }

                reply.code >= 400 -> {
                    addInt(session, KEY_FAILED, 1)
                    eventBus.tryEmit(ServerEvent.TransferFailed(ip, path, upload))
                }
            }
        }
        return FtpletResult.DEFAULT
    }

    // ---- Helpers ----

    private fun ip(session: FtpSession): String =
        session.clientAddress?.address?.hostAddress ?: "unknown"

    private fun fileSize(session: FtpSession, path: String?): Long = runCatching {
        if (path.isNullOrEmpty()) 0L else session.fileSystemView.getFile(path).size
    }.getOrDefault(0L)

    private fun getInt(session: FtpSession, key: String): Int =
        (session.getAttribute(key) as? Int) ?: 0

    private fun getLong(session: FtpSession, key: String): Long =
        (session.getAttribute(key) as? Long) ?: 0L

    private fun addInt(session: FtpSession, key: String, delta: Int) {
        session.setAttribute(key, getInt(session, key) + delta)
    }

    private fun addLong(session: FtpSession, key: String, delta: Long) {
        session.setAttribute(key, getLong(session, key) + delta)
    }

    private companion object {
        const val KEY_FILES = "fb.files"
        const val KEY_BYTES = "fb.bytes"
        const val KEY_FAILED = "fb.failed"
    }
}
