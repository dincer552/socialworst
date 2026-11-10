package com.dincer552.hattrickssh.game.character

/** RescueStatus - Tracks whether a crew member has been rescued. */
enum class RescueStatus {
    NOT_RESCUED,   // Not yet found
    RESCUED,       // Rescued and playable
    CAPTURED_AGAIN // Rescued but captured again during Toad Mothership
}

/** PlanetAccess - Tracks which planets the player can access.
 *
 * Blue Planet requires Blinky (his weapon breaks ice blocks).
 * Other planets are accessible from the start.
 */
enum class PlanetAccess {
    GREEN,   // Always accessible
    RED,     // Always accessible
    BLUE,    // Requires Blinky rescued
    YELLOW   // Always accessible
}

/** CharacterManager - Manages character unlock status, switching, and rescue progression.
 *
 * Responsibilities:
 * - Track which characters are rescued/unlocked
 * - Handle character switching during gameplay
 * - Track shared team health
 * * Track planet access dependencies (Blinky required for Blue Planet)
 * - Manage rescue events
 */
class CharacterManager {

    // Shared team health (single pool across all characters)
    private var teamHealth: Int = 3
    private val maxTeamHealth: Int = 3

    // Rescue status per character
    private val rescueStatus: MutableMap<CharacterType, RescueStatus> = mutableMapOf(
        CharacterType.BUCKY to RescueStatus.RESCUED,      // Starting character
        CharacterType.BLINKY to RescueStatus.NOT_RESCUED,
        CharacterType.DEAD_EYE to RescueStatus.NOT_RESCUED,
        CharacterType.JENNY to RescueStatus.NOT_RESCUED,
        CharacterType.WILLY to RescueStatus.NOT_RESCUED
    )

    // Currently active character
    private var activeCharacterType: CharacterType = CharacterType.BUCKY

    // Character instances (instantiated when needed)
    private val characterInstances: MutableMap<CharacterType, Character> = mutableMapOf(
        CharacterType.BUCKY to CharacterFactory.create(CharacterType.BUCKY)
    )

    // Planet access status
    private val planetAccess: MutableMap<PlanetAccess, Boolean> = mutableMapOf(
        PlanetAccess.GREEN to true,
        PlanetAccess.RED to true,
        PlanetAccess.BLUE to false,   // Locked until Blinky rescued
        PlanetAccess.YELLOW to true
    )

    init {
        // Initialize power gauges for all characters
        for (type in CharacterRegistry.allTypes) {
            val character = CharacterFactory.create(type)
            characterInstances[type] = character
        }
    }

    // ========================================================================
    // Character Access
    // ========================================================================

    /** Get the currently active character. */
    val getActiveCharacter: Character
        get() = characterInstances[activeCharacterType]!!
    val getActiveCharacterType: CharacterType
        get() = activeCharacterType

    /** Get all character instances. */
    val getAllCharacters: Map<CharacterType, Character>
        get() = characterInstances

    /** Check if a character is unlocked/rescued. */
    fun isUnlocked(type: CharacterType): Boolean {
        return rescueStatus[type] == RescueStatus.RESCUED
    }

    /** Get all unlocked character types. */
    val getUnlockedTypes: List<CharacterType>
        get() = CharacterRegistry.allTypes.filter { isUnlocked(it) }

    /** Switch to a different character. Returns true if switch succeeded. */
    fun switchTo(type: CharacterType): Boolean {
        if (!isUnlocked(type)) return false
        if (type == activeCharacterType) return true

        // Save current state before switching
        val oldChar = characterInstances[activeCharacterType]!!

        // Transfer current health to the new character
        val newChar = characterInstances[type]!!
        newChar.currentHealth = oldChar.currentHealth

        // Switch
        activeCharacterType = type
        return true
    }

