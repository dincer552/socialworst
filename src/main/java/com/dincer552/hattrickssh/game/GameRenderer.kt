package com.dincer552.hattrickssh.game

import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.character.SpriteSheet
import com.dincer552.hattrickssh.game.character.SpecialAbilityManager
import com.dincer552.hattrickssh.game.character.GamePosition
import com.dincer552.hattrickssh.game.character.GameVelocity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect

/** GameRenderer - Canvas-based rendering with sprite sheet support.
 * 
 * This is a plain rendering class (NOT a View subclass) used by GameView
 * to draw game objects onto a Canvas provided by SurfaceView.lockCanvas().
 * 
 * Responsibilities:
 * - Screen clearing with configured background color
 * - Viewport scaling and coordinate system setup
 * - Pixel-art-friendly rendering configuration (nearest-neighbor)
 * - Drawing sprite sheet frames, rectangles, and text
 * - Decouples rendering logic from game loop
 */
class GameRenderer(private val context: Context) {

    private val paint = Paint().apply { isAntiAlias = false } // Pixel art: no anti-aliasing
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    private var viewWidth: Int = GameConfig.VIEWPORT_WIDTH
    private var viewHeight: Int = GameConfig.VIEWPORT_HEIGHT

    init {
        setupPixelArtMode(false)
    }

    fun clearScreen(canvas: Canvas) {
        canvas.drawColor(GameConfig.COLOR_BG)
    }

    fun setupPixelArtMode(antiAlias: Boolean = false) {
        paint.isAntiAlias = antiAlias
        paint.isFilterBitmap = false // Nearest-neighbor scaling
    }

    fun updateScale(viewWidth: Int, viewHeight: Int) {
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        scaleX = viewWidth.toFloat() / GameConfig.VIEWPORT_WIDTH
        scaleY = viewHeight.toFloat() / GameConfig.VIEWPORT_HEIGHT
    }

    fun viewportToScreen(x: Float, y: Float): Pair<Float, Float> {
        val viewportX = x * scaleX + ((viewWidth.toFloat() - GameConfig.VIEWPORT_WIDTH * scaleX) / 2f)
        val viewportY = y * scaleY + ((viewHeight.toFloat() - GameConfig.VIEWPORT_HEIGHT * scaleY) / 2f)
        return Pair(viewportX, viewportY)
    }

    /** Draw a sprite sheet frame at the given position. */
    fun drawSpriteFrame(
        canvas: Canvas,
        spriteSheet: com.dincer552.hattrickssh.game.character.SpriteSheet?,
        frameRect: Rect?,
        x: Float,
        y: Float,
        faceRight: Boolean = true,
        alpha: Float = 1f
    ) {
        val bitmap = spriteSheet?.getBitmap ?: return
        val rect = frameRect ?: return

        paint.alpha = (255 * alpha).toInt()

        val destX = x
        val destY = y
        val destW = rect.width().toFloat()
        val destH = rect.height().toFloat()

        if (faceRight) {
            canvas.drawBitmap(bitmap, rect, Rect(destX.toInt(), destY.toInt(), (destX + destW).toInt(), (destY + destH).toInt()), paint)
        } else {
            // Flip horizontally
            val matrix = Matrix()
            matrix.setScale(-1f, 1f)
            matrix.postTranslate(destX + destW, destY)
            canvas.drawBitmap(bitmap, rect, matrix, paint)
        }

        paint.alpha = 255
    }

    /** Draw a filled rectangle. */
    fun drawRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    /** Draw text. */
    fun drawText(canvas: Canvas, text: String, x: Float, y: Float, color: Int = 0xFFFFFFFF.toInt(), size: Float = 16f) {
        paint.color = color
        paint.textSize = size
        paint.isAntiAlias = true
        canvas.drawText(text, x, y, paint)
        paint.isAntiAlias = false
    }

    val getPaint: Paint get() = paint
    val getScaleX: Float get() = scaleX
    val getScaleY: Float get() = scaleY
    val getViewWidth: Int get() = viewWidth
    val getViewHeight: Int get() = viewHeight
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
}

/** Represents a 2D position in the game world. */
data class GamePosition(var x: Float, var y: Float)

/** Represents a 2D velocity in the game world. */
data class GameVelocity(var x: Float, var y: Float)

/** Collision rectangle for an object in the game world. */
data class GameRect(var left: Float, var top: Float, var right: Float, var bottom: Float) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
}

