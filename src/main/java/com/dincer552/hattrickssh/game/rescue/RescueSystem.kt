package com.dincer552.hattrickssh.game.rescue

import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.character.CharacterType
import com.dincer552.hattrickssh.game.planet.PlanetType

/** RescueState - State of the rescue sequence. */
enum class RescueState {
    IDLE,           // Not in a rescue sequence
    RESCUING,       // Currently showing rescue animation
    INTRODUCTION,   // Showing character introduction
    COMPLETE        // Rescue complete, return to planet selection
}

/** CrewMember - Data about a rescued crew member. */
data class CrewMember(
    val type: CharacterType,
    val name: String,
    val planetType: PlanetType,
    val description: String,
    val isRescued: Boolean = false
)

/** CrewRegistry - Static data for all crew members. */
object CrewRegistry {
    private val crew: Map<CharacterType, CrewMember> = mapOf(
        CharacterType.BUCKY to CrewMember(
            type = CharacterType.BUCKY,
            name = "Bucky O'Hare",
            planetType = PlanetType.GREEN,  // Starting character, not rescued
            description = "The fearless leader of the team.",
            isRescued = true
        ),
        CharacterType.BLINKY to CrewMember(
            type = CharacterType.BLINKY,
            name = "A.F.C. Blinky",
            planetType = PlanetType.GREEN,
            description = "Small android with powerful weapons that can break ice blocks.",
            isRescued = false
        ),
        CharacterType.DEAD_EYE to CrewMember(
            type = CharacterType.DEAD_EYE,
            name = "Dead-Eye Duck",
            planetType = PlanetType.RED,
            description = "Expert marksman with three-direction spread shot.",
            isRescued = false
        ),
        CharacterType.JENNY to CrewMember(
            type = CharacterType.JENNY,
            name = "Jenny",
            planetType = PlanetType.BLUE,
            description = "Mysterious feline with psychic abilities and guided orb.",
            isRescued = false
        ),
        CharacterType.WILLY to CrewMember(
            type = CharacterType.WILLY,
            name = "Willy DuWitt",
            planetType = PlanetType.YELLOW,
            description = "Human genius with powerful piercing laser.",
            isRescued = false
        )
    )

    fun get(type: CharacterType): CrewMember = crew[type]
        ?: throw IllegalArgumentException("Unknown crew member: $type")

    val allCrew: List<CrewMember> = crew.values.toList()

    fun getByPlanet(planetType: PlanetType): CrewMember =
        crew.values.first { it.planetType == planetType }
}

/** RescueSystem - Manages crew rescue sequences and unlock progression.
 *
 * Responsibilities:
 * - Track which crew members have been rescued
 * - Manage rescue sequence animations
 * * Handle character unlock after rescue
 * * Track planet dependencies (Blue Planet requires Blinky)
 */
class RescueSystem(private val characterManager: com.dincer552.hattrickssh.game.character.CharacterManager) {

    private var rescueState: RescueState = RescueState.IDLE
    private var currentRescue: CharacterType? = null
    private var rescueTimer: Float = 0f
    private val rescueSequenceDuration: Float = 3.0f  // Seconds for rescue animation
    private val introductionDuration: Float = 4.0f    // Seconds for introduction

    // ========================================================================
    // Rescue State
    // ========================================================================

    val getRescueState: RescueState get() = rescueState
    val getCurrentRescue: CharacterType? get() = currentRescue

    /** Check if a rescue sequence is in progress. */
    val isRescuing: Boolean get() = rescueState == RescueState.RESCUING

    val isInIntroduction: Boolean get() = rescueState == RescueState.INTRODUCTION

    /** Check if all crew members have been rescued. */
    val allCrewRescued: Boolean
        get() = CharacterType.values().all { characterManager.isUnlocked(it) }

    // ========================================================================
    // Rescue Sequence
    // ========================================================================

    /** Start a rescue sequence for a crew member. */
    fun startRescue(type: CharacterType) {
        if (characterManager.isUnlocked(type)) return // Already rescued

        currentRescue = type
        rescueState = RescueState.RESCUING
        rescueTimer = 0f
    }

    /** Update the rescue sequence each frame. */
    fun update(deltaTime: Float) {
        when (rescueState) {
            RescueState.RESCUING -> {
                rescueTimer += deltaTime
                if (rescueTimer >= rescueSequenceDuration) {
                    rescueState = RescueState.INTRODUCTION
                    rescueTimer = 0f
                }
            }
            RescueState.INTRODUCTION -> {
                rescueTimer += deltaTime
                if (rescueTimer >= introductionDuration) {
                    completeRescue()
                }
            }
            RescueState.IDLE, RescueState.COMPLETE -> {
                // Do nothing
            }
        }
    }

    /** Complete the rescue sequence. */
    private fun completeRescue() {
        val type = currentRescue ?: return

        // Rescue the character via character manager
        characterManager.rescue(type)

        // Update crew registry state
        // Note: CrewRegistry data is immutable, we track via CharacterManager

        rescueState = RescueState.COMPLETE
        currentRescue = null
        rescueTimer = 0f
    }

    /** Get the current crew member being rescued. */
    val getCurrentCrewMember: CrewMember?
        get() = currentRescue?.let { CrewRegistry.get(it) }

    /** Get rescue progress - how many crew members rescued. */
    val getRescuedCount: Int
        get() = CharacterType.values().count { characterManager.isUnlocked(it) }

    val getTotalCrewCount: Int
        get() = CharacterType.values().size

    /** Get rescue progress percentage (0.0 to 1.0). */
    val getRescueProgress: Float
        get() = getRescuedCount.toFloat() / getTotalCrewCount.toFloat()

    // ========================================================================
    // Rescue Dependencies
    // ========================================================================

    /** Check if Blue Planet is accessible (requires Blinky rescued). */
    fun canAccessBluePlanet(): Boolean {
        return characterManager.isUnlocked(CharacterType.BLINKY)
    }

    /** Get a message describing why Blue Planet is locked. */
    val bluePlanetLockMessage: String
        get() = if (canAccessBluePlanet()) "" else "Requires Blinky to break ice blocks"

    /** Get the next crew member that can be rescued (based on dependencies). */
    val getNextRescuableCrew: CharacterType?
        get() {
            // Check each planet in order
            val order = listOf(
                CharacterType.BLINKY,   // Green Planet - always accessible
                CharacterType.DEAD_EYE, // Red Planet - always accessible
                CharacterType.JENNY,    // Blue Planet - requires Blinky
                CharacterType.WILLY     // Yellow Planet - always accessible
            )
            return order.firstOrNull { !characterManager.isUnlocked(it) &&
                (it != CharacterType.JENNY || characterManager.isUnlocked(CharacterType.BLINKY)) }
        }
}
