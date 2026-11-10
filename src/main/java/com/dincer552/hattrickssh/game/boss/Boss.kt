package com.dincer552.hattrickssh.game.boss

import com.dincer552.hattrickssh.game.*
import com.dincer552.hattrickssh.game.planet.PlanetType

/** BossType - The bosses in each planet. */
enum class BossType {
    TOAD_COMMANDER,       // Green Planet boss
    TOAD_STORMTROOPER,    // Red Planet boss
    TOAD_AIR_MARSHALL,    // Blue Planet boss
    TOAD_BORG,            // Yellow Planet boss
    TOAD_MOTHERSHIP_CORE  // Mothership final boss
}

/** BossPhase - Phases of a boss fight. */
enum class BossPhase {
    PHASE_1,   // Initial attack pattern
    PHASE_2,   // Enraged/changed pattern
    PHASE_3,   // Final desperate attack
    DEFEATED   // Boss defeated
}

/** BossData - Static data for each boss. */
data class BossData(
    val type: BossType,
    val name: String,
    val planetType: com.dincer552.hattrickssh.game.planet.PlanetType?,
    val maxHealth: Int,
    val damagePerHit: Int,
    val numPhases: Int,
    val attackPattern: String,
    val weakPoint: String,
    val defeatReward: String
)

/** BossRegistry - Static data for all bosses. */
object BossRegistry {
    private val bosses: Map<BossType, BossData> = mapOf(
        BossType.TOAD_COMMANDER to BossData(
            type = BossType.TOAD_COMMANDER,
            name = "Toad Commander",
            planetType = com.dincer552.hattrickssh.game.planet.PlanetType.GREEN,
            maxHealth = 10,
            damagePerHit = 1,
            numPhases = 2,
            attackPattern = "Jumps and shoots fireballs",
            weakPoint = "Head",
            defeatReward = "Rescue A.F.C. Blinky"
        ),
        BossType.TOAD_STORMTROOPER to BossData(
            type = BossType.TOAD_STORMTROOPER,
            name = "Toad Stormtrooper",
            planetType = com.dincer552.hattrickssh.game.planet.PlanetType.RED,
            maxHealth = 12,
            damagePerHit = 1,
            numPhases = 2,
            attackPattern = "Shoots spread fireballs and charges",
            weakPoint = "Helmet visor",
            defeatReward = "Rescue Dead-Eye Duck"
        ),
        BossType.TOAD_AIR_MARSHALL to BossData(
            type = BossType.TOAD_AIR_MARSHALL,
            name = "Toad Air Marshall",
            planetType = com.dincer552.hattrickssh.game.planet.PlanetType.BLUE,
            maxHealth = 15,
            damagePerHit = 1,
            numPhases = 3,
            attackPattern = "Flies and drops ice bombs",
            weakPoint = "Jetpack engine",
            defeatReward = "Rescue Jenny"
        ),
        BossType.TOAD_BORG to BossData(
            type = BossType.TOAD_BORG,
            name = "Toad Borg",
            planetType = com.dincer552.hattrickssh.game.planet.PlanetType.YELLOW,
            maxHealth = 20,
            damagePerHit = 2,
            numPhases = 3,
            attackPattern = "Fires laser beams and deploys drones",
            weakPoint = "Core processor",
            defeatReward = "Rescue Willy DuWitt"
        ),
        BossType.TOAD_MOTHERSHIP_CORE to BossData(
            type = BossType.TOAD_MOTHERSHIP_CORE,
            name = "Toad Mothership Core",
            planetType = null,  // Mothership, not a planet
            maxHealth = 30,
            damagePerHit = 2,
            numPhases = 4,
            attackPattern = "Multi-phase core battle",
            weakPoint = "Reactor core",
            defeatReward = "Final escape"
        )
    )

    fun get(type: BossType): BossData = bosses[type]
        ?: throw IllegalArgumentException("Unknown boss type: $type")

    fun getByPlanet(planetType: com.dincer552.hattrickssh.game.planet.PlanetType): BossData =
        bosses.values.first { it.planetType == planetType }

    val allBosses: List<BossData> = bosses.values.toList()
}

/** BossState - Runtime state of a boss fight. */
data class BossState(
    val type: BossType,
    var currentHealth: Int,
    var maxHealth: Int,
    var phase: BossPhase = BossPhase.PHASE_1,
    var isDefeated: Boolean = false,
    var isActive: Boolean = false
)

