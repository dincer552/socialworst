package com.dincer552.hattrickssh.game.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.dincer552.hattrickssh.game.SaveManager
import com.dincer552.hattrickssh.game.models.*
import kotlin.math.sin

class GameRenderer {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val projPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val uiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ctrlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var cameraX: Float = 0f
    var cameraY: Float = 0f

    // Touch control bounding boxes for UI input feedback
    val dpadLeftRect = RectF()
    val dpadRightRect = RectF()
    val dpadUpRect = RectF()
    val dpadDownRect = RectF()
    val btnJumpRect = RectF()
    val btnShootRect = RectF()
    val btnSpecialRect = RectF()
    val btnSwapRect = RectF()
    val btnPauseRect = RectF()

    fun renderTitle(canvas: Canvas, screenW: Int, screenH: Int, frameTimer: Float, highScore: Int) {
        canvas.drawColor(Color.rgb(10, 15, 25))

        // Draw starfield
        bgPaint.color = Color.WHITE
        for (i in 0 until 60) {
            val sx = (i * 137 + (frameTimer * 20).toInt()) % screenW
            val sy = (i * 91) % screenH
            val size = if (i % 3 == 0) 4f else 2f
            canvas.drawCircle(sx.toFloat(), sy.toFloat(), size, bgPaint)
        }

        // Title Header
        textPaint.color = Color.rgb(34, 197, 94)
        textPaint.textSize = screenH * 0.09f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        canvas.drawText("BUCKY O'HARE", screenW / 2f, screenH * 0.28f, textPaint)

        // Subtitle
        textPaint.color = Color.rgb(234, 179, 8)
        textPaint.textSize = screenH * 0.045f
        canvas.drawText("RIGHTEOUS INDIGNATION • NES CLASSIC", screenW / 2f, screenH * 0.38f, textPaint)

        // Character portrait previews
        val chars = BuckyCharacter.values()
        val portraitW = screenW * 0.12f
        val startX = (screenW - (chars.size * portraitW * 1.2f)) / 2f
        for (i in chars.indices) {
            val cx = startX + i * portraitW * 1.2f
            val cy = screenH * 0.52f
            uiPaint.color = chars[i].primaryColor
            canvas.drawRoundRect(cx, cy, cx + portraitW, cy + portraitW, 12f, 12f, uiPaint)
            textPaint.color = Color.BLACK
            textPaint.textSize = portraitW * 0.25f
            canvas.drawText(chars[i].displayName.take(5), cx + portraitW / 2f, cy + portraitW * 0.6f, textPaint)
        }

        // Flash "PRESS START / TAP TO PLAY"
        if ((frameTimer * 3).toInt() % 2 == 0) {
            textPaint.color = Color.WHITE
            textPaint.textSize = screenH * 0.05f
            canvas.drawText("TAP SCREEN TO PLAY", screenW / 2f, screenH * 0.75f, textPaint)
        }

        textPaint.color = Color.rgb(148, 163, 184)
        textPaint.textSize = screenH * 0.035f
        canvas.drawText("HIGH SCORE: $highScore", screenW / 2f, screenH * 0.88f, textPaint)
    }

