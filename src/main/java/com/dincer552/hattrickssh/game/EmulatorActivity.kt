/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Embedded NES player built on LibretroDroid / FCEUmm.
 * ROM images are imported by the user and kept in app-private storage.
 */
package com.dincer552.hattrickssh.game

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class EmulatorActivity : ComponentActivity() {

    private var retroView: GLRetroView? = null

    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importAndStart(uri)
        } else if (retroView == null) {
            finish()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val savedRom = File(filesDir, "nes-roms/current.nes")
        if (savedRom.isFile && savedRom.length() > 16L) {
            startEmulator(savedRom)
        } else {
            showPickerPrompt()
            openRomPicker()
        }
    }

    private fun showPickerPrompt() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 12, 20)) }
        val prompt = TextView(this).apply {
            text = "Bucky O'Hare NES\n\nChoose a .nes or .zip ROM file"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        root.addView(prompt, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun openRomPicker() {
        romPicker.launch(arrayOf("*/*"))
    }

    private fun importAndStart(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected file could not be opened.")
            val sourceBytes = input.use { readBounded(it, MAX_ARCHIVE_BYTES) }
            val romBytes = if (
                sourceBytes.size >= 4 &&
                sourceBytes[0] == 'P'.code.toByte() &&
                sourceBytes[1] == 'K'.code.toByte()
            ) {
                extractNesFromZip(sourceBytes)
            } else {
                sourceBytes
            }

            validateNesRom(romBytes)
            val romDirectory = File(filesDir, "nes-roms").apply { mkdirs() }
            val romFile = File(romDirectory, "current.nes")
            romFile.writeBytes(romBytes)
            if (retroView != null) {
                recreate()
            } else {
                startEmulator(romFile)
            }
        } catch (error: Exception) {
            Toast.makeText(this, "ROM could not be loaded: ${error.message}", Toast.LENGTH_LONG).show()
            if (retroView == null) finish()
        }
    }

    private fun extractNesFromZip(archive: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/').endsWith(".nes", ignoreCase = true)) {
                    return readBounded(zip, MAX_ROM_BYTES)
                }
            }
        }
        throw IllegalArgumentException("No .nes file was found inside the ZIP archive.")
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximum) { "The selected file is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun validateNesRom(bytes: ByteArray) {
        require(bytes.size in 16..MAX_ROM_BYTES) { "Invalid ROM size." }
        require(
            bytes[0] == 'N'.code.toByte() &&
                bytes[1] == 'E'.code.toByte() &&
                bytes[2] == 'S'.code.toByte() &&
                bytes[3] == 0x1A.toByte()
        ) { "This file does not have a valid iNES header." }
    }

    private fun startEmulator(romFile: File) {
        val coreFile = File(applicationInfo.nativeLibraryDir, CORE_FILE_NAME)
        if (!coreFile.isFile) {
            Toast.makeText(this, "NES core is missing from this build.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val systemDirectory = File(filesDir, "nes-system").apply { mkdirs() }
        val savesDirectory = File(filesDir, "nes-saves").apply { mkdirs() }
        val data = GLRetroViewData(this).apply {
            coreFilePath = coreFile.absolutePath
            gameFilePath = romFile.absolutePath
            systemDirectory = systemDirectory.absolutePath
            savesDirectory = savesDirectory.absolutePath
            preferLowLatencyAudio = true
        }

        val emulatorView = GLRetroView(this, data)
        retroView = emulatorView
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(emulatorView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        addControls(root)
        setContentView(root)
        lifecycle.addObserver(emulatorView)
        hideSystemUi()
    }

    private fun addControls(root: FrameLayout) {
        addKey(root, "◀", KeyEvent.KEYCODE_DPAD_LEFT, Gravity.START or Gravity.BOTTOM, left = 24, bottom = 68)
        addKey(root, "▶", KeyEvent.KEYCODE_DPAD_RIGHT, Gravity.START or Gravity.BOTTOM, left = 142, bottom = 68)
        addKey(root, "▲", KeyEvent.KEYCODE_DPAD_UP, Gravity.START or Gravity.BOTTOM, left = 83, bottom = 126)
        addKey(root, "▼", KeyEvent.KEYCODE_DPAD_DOWN, Gravity.START or Gravity.BOTTOM, left = 83, bottom = 10)

        addKey(root, "B", KeyEvent.KEYCODE_BUTTON_B, Gravity.END or Gravity.BOTTOM, right = 105, bottom = 22)
        addKey(root, "A", KeyEvent.KEYCODE_BUTTON_A, Gravity.END or Gravity.BOTTOM, right = 26, bottom = 82)
        addKey(root, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Gravity.END or Gravity.BOTTOM,
            right = 220, bottom = 18, size = 52, textSize = 10f)
        addKey(root, "START", KeyEvent.KEYCODE_BUTTON_START, Gravity.END or Gravity.BOTTOM,
            right = 158, bottom = 18, size = 52, textSize = 10f)

        val romButton = TextView(this).apply {
            text = "ROM SEÇ"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBackground(0xCC243447.toInt(), 0xFF8BA0B5.toInt(), 10)
            setOnClickListener { openRomPicker() }
            contentDescription = "Choose another NES ROM"
        }
        val params = FrameLayout.LayoutParams(dp(82), dp(42), Gravity.TOP or Gravity.START)
        params.setMargins(dp(12), dp(12), 0, 0)
        root.addView(romButton, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addKey(
        root: FrameLayout,
        label: String,
        keyCode: Int,
        gravity: Int,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
        size: Int = 58,
        textSize: Float = 18f
    ) {
        val control = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            this.gravity = Gravity.CENTER
            background = roundedBackground(0xAA26384B.toInt(), 0xFFB7C5D4.toInt(), size / 2)
            isClickable = true
            isFocusable = false
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                        view.alpha = 0.62f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        retroView?.sendKeyEvent(KeyEvent.ACTION_UP, keyCode)
                        view.alpha = 1f
                    }
                }
                true
            }
        }
        val params = FrameLayout.LayoutParams(dp(size), dp(size), gravity)
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom))
        root.addView(control, params)
    }

    private fun roundedBackground(color: Int, strokeColor: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), strokeColor)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    companion object {
        private const val CORE_FILE_NAME = "libfceumm_libretro_android.so"
        private const val MAX_ROM_BYTES = 16 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    }
}
