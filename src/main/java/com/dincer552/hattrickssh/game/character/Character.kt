package com.dincer552.hattrickssh.game.character

/** CharacterType - The five playable characters from Bucky O'Hare NES. */
enum class CharacterType {
    BUCKY,      // Starting character - straight shot, high jump
    BLINKY,     // Green Planet rescue - downward arc shot (breaks ice), jetpack
    DEAD_EYE,   // Red Planet rescue - three-direction spread, wall climbing
    JENNY,      // Blue Planet rescue - blue laser, controllable guided orb
    WILLY       // Yellow Planet rescue - piercing laser, increased power
}

/** WeaponType - Different weapon behaviors per character. */
enum class WeaponType {
    STRAIGHT,      // Bucky - standard straight-firing blaster
    DOWNWARD_ARC,  // Blinky - short-range downward arc (breaks ice/stone)
    SPREAD_THREE,  // Dead-Eye - three-direction spread shot
    BLUE_LASER,    // Jenny - blue laser weapon
    PIERCING       // Willy - powerful piercing laser
}

/** SpecialAbilityType - Unique special abilities per character. */
enum class SpecialAbilityType {
    HIGH_JUMP,       // Bucky - charged high jump
    JETPACK_FLIGHT,  // Blinky - jetpack flight
    WALL_CLIMB,      // Dead-Eye - wall climbing
    GUIDED_ORB,      // Jenny - controllable guided energy orb
    POWER_BOOST      // Willy - increased laser power
}

/** ChargeState - Tracks the charge state for special abilities. */
enum class ChargeState {
    IDLE,      // Not charging
    CHARGING,  // Player is holding the charge button
    CHARGED,   // Charge is complete, ready to release
    ACTIVE,    // Special ability is currently active
    COOLDOWN   // Ability is on cooldown
}

/** CharacterData - Static data for each character type. */
data class CharacterData(
    val type: CharacterType,
    val name: String,
    val weaponType: WeaponType,
    val specialAbility: SpecialAbilityType,
    val maxHealth: Int,
    val maxPowerGauge: Int,
    val moveSpeed: Float,
    val jumpPower: Float,
    val isUnlocked: Boolean = false
)

/** CharacterRegistry - Static lookup table for all five characters. */
object CharacterRegistry {
    private val characters: Map<CharacterType, CharacterData> = mapOf(
        CharacterType.BUCKY to CharacterData(
            type = CharacterType.BUCKY,
            name = "Bucky O'Hare",
            weaponType = WeaponType.STRAIGHT,
            specialAbility = SpecialAbilityType.HIGH_JUMP,
            maxHealth = 3,
            maxPowerGauge = 100,
            moveSpeed = 2.0f,
            jumpPower = -6.5f,
            isUnlocked = true  // Starting character
        ),
        CharacterType.BLINKY to CharacterData(
            type = CharacterType.BLINKY,
            name = "A.F.C. Blinky",
            weaponType = WeaponType.DOWNWARD_ARC,
            specialAbility = SpecialAbilityType.JETPACK_FLIGHT,
            maxHealth = 3,
            maxPowerGauge = 100,
            moveSpeed = 1.8f,
            jumpPower = -6.0f,
            isUnlocked = false
        ),
        CharacterType.DEAD_EYE to CharacterData(
            type = CharacterType.DEAD_EYE,
            name = "Dead-Eye Duck",
            weaponType = WeaponType.SPREAD_THREE,
            specialAbility = SpecialAbilityType.WALL_CLIMB,
            maxHealth = 3,
            maxPowerGauge = 100,
            moveSpeed = 1.7f,
            jumpPower = -5.8f,
            isUnlocked = false
        ),
        CharacterType.JENNY to CharacterData(
            type = CharacterType.JENNY,
            name = "Jenny",
            weaponType = WeaponType.BLUE_LASER,
            specialAbility = SpecialAbilityType.GUIDED_ORB,
            maxHealth = 3,
            maxPowerGauge = 100,
            moveSpeed = 1.9f,
            jumpPower = -6.2f,
            isUnlocked = false
        ),
        CharacterType.WILLY to CharacterData(
            type = CharacterType.WILLY,
            name = "Willy DuWitt",
            weaponType = WeaponType.PIERCING,
            specialAbility = SpecialAbilityType.POWER_BOOST,
            maxHealth = 3,
            maxPowerGauge = 100,
            moveSpeed = 1.6f,
            jumpPower = -5.5f,
            isUnlocked = false
        )
    )

