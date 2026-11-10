package com.dincer552.hattrickssh.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** SpriteSheet - Loads PNG sprite sheet and extracts frames. */
class SpriteSheet {
    var bitmap: Bitmap? = null
    var frameWidth: Int = 32
    var frameHeight: Int = 32
    var cols: Int = 1
    var rows: Int = 1
    var isLoaded: Boolean = false

    val getBitmap: Bitmap? get() = bitmap

    constructor(bitmap: Bitmap, frameWidth: Int, frameHeight: Int) {
        this.bitmap = bitmap
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.cols = (bitmap.width / frameWidth).coerceAtLeast(1)
        this.rows = (bitmap.height / frameHeight).coerceAtLeast(1)
        this.isLoaded = true
    }

    constructor(context: Context, assetPath: String, cols: Int, rows: Int) {
        try {
            val isStream = context.assets.open(assetPath)
            val bmp = BitmapFactory.decodeStream(isStream)
            isStream.close()
            if (bmp != null) {
                this.bitmap = bmp
                this.cols = cols
                this.rows = rows
                this.frameWidth = bmp.width / cols
                this.frameHeight = bmp.height / rows
                this.isLoaded = true
            }
        } catch (_: Exception) {
            this.isLoaded = false
        }
    }

    fun getBitmap(frameIndex: Int): Bitmap? {
        val bmp = bitmap ?: return null
        val c = cols.coerceAtLeast(1)
        val col = frameIndex % c
        val row = frameIndex / c
        val x = (col * frameWidth).coerceIn(0, (bmp.width - frameWidth).coerceAtLeast(0))
        val y = (row * frameHeight).coerceIn(0, (bmp.height - frameHeight).coerceAtLeast(0))
        val w = frameWidth.coerceAtMost(bmp.width - x)
        val h = frameHeight.coerceAtMost(bmp.height - y)
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    fun getFrameRect(frameIndex: Int): Rect {
        val c = cols.coerceAtLeast(1)
        val col = frameIndex % c
        val row = frameIndex / c
        return Rect(col * frameWidth, row * frameHeight, (col + 1) * frameWidth, (row + 1) * frameHeight)
    }
}

/** AnimatedSprite - Handles frame-based sprite animation. */
class AnimatedSprite(
    val spriteSheet: SpriteSheet?,
    val frames: Int = 1,
    val fps: Int = 10,
    val loop: Boolean = true,
    val startFrame: Int = 0
) {
    val isLoaded: Boolean get() = spriteSheet?.isLoaded ?: false
    var currentFrame: Int = startFrame
    private var timer: Float = 0f
    val frameDuration: Float get() = if (fps > 0) 1f / fps else 0.1f

    fun update(deltaTime: Float) {
        timer += deltaTime
        if (timer >= frameDuration) {
            timer = 0f
            currentFrame++
            if (currentFrame >= startFrame + frames) {
                currentFrame = if (loop) startFrame else startFrame + frames - 1
            }
        }
    }

    fun getCurrentFrameRect(): Rect {
        val sw = spriteSheet?.frameWidth ?: 32
        val sh = spriteSheet?.frameHeight ?: 32
        val cols = (spriteSheet?.cols ?: 1).coerceAtLeast(1)
        val col = currentFrame % cols
        val row = currentFrame / cols
        return Rect(col * sw, row * sh, (col + 1) * sw, (row + 1) * sh)
    }

    fun draw(canvas: Canvas, x: Float, y: Float, paint: Paint? = null) {
        val bmp = spriteSheet?.getBitmap(currentFrame) ?: return
        canvas.drawBitmap(bmp, x, y, paint)
    }
}
