package com.dincer552.hattrickssh.cloudflare

import android.content.Context
import android.net.Uri
import com.dincer552.hattrickssh.Constants
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implements OpenSSH ProxyCommand using cloudflared's stdio.
 * stdout is the SSH byte stream; stderr is diagnostics and possible login URL.
 */
class CloudflareTunnelManager(
    context: Context,
    private val onAuthUrl: (Uri) -> Unit,
    private val onDiagnostic: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private var process: Process? = null
    private var processInput: InputStream? = null
    private var processOutput: OutputStream? = null
    private var stderrThread: Thread? = null

    @Synchronized
    fun createSshProxy(): Proxy = CloudflareStdioProxy()

    @Synchronized
    fun stop() {
        try { processInput?.close() } catch (_: Exception) {}
        try { processOutput?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        try { if (process?.isAlive == true) process?.destroyForcibly() } catch (_: Exception) {}
        process = null
        processInput = null
        processOutput = null
        stderrThread?.interrupt()
        stderrThread = null
    }

    @Synchronized
    private fun start(hostname: String) {
        stop()
        val binary = packagedExecutable(Constants.CLOUDFLARED_NATIVE_LIB_NAME)
        val home = File(appContext.filesDir, "cloudflared-home")
        val config = File(home, ".cloudflared")
        check(config.exists() || config.mkdirs()) { "Cloudflare private config directory could not be created" }

        val child = ProcessBuilder(binary.absolutePath, "access", "ssh", "--hostname", hostname)
            .directory(home)
            .apply {
                environment()["HOME"] = home.absolutePath
                environment()["XDG_CONFIG_HOME"] = home.absolutePath
                environment()["TERM"] = "xterm-256color"
            }
            .start()

        process = child
        processInput = child.inputStream
        processOutput = child.outputStream
        readDiagnostics(child, hostname)
    }

    private fun packagedExecutable(fileName: String): File {
        val nativeDirectory = File(appContext.applicationInfo.nativeLibraryDir)
        val executable = File(nativeDirectory, fileName)
        check(executable.isFile && executable.length() > 1024) {
            "$fileName is missing from the APK native library directory."
        }
        check(executable.canExecute()) {
            "Android did not mark $fileName executable in nativeLibraryDir."
        }
        return executable
    }
    private fun readDiagnostics(child: Process, hostname: String) {
        stderrThread = Thread({
            try {
                child.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val url = AUTH_URL.find(line)?.value?.trimEnd('.', ',', ')', ']', '}')
                        if (url != null && isCloudflareAccessUrl(url, hostname)) {
                            onAuthUrl(Uri.parse(url))
                        } else {
                            onDiagnostic(sanitizeDiagnostic(line))
                        }
                    }
                }
            } catch (_: Exception) {
                // Normal during disconnect.
            }
        }, "cloudflared-stderr").apply {
            isDaemon = true
            start()
        }
    }

    private fun isCloudflareAccessUrl(value: String, hostname: String): Boolean = try {
        val uri = Uri.parse(value)
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        val officialAccessHost = host == "cloudflareaccess.com" ||
            host.endsWith(".cloudflareaccess.com") ||
            host == "cloudflareaccess.org" ||
            host.endsWith(".cloudflareaccess.org")
        val configuredTunnelAccessPath =
            host.equals(hostname, ignoreCase = true) &&
                path.startsWith("/cdn-cgi/access/")
        uri.scheme == "https" && (officialAccessHost || configuredTunnelAccessPath)
    } catch (_: Exception) {
        false
    }

    private fun sanitizeDiagnostic(line: String): String {
        return line
            .replace(AUTH_URL, "[Cloudflare URL hidden]")
            .replace(Regex("(?i)Bearer\\s+[^\\s]+"), "Bearer [redacted]")
            .replace(Regex("(?i)(token|secret|authorization|cookie)=?[^\\s]+"), "$1=[redacted]")
            .take(240)
    }

    private inner class CloudflareStdioProxy : Proxy {
        override fun connect(socket_factory: SocketFactory?, host: String, port: Int, timeout: Int) {
            start(host)
            val child = process ?: error("cloudflared did not start")
            check(child.isAlive) {
                val code = try { child.exitValue() } catch (_: Exception) { -1 }
                "cloudflared exited before SSH started (exit $code)"
            }
        }

        override fun getInputStream(): InputStream =
            processInput ?: error("cloudflared stdout unavailable")

        override fun getOutputStream(): OutputStream =
            processOutput ?: error("cloudflared stdin unavailable")

        override fun getSocket(): Socket? = null

        override fun close() = stop()
    }

    companion object {
        private val AUTH_URL = Regex("""https://\S+""")
    }
}