    fun get(type: CharacterType): CharacterData = characters[type]
        ?: throw IllegalArgumentException("Unknown character type: $type")

    val allTypes: List<CharacterType> = characters.keys.toList()
    val unlockedTypes: List<CharacterType> get() = characters.values.filter { it.isUnlocked }.map { it.type }
}

/** Character - Represents a playable character instance during gameplay.
 *
 * Each character has unique weapon behavior, special ability, movement properties,
 * and a personal power gauge. All characters share the same team health.
 */
abstract class Character(
    val type: CharacterType,
    val data: CharacterData
) {
    var currentHealth: Int = data.maxHealth
    var currentPowerGauge: Int = data.maxPowerGauge
    var isActive: Boolean = false
    var chargeState: ChargeState = ChargeState.IDLE
    var chargeTimer: Float = 0f
    private val chargeDuration: Float = 1.0f // Seconds to fully charge

    /** The character's weapon type. */
    val weaponType: WeaponType get() = data.weaponType

    /** The character's special ability type. */
    val specialAbility: SpecialAbilityType get() = data.specialAbility

    /** Whether this character can break ice/stone blocks (only Blinky). */
    val canBreakIce: Boolean get() = weaponType == WeaponType.DOWNWARD_ARC

    /** Whether this character can climb walls (only Dead-Eye). */
    val canClimbWalls: Boolean get() = specialAbility == SpecialAbilityType.WALL_CLIMB

    /** Whether this character can fly with jetpack (only Blinky). */
    val canJetpack: Boolean get() = specialAbility == SpecialAbilityType.JETPACK_FLIGHT

    /** Whether this character has piercing shots (only Willy). */
    val hasPiercingShots: Boolean get() = weaponType == WeaponType.PIERCING

    /** Whether this character has guided orb ability (only Jenny). */
    val hasGuidedOrb: Boolean get() = specialAbility == SpecialAbilityType.GUIDED_ORB

    /** Whether this character can charge jump higher (only Bucky). */
    val canHighJump: Boolean get() = specialAbility == SpecialAbilityType.HIGH_JUMP

    /** Start charging the special ability. */
    fun startCharge() {
        if (chargeState == ChargeState.IDLE || chargeState == ChargeState.CHARGING) {
            chargeState = ChargeState.CHARGING
            chargeTimer = 0f
        }
    }

    /** Release the charged special ability. */
    fun releaseCharge(): Boolean {
        if (chargeState == ChargeState.CHARGED) {
            chargeState = ChargeState.ACTIVE
            chargeTimer = 0f
            return true
        }
        chargeState = ChargeState.IDLE
        return false
    }

    /** Cancel charging. */
    fun cancelCharge() {
        chargeState = ChargeState.IDLE
        chargeTimer = 0f
    }

    /** Update charge state each frame. */
    fun updateCharge(deltaTime: Float) {
        if (chargeState == ChargeState.CHARGING) {
            chargeTimer += deltaTime
            if (chargeTimer >= chargeDuration) {
                chargeState = ChargeState.CHARGED
                chargeTimer = 0f
            }
        } else if (chargeState == ChargeState.ACTIVE) {
            chargeTimer += deltaTime
            if (chargeTimer >= 0.5f) { // Active for 0.5 seconds
                chargeState = ChargeState.COOLDOWN
                chargeTimer = 0f
            }
        } else if (chargeState == ChargeState.COOLDOWN) {
            chargeTimer += deltaTime
            if (chargeTimer >= 1.0f) { // Cooldown for 1.0 seconds
                chargeState = ChargeState.IDLE
                chargeTimer = 0f
            }
        }
    }

    /** Consume power gauge for special ability. Returns true if enough power. */
    fun consumePower(amount: Int): Boolean {
        if (currentPowerGauge >= amount) {
            currentPowerGauge -= amount
            return true
        }
        return false
    }

    /** Recharge power gauge over time. */
    fun rechargePower(deltaTime: Float) {
        if (currentPowerGauge < data.maxPowerGauge) {
            currentPowerGauge = minOf(data.maxPowerGauge, currentPowerGauge + (10 * deltaTime).toInt())
        }
    }

    /** Take damage. Returns true if character is defeated. */
    fun takeDamage(amount: Int): Boolean {
        currentHealth = maxOf(0, currentHealth - amount)
        return currentHealth <= 0
    }

    /** Heal the character. */
    fun heal(amount: Int) {
        currentHealth = minOf(data.maxHealth, currentHealth + amount)
    }

    /** Get the charge percentage (0.0 to 1.0). */
    fun getChargePercent(): Float {
        return when (chargeState) {
            ChargeState.CHARGING, ChargeState.CHARGED -> minOf(1.0f, chargeTimer / chargeDuration)
            else -> 0.0f
        }
    }

    abstract fun getWeaponProjectileCount(): Int
    abstract fun getWeaponSpreadAngle(): Float
    abstract fun getSpecialDescription(): String
}