    /** Cycle to the next unlocked character. */
    fun cycleNext(): Boolean {
        val unlocked = CharacterRegistry.allTypes.filter { isUnlocked(it) }
        if (unlocked.size <= 1) return false

        val currentIndex = unlocked.indexOf(activeCharacterType)
        val nextIndex = (currentIndex + 1) % unlocked.size
        return switchTo(unlocked[nextIndex])
    }

    /** Cycle to the previous unlocked character. */
    fun cyclePrevious(): Boolean {
        val unlocked = CharacterRegistry.allTypes.filter { isUnlocked(it) }
        if (unlocked.size <= 1) return false

        val currentIndex = unlocked.indexOf(activeCharacterType)
        val prevIndex = (currentIndex - 1 + unlocked.size) % unlocked.size
        return switchTo(unlocked[prevIndex])
    }

    // ========================================================================
    // Team Health
    // ========================================================================

    val getTeamHealth: Int
        get() = teamHealth
    val getMaxTeamHealth: Int
        get() = maxTeamHealth

    /** Set team health (shared across all characters). */
    fun setTeamHealth(amount: Int) {
        teamHealth = maxOf(0, minOf(maxTeamHealth, amount))
        // Sync to active character
        characterInstances[activeCharacterType]?.currentHealth = teamHealth
    }

    /** Heal team health. */
    fun healTeam(amount: Int) {
        teamHealth = minOf(maxTeamHealth, teamHealth + amount)
        characterInstances[activeCharacterType]?.currentHealth = teamHealth
    }

    /** Damage team health. Returns true if team is defeated. */
    fun damageTeam(amount: Int): Boolean {
        teamHealth = maxOf(0, teamHealth - amount)
        characterInstances[activeCharacterType]?.currentHealth = teamHealth
        return teamHealth <= 0
    }

    /** Sync team health to all characters. */
    fun syncHealthToAll() {
        val currentHealth = teamHealth
        for (character in characterInstances.values) {
            character.currentHealth = currentHealth
        }
    }

    // ========================================================================
    // Rescue System
    // ========================================================================

    /** Rescue a character. Returns true if rescue was successful (newly unlocked). */
    fun rescue(type: CharacterType): Boolean {
        if (rescueStatus[type] == RescueStatus.RESCUED) return false // Already rescued

        rescueStatus[type] = RescueStatus.RESCUED

        // Unlock the character in the registry
        // Note: CharacterRegistry.isUnlocked is immutable data, we track via rescueStatus

        // Sync health to the newly rescued character
        val character = characterInstances[type]!!
        character.currentHealth = teamHealth

        // Update planet access if Blinky was rescued
        if (type == CharacterType.BLINKY) {
            planetAccess[PlanetAccess.BLUE] = true
        }

        return true
    }

    /** Capture a character again (during Toad Mothership stages). */
    fun captureAgain(type: CharacterType) {
        if (rescueStatus[type] == RescueStatus.RESCUED) {
            rescueStatus[type] = RescueStatus.CAPTURED_AGAIN
        }
    }

    /** Re-rescue a captured character. */
    fun reRescue(type: CharacterType) {
        rescueStatus[type] = RescueStatus.RESCUED
    }

    val getRescueStatus: Map<CharacterType, RescueStatus>
        get() = rescueStatus.toMap()

    // ========================================================================
    // Planet Access
    // ========================================================================

    fun isPlanetAccessible(planet: PlanetAccess): Boolean {
        return planetAccess[planet] ?: false
    }

    val getPlanetAccess: Map<PlanetAccess, Boolean>
        get() = planetAccess.toMap()

    /** Check if Blue Planet is accessible (requires Blinky). */
    fun canAccessBluePlanet(): Boolean {
        return isUnlocked(CharacterType.BLINKY)
    }

    // ========================================================================
    // Power Gauge
    // ========================================================================

    /** Get the active character's power gauge. */
    val getActivePowerGauge: Int
        get() = characterInstances[activeCharacterType]?.currentPowerGauge ?: 0

    /** Get the active character's max power gauge. */
    val getActiveMaxPowerGauge: Int
        get() = characterInstances[activeCharacterType]?.data?.maxPowerGauge ?: 100

