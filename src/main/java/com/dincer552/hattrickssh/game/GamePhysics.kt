package com.dincer552.hattrickssh.game

import android.graphics.Canvas
import android.graphics.Paint

/** Represents a 2D position in the game world. */
data class GamePosition(var x: Float = 0f, var y: Float = 0f)

/** Represents a 2D velocity in the game world. */
data class GameVelocity(var x: Float = 0f, var y: Float = 0f) {
    var vx: Float
        get() = x
        set(v) { x = v }
    var vy: Float
        get() = y
        set(v) { y = v }
}

/** Collision rectangle for an object in the game world. */
data class GameRect(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    constructor(x: Float, y: Float, width: Float, height: Float, isBounds: Boolean = false) : this(x, y, x + width, y + height)

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val x: Float get() = left
    val y: Float get() = top

    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f

    fun set(x: Float, y: Float, w: Float, h: Float) {
        left = x
        top = y
        right = x + w
        bottom = y + h
    }

    fun intersects(other: GameRect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun collidesWith(other: GameRect): Boolean = intersects(other)
}

/** Base class for all game objects with position, velocity, and collision. */
open class GameObject(
    var position: GamePosition,
    var velocity: GameVelocity,
    var collisionRect: GameRect,
    var isActive: Boolean = true
) {
    val x: Float get() = position.x
    val y: Float get() = position.y
    val width: Float get() = collisionRect.width
    val height: Float get() = collisionRect.height

    open fun update(deltaTime: Float) {}
    open fun draw(canvas: Canvas, paint: Paint, offsetX: Float, offsetY: Float) {}
    open fun draw(canvas: Canvas) {}

    fun updateCollisionRect(w: Float = collisionRect.width, h: Float = collisionRect.height) {
        collisionRect.set(position.x, position.y, w, h)
    }

    fun collidesWith(other: GameObject): Boolean = collisionRect.intersects(other.collisionRect)
}

/** Physics constants for platform game physics. */
object PhysicsConfig {
    const val GRAVITY = 0.5f          // pixels per frame^2
    const val MAX_FALL_SPEED = 8.0f   // pixels per frame
    const val WALK_SPEED = 2.0f       // pixels per frame
    const val RUN_SPEED = 3.5f        // pixels per frame
    const val JUMP_SPEED = -6.5f      // pixels per frame (negative = up)
    const val JUMP_PREP_FRAMES = 2    // frames of crouch before jump
    const val GROUND_FRICTION = 0.8f  // velocity multiplier when no input
    const val AIR_RESISTANCE = 0.98f
}