/** Base class for all game objects with position, velocity, and collision. */
abstract class GameObject(
    var position: GamePosition,
    var velocity: GameVelocity,
    var collisionRect: GameRect,
    var isActive: Boolean = true
) {
    abstract fun update(deltaTime: Float)
    abstract fun draw(canvas: Canvas, paint: Paint, offsetX: Float, offsetY: Float)

    /** Check collision with another GameObject using AABB. */
    fun collidesWith(other: GameObject): Boolean {
        return collisionRect.left < other.collisionRect.right &&
               collisionRect.right > other.collisionRect.left &&
               collisionRect.top < other.collisionRect.bottom &&
               collisionRect.bottom > other.collisionRect.top
    }

    fun updateCollisionRect() {
        collisionRect.left = position.x
        collisionRect.top = position.y
        collisionRect.right = position.x + collisionRect.width()
        collisionRect.bottom = position.y + collisionRect.height()
    }
}

/** Platform - A solid object that the player can stand on. */
class Platform(
    position: GamePosition,
    width: Float,
    height: Float,
    val isConveyor: Boolean = false,
    val conveyorSpeed: Float = 0f
) : GameObject(
    position,
    GameVelocity(0f, 0f),
    GameRect(position.x, position.y, position.x + width, position.y + height)
) {
    override fun update(deltaTime: Float) {
        // Platforms are static by default
    }

    override fun draw(canvas: Canvas, paint: Paint, offsetX: Float, offsetY: Float) {
        paint.color = 0xFF4A4A4A.toInt()
        canvas.drawRect(
            position.x - offsetX,
            position.y - offsetY,
            position.x + width - offsetX,
            position.y + height - offsetY,
            paint
        )
        // Platform top edge highlight
        paint.color = 0xFF8A8A8A.toInt()
        canvas.drawLine(
            position.x - offsetX,
            position.y - offsetY,
            position.x + width - offsetX,
            position.y - offsetY,
            paint
        )
    }
}

