package com.dincer552.hattrickssh.game

import android.content.Context
import android.graphics.Color
import com.dincer552.hattrickssh.game.audio.SoundSynth
import com.dincer552.hattrickssh.game.models.*

enum class GameState {
    TITLE,
    PLANET_SELECT,
    PLAYING,
    PAUSED,
    STAGE_CLEAR,
    GAME_OVER,
    VICTORY
}

class GameEngine(context: Context) {

    val saveManager = SaveManager(context)
    val soundSynth = SoundSynth()
    private val levelManager = LevelManager()

    var state: GameState = GameState.TITLE
    var currentPlanetIndex: Int = 0
    var frameTimer: Float = 0f

    val player = Player()
    var level: LevelData = levelManager.createLevel(0)
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val particles = mutableListOf<Particle>()
    val pickups = mutableListOf<Pickup>()

    // Input state flags
    var inputLeft = false
    var inputRight = false
    var inputUp = false
    var inputDown = false
    var inputJump = false
    var inputShoot = false
    var inputSpecial = false

    private var shootCooldown = 0f
    private var specialCooldown = 0f

    init {
        soundSynth.isEnabled = saveManager.isSoundEnabled
        soundSynth.startMusic("TITLE")
    }

    fun startPlanet(planetIdx: Int) {
        currentPlanetIndex = planetIdx
        level = levelManager.createLevel(planetIdx)
        player.resetState(level.spawnX, level.spawnY)
        enemies.clear()
        projectiles.clear()
        particles.clear()
        pickups.clear()

        for (es in level.enemySpawns) {
            enemies.add(Enemy(es.type, es.x, es.y))
        }
        for (ps in level.pickupSpawns) {
            pickups.add(Pickup(ps.type, ps.x, ps.y))
        }

        state = GameState.PLAYING
        val track = if (planetIdx == 4) "BOSS" else "STAGE"
        soundSynth.startMusic(track)
    }

    fun update(deltaTime: Float) {
        frameTimer += deltaTime

        when (state) {
            GameState.PLAYING -> updatePlaying(deltaTime)
            else -> {}
        }
    }

