package com.dincer552.hattrickssh.ssh

import android.content.Context
import android.net.Uri
import com.dincer552.hattrickssh.Constants
import com.dincer552.hattrickssh.cloudflare.CloudflareTunnelManager
import com.dincer552.hattrickssh.security.SecureKeyStorage
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayInputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one real SSH session and bridges it to the app's local PTY terminal. */
class SshConnectionManager(context: Context) {

    private val appContext = context.applicationContext
    private val secureStorage = SecureKeyStorage(appContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cloudflare: CloudflareTunnelManager? = null
    private var session: com.jcraft.jsch.Session? = null
    private var shell: ChannelShell? = null
    private var terminalSocket: Socket? = null
    private val connected = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    interface SshCallback {
        fun onConnecting()
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
        fun onAuthRequired(uri: Uri)
        fun onHostKeyVerification(message: String, answer: (Boolean) -> Unit)
        fun onDiagnostic(message: String)
    }

    fun connect(
        host: String = Constants.DEFAULT_HOST,
        port: Int = Constants.DEFAULT_PORT,
        username: String = Constants.DEFAULT_USERNAME,
        callback: SshCallback
    ) {
        callback.onConnecting()
        executor.execute {
            var privateKey: ByteArray? = null
            try {
                closed.set(false)
                check(!connected.get()) { "An SSH session is already active." }

                privateKey = secureStorage.loadPrivateKey()
                    ?: error("SSH private key is not imported. Select your .pem file first.")

                val jsch = JSch()
                jsch.addIdentity("azure-rsa-key", privateKey, null, null)

                val knownHosts = secureStorage.loadKnownHosts()
                if (!knownHosts.isNullOrEmpty()) {
                    ByteArrayInputStream(knownHosts).use { jsch.setKnownHosts(it) }
                }

                val tunnel = CloudflareTunnelManager(
                    appContext,
                    onAuthUrl = { callback.onAuthRequired(it) },
                    onDiagnostic = { callback.onDiagnostic(it) }
                )
                cloudflare = tunnel

                val sshSession = jsch.getSession(username, host, port)
                session = sshSession
                sshSession.setProxy(tunnel.createSshProxy())
                sshSession.setConfig("PreferredAuthentications", "publickey")
                sshSession.setConfig(
                    "StrictHostKeyChecking",
                    if (knownHosts.isNullOrEmpty()) "ask" else "yes"
                )
                sshSession.setUserInfo(object : UserInfo {
                    override fun getPassphrase(): String? = null
                    override fun getPassword(): String? = null
                    override fun promptPassword(message: String): Boolean = false
                    override fun promptPassphrase(message: String): Boolean = false

                    override fun promptYesNo(message: String): Boolean {
                        val latch = CountDownLatch(1)
                        var accepted = false
                        callback.onHostKeyVerification(message) { result ->
                            accepted = result
                            latch.countDown()
                        }
                        return try {
                            latch.await(5, TimeUnit.MINUTES) && accepted
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            false
                        }
                    }

                    override fun showMessage(message: String) {
                        callback.onDiagnostic(message.take(200))
                    }
                })

                sshSession.setTimeout(Constants.CONNECT_TIMEOUT_MS)
                sshSession.connect(Constants.CONNECT_TIMEOUT_MS)
                persistKnownHosts(jsch)

                val channel = sshSession.openChannel("shell") as ChannelShell
                shell = channel
                channel.setPty(true)
                channel.setPtyType("xterm-256color")
                channel.setPtySize(
                    Constants.DEFAULT_COLS,
                    Constants.DEFAULT_ROWS,
                    Constants.DEFAULT_PX_PER_COL,
                    Constants.DEFAULT_PX_PER_ROW
                )
                channel.connect(Constants.CONNECT_TIMEOUT_MS)
                connected.set(true)
                callback.onConnected()
            } catch (e: Exception) {
                disconnectInternal()
                callback.onError(safeMessage(e))
            } finally {
                privateKey?.fill(0)
            }
        }
    }

    private fun persistKnownHosts(jsch: JSch) {
        val keys = jsch.hostKeyRepository.hostKey
        check(keys.isNotEmpty()) { "SSH sunucu parmak izi alınamadı; bağlantı güvenli biçimde kaydedilemedi." }
        val text = buildString {
            keys.forEach { key ->
                append(key.host).append(' ')
                    .append(key.type).append(' ')
                    .append(key.key).append('\n')
            }
        }
        check(text.isNotBlank()) { "SSH sunucu parmak izi boş." }
        check(secureStorage.storeKnownHosts(text.toByteArray(Charsets.US_ASCII))) {
            "SSH sunucu parmak izi güvenli olarak kaydedilemedi."
        }
    }

    /** Called after the terminal's sshbridge process connects to this loopback socket. */
    fun attachTerminalSocket(socket: Socket, callback: SshCallback) {
        val channel = shell
        if (!connected.get() || channel == null || !channel.isConnected) {
            socket.close()
            callback.onError("SSH shell is not connected.")
            return
        }
        terminalSocket = socket
        socket.tcpNoDelay = true

        val remoteInput = channel.inputStream
        val remoteOutput = channel.outputStream

        Thread({
            try {
                socket.getInputStream().use { localInput ->
                    remoteOutput.use { sshOutput -> copyStream(localInput, sshOutput) }
                }
            } catch (e: Exception) {
                if (!closed.get()) callback.onDiagnostic("Terminal input ended: ${safeMessage(e)}")
            } finally {
                closeTerminalSocket()
            }
        }, "pty-to-ssh").apply { isDaemon = true; start() }

        Thread({
            try {
                remoteInput.use { sshInput ->
                    socket.getOutputStream().use { localOutput -> copyStream(sshInput, localOutput) }
                }
            } catch (e: Exception) {
                if (!closed.get()) callback.onDiagnostic("SSH output ended: ${safeMessage(e)}")
            } finally {
                closeTerminalSocket()
                if (!closed.get()) callback.onDisconnected()
            }
        }, "ssh-to-pty").apply { isDaemon = true; start() }
    }

    private fun copyStream(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(8192)
        while (!closed.get()) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) {
                output.write(buffer, 0, count)
                output.flush()
            }
        }
    }

    fun resizeTerminal(columns: Int, rows: Int, pixelWidth: Int, pixelHeight: Int) {
        try {
            shell?.setPtySize(columns.coerceAtLeast(1), rows.coerceAtLeast(1), pixelWidth, pixelHeight)
        } catch (_: Exception) {
            // The next PTY update will retry with the new dimensions.
        }
    }

    fun disconnect() {
        closed.set(true)
        disconnectInternal()
        executor.shutdownNow()
    }

    @Synchronized
    private fun disconnectInternal() {
        connected.set(false)
        closeTerminalSocket()
        try { shell?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        try { cloudflare?.stop() } catch (_: Exception) {}
        shell = null
        session = null
        cloudflare = null
    }

    private fun closeTerminalSocket() {
        try { terminalSocket?.close() } catch (_: Exception) {}
        terminalSocket = null
    }

    fun isConnected(): Boolean = connected.get()

    private fun safeMessage(error: Throwable): String {
        return (error.message ?: error.javaClass.simpleName)
            .replace(Regex("(?i)(token|secret|authorization|cookie)=?[^\\s]+"), "$1=[redacted]")
            .take(300)
    }
}
