package com.aionyxe.filebridge.data.server

import com.aionyxe.filebridge.domain.server.ServerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import org.apache.ftpserver.ftplet.DefaultFtplet
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
        connectedClientCount.value++
        eventBus.tryEmit(ServerEvent.ClientConnected(ip))
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        val ip = session.clientAddress.address.hostAddress ?: "unknown"
        connectedClientCount.value = maxOf(0, connectedClientCount.value - 1)
        SessionContext.currentIp.remove()
        eventBus.tryEmit(ServerEvent.ClientDisconnected(ip))
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
}
