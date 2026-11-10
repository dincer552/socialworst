package com.dincer552.hattrickssh.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.dincer552.hattrickssh.game.character.*
import com.dincer552.hattrickssh.game.graphics.*

/** GameRenderer - Canvas-based rendering with sprite sheet support. */
class GameRenderer(private val context: Context) {

    private val paint = Paint().apply { isAntiAlias = false }
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
        paint.isFilterBitmap = false
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
        spriteSheet: SpriteSheet?,
        frameRect: Rect?,
        x: Float,
        y: Float,
        faceRight: Boolean = true,
        alpha: Float = 1f
    ) {
        val bitmap = spriteSheet?.bitmap ?: return
        val rect = frameRect ?: return

        paint.alpha = (255 * alpha).toInt()

        val destX = x
        val destY = y
        val destW = rect.width().toFloat()
        val destH = rect.height().toFloat()

        if (faceRight) {
            canvas.drawBitmap(bitmap, rect, Rect(destX.toInt(), destY.toInt(), (destX + destW).toInt(), (destY + destH).toInt()), paint)
        } else {
            canvas.save()
            canvas.translate(destX + destW, destY)
            canvas.scale(-1f, 1f)
            canvas.drawBitmap(bitmap, rect, Rect(0, 0, destW.toInt(), destH.toInt()), paint)
            canvas.restore()
        }

        paint.alpha = 255
    }

    fun drawRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    fun drawText(canvas: Canvas, text: String, x: Float, y: Float, color: Int = 0xFFFFFFFF.toInt(), size: Float = 16f) {
        paint.color = color
        paint.textSize = size
        paint.isAntiAlias = true
        canvas.drawText(text, x, y, paint)
        paint.isAntiAlias = false
    }

    fun drawText(canvas: Canvas, text: String, x: Float, y: Float, p: Paint) {
        canvas.drawText(text, x, y, p)
    }

    val getPaint: Paint get() = paint
    val getScaleX: Float get() = scaleX
    val getScaleY: Float get() = scaleY
    val getViewWidth: Int get() = viewWidth
    val getViewHeight: Int get() = viewHeight
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
    override fun update(deltaTime: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, offsetX: Float, offsetY: Float) {
        paint.color = 0xFF4A4A4A.toInt()
        canvas.drawRect(
            position.x - offsetX,
            position.y - offsetY,
            position.x + width - offsetX,
            position.y + height - offsetY,
            paint
        )
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
    private val maxLifeTime: Float = 2.0f

    override fun update(deltaTime: Float) {
        position.x += velocity.x * deltaTime * 60f
        position.y += velocity.y * deltaTime * 60f
        lifeTime += deltaTime
        updateCollisionRect()

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
    val speed = Math.sqrt((data.vx * data.vx + data.vy * data.vy).toDouble()).toFloat()
    val projectile = Projectile(
        position = GamePosition(data.x, data.y),
        direction = if (data.vx >= 0) 1 else -1,
        speed = speed,
        damage = data.damage,
        size = data.size,
        color = data.color,
        glowColor = data.glowColor,
        canPierce = data.canPierce,
        canBreakIce = data.canBreakIce
    )
    projectile.velocity = GameVelocity(data.vx, data.vy)
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

        projectiles.removeIf { !it.isActive }
        projectiles.forEach { it.update(deltaTime) }

        targets.removeIf { it.isDestroyed }
        targets.forEach { it.update(deltaTime) }

        platforms.forEach { it.update(deltaTime) }

        platforms.filter { it.isConveyor }.forEach { platform ->
            if (player.collidesWith(platform) && player.isOnGround) {
                player.velocity.x += platform.conveyorSpeed * deltaTime * 60f
            }
        }

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

        handlePlatformCollisions()
        updateCamera()

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
        val targetX = player.position.x - 128f
        cameraX += (targetX - cameraX) * 0.1f
    }

    fun addPlatform(platform: Platform) {
        platforms.add(platform)
    }

    fun addProjectile(projectile: Projectile) {
        projectiles.add(projectile)
    }

    fun addProjectile(data: com.dincer552.hattrickssh.game.character.ProjectileData) {
        addProjectile(createProjectileFromData(data))
    }

    fun addTarget(target: Target) {
        targets.add(target)
    }

    fun removeProjectile(projectile: Projectile) {
        projectiles.remove(projectile)
    }

    val getPlayer: Player get() = player
    val getPlatforms: List<Platform> get() = platforms
    val getProjectiles: List<Projectile> get() = projectiles
    val getTargets: List<Target> get() = targets
    val getCameraX: Float get() = cameraX
    val getCameraY: Float get() = cameraY
    val getScore: Int get() = score
}