/** Projectile - A shot fired by the player. */
class Projectile(
    position: GamePosition,
    val direction: Int, // 1 = right, -1 = left
    val speed: Float = 6.0f,
    val damage: Int = 1,
    val size: Float = 8.0f,
    val color: Int = 0xFFFFF078.toInt(),
    val glowColor: Int = 0xFFC89628.toInt(),
    val canPierce: Boolean = false,
    val canBreakIce: Boolean = false
) : GameObject(
    position,
    GameVelocity(direction * speed, 0f),
    GameRect(position.x, position.y, position.x + size, position.y + size),
    isActive = true
) {
    private var lifeTime: Float = 0f
    private val maxLifeTime: Float = 2.0f // seconds

    override fun update(deltaTime: Float) {
        position.x += velocity.x * deltaTime * 60f // scale to 60 FPS
        position.y += velocity.y * deltaTime * 60f
        lifeTime += deltaTime
        updateCollisionRect()

        // Deactivate if out of lifetime
        if (lifeTime > maxLifeTime) {
            isActive = false
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, offsetX: Float, offsetY: Float) {
        paint.color = color
        canvas.drawCircle(
            position.x - offsetX + size / 2f,
            position.y - offsetY + size / 2f,
            size / 2f,
            paint
        )
        paint.color = glowColor
        canvas.drawCircle(
            position.x - offsetX + size / 2f,
            position.y - offsetY + size / 2f,
            size / 4f,
            paint
        )
    }
}

/** Creates a Projectile from ProjectileData. */
fun createProjectileFromData(data: com.dincer552.hattrickssh.game.character.ProjectileData): Projectile {
    val projectile = Projectile(
        position = com.dincer552.hattrickssh.game.character.GamePosition(data.x, data.y),
        direction = if (data.vx >= 0) 1 else -1,
        speed = Math.sqrt(data.vx * data.vx + data.vy * data.vy).toFloat(),
        damage = data.damage,
        size = data.size,
        color = data.color,
        glowColor = data.glowColor,
        canPierce = data.canPierce,
        canBreakIce = data.canBreakIce
    )
    projectile.velocity = com.dincer552.hattrickssh.game.character.GameVelocity(data.vx, data.vy)
    return projectile
}

/** Target - A destructible target for shooting practice. */
class Target(
    position: GamePosition,
    val hitPoints: Int = 1
) : GameObject(
    position,
    GameVelocity(0f, 0f),
    GameRect(position.x, position.y, position.x + 24, position.y + 24),
    isActive = true
) {
    private var currentHP: Int = hitPoints
    private var flashTimer: Float = 0f

    fun takeDamage(damage: Int): Boolean {
        currentHP -= damage
        flashTimer = 0.1f
        return currentHP <= 0
    }

    override fun update(deltaTime: Float) {
        if (flashTimer > 0) {
            flashTimer -= deltaTime
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, offsetX: Float, offsetY: Float) {
        paint.color = if (flashTimer > 0) 0xFFFFFF00.toInt() else 0xFFFF6B6B.toInt()
        canvas.drawRect(
            position.x - offsetX,
            position.y - offsetY,
            position.x + 24 - offsetX,
            position.y + 24 - offsetY,
            paint
        )
        // Target center
        paint.color = 0xFF000000.toInt()
        canvas.drawCircle(
            position.x - offsetX + 12f,
            position.y - offsetY + 12f,
            4f,
            paint
        )
    }

    val isDestroyed: Boolean get() = currentHP <= 0
    val getCurrentHP: Int get() = currentHP
}

/** GameWorld - Manages all game objects and physics. */
class GameWorld(
    private val player: Player
) {
    private val platforms: MutableList<Platform> = mutableListOf()
    private val projectiles: MutableList<Projectile> = mutableListOf()
    private val targets: MutableList<Target> = mutableListOf()
    private var cameraX: Float = 0f
    private var cameraY: Float = 0f
    private var score: Int = 0

    fun update(deltaTime: Float) {
        player.update(deltaTime)

        // Update projectiles
        projectiles.removeIf { !it.isActive }
        projectiles.forEach { it.update(deltaTime) }

        // Update targets
        targets.removeIf { it.isDestroyed }
        targets.forEach { it.update(deltaTime) }

        // Update platforms
        platforms.forEach { it.update(deltaTime) }

        // Handle platform movement (conveyors)
        platforms.filter { it.isConveyor }.forEach { platform ->
            if (player.collidesWith(platform) && player.isOnGround) {
                player.velocity.x += platform.conveyorSpeed * deltaTime * 60f
            }
        }

        // Handle projectile-target collisions
        projectiles.forEach { projectile ->
            targets.forEach { target ->
                if (projectile.collidesWith(target)) {
                    val destroyed = target.takeDamage(projectile.damage)
                    projectile.isActive = false
                    if (destroyed) {
                        score += 100
                    }
                }
            }
        }

        // Handle player-platform collisions
        handlePlatformCollisions()

        // Update camera to follow player
        updateCamera()

        // Remove off-screen projectiles
        projectiles.removeIf {
            it.position.x < cameraX - 50 || it.position.x > cameraX + 306 ||
            it.position.y < cameraY - 50 || it.position.y > cameraY + 274
        }
    }

    private fun handlePlatformCollisions() {
        val playerRect = player.collisionRect

        var landedOnPlatform = false

        platforms.forEach { platform ->
            if (!player.collidesWith(platform)) return@forEach

            // Check if player is falling onto platform from above
            if (player.velocity.y >= 0 &&
                playerRect.bottom - player.velocity.y * 0.16f <= platform.collisionRect.top + 2f &&
                playerRect.right > platform.collisionRect.left + 4f &&
                playerRect.left < platform.collisionRect.right - 4f) {
                player.land(platform.collisionRect.top)
                landedOnPlatform = true
            }
        }

        if (!landedOnPlatform && player.velocity.y > 0) {
            player.setGrounded(false)
        }
    }

    private fun updateCamera() {
        // Center camera on player with dead zone
        val targetX = player.position.x - 128f // Half viewport width
        cameraX += (targetX - cameraX) * 0.1f // Smooth camera follow
    }

    fun addPlatform(platform: Platform) {
        platforms.add(platform)
    }

    fun addProjectile(projectile: Projectile) {
        projectiles.add(projectile)
    }

    fun addTarget(target: Target) {
        targets.add(target)
    }

    fun removeProjectile(projectile: Projectile) {
        projectiles.remove(projectile)
    }

    // Getters
    val getPlayer: Player get() = player
    val getPlatforms: List<Platform> get() = platforms
    val getProjectiles: List<Projectile> get() = projectiles
    val getTargets: List<Target> get() = targets
    val getCameraX: Float get() = cameraX
    val getCameraY: Float get() = cameraY
    val getScore: Int get() = score
}
