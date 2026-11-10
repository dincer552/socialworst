package com.dincer552.hattrickssh.game

import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.character.SpecialAbilityManager

/** Player - The main playable character with physics, animation, and shooting.
 * 
 * Supports multiple character types with unique weapons, special abilities,
 * and movement characteristics. Character switching is handled via CharacterManager.
 */
class Player(
    position: com.dincer552.hattrickssh.game.character.GamePosition,
    private val characterManager: CharacterManager,
    private val specialAbilityManager: SpecialAbilityManager
) : com.dincer552.hattrickssh.game.character.GameObject(
    position,
    com.dincer552.hattrickssh.game.character.GameVelocity(0f, 0f),
    com.dincer552.hattrickssh.game.character.GameRect(
        position.x + 4f, position.y + 4f, position.x + 28f, position.y + 28f
    ),
    isActive = true
) {

    private var isGrounded: Boolean = false
    private var isJumpPrepping: Boolean = false
    private var jumpPrepTimer: Float = 0f
    private var shootTimer: Float = 0f
    private var shootCooldown: Float = 0.15f
    private var invincibleTimer: Float = 0f
    private var isOnConveyor: Boolean = false

    override fun update(deltaTime: Float) {
        if (!isActive) return

        // Update charge state
        characterManager.updateActiveCharge(deltaTime)

        // Update special ability state
        specialAbilityManager.update(deltaTime)

        // Update invincibility timer
        if (invincibleTimer > 0) {
            invincibleTimer -= deltaTime
        }

        // Update shoot cooldown
        if (shootTimer > 0) {
            shootTimer -= deltaTime
        }

        // Handle jump preparation
        if (isJumpPrepping) {
            jumpPrepTimer += deltaTime
            if (jumpPrepTimer >= 0.15f) {
                isJumpPrepping = false
                performJump()
            }
        }

        // Apply gravity (unless jetpack is active)
        if (!characterManager.getActiveCanJetpack || !specialAbilityManager.isActive(com.dincer552.hattrickssh.game.character.SpecialAbilityType.JETPACK_FLIGHT)) {
            velocity.y += com.dincer552.hattrickssh.game.character.PhysicsConfig.GRAVITY * deltaTime * 60f
            if (velocity.y > com.dincer552.hattrickssh.game.character.PhysicsConfig.MAX_FALL_SPEED) {
                velocity.y = com.dincer552.hattrickssh.game.character.PhysicsConfig.MAX_FALL_SPEED
            }
        }

        // Apply horizontal movement
        position.x += velocity.x * deltaTime * 60f
        position.y += velocity.y * deltaTime * 60f

        // Ground friction
        if (isGrounded && Math.abs(velocity.x) > 0.1f) {
            velocity.x *= com.dincer552.hattrickssh.game.character.PhysicsConfig.GROUND_FRICTION
            if (Math.abs(velocity.x) < 0.1f) velocity.x = 0f
        }

        // Apply conveyor belt movement
        if (isOnConveyor && characterManager.getActiveCanJetpack == false) {
            // Conveyor handled in GameWorld
        }

        // Update collision rect
        updateCollisionRect()
    }

    /** Perform the actual jump. */
    private fun performJump() {
        var jumpPower = characterManager.getActiveJumpPower

        // Apply high jump boost if Bucky's special is active
        if (specialAbilityManager.isActive(com.dincer552.hattrickssh.game.character.SpecialAbilityType.HIGH_JUMP)) {
            jumpPower = specialAbilityManager.applyHighJump(jumpPower)
        }

        velocity.y = jumpPower
        isGrounded = false
        isJumpPrepping = false
    }

    /** Start moving left or right. */
    fun startMove(direction: Int, isRun: Boolean = false) {
        if (!isActive || isJumpPrepping) return
        velocity.x = direction * (if (isRun) com.dincer552.hattrickssh.game.character.PhysicsConfig.RUN_SPEED else com.dincer552.hattrickssh.game.character.PhysicsConfig.WALK_SPEED)
    }

    /** Stop horizontal movement. */
    fun stopMove() {
        velocity.x = 0f
    }

    /** Start jump preparation (crouch before jump). */
    fun startJump() {
        if (!isActive || !isGrounded || isJumpPrepping) return
        isJumpPrepping = true
        jumpPrepTimer = 0f
    }

    /** Abort jump preparation. */
    fun abortJump() {
        isJumpPrepping = false
        jumpPrepTimer = 0f
    }

    /** Fire a projectile. Returns the projectile data if successful. */
    fun fire(): com.dincer552.hattrickssh.game.character.ProjectileData? {
        if (!isActive || shootTimer > 0 || isJumpPrepping) return null
        shootTimer = shootCooldown

        val config = com.dincer552.hattrickssh.game.character.WeaponRegistry.get(
            characterManager.getActiveWeaponType
        )

        // Apply power boost speed multiplier if Willy's special is active
        val effectiveSpeed = if (specialAbilityManager.isActive(com.dincer552.hattrickssh.game.character.SpecialAbilityType.POWER_BOOST)) {
            config.stats.speed * specialAbilityManager.getPowerBoostSpeedMultiplier()
        } else {
            config.stats.speed
        }

        val facingRight = velocity.x >= 0 || true // Default to facing right; updated by input

        val projectiles = com.dincer552.hattrickssh.game.character.WeaponFactory.createProjectiles(
            config,
            position.x,
            position.y,
            facingRight
        )

        // Apply power boost damage multiplier
        val damageMultiplier = if (specialAbilityManager.isActive(com.dincer552.hattrickssh.game.character.SpecialAbilityType.POWER_BOOST)) {
            specialAbilityManager.getPowerBoostDamageMultiplier()
        } else {
            1.0f
        }

        // Return the first projectile (others handled by caller for spread patterns)
        if (projectiles.isNotEmpty()) {
            val p = projectiles[0]
            return com.dincer552.hattrickssh.game.character.ProjectileData(
                x = p.x,
                y = p.y,
                vx = p.vx * damageMultiplier.toInt().toFloat(), // Approximate
                vy = p.vy,
                weaponType = p.weaponType,
                damage = (p.damage * damageMultiplier).toInt(),
                size = p.size,
                color = p.color,
                glowColor = p.glowColor,
                lifetime = p.lifetime,
                canPierce = p.canPierce,
                canBreakIce = p.canBreakIce,
                ownerType = p.ownerType
            )
        }
        return null
    }

    /** Take damage. Returns true if player is defeated. */
    fun takeDamage(amount: Int): Boolean {
        if (!isActive || invincibleTimer > 0) return false
        invincibleTimer = 1.0f // 1 second invincibility
        return characterManager.damageTeam(amount)
    }

    /** Land on ground. */
    fun land(groundY: Float) {
        position.y = groundY - 24f // 24 = player height
        velocity.y = 0f
        isGrounded = true
        isJumpPrepping = false
    }

    /** Set grounded state. */
    fun setGrounded(grounded: Boolean) {
        isGrounded = grounded
    }

    /** Check if player is on ground. */
    val isOnGround: Boolean
        get() = isGrounded

    /** Check if player is invincible. */
    val isInvincible: Boolean
        get() = invincibleTimer > 0

    /** Get the active character type. */
    val getActiveCharacterType: com.dincer552.hattrickssh.game.character.CharacterType
        get() = characterManager.getActiveCharacterType

    /** Check if player can break ice blocks. */
    val canBreakIce: Boolean
        get() = characterManager.getActiveCanBreakIce

    /** Check if player can climb walls. */
    val canClimbWalls: Boolean
        get() = characterManager.getActiveCanClimbWalls

    /** Check if player can jetpack. */
    val canJetpack: Boolean
        get() = characterManager.getActiveCanJetpack

    /** Check if player has piercing shots. */
    val hasPiercingShots: Boolean
        get() = characterManager.getActiveHasPiercingShots

    /** Check if player has guided orb ability. */
    val hasGuidedOrb: Boolean
        get() = characterManager.getActiveHasGuidedOrb

    /** Check if player can high jump. */
    val canHighJump: Boolean
        get() = characterManager.getActiveCanHighJump

    /** Get the active weapon type. */
    val getActiveWeaponType: com.dincer552.hattrickssh.game.character.WeaponType
        get() = characterManager.getActiveWeaponType

    /** Get the active special ability type. */
    val getActiveSpecialAbility: com.dincer552.hattrickssh.game.character.SpecialAbilityType
        get() = characterManager.getActiveSpecialAbility

    /** Get charge percentage. */
    val getChargePercent: Float
        get() = characterManager.getActiveChargePercent

    /** Get current health. */
    val getHealth: Int
        get() = characterManager.getTeamHealth

    /** Get max health. */
    val getMaxHealth: Int
        get() = characterManager.getMaxTeamHealth

    /** Get current power gauge. */
    val getPowerGauge: Int
        get() = characterManager.getActivePowerGauge

    /** Get max power gauge. */
    val getMaxPowerGauge: Int
        get() = characterManager.getActiveMaxPowerGauge

    /** Get power gauge percentage. */
    val getPowerGaugePercent: Float
        get() = characterManager.getPowerGaugePercent

    /** Start charging special ability. */
    fun startCharge() {
        characterManager.startActiveCharge()
    }

    /** Release charged special ability. */
    fun releaseCharge(): Boolean {
        return characterManager.releaseActiveCharge()
    }

    /** Cancel charging. */
    fun cancelCharge() {
        characterManager.cancelActiveCharge()
    }

    /** Switch to a different character. */
    fun switchCharacter(type: com.dincer552.hattrickssh.game.character.CharacterType): Boolean {
        return characterManager.switchTo(type)
    }

    /** Cycle to next character. */
    fun cycleNextCharacter(): Boolean {
        return characterManager.cycleNext()
    }

    /** Cycle to previous character. */
    fun cyclePreviousCharacter(): Boolean {
        return characterManager.cyclePrevious()
    }

    /** Get special ability description. */
    val getSpecialDescription: String
        get() = characterManager.getActiveSpecialDescription
}
