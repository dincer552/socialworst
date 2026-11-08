package com.dincer552.hattrickssh

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.dincer552.hattrickssh.ssh.SshConnectionManager
import com.dincer552.hattrickssh.ssh.SshServerProfile
import com.dincer552.hattrickssh.ssh.SshServerProfileStore
import com.dincer552.hattrickssh.ssh.AuthenticationMethod
import com.dincer552.hattrickssh.security.SshKeyImportManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class TerminalActivity : ComponentActivity(), SshConnectionManager.SshCallback {

    private lateinit var sshManager: SshConnectionManager
    private lateinit var terminalView: TerminalView
    private lateinit var statusView: TextView
    private var terminalSession: TerminalSession? = null
    private var localServer: ServerSocket? = null
    private var tearingDown = false
    private lateinit var profile: SshServerProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 8, 11)
        window.navigationBarColor = android.graphics.Color.rgb(5, 8, 11)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        profile = profileId?.let { SshServerProfileStore(applicationContext).find(it) } ?: run {
            Toast.makeText(this, "Sunucu profili bulunamadı.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sshManager = SshConnectionManager(applicationContext)
        buildTerminalLayout()

        if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY &&
            !SshKeyImportManager(applicationContext).hasPrivateKey(profile.id)
        ) {
            statusView.text = "SSH anahtarı bulunamadı"
            Toast.makeText(this, "Önce bu sunucu profili için private key seçin.", Toast.LENGTH_LONG).show()
        } else {
            sshManager.connect(profile, callback = this)
        }
    }

    private fun buildTerminalLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(5, 8, 11))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(TextView(this).apply {
            text = "SSH CLIENT"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        statusView = TextView(this).apply {
            text = "Cloudflare ile bağlanıyor…"
            textSize = 10f
            setTextColor(android.graphics.Color.rgb(145, 161, 178))
        }
        titleColumn.addView(statusView)
        header.addView(titleColumn, LinearLayout.LayoutParams(0, dp(48), 1f))

        val retryButton = Button(this).apply {
            text = "TEKRAR"
            textSize = 9f
            setOnClickListener { retryConnection() }
        }
        header.addView(retryButton, LinearLayout.LayoutParams(dp(76), dp(42)))

        val disconnectButton = Button(this).apply {
            text = "KES"
            textSize = 10f
            setOnClickListener {
                tearingDown = true
                sshManager.disconnect()
                closeLocalResources()
                statusView.text = "Bağlantı kesildi"
                finish()
            }
        }
        header.addView(disconnectButton, LinearLayout.LayoutParams(dp(58), dp(42)))
        root.addView(header)

        terminalView = TerminalView(this, null).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            setTextSize(12)
            setTerminalViewClient(createTerminalViewClient())
            isFocusable = true
            isFocusableInTouchMode = true
            addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                val emulator = (view as TerminalView).mEmulator
                    ?: return@addOnLayoutChangeListener
                sshManager.resizeTerminal(
                    emulator.mColumns,
                    emulator.mRows,
                    (right - left).coerceAtLeast(1),
                    (bottom - top).coerceAtLeast(1)
                )
            }
        }
        root.addView(
            terminalView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val shortcutScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        addKeyButton(shortcuts, "ESC", byteArrayOf(0x1B))
        addKeyButton(shortcuts, "TAB", byteArrayOf(0x09))
        addKeyButton(shortcuts, "CTRL+C", byteArrayOf(0x03))
        addKeyButton(shortcuts, "CTRL+D", byteArrayOf(0x04))
        addKeyButton(shortcuts, "↑", "\u001b[A".toByteArray())
        addKeyButton(shortcuts, "↓", "\u001b[B".toByteArray())
        addKeyButton(shortcuts, "←", "\u001b[D".toByteArray())
        addKeyButton(shortcuts, "→", "\u001b[C".toByteArray())
        addKeyButton(shortcuts, "ENTER", byteArrayOf(0x0D))
        shortcutScroll.addView(shortcuts)
        root.addView(shortcutScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ))

        setContentView(root)
    }

    private fun addKeyButton(parent: LinearLayout, label: String, bytes: ByteArray) {
        val button = Button(this).apply {
            text = label
            textSize = 9f
            minWidth = dp(44)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setOnClickListener { terminalSession?.write(bytes, 0, bytes.size) }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(42)
        )
        params.marginEnd = dp(3)
        parent.addView(button, params)
    }

    private fun retryConnection() {
        if (tearingDown) return
        closeLocalResources()
        sshManager.disconnect()
        sshManager = SshConnectionManager(applicationContext)
        statusView.text = "Yeniden bağlanıyor…"
        sshManager.connect(profile, callback = this)
    }

    private fun startTerminalBridge() {
        try {
            val bridge = packagedBridge()

            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)
            localServer = server

            Thread({
                try {
                    val socket = server.accept()
                    if (tearingDown) {
                        socket.close()
                    } else {
                        sshManager.attachTerminalSocket(socket, this)
                    }
                } catch (e: Exception) {
                    if (!tearingDown) onError("PTY bağlantısı kurulamadı: ${e.message ?: "socket error"}")
                }
            }, "sshbridge-accept").apply { isDaemon = true; start() }

            val home = File(filesDir, "terminal-home").apply { mkdirs() }
            val args = arrayOf(bridge.absolutePath, "127.0.0.1", server.localPort.toString())
            val env = arrayOf(
                "HOME=${home.absolutePath}",
                "TERM=xterm-256color",
                "PATH=/system/bin:/system/xbin",
                "LANG=C.UTF-8"
            )
            val session = TerminalSession(
                bridge.absolutePath,
                home.absolutePath,
                args,
                env,
                3000,
                createTerminalSessionClient()
            )
            terminalSession = session
            terminalView.attachSession(session)
            terminalView.mEmulator?.let { emulator ->
                sshManager.resizeTerminal(
                    emulator.mColumns,
                    emulator.mRows,
                    terminalView.width.coerceAtLeast(1),
                    terminalView.height.coerceAtLeast(1)
                )
            }
            terminalView.requestFocus()
            terminalView.post {
                terminalView.requestFocus()
                val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                keyboard.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (e: Exception) {
            onError("Terminal başlatılamadı: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun packagedBridge(): File {
        val nativeDirectory = File(applicationInfo.nativeLibraryDir)
        val bridge = File(nativeDirectory, Constants.SSHBRIDGE_LIB_NAME)
        check(bridge.isFile && bridge.canExecute()) {
            "Android native PTY bridge is unavailable or not executable in nativeLibraryDir."
        }
        return bridge
    }
    private fun createTerminalSessionClient() = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            runOnUiThread { terminalView.onScreenUpdated() }
        }
        override fun onTitleChanged(changedSession: TerminalSession) {
            runOnUiThread { statusView.text = changedSession.title ?: "SSH • ${profile.host}" }
        }
        override fun onSessionFinished(finishedSession: TerminalSession) {
            runOnUiThread {
                if (!tearingDown) statusView.text = "Terminal işlemi sona erdi"
            }
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Terminal", text))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@TerminalActivity)?.toString() ?: return
            val bytes = text.toByteArray(Charsets.UTF_8)
            session.write(bytes, 0, bytes.size)
        }
        override fun onBell(session: TerminalSession) {
            runOnUiThread { terminalView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
        }
        override fun onColorsChanged(session: TerminalSession) {
            runOnUiThread { terminalView.invalidate() }
        }
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String, message: String) {}
        override fun logWarn(tag: String, message: String) {}
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    private fun createTerminalViewClient() = object : TerminalViewClient {
        override fun onScale(scale: Float): Float = scale
        override fun onSingleTapUp(e: MotionEvent) {
            terminalView.requestFocus()
        }
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = true
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
        override fun onLongPress(event: MotionEvent): Boolean = false
        override fun readControlKey(): Boolean = false
        override fun readAltKey(): Boolean = false
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
        override fun onEmulatorSet() {}
        override fun logError(tag: String, message: String) {}
        override fun logWarn(tag: String, message: String) {}
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    override fun onConnecting() {
        runOnUiThread { statusView.text = "Cloudflare Access / SSH bağlanıyor…" }
    }

    override fun onConnected() {
        runOnUiThread {
            statusView.text = "Bağlı • ${profile.username}@${profile.host}"
            startTerminalBridge()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            statusView.text = "SSH bağlantısı kesildi • TEKRAR ile yeniden bağlan"
            if (!tearingDown) Toast.makeText(this, "SSH bağlantısı kesildi.", Toast.LENGTH_SHORT).show()
            sshManager.disconnect()
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            statusView.text = "Bağlantı hatası"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onAuthRequired(uri: Uri) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                statusView.text = "Cloudflare girişini tarayıcıda tamamla"
            } catch (_: Exception) {
                Toast.makeText(this, "Cloudflare giriş URL'si açılamadı.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onHostKeyVerification(message: String, answer: (Boolean) -> Unit) {
        runOnUiThread {
            val changed = message.contains("REMOTE HOST IDENTIFICATION HAS CHANGED", ignoreCase = true) ||
                message.contains("host key has been changed", ignoreCase = true)
            val dialog = AlertDialog.Builder(this)
                .setTitle(if (changed) "SSH sunucu anahtarı değişti" else "İlk SSH sunucu doğrulaması")
                .setMessage(
                    "$message\n\n" +
                        if (changed) {
                            "Güvenlik nedeniyle değişmiş bir sunucu anahtarı otomatik kabul edilmeyecek."
                        } else {
                            "Bu parmak izini Azure VM'in gerçek SSH host fingerprint'i ile doğruladıktan sonra güven."
                        }
                )
                .setNegativeButton(if (changed) "Kapat" else "Reddet") { _, _ -> answer(false) }
                .setOnCancelListener { answer(false) }
            if (!changed) {
                dialog.setPositiveButton("Parmak izini kaydet") { _, _ -> answer(true) }
            }
            dialog.show()
        }
    }

    override fun onDiagnostic(message: String) {
        // Diagnostics are intentionally not written to logcat or terminal input.
        runOnUiThread {
            if (message.contains("error", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true)
            ) {
                statusView.text = "Cloudflare: ${message.take(90)}"
            }
        }
    }

    private fun closeLocalResources() {
        try { terminalSession?.finishIfRunning() } catch (_: Exception) {}
        terminalSession = null
        try { localServer?.close() } catch (_: Exception) {}
        localServer = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        tearingDown = true
        closeLocalResources()
        sshManager.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.dincer552.hattrickssh.EXTRA_PROFILE_ID"
    }

}