/** Concrete character implementations. */

class BuckyCharacter : Character(CharacterType.BUCKY, CharacterRegistry.get(CharacterType.BUCKY)) {
    override fun getWeaponProjectileCount(): Int = 1
    override fun getWeaponSpreadAngle(): Float = 0f
    override fun getSpecialDescription(): String = "Hold to charge high jump, release to leap"
}

class BlinkyCharacter : Character(CharacterType.BLINKY, CharacterRegistry.get(CharacterType.BLINKY)) {
    override fun getWeaponProjectileCount(): Int = 1
    override fun getWeaponSpreadAngle(): Float = 45f // Downward arc
    override fun getSpecialDescription(): String = "Hold to charge jetpack, release to fly"
}

class DeadEyeCharacter : Character(CharacterType.DEAD_EYE, CharacterRegistry.get(CharacterType.DEAD_EYE)) {
    override fun getWeaponProjectileCount(): Int = 3
    override fun getWeaponSpreadAngle(): Float = 30f // Three-direction spread
    override fun getSpecialDescription(): String = "Hold to charge wall climb, release to stick"
}

class JennyCharacter : Character(CharacterType.JENNY, CharacterRegistry.get(CharacterType.JENNY)) {
    override fun getWeaponProjectileCount(): Int = 1
    override fun getWeaponSpreadAngle(): Float = 0f
    override fun getSpecialDescription(): String = "Hold to charge guided orb, release to launch"
}

class WillyCharacter : Character(CharacterType.WILLY, CharacterRegistry.get(CharacterType.WILLY)) {
    override fun getWeaponProjectileCount(): Int = 1
    override fun getWeaponSpreadAngle(): Float = 0f
    override fun getSpecialDescription(): String = "Hold to charge power boost, release for piercing shots"
}

/** Factory for creating character instances. */
object CharacterFactory {
    fun create(type: CharacterType): Character {
        return when (type) {
            CharacterType.BUCKY -> BuckyCharacter()
            CharacterType.BLINKY -> BlinkyCharacter()
            CharacterType.DEAD_EYE -> DeadEyeCharacter()
            CharacterType.JENNY -> JennyCharacter()
            CharacterType.WILLY -> WillyCharacter()
        }
    }
}