/** Boss - A boss enemy with health, phases, and attack patterns. */
class Boss(
    val type: BossType,
    private val data: BossData
) : GameObject(
    GamePosition(200f, 100f),
    GameVelocity(0f, 0f),
    GameRect(200f, 100f, 248f, 148f),
    isActive = false
) {
    private var state: BossState = BossState(
        type = type,
        currentHealth = data.maxHealth,
        maxHealth = data.maxHealth
    )

    init {
        collisionRect = GameRect(
            position.x, position.y, position.x + 48f, position.y + 48f
        )
    }

    /** Get boss data. */
    val getData: BossData get() = data

    /** Get current state. */
    val getState: BossState get() = state

    /** Get current health percentage (0.0 to 1.0). */
    val getHealthPercent: Float
        get() = state.currentHealth.toFloat() / state.maxHealth.toFloat()

    /** Check if boss is in final phase. */
    val isFinalPhase: Boolean
        get() = state.phase == BossPhase.PHASE_3 || state.phase == BossPhase.DEFEATED

    /** Activate the boss. */
    fun activate() {
        isActive = true
        state.isActive = true
        state.phase = BossPhase.PHASE_1
        state.currentHealth = data.maxHealth
    }

    /** Deactivate the boss. */
    fun deactivate() {
        isActive = false
        state.isActive = false
    }

    /** Take damage. Returns true if boss is defeated. */
    fun takeDamage(amount: Int): Boolean {
        if (!isActive || state.isDefeated) return false

        state.currentHealth = maxOf(0, state.currentHealth - amount)

        // Update phase based on health remaining
        val healthPercent = state.currentHealth.toFloat() / state.maxHealth.toFloat()
        if (healthPercent <= 0.25f && state.phase != BossPhase.PHASE_3) {
            state.phase = BossPhase.PHASE_3
        } else if (healthPercent <= 0.5f && state.phase == BossPhase.PHASE_1) {
            state.phase = BossPhase.PHASE_2
        }

        if (state.currentHealth <= 0) {
            state.isDefeated = true
            state.phase = BossPhase.DEFEATED
            isActive = false
            return true
        }

        return false
    }

    /** Update boss AI each frame. */
    override fun update(deltaTime: Float) {
        if (!isActive || state.isDefeated) return

        // Boss movement and attack patterns would be implemented here
        // based on the current phase
        updateCollisionRect()
    }

    override fun draw(canvas: android.graphics.Canvas, paint: android.graphics.Paint, offsetX: Float, offsetY: Float) {
        if (!isActive) return

        // Draw boss based on type
        paint.color = when (type) {
            BossType.TOAD_COMMANDER -> 0xFF4A4A4A.toInt()  // Gray
            BossType.TOAD_STORMTROOPER -> 0xFF6A4A4A.toInt()  // Dark red
            BossType.TOAD_AIR_MARSHALL -> 0xFF4A4A6A.toInt()  // Dark blue
            BossType.TOAD_BORG -> 0xFF4A6A4A.toInt()  // Dark green
            BossType.TOAD_MOTHERSHIP_CORE -> 0xFF6A4A6A.toInt()  // Purple
        }
        canvas.drawRect(
            position.x - offsetX,
            position.y - offsetY,
            position.x + 48f - offsetX,
            position.y + 48f - offsetY,
            paint
        )

        // Draw health bar
        val barWidth = 48f
        val barHeight = 6f
        val barX = position.x - offsetX
        val barY = position.y - offsetY - 8f
        paint.color = 0xFF444444.toInt()
        canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint)
        paint.color = 0xFFFF0000.toInt()
        val healthWidth = (barWidth * getHealthPercent).toFloat()
        canvas.drawRect(barX, barY, barX + healthWidth, barY + barHeight, paint)
    }

    /** Get the attack pattern description. */
    val getAttackPattern: String
        get() = data.attackPattern

    /** Get the weak point description. */
    val getWeakPoint: String
        get() = data.weakPoint
}

/** BossFactory - Creates boss instances. */
object BossFactory {
    fun create(type: BossType): Boss {
        return Boss(type, BossRegistry.get(type))
    }
}