    /** Get power gauge percentage (0.0 to 1.0). */
    val getPowerGaugePercent: Float
        get() {
            val char = characterInstances[activeCharacterType] ?: return 0f
            return char.currentPowerGauge.toFloat() / (char.data?.maxPowerGauge ?: 100)
        }

    /** Consume power from the active character. */
    fun consumeActivePower(amount: Int): Boolean {
        return characterInstances[activeCharacterType]?.consumePower(amount) ?: false
    }

    /** Recharge the active character's power gauge. */
    fun rechargeActivePower(deltaTime: Float) {
        characterInstances[activeCharacterType]?.rechargePower(deltaTime)
    }

    /** Sync power gauge to all characters when switching. */
    fun syncPowerToAll() {
        val activeChar = characterInstances[activeCharacterType]
        if (activeChar == null) return
        val gauge = activeChar.currentPowerGauge
        for (character in characterInstances.values) {
            character.currentPowerGauge = gauge
        }
    }

    // ========================================================================
    // Charge State
    // ========================================================================

    /** Get the active character's charge state. */
    val getActiveChargeState: ChargeState
        get() = characterInstances[activeCharacterType]?.chargeState ?: ChargeState.IDLE

    /** Start charging the active character's special ability. */
    fun startActiveCharge() {
        characterInstances[activeCharacterType]?.startCharge()
    }

    /** Release the active character's charged special ability. */
    fun releaseActiveCharge(): Boolean {
        return characterInstances[activeCharacterType]?.releaseCharge() ?: false
    }

    /** Cancel the active character's charge. */
    fun cancelActiveCharge() {
        characterInstances[activeCharacterType]?.cancelCharge()
    }

    /** Update the active character's charge state. */
    fun updateActiveCharge(deltaTime: Float) {
        characterInstances[activeCharacterType]?.updateCharge(deltaTime)
    }

    /** Get the active character's charge percentage. */
    val getActiveChargePercent: Float
        get() = characterInstances[activeCharacterType]?.getChargePercent() ?: 0f

    // ========================================================================
    // Character Properties (delegated to active character)
    // ========================================================================

    val getActiveWeaponType: WeaponType
        get() = characterInstances[activeCharacterType]?.weaponType ?: WeaponType.STRAIGHT

    val getActiveSpecialAbility: SpecialAbilityType
        get() = characterInstances[activeCharacterType]?.specialAbility ?: SpecialAbilityType.HIGH_JUMP

    val getActiveCanBreakIce: Boolean
        get() = characterInstances[activeCharacterType]?.canBreakIce ?: false

    val getActiveCanClimbWalls: Boolean
        get() = characterInstances[activeCharacterType]?.canClimbWalls ?: false

    val getActiveCanJetpack: Boolean
        get() = characterInstances[activeCharacterType]?.canJetpack ?: false

    val getActiveHasPiercingShots: Boolean
        get() = characterInstances[activeCharacterType]?.hasPiercingShots ?: false

    val getActiveHasGuidedOrb: Boolean
        get() = characterInstances[activeCharacterType]?.hasGuidedOrb ?: false

    val getActiveCanHighJump: Boolean
        get() = characterInstances[activeCharacterType]?.canHighJump ?: false

    val getActiveMoveSpeed: Float
        get() = characterInstances[activeCharacterType]?.data?.moveSpeed ?: 2.0f

    val getActiveJumpPower: Float
        get() = characterInstances[activeCharacterType]?.data?.jumpPower ?: -6.5f

    val getActiveWeaponProjectileCount: Int
        get() = characterInstances[activeCharacterType]?.getWeaponProjectileCount() ?: 1

    val getActiveWeaponSpreadAngle: Float
        get() = characterInstances[activeCharacterType]?.getWeaponSpreadAngle() ?: 0f

    val getActiveSpecialDescription: String
        get() = characterInstances[activeCharacterType]?.getSpecialDescription() ?: ""
}