    private fun updatePlaying(dt: Float) {
        if (player.invulnerabilityTimer > 0f) {
            player.invulnerabilityTimer -= dt
        }
        if (shootCooldown > 0f) shootCooldown -= dt
        if (specialCooldown > 0f) specialCooldown -= dt

        // Player horizontal physics
        val moveSpeed = 300f * player.activeCharacter.speedMultiplier
        if (inputLeft) {
            player.vx = -moveSpeed
            player.isFacingRight = false
        } else if (inputRight) {
            player.vx = moveSpeed
            player.isFacingRight = true
        } else {
            player.vx *= 0.7f
        }

        // Ladder Climbing
        val tileX = ((player.x + player.width / 2f) / level.tileSize).toInt().coerceIn(0, level.widthTiles - 1)
        val tileY = ((player.y + player.height / 2f) / level.tileSize).toInt().coerceIn(0, level.heightTiles - 1)
        val currentTile = level.tiles[tileY][tileX]

        if (currentTile == Tile.LADDER && (inputUp || inputDown)) {
            player.isClimbing = true
        }
        if (player.isClimbing) {
            player.vy = if (inputUp) -200f else if (inputDown) 200f else 0f
            if (currentTile != Tile.LADDER) player.isClimbing = false
        } else {
            // Gravity
            val gravity = if (player.activeCharacter == BuckyCharacter.JENNY && player.vy > 0 && inputJump) 300f else 900f
            player.vy += gravity * dt

            // Blinky Jetpack
            if (player.activeCharacter == BuckyCharacter.BLINKY && inputJump && player.jetpackFuel > 0f) {
                player.vy = -260f
                player.jetpackFuel -= 40f * dt
                createExplosionParticles(player.x + player.width / 2f, player.y + player.height, Color.YELLOW, 2)
            } else if (player.isGrounded) {
                player.jetpackFuel = (player.jetpackFuel + 60f * dt).coerceAtMost(100f)
            }
        }

        // Jump
        if (inputJump && player.isGrounded && !player.isClimbing) {
            val jumpForce = -520f * player.activeCharacter.jumpMultiplier
            player.vy = jumpForce
            player.isGrounded = false
            soundSynth.playSfx(SoundSynth.SfxType.JUMP)
        }

        // Shooting
        if (inputShoot && shootCooldown <= 0f) {
            shootCooldown = 0.22f
            firePrimaryWeapon()
        }

        // Special Power / Charge
        if (inputSpecial && specialCooldown <= 0f && player.energy >= player.activeCharacter.specialEnergyCost) {
            specialCooldown = 0.5f
            player.energy -= player.activeCharacter.specialEnergyCost
            fireSpecialPower()
        }

        // Move & Collide Player
        player.x += player.vx * dt
        checkPlayerTileCollision(horizontal = true)
        player.y += player.vy * dt
        checkPlayerTileCollision(horizontal = false)

        // Out of bounds death check
        if (player.y > level.heightTiles * level.tileSize + 100f) {
            playerDamage(100)
        }

        // Update Projectiles
        val pIter = projectiles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.lifetime -= dt

            // Homing magic sphere trajectory logic
            if (p.type == ProjectileType.HOMING_MAGIC) {
                var target = p.homingTarget
                if (target == null || !target.isAlive) {
                    target = enemies.firstOrNull { it.isAlive }
                    p.homingTarget = target
                }
                if (target != null) {
                    val dx = (target.x + target.width / 2f) - p.x
                    val dy = (target.y + target.height / 2f) - p.y
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                    p.vx += (dx / dist) * 600f * dt
                    p.vy += (dy / dist) * 600f * dt
                }
            }

            if (p.lifetime <= 0f) {
                p.isAlive = false
            }

            // Projectile vs Tile Collision
            val tx = (p.x / level.tileSize).toInt().coerceIn(0, level.widthTiles - 1)
            val ty = (p.y / level.tileSize).toInt().coerceIn(0, level.heightTiles - 1)
            val t = level.tiles[ty][tx]
            if (t == Tile.SOLID || (t == Tile.DESTRUCTIBLE && p.type == ProjectileType.BOMB)) {
                if (t == Tile.DESTRUCTIBLE) {
                    level.tiles[ty][tx] = Tile.EMPTY
                    createExplosionParticles(p.x, p.y, Color.rgb(180, 100, 50), 10)
                }
                p.isAlive = false
                createExplosionParticles(p.x, p.y, Color.YELLOW, 4)
            }

            // Projectile vs Entities
            if (p.isFromPlayer) {
                for (enemy in enemies) {
                    if (enemy.isAlive && checkCircleBoxIntersect(p.x, p.y, p.radius, enemy.x, enemy.y, enemy.width, enemy.height)) {
                        enemy.takeDamage(p.damage)
                        p.isAlive = false
                        soundSynth.playSfx(SoundSynth.SfxType.HIT)
                        createExplosionParticles(p.x, p.y, Color.rgb(255, 165, 0), 6)
                        if (!enemy.isAlive) {
                            player.score += if (enemy.type.isBoss) 5000 else 200
                            soundSynth.playSfx(if (enemy.type.isBoss) SoundSynth.SfxType.BOSS_DEFEAT else SoundSynth.SfxType.EXPLOSION)
                            createExplosionParticles(enemy.x + enemy.width / 2f, enemy.y + enemy.height / 2f, Color.YELLOW, 20)

                            if (enemy.type.isBoss) {
                                stageCleared()
                            }
                        }
                        break
                    }
                }
            } else {
                if (player.invulnerabilityTimer <= 0f && checkCircleBoxIntersect(p.x, p.y, p.radius, player.x, player.y, player.width, player.height)) {
                    p.isAlive = false
                    playerDamage(p.damage)
                }
            }

            if (!p.isAlive) pIter.remove()
        }

        // Update Enemies
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            if (enemy.hitFlashTimer > 0f) enemy.hitFlashTimer -= dt

            // Basic patrol & shooting AI
            enemy.shootCooldown -= dt
            val dx = player.x - enemy.x
            enemy.isFacingRight = dx > 0

            if (enemy.type.isBoss) {
                // Boss attack pattern logic
                if (enemy.shootCooldown <= 0f) {
                    enemy.shootCooldown = 1.2f
                    val bVx = if (enemy.isFacingRight) 350f else -350f
                    projectiles.add(Projectile(ProjectileType.BOSS_LASER, enemy.x + enemy.width / 2f, enemy.y + enemy.height / 2f, bVx, 0f, 20, false, radius = 12f))
                    soundSynth.playSfx(SoundSynth.SfxType.LASER)
                }
            } else if (Math.abs(dx) < 400f && enemy.shootCooldown <= 0f) {
                enemy.shootCooldown = 2.0f
                val bVx = if (enemy.isFacingRight) 250f else -250f
                projectiles.add(Projectile(ProjectileType.ENEMY_BULLET, enemy.x + enemy.width / 2f, enemy.y + enemy.height / 3f, bVx, 0f, 10, false))
            }

