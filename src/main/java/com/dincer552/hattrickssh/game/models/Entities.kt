package com.dincer552.hattrickssh.game.models

class Player(
    var x: Float = 100f,
    var y: Float = 300f,
    val width: Float = 36f,
    val height: Float = 48f
) {
    var vx: Float = 0f
    var vy: Float = 0f
    var isGrounded: Boolean = false
    var isClimbing: Boolean = false
    var isFacingRight: Boolean = true
    var health: Int = 100
    val maxHealth: Int = 100
    var energy: Int = 100
    val maxEnergy: Int = 100
    var activeCharacter: BuckyCharacter = BuckyCharacter.BUCKY
    var chargeTime: Float = 0f
    var isCharging: Boolean = false
    var invulnerabilityTimer: Float = 0f
    var jetpackActive: Boolean = false
    var jetpackFuel: Float = 100f
    var lives: Int = 3
    var score: Int = 0

    fun resetState(spawnX: Float, spawnY: Float) {
        x = spawnX
        y = spawnY
        vx = 0f
        vy = 0f
        isGrounded = false
        isClimbing = false
        health = maxHealth
        energy = maxEnergy
        invulnerabilityTimer = 0f
        jetpackFuel = 100f
    }
}

enum class EnemyType(
    val maxHp: Int,
    val contactDamage: Int,
    val width: Float,
    val height: Float,
    val isBoss: Boolean = false
) {
    TOAD_SOLDIER(30, 10, 36f, 44f),
    FLYING_COPTER(25, 12, 40f, 36f),
    MECH_WALKER(60, 20, 50f, 60f),
    LAVA_SLUG(20, 15, 32f, 24f),
    TURRET(40, 15, 36f, 36f),
    BOSS_TOAD(300, 25, 80f, 96f, true),
    BOSS_AIR_MARSHAL(400, 30, 96f, 100f, true),
    BOSS_KOMPLEX(550, 35, 120f, 120f, true)
}

class Enemy(
    val type: EnemyType,
    var x: Float,
    var y: Float
) {
    val width: Float = type.width
    val height: Float = type.height
    var vx: Float = 0f
    var vy: Float = 0f
    var hp: Int = type.maxHp
    val maxHp: Int = type.maxHp
    var isFacingRight: Boolean = false
    var shootCooldown: Float = 0f
    var aiStateTimer: Float = 0f
    var isAlive: Boolean = true
    var hitFlashTimer: Float = 0f

    fun takeDamage(damage: Int) {
        hp -= damage
        hitFlashTimer = 0.15f
        if (hp <= 0) {
            hp = 0
            isAlive = false
        }
    }
}

enum class ProjectileType {
    LASER_PLAYER,
    HOMING_MAGIC,
    MEGA_SHOT,
    SPREAD_SHOT,
    BOMB,
    ENEMY_BULLET,
    BOSS_LASER
}

class Projectile(
    val type: ProjectileType,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Int,
    val isFromPlayer: Boolean,
    val radius: Float = 8f,
    var lifetime: Float = 3f
) {
    var isAlive: Boolean = true
    var homingTarget: Enemy? = null
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Int,
    val size: Float,
    val maxLife: Float
) {
    var currentLife: Float = maxLife
    val isAlive: Boolean get() = currentLife > 0f
}

enum class PickupType {
    HEALTH,
    ENERGY,
    LIFE_UP,
    SCORE_STAR
}

class Pickup(
    val type: PickupType,
    var x: Float,
    var y: Float
) {
    val width: Float = 24f
    val height: Float = 24f
    var isAlive: Boolean = true
}
