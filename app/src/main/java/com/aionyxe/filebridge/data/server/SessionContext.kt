package com.aionyxe.filebridge.data.server

/**
 * Thread-local storage that lets [EventBridgeFtplet] propagate the current client IP to
 * [AppUserManager.authenticate], which runs on the same FTPServer worker thread.
 */
internal object SessionContext {
    val currentIp: ThreadLocal<String> = ThreadLocal()
}
