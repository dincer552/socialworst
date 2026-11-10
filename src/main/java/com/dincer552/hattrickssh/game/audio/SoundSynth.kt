package com.dincer552.hattrickssh.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.sin

/**
 * 8-Bit Chiptune Synthesizer using PCM AudioTrack for retro NES sound effects & music.
 */
class SoundSynth {
    private val executor = Executors.newSingleThreadExecutor()
    private val sampleRate = 22050
    var isEnabled = true

    @Volatile
    private var isPlayingMusic = false
    private var musicThread: Thread? = null

    enum class SfxType { JUMP, LASER, HOMING_SHOT, MEGA_SHOT, BOMB, EXPLOSION, HIT, POWERUP, SELECT, BOSS_DEFEAT }

    fun playSfx(type: SfxType) {
        if (!isEnabled) return
        executor.execute {
            try {
                val pcm = when (type) {
                    SfxType.JUMP -> generateSweepSquare(300f, 600f, 0.12f, 0.4f)
                    SfxType.LASER -> generateSweepSquare(800f, 200f, 0.08f, 0.5f)
                    SfxType.HOMING_SHOT -> generateSweepSquare(600f, 900f, 0.1f, 0.4f)
                    SfxType.MEGA_SHOT -> generateSweepSquare(200f, 800f, 0.25f, 0.7f)
                    SfxType.BOMB -> generateNoise(0.3f, 0.8f)
                    SfxType.EXPLOSION -> generateNoise(0.22f, 0.6f)
                    SfxType.HIT -> generateSweepSquare(150f, 80f, 0.15f, 0.5f)
                    SfxType.POWERUP -> generateArpeggio(listOf(440f, 554f, 659f, 880f), 0.05f, 0.5f)
                    SfxType.SELECT -> generateSweepSquare(500f, 700f, 0.06f, 0.3f)
                    SfxType.BOSS_DEFEAT -> generateArpeggio(listOf(523f, 659f, 784f, 1046f, 1318f), 0.1f, 0.7f)
                }
                playBuffer(pcm)
            } catch (_: Exception) {}
        }
    }

    fun startMusic(track: String) {
        stopMusic()
        if (!isEnabled) return
        isPlayingMusic = true
        musicThread = Thread {
            val trackNotes = when (track) {
                "TITLE" -> listOf(261, 329, 392, 523, 392, 329, 261, 196, 220, 261, 293, 349, 329, 261, 196)
                "BOSS" -> listOf(130, 138, 146, 155, 130, 146, 164, 174, 130, 155, 174, 185)
                else -> listOf(220, 261, 329, 293, 220, 261, 349, 329, 196, 246, 293, 261)
            }
            var noteIndex = 0
            while (isPlayingMusic) {
                val freq = trackNotes[noteIndex % trackNotes.size].toFloat()
                noteIndex++
                val buffer = generateSquareWave(freq, 0.14f, 0.25f)
                playBuffer(buffer)
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stopMusic() {
        isPlayingMusic = false
        musicThread?.interrupt()
        musicThread = null
    }

    private fun playBuffer(pcm: ShortArray) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack.write(pcm, 0, pcm.size)
        audioTrack.play()
        audioTrack.stop()
        audioTrack.release()
    }

    private fun generateSquareWave(freq: Float, durationSec: Float, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val periodSamples = sampleRate / freq
        for (i in 0 until numSamples) {
            val pos = i % periodSamples
            val sampleVal = if (pos < periodSamples / 2) 32767 * volume else -32768 * volume
            val envelope = (1f - (i.toFloat() / numSamples)).coerceIn(0f, 1f)
            buffer[i] = (sampleVal * envelope).toInt().toShort()
        }
        return buffer
    }

    private fun generateSweepSquare(startFreq: Float, endFreq: Float, durationSec: Float, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val phaseInc = 2.0 * Math.PI * freq / sampleRate
            phase += phaseInc
            val sampleVal = if (sin(phase) >= 0) 32767 * volume else -32768 * volume
            val envelope = (1f - progress).coerceIn(0f, 1f)
            buffer[i] = (sampleVal * envelope).toInt().toShort()
        }
        return buffer
    }

    private fun generateNoise(durationSec: Float, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val random = java.util.Random()
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val raw = (random.nextFloat() * 2f - 1f) * 32767 * volume
            val envelope = (1f - progress * progress).coerceIn(0f, 1f)
            buffer[i] = (raw * envelope).toInt().toShort()
        }
        return buffer
    }

    private fun generateArpeggio(freqs: List<Float>, noteDuration: Float, volume: Float): ShortArray {
        val totalSamples = (sampleRate * noteDuration * freqs.size).toInt()
        val buffer = ShortArray(totalSamples)
        var idx = 0
        for (freq in freqs) {
            val sub = generateSquareWave(freq, noteDuration, volume)
            for (s in sub) {
                if (idx < totalSamples) buffer[idx++] = s
            }
        }
        return buffer
    }

    fun release() {
        stopMusic()
        executor.shutdown()
    }
}