            // Contact Damage
            if (player.invulnerabilityTimer <= 0f && checkBoxIntersect(player.x, player.y, player.width, player.height, enemy.x, enemy.y, enemy.width, enemy.height)) {
                playerDamage(enemy.type.contactDamage)
            }
        }

        // Update Pickups
        val pkIter = pickups.iterator()
        while (pkIter.hasNext()) {
            val pk = pkIter.next()
            if (pk.isAlive && checkBoxIntersect(player.x, player.y, player.width, player.height, pk.x, pk.y, pk.width, pk.height)) {
                pk.isAlive = false
                soundSynth.playSfx(SoundSynth.SfxType.POWERUP)
                when (pk.type) {
                    PickupType.HEALTH -> player.health = (player.health + 30).coerceAtMost(player.maxHealth)
                    PickupType.ENERGY -> player.energy = (player.energy + 40).coerceAtMost(player.maxEnergy)
                    PickupType.LIFE_UP -> player.lives++
                    PickupType.SCORE_STAR -> player.score += 500
                }
                pkIter.remove()
            }
        }

        // Update Particles
        val partIter = particles.iterator()
        while (partIter.hasNext()) {
            val part = partIter.next()
            part.x += part.vx * dt
            part.y += part.vy * dt
            part.currentLife -= dt
            if (!part.isAlive) partIter.remove()
        }

        // Energy passive recharge
        player.energy = (player.energy + 8f * dt).toInt().coerceAtMost(player.maxEnergy)
    }

    private fun firePrimaryWeapon() {
        val px = if (player.isFacingRight) player.x + player.width + 5f else player.x - 15f
        val py = player.y + player.height * 0.4f
        val vx = if (player.isFacingRight) 650f else -650f

        when (player.activeCharacter) {
            BuckyCharacter.BUCKY -> {
                projectiles.add(Projectile(ProjectileType.LASER_PLAYER, px, py, vx, 0f, 15, true))
                soundSynth.playSfx(SoundSynth.SfxType.LASER)
            }
            BuckyCharacter.JENNY -> {
                projectiles.add(Projectile(ProjectileType.HOMING_MAGIC, px, py, vx, 0f, 20, true, radius = 10f))
                soundSynth.playSfx(SoundSynth.SfxType.HOMING_SHOT)
            }
            BuckyCharacter.WILLY -> {
                projectiles.add(Projectile(ProjectileType.MEGA_SHOT, px, py, vx * 1.2f, 0f, 35, true, radius = 14f))
                soundSynth.playSfx(SoundSynth.SfxType.MEGA_SHOT)
            }
            BuckyCharacter.DEAD_EYE -> {
                projectiles.add(Projectile(ProjectileType.SPREAD_SHOT, px, py, vx, -150f, 12, true))
                projectiles.add(Projectile(ProjectileType.SPREAD_SHOT, px, py, vx, 0f, 12, true))
                projectiles.add(Projectile(ProjectileType.SPREAD_SHOT, px, py, vx, 150f, 12, true))
                soundSynth.playSfx(SoundSynth.SfxType.LASER)
            }
            BuckyCharacter.BLINKY -> {
                projectiles.add(Projectile(ProjectileType.BOMB, px, py, vx * 0.6f, -100f, 45, true, radius = 12f))
                soundSynth.playSfx(SoundSynth.SfxType.BOMB)
            }
        }
    }

    private fun fireSpecialPower() {
        when (player.activeCharacter) {
            BuckyCharacter.BUCKY -> {
                player.vy = -650f // Super Jump
                soundSynth.playSfx(SoundSynth.SfxType.JUMP)
                firePrimaryWeapon()
            }
            BuckyCharacter.JENNY -> {
                player.vy = -300f
                for (angle in listOf(-0.4f, -0.2f, 0f, 0.2f, 0.4f)) {
                    val pvx = if (player.isFacingRight) 500f else -500f
                    projectiles.add(Projectile(ProjectileType.HOMING_MAGIC, player.x, player.y, pvx, angle * 500f, 25, true, radius = 10f))
                }
                soundSynth.playSfx(SoundSynth.SfxType.HOMING_SHOT)
            }
            BuckyCharacter.WILLY -> {
                val vx = if (player.isFacingRight) 900f else -900f
                projectiles.add(Projectile(ProjectileType.MEGA_SHOT, player.x, player.y + 10f, vx, 0f, 75, true, radius = 22f))
                soundSynth.playSfx(SoundSynth.SfxType.MEGA_SHOT)
            }
            BuckyCharacter.DEAD_EYE -> {
                val vx = if (player.isFacingRight) 700f else -700f
                for (vy in listOf(-300f, -150f, 0f, 150f, 300f)) {
                    projectiles.add(Projectile(ProjectileType.SPREAD_SHOT, player.x, player.y, vx, vy, 18, true))
                }
                soundSynth.playSfx(SoundSynth.SfxType.LASER)
            }
            BuckyCharacter.BLINKY -> {
                createExplosionParticles(player.x + player.width / 2f, player.y + player.height / 2f, Color.RED, 30)
                for (e in enemies) {
                    if (e.isAlive && Math.hypot((e.x - player.x).toDouble(), (e.y - player.y).toDouble()) < 350.0) {
                        e.takeDamage(100)
                    }
                }
                soundSynth.playSfx(SoundSynth.SfxType.BOMB)
            }
        }
    }

    fun swapCharacter() {
        val unlockedChars = BuckyCharacter.values().filter { saveManager.isCharacterUnlocked(it.name) }
        if (unlockedChars.isEmpty()) return
        val currentIdx = unlockedChars.indexOf(player.activeCharacter)
        val nextIdx = (currentIdx + 1) % unlockedChars.size
        player.activeCharacter = unlockedChars[nextIdx]
        soundSynth.playSfx(SoundSynth.SfxType.SELECT)
    }

    private fun stageCleared() {
        state = GameState.STAGE_CLEAR
        saveManager.setPlanetCompleted(currentPlanetIndex, true)
        val info = level.planetInfo
        if (info.rescuedChar != null) {
            saveManager.unlockCharacter(info.rescuedChar.name)
        }
        if (player.score > saveManager.highScore) {
            saveManager.highScore = player.score
        }
        soundSynth.playSfx(SoundSynth.SfxType.BOSS_DEFEAT)
    }

    private fun playerDamage(amount: Int) {
        player.health -= amount
        player.invulnerabilityTimer = 1.2f
        soundSynth.playSfx(SoundSynth.SfxType.HIT)
        createExplosionParticles(player.x + player.width / 2f, player.y + player.height / 2f, Color.RED, 8)

        if (player.health <= 0) {
            player.lives--
            if (player.lives <= 0) {
                state = GameState.GAME_OVER
                soundSynth.stopMusic()
            } else {
                player.resetState(level.spawnX, level.spawnY)
            }
        }
    }

    private fun checkPlayerTileCollision(horizontal: Boolean) {
        val tileSize = level.tileSize
        val minX = (player.x / tileSize).toInt().coerceIn(0, level.widthTiles - 1)
        val maxX = ((player.x + player.width) / tileSize).toInt().coerceIn(0, level.widthTiles - 1)
        val minY = (player.y / tileSize).toInt().coerceIn(0, level.heightTiles - 1)
        val maxY = ((player.y + player.height) / tileSize).toInt().coerceIn(0, level.heightTiles - 1)

        if (!horizontal) player.isGrounded = false

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val tile = level.tiles[y][x]
                if (tile == Tile.EMPTY || tile == Tile.LADDER) continue

                val tx = x * tileSize
                val ty = y * tileSize

                if (tile == Tile.SPIKE || tile == Tile.LAVA || tile == Tile.ACID) {
                    if (checkBoxIntersect(player.x, player.y, player.width, player.height, tx, ty, tileSize, tileSize)) {
                        playerDamage(30)
                    }
                    continue
                }

                if (tile == Tile.DOOR_GOAL) {
                    if (checkBoxIntersect(player.x, player.y, player.width, player.height, tx, ty, tileSize, tileSize)) {
                        stageCleared()
                    }
                    continue
                }

                if (tile == Tile.SOLID || tile == Tile.DESTRUCTIBLE || (tile == Tile.PLATFORM && !horizontal && player.vy > 0 && player.y + player.height - player.vy <= ty)) {
                    if (checkBoxIntersect(player.x, player.y, player.width, player.height, tx, ty, tileSize, tileSize)) {
                        if (horizontal) {
                            if (player.vx > 0) player.x = tx - player.width
                            else if (player.vx < 0) player.x = tx + tileSize
                            player.vx = 0f
                        } else {
                            if (player.vy > 0) {
                                player.y = ty - player.height
                                player.isGrounded = true
                                player.isClimbing = false
                            } else if (player.vy < 0 && tile != Tile.PLATFORM) {
                                player.y = ty + tileSize
                            }
                            player.vy = 0f
                        }
                    }
                }
            }
        }
    }

    private fun createExplosionParticles(x: Float, y: Float, color: Int, count: Int) {
        val random = java.util.Random()
        for (i in 0 until count) {
            val vx = (random.nextFloat() * 2f - 1f) * 200f
            val vy = (random.nextFloat() * 2f - 1f) * 200f
            val size = random.nextFloat() * 8f + 4f
            val life = random.nextFloat() * 0.4f + 0.2f
            particles.add(Particle(x, y, vx, vy, color, size, life))
        }
    }

    private fun checkBoxIntersect(x1: Float, y1: Float, w1: Float, h1: Float, x2: Float, y2: Float, w2: Float, h2: Float): Boolean {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2
    }

    private fun checkCircleBoxIntersect(cx: Float, cy: Float, cr: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        val closestX = cx.coerceIn(bx, bx + bw)
        val closestY = cy.coerceIn(by, by + bh)
        val dx = cx - closestX
        val dy = cy - closestY
        return (dx * dx + dy * dy) < (cr * cr)
    }

    fun release() {
        soundSynth.release()
    }
}
