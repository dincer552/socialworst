package com.dincer552.hattrickssh.game.character

/** SpecialAbilityTypeData - Configuration for each special ability type. */
data class SpecialAbilityConfig(
    val type: SpecialAbilityType,
    val powerCost: Int,           // Power gauge cost to activate
    val durationSeconds: Float,   // How long the ability lasts
    val cooldownSeconds: Float,   // Cooldown after activation
    val description: String
)

/** SpecialAbilityRegistry - Special ability configurations for each character type. */
object SpecialAbilityRegistry {
    private val configs: Map<SpecialAbilityType, SpecialAbilityConfig> = mapOf(
        SpecialAbilityType.HIGH_JUMP to SpecialAbilityConfig(
            type = SpecialAbilityType.HIGH_JUMP,
            powerCost = 25,
            durationSeconds = 0.0f,   // Instant jump
            cooldownSeconds = 0.5f,
            description = "Charged high jump"
        ),
        SpecialAbilityType.JETPACK_FLIGHT to SpecialAbilityConfig(
            type = SpecialAbilityType.JETPACK_FLIGHT,
            powerCost = 30,
            durationSeconds = 2.0f,
            cooldownSeconds = 1.0f,
            description = "Jetpack flight"
        ),
        SpecialAbilityType.WALL_CLIMB to SpecialAbilityConfig(
            type = SpecialAbilityType.WALL_CLIMB,
            powerCost = 20,
            durationSeconds = 1.5f,
            cooldownSeconds = 0.5f,
            description = "Wall climbing"
        ),
        SpecialAbilityType.GUIDED_ORB to SpecialAbilityType.GUIDED_ORB,
        powerCost = 40,
        durationSeconds = 3.0f,
        cooldownSeconds = 1.0f,
        description = "Controllable guided energy orb"
        ),
        SpecialAbilityType.POWER_BOOST to SpecialAbilityConfig(
            type = SpecialAbilityType.POWER_BOOST,
            powerCost = 35,
            durationSeconds = 4.0f,
            cooldownSeconds = 1.5f,
            description = "Increased laser power"
        )
    )

    fun get(type: SpecialAbilityType): SpecialAbilityConfig = configs[type]
        ?: throw IllegalArgumentException("No special ability config for $type")
}

/** SpecialAbilityState - Runtime state of an active special ability. */
data class SpecialAbilityState(
    val type: SpecialAbilityType,
    var isActive: Boolean = false,
    var elapsedTime: Float = 0f,
    var remainingDuration: Float = 0f,
    var cooldownRemaining: Float = 0f
)

/** SpecialAbilityManager - Manages special abilities for all characters.
 *
 * Handles charging, activation, and runtime behavior of special abilities.
 * Each character type has a different ability with different effects.
 */
class SpecialAbilityManager {

    private val abilityStates: MutableMap<SpecialAbilityType, SpecialAbilityState> = mutableMapOf()

    init {
        for (type in SpecialAbilityType.values()) {
            abilityStates[type] = SpecialAbilityState(type = type)
        }
    }

    // ========================================================================
    // Charge Management
    // ========================================================================

    /** Start charging the specified ability. */
    fun startCharge(type: SpecialAbilityType) {
        val state = abilityStates[type] ?: return
        if (state.isActive || state.cooldownRemaining > 0) return
        state.isActive = false // Will be set when released
    }

    /** Check if the ability is fully charged and ready to release. */
    fun isFullyCharged(type: SpecialAbilityType): Boolean {
        return abilityStates[type]?.let { !it.isActive && it.cooldownRemaining <= 0 } ?: false
    }

    /** Release the charged ability. Returns true if activation succeeded. */
    fun releaseCharge(type: SpecialAbilityType): Boolean {
        val state = abilityStates[type] ?: return false
        if (state.isActive || state.cooldownRemaining > 0) return false

        val config = SpecialAbilityRegistry.get(type)
        state.isActive = true
        state.elapsedTime = 0f
        state.remainingDuration = config.durationSeconds
        state.cooldownRemaining = config.cooldownSeconds
        return true
    }

    /** Cancel charging. */
    fun cancelCharge(type: SpecialAbilityType) {
        val state = abilityStates[type] ?: return
        state.isActive = false
        state.elapsedTime = 0f
    }