    fun renderPlanetSelect(
        canvas: Canvas,
        screenW: Int,
        screenH: Int,
        planets: List<PlanetInfo>,
        selectedIndex: Int,
        saveManager: SaveManager
    ) {
        canvas.drawColor(Color.rgb(8, 10, 18))

        textPaint.color = Color.rgb(56, 217, 138)
        textPaint.textSize = screenH * 0.06f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        canvas.drawText("SELECT MISSION PLANET", screenW / 2f, screenH * 0.15f, textPaint)

        val planetSpacing = screenW / (planets.size + 1).toFloat()
        val planetY = screenH * 0.5f
        val radius = screenH * 0.12f

        for (i in planets.indices) {
            val px = planetSpacing * (i + 1)
            val info = planets[i]
            val isCompleted = saveManager.isPlanetCompleted(i)
            val isSelected = i == selectedIndex

            // Connection lines
            if (i < planets.size - 1) {
                uiPaint.color = Color.DKGRAY
                uiPaint.strokeWidth = 6f
                canvas.drawLine(px, planetY, planetSpacing * (i + 2), planetY, uiPaint)
            }

            // Planet circle
            uiPaint.color = if (isCompleted) Color.GRAY else info.themeColor
            uiPaint.style = Paint.Style.FILL
            canvas.drawCircle(px, planetY, radius, uiPaint)

            if (isSelected) {
                uiPaint.color = Color.WHITE
                uiPaint.style = Paint.Style.STROKE
                uiPaint.strokeWidth = 8f
                canvas.drawCircle(px, planetY, radius + 10f, uiPaint)
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = radius * 0.35f
            textPaint.isFakeBoldText = true
            canvas.drawText(info.name, px, planetY - radius * 0.2f, textPaint)

            textPaint.textSize = radius * 0.25f
            textPaint.color = if (isCompleted) Color.GREEN else Color.YELLOW
            val statusText = if (isCompleted) "CLEARED" else if (i == 4 && !saveManager.isPlanetCompleted(3)) "LOCKED" else "RESCUE"
            canvas.drawText(statusText, px, planetY + radius * 0.3f, textPaint)
        }

        val currentInfo = planets[selectedIndex]
        textPaint.color = Color.CYAN
        textPaint.textSize = screenH * 0.045f
        canvas.drawText("${currentInfo.name} - ${currentInfo.subtitle}", screenW / 2f, screenH * 0.8f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = screenH * 0.038f
        canvas.drawText("TAP PLANET OR TAP HERE TO START MISSION", screenW / 2f, screenH * 0.9f, textPaint)
    }

    fun renderGame(
        canvas: Canvas,
        screenW: Int,
        screenH: Int,
        player: Player,
        level: LevelData,
        enemies: List<Enemy>,
        projectiles: List<Projectile>,
        particles: List<Particle>,
        pickups: List<Pickup>,
        saveManager: SaveManager,
        boss: Enemy?,
        frameTimer: Float
    ) {
        // Camera smooth center on player
        cameraX = (player.x - screenW / 2f).coerceIn(0f, (level.widthTiles * level.tileSize - screenW).coerceAtLeast(0f))
        cameraY = (player.y - screenH / 2f).coerceIn(0f, (level.heightTiles * level.tileSize - screenH).coerceAtLeast(0f))

        // Background
        canvas.drawColor(level.planetInfo.themeColor and 0x33FFFFFF or 0xFF0A0E14.toInt())

        canvas.save()
        canvas.translate(-cameraX, -cameraY)

        // Draw level tiles
        for (y in 0 until level.heightTiles) {
            for (x in 0 until level.widthTiles) {
                val tileType = level.tiles[y][x]
                if (tileType == Tile.EMPTY) continue
                val tx = x * level.tileSize
                val ty = y * level.tileSize

                // View frustum culling
                if (tx + level.tileSize < cameraX || tx > cameraX + screenW ||
                    ty + level.tileSize < cameraY || ty > cameraY + screenH
                ) continue

                when (tileType) {
                    Tile.SOLID -> {
                        tilePaint.color = Color.rgb(40, 50, 65)
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + level.tileSize, tilePaint)
                        tilePaint.color = level.planetInfo.themeColor
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + 6f, tilePaint)
                    }
                    Tile.PLATFORM -> {
                        tilePaint.color = Color.rgb(100, 116, 139)
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + 12f, tilePaint)
                    }
                    Tile.LADDER -> {
                        tilePaint.color = Color.rgb(234, 179, 8)
                        canvas.drawRect(tx + 8f, ty, tx + 16f, ty + level.tileSize, tilePaint)
                        canvas.drawRect(tx + level.tileSize - 16f, ty, tx + level.tileSize - 8f, ty + level.tileSize, tilePaint)
                        canvas.drawRect(tx + 8f, ty + 16f, tx + level.tileSize - 8f, ty + 24f, tilePaint)
                    }
                    Tile.SPIKE -> {
                        tilePaint.color = Color.RED
                        val path = Path()
                        path.moveTo(tx, ty + level.tileSize)
                        path.lineTo(tx + level.tileSize / 2f, ty)
                        path.lineTo(tx + level.tileSize, ty + level.tileSize)
                        path.close()
                        canvas.drawPath(path, tilePaint)
                    }
                    Tile.LAVA -> {
                        val glow = (sin(frameTimer * 6 + x) * 40).toInt()
                        tilePaint.color = Color.rgb(230 + glow.coerceIn(-20, 25), 60, 20)
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + level.tileSize, tilePaint)
                    }
                    Tile.ACID -> {
                        tilePaint.color = Color.rgb(34, 197, 94)
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + level.tileSize, tilePaint)
                    }
                    Tile.DESTRUCTIBLE -> {
                        tilePaint.color = Color.rgb(180, 100, 50)
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + level.tileSize, tilePaint)
                        tilePaint.color = Color.BLACK
                        canvas.drawLine(tx, ty, tx + level.tileSize, ty + level.tileSize, tilePaint)
                    }
                    Tile.DOOR_GOAL -> {
                        tilePaint.color = Color.CYAN
                        canvas.drawRect(tx, ty, tx + level.tileSize, ty + level.tileSize, tilePaint)
                    }
                }
            }
        }

        // Draw Pickups
        for (pickup in pickups) {
            if (!pickup.isAlive) continue
            uiPaint.color = when (pickup.type) {
                PickupType.HEALTH -> Color.GREEN
                PickupType.ENERGY -> Color.CYAN
                PickupType.LIFE_UP -> Color.MAGENTA
                PickupType.SCORE_STAR -> Color.YELLOW
            }
            canvas.drawCircle(pickup.x + pickup.width / 2f, pickup.y + pickup.height / 2f, pickup.width / 2f, uiPaint)
        }

        // Draw Enemies
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            enemyPaint.color = if (enemy.hitFlashTimer > 0f) Color.WHITE else if (enemy.type.isBoss) Color.MAGENTA else Color.RED
            canvas.drawRect(enemy.x, enemy.y, enemy.x + enemy.width, enemy.y + enemy.height, enemyPaint)

            // Boss health bar directly above boss
            if (enemy.type.isBoss) {
                val hpPct = enemy.hp.toFloat() / enemy.maxHp
                uiPaint.color = Color.BLACK
                canvas.drawRect(enemy.x, enemy.y - 20f, enemy.x + enemy.width, enemy.y - 10f, uiPaint)
                uiPaint.color = Color.RED
                canvas.drawRect(enemy.x, enemy.y - 20f, enemy.x + enemy.width * hpPct, enemy.y - 10f, uiPaint)
            }
        }

        // Draw Player
        val isFlashing = player.invulnerabilityTimer > 0f && (frameTimer * 20).toInt() % 2 == 0
        if (!isFlashing) {
            playerPaint.color = player.activeCharacter.primaryColor
            canvas.drawRoundRect(player.x, player.y, player.x + player.width, player.y + player.height, 8f, 8f, playerPaint)

            // Character eye visor / face direction detail
            playerPaint.color = Color.WHITE
            val eyeX = if (player.isFacingRight) player.x + player.width * 0.65f else player.x + player.width * 0.15f
            canvas.drawRect(eyeX, player.y + 10f, eyeX + 8f, player.y + 18f, playerPaint)

            // Charging aura effect
            if (player.isCharging) {
                playerPaint.color = Color.argb(150, 255, 255, 0)
                canvas.drawCircle(player.x + player.width / 2f, player.y + player.height / 2f, player.width * (0.8f + sin(frameTimer * 10) * 0.2f), playerPaint)
            }
        }

        // Draw Projectiles
        for (p in projectiles) {
            if (!p.isAlive) continue
            projPaint.color = when (p.type) {
                ProjectileType.LASER_PLAYER -> Color.GREEN
                ProjectileType.HOMING_MAGIC -> Color.MAGENTA
                ProjectileType.MEGA_SHOT -> Color.CYAN
                ProjectileType.SPREAD_SHOT -> Color.YELLOW
                ProjectileType.BOMB -> Color.RED
                else -> Color.RED
            }
            canvas.drawCircle(p.x, p.y, p.radius, projPaint)
        }

        // Draw Particles
        for (part in particles) {
            if (!part.isAlive) continue
            particlePaint.color = part.color
            particlePaint.alpha = (255 * (part.currentLife / part.maxLife)).toInt().coerceIn(0, 255)
            canvas.drawCircle(part.x, part.y, part.size, particlePaint)
        }

        canvas.restore()

        // Draw HUD & On-screen controls
        renderHUD(canvas, screenW, screenH, player, boss, saveManager)
        renderTouchControls(canvas, screenW, screenH)

        if (saveManager.isScanlinesEnabled) {
            renderScanlines(canvas, screenW, screenH)
        }
    }

    private fun renderHUD(
        canvas: Canvas,
        screenW: Int,
        screenH: Int,
        player: Player,
        boss: Enemy?,
        saveManager: SaveManager
    ) {
        val barW = screenW * 0.25f
        val barH = 18f
        val startX = 20f
        val startY = 20f

        // Player HP
        uiPaint.color = Color.DKGRAY
        canvas.drawRoundRect(startX, startY, startX + barW, startY + barH, 6f, 6f, uiPaint)
        val hpPct = (player.health.toFloat() / player.maxHealth).coerceIn(0f, 1f)
        uiPaint.color = if (hpPct > 0.5f) Color.GREEN else if (hpPct > 0.2f) Color.YELLOW else Color.RED
        canvas.drawRoundRect(startX, startY, startX + barW * hpPct, startY + barH, 6f, 6f, uiPaint)

        // Player Energy
        uiPaint.color = Color.DKGRAY
        canvas.drawRoundRect(startX, startY + 26f, startX + barW, startY + 26f + barH, 6f, 6f, uiPaint)
        val energyPct = (player.energy.toFloat() / player.maxEnergy).coerceIn(0f, 1f)
        uiPaint.color = Color.CYAN
        canvas.drawRoundRect(startX, startY + 26f, startX + barW * energyPct, startY + 26f + barH, 6f, 6f, uiPaint)

        // Stats Text
        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${player.activeCharacter.displayName} • LIVES: ${player.lives} • SCORE: ${player.score}", startX, startY + 70f, textPaint)

        // Character Select Dock (Icons at top center)
        val chars = BuckyCharacter.values()
        val dockSize = 44f
        val dockStartX = screenW / 2f - (chars.size * (dockSize + 10f)) / 2f
        for (i in chars.indices) {
            val c = chars[i]
            val cx = dockStartX + i * (dockSize + 10f)
            val cy = 16f
            val isUnlocked = saveManager.isCharacterUnlocked(c.name)
            val isSelected = player.activeCharacter == c

            uiPaint.color = if (isUnlocked) c.primaryColor else Color.GRAY
            uiPaint.alpha = if (isUnlocked) 255 else 100
            canvas.drawRoundRect(cx, cy, cx + dockSize, cy + dockSize, 8f, 8f, uiPaint)

            if (isSelected) {
                uiPaint.color = Color.WHITE
                uiPaint.style = Paint.Style.STROKE
                uiPaint.strokeWidth = 4f
                canvas.drawRoundRect(cx - 2f, cy - 2f, cx + dockSize + 2f, cy + dockSize + 2f, 8f, 8f, uiPaint)
                uiPaint.style = Paint.Style.FILL
            }
        }

        // Boss Health Bar at top right if present
        if (boss != null && boss.isAlive) {
            val bossBarW = screenW * 0.3f
            val bossStartX = screenW - bossBarW - 20f
            uiPaint.color = Color.DKGRAY
            canvas.drawRoundRect(bossStartX, startY, bossStartX + bossBarW, startY + barH, 6f, 6f, uiPaint)
            val bossHpPct = (boss.hp.toFloat() / boss.maxHp).coerceIn(0f, 1f)
            uiPaint.color = Color.MAGENTA
            canvas.drawRoundRect(bossStartX, startY, bossStartX + bossBarW * bossHpPct, startY + barH, 6f, 6f, uiPaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 24f
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("BOSS: ${boss.type.name}", screenW - 20f, startY + 45f, textPaint)
        }
    }

    private fun renderTouchControls(canvas: Canvas, screenW: Int, screenH: Int) {
        val dpadSize = screenH * 0.22f
        val dpadX = screenW * 0.12f
        val dpadY = screenH * 0.72f

        dpadLeftRect.set(dpadX - dpadSize, dpadY - dpadSize / 3f, dpadX - dpadSize / 3f, dpadY + dpadSize / 3f)
        dpadRightRect.set(dpadX + dpadSize / 3f, dpadY - dpadSize / 3f, dpadX + dpadSize, dpadY + dpadSize / 3f)
        dpadUpRect.set(dpadX - dpadSize / 3f, dpadY - dpadSize, dpadX + dpadSize / 3f, dpadY - dpadSize / 3f)
        dpadDownRect.set(dpadX - dpadSize / 3f, dpadY + dpadSize / 3f, dpadX + dpadSize / 3f, dpadY + dpadSize)

        ctrlPaint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(dpadLeftRect, 10f, 10f, ctrlPaint)
        canvas.drawRoundRect(dpadRightRect, 10f, 10f, ctrlPaint)
        canvas.drawRoundRect(dpadUpRect, 10f, 10f, ctrlPaint)
        canvas.drawRoundRect(dpadDownRect, 10f, 10f, ctrlPaint)

        textPaint.color = Color.BLACK
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("◄", dpadLeftRect.centerX(), dpadLeftRect.centerY() + 10f, textPaint)
        canvas.drawText("►", dpadRightRect.centerX(), dpadRightRect.centerY() + 10f, textPaint)
        canvas.drawText("▲", dpadUpRect.centerX(), dpadUpRect.centerY() + 10f, textPaint)
        canvas.drawText("▼", dpadDownRect.centerX(), dpadDownRect.centerY() + 10f, textPaint)

        // Action Buttons
        val btnRadius = screenH * 0.08f
        val btnY = screenH * 0.75f

        btnJumpRect.set(screenW * 0.88f - btnRadius, btnY - btnRadius, screenW * 0.88f + btnRadius, btnY + btnRadius)
        btnShootRect.set(screenW * 0.74f - btnRadius, btnY - btnRadius, screenW * 0.74f + btnRadius, btnY + btnRadius)
        btnSpecialRect.set(screenW * 0.81f - btnRadius, btnY - btnRadius * 2.2f, screenW * 0.81f + btnRadius, btnY - btnRadius * 0.2f)
        btnSwapRect.set(screenW * 0.60f - btnRadius, btnY - btnRadius, screenW * 0.60f + btnRadius, btnY + btnRadius)
        btnPauseRect.set(screenW - 120f, 20f, screenW - 20f, 70f)

        ctrlPaint.color = Color.argb(150, 34, 197, 94)
        canvas.drawOval(btnJumpRect, ctrlPaint)

        ctrlPaint.color = Color.argb(150, 239, 68, 68)
        canvas.drawOval(btnShootRect, ctrlPaint)

        ctrlPaint.color = Color.argb(150, 234, 179, 8)
        canvas.drawOval(btnSpecialRect, ctrlPaint)

        ctrlPaint.color = Color.argb(150, 59, 130, 246)
        canvas.drawOval(btnSwapRect, ctrlPaint)

        ctrlPaint.color = Color.argb(180, 100, 116, 139)
        canvas.drawRoundRect(btnPauseRect, 8f, 8f, ctrlPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        canvas.drawText("JUMP", btnJumpRect.centerX(), btnJumpRect.centerY() + 8f, textPaint)
        canvas.drawText("FIRE", btnShootRect.centerX(), btnShootRect.centerY() + 8f, textPaint)
        canvas.drawText("SPEC", btnSpecialRect.centerX(), btnSpecialRect.centerY() + 8f, textPaint)
        canvas.drawText("SWAP", btnSwapRect.centerX(), btnSwapRect.centerY() + 8f, textPaint)
        canvas.drawText("PAUSE", btnPauseRect.centerX(), btnPauseRect.centerY() + 8f, textPaint)
    }

    private fun renderScanlines(canvas: Canvas, screenW: Int, screenH: Int) {
        uiPaint.color = Color.argb(30, 0, 0, 0)
        uiPaint.strokeWidth = 2f
        var y = 0f
        while (y < screenH) {
            canvas.drawLine(0f, y, screenW.toFloat(), y, uiPaint)
            y += 6f
        }
    }

    fun renderOverlayMessage(canvas: Canvas, screenW: Int, screenH: Int, title: String, subtitle: String) {
        uiPaint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), uiPaint)

        textPaint.color = Color.YELLOW
        textPaint.textSize = screenH * 0.08f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, screenW / 2f, screenH * 0.45f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = screenH * 0.045f
        canvas.drawText(subtitle, screenW / 2f, screenH * 0.58f, textPaint)
    }
}