    /** Update ability states each frame. */
    fun update(deltaTime: Float) {
        for ((type, state) in abilityStates) {
            if (state.isActive) {
                state.elapsedTime += deltaTime
                state.remainingDuration = maxOf(0f, state.remainingDuration - deltaTime)
                if (state.remainingDuration <= 0) {
                    state.isActive = false
                    state.cooldownRemaining = SpecialAbilityRegistry.get(type).cooldownSeconds
                }
            } else if (state.cooldownRemaining > 0) {
                state.cooldownRemaining = maxOf(0f, state.cooldownRemaining - deltaTime)
            }
        }
    }

    /** Check if a specific ability is currently active. */
    fun isActive(type: SpecialAbilityType): Boolean {
        return abilityStates[type]?.isActive ?: false
    }

    /** Get the remaining duration of an active ability. */
    fun getRemainingDuration(type: SpecialAbilityType): Float {
        return abilityStates[type]?.remainingDuration ?: 0f
    }

    /** Get the cooldown remaining for an ability. */
    fun getCooldownRemaining(type: SpecialAbilityType): Float {
        return abilityStates[type]?.cooldownRemaining ?: 0f
    }

    /** Get the charge percentage for display (0.0 to 1.0). */
    fun getChargePercent(type: SpecialAbilityType): Float {
        // Simple charge visualization based on time since last activation
        val state = abilityStates[type] ?: return 0f
        if (state.isActive || state.cooldownRemaining > 0) return 0f
        // Simple oscillation for visual feedback when ready
        return 1.0f
    }

    /** Check if an ability can be activated (not on cooldown, not active). */
    fun canActivate(type: SpecialAbilityType): Boolean {
        val state = abilityStates[type] ?: return false
        return !state.isActive && state.cooldownRemaining <= 0
    }

    // ========================================================================
    // Ability-Specific Effects
    // ========================================================================

    /** Apply high jump effect (Bucky). Returns the modified jump velocity. */
    fun applyHighJump(baseJumpPower: Float): Float {
        if (!isActive(SpecialAbilityType.HIGH_JUMP)) return baseJumpPower
        return baseJumpPower * 1.5f // 50% higher jump
    }

    /** Apply jetpack flight effect (Blinky). Returns vertical velocity modifier. */
    fun applyJetpackFlight(currentVy: Float, deltaTime: Float): Float {
        if (!isActive(SpecialAbilityType.JETPACK_FLIGHT)) return currentVy
        return currentVy - 4.0f * deltaTime * 60f // Upward thrust
    }

    /** Check if wall climbing is active (Dead-Eye). */
    fun canWallClimb(): Boolean {
        return isActive(SpecialAbilityType.WALL_CLIMB)
    }

    /** Create a guided orb projectile (Jenny). */
    fun createGuidedOrb(
        startX: Float,
        startY: Float,
        targetX: Float,
        targetY: Float,
        speed: Float = 5.0f
    ): ProjectileData {
        val dx = targetX - startX
        val dy = targetY - startY
        val distance = Math.sqrt(dx.toDouble() * dx.toDouble() + dy.toDouble() * dy.toDouble()).toFloat()
        val vx = if (distance > 0) dx / distance * speed else 0f
        val vy = if (distance > 0) dy / distance * speed else 0f

        return ProjectileData(
            x = startX,
            y = startY,
            vx = vx,
            vy = vy,
            weaponType = WeaponType.BLUE_LASER,
            damage = 2,
            size = 10.0f,
            color = 0xFF00FFFF.toInt(),
            glowColor = 0xFF88FFFF.toInt(),
            lifetime = 4.0f,
            canPierce = true,
            canBreakIce = false,
            ownerType = CharacterType.JENNY
        )
    }

    /** Check if power boost is active (Willy). Returns damage multiplier. */
    fun getPowerBoostDamageMultiplier(): Float {
        if (!isActive(SpecialAbilityType.POWER_BOOST)) return 1.0f
        return 2.0f // Double damage during power boost
    }

    /** Check if power boost increases shot speed (Willy). */
    fun getPowerBoostSpeedMultiplier(): Float {
        if (!isActive(SpecialAbilityType.POWER_BOOST)) return 1.0f
        return 1.5f // 50% faster shots during power boost
    }
}
