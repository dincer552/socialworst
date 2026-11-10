package com.dincer552.hattrickssh.game.planet

import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.character.CharacterType

/** GamePhase - Which part of the game we're in. */
enum class GamePhase {
    PLANET_SELECTION,   // Player chooses which planet to play
    PLAYING_PLANET,     // Playing within a planet level
    BOSS_FIGHT,         // Fighting a planet boss
    RESCUE_SEQUENCE,    // Showing crew rescue animation
    MOTHERSHIP,         // Playing in Toad Mothership second half
    GAME_COMPLETE       // Game completed
}

/** PlanetProgress - Tracks progress for a specific planet. */
data class PlanetProgress(
    val type: PlanetType,
    val currentAct: Int = 1,
    val actsCompleted: Int = 0,
    val bossDefeated: Boolean = false,
    val crewRescued: Boolean = false,
    val planetCompleted: Boolean = false
)

/** MothershipProgress - Tracks progress in the Toad Mothership second half. */
data class MothershipProgress(
    val currentStage: MothershipStage = MothershipStage.CELL,
    val stagesCompleted: Int = 0,
    val crewRescuedInMothership: Int = 0,
    val mothershipCompleted: Boolean = false
)

/** PlanetManager - Manages planet selection, progression, and Blinky dependency.
 *
 * Responsibilities:
 * - Track which planets are accessible (Blinky required for Blue Planet)
 * * Track act progression within each planet
 * * Handle boss fights and crew rescue
 * * Manage transition to Toad Mothership second half
 * * Save/load planet progress
 */
class PlanetManager(private val characterManager: com.dincer552.hattrickssh.game.character.CharacterManager) {

    // Current game phase
    private var gamePhase: GamePhase = GamePhase.PLANET_SELECTION

    // Currently selected planet
    private var selectedPlanet: PlanetType? = null

    // Current act within the selected planet
    private var currentAct: Int = 1

    // Progress for each planet
    private val planetProgress: MutableMap<PlanetType, PlanetProgress> = mutableMapOf()

    // Mothership progress (second half)
    private val mothershipProgress: MothershipProgress = MothershipProgress()

    // Initialize planet progress
    init {
        for (type in PlanetRegistry.allTypes) {
            planetProgress[type] = PlanetProgress(type = type)
        }
    }

    // ========================================================================
    // Game Phase
    // ========================================================================

    val getGamePhase: GamePhase
        get() = gamePhase

    fun setGamePhase(phase: GamePhase) {
        gamePhase = phase
    }

    val isInPlanetSelection: Boolean
        get() = gamePhase == GamePhase.PLANET_SELECTION

    val isPlayingPlanet: Boolean
        get() = gamePhase == GamePhase.PLAYING_PLANET

    val isInBossFight: Boolean
        get() = gamePhase == GamePhase.BOSS_FIGHT

    val isInRescueSequence: Boolean
        get() = gamePhase == GamePhase.RESCUE_SEQUENCE

    val isInMothership: Boolean
        get() = gamePhase == GamePhase.MOTHERSHIP

    val isGameComplete: Boolean
        get() = gamePhase == GamePhase.GAME_COMPLETE

    // ========================================================================
    // Planet Selection
    // ========================================================================

    /** Get all planets with their accessibility status. */
    fun getPlanetStatuses(): List<Pair<PlanetType, Boolean>> {
        return PlanetRegistry.allTypes.map { type to PlanetRegistry.isAccessible(type, characterManager) }
    }

    /** Check if a planet can be selected. */
    fun canSelectPlanet(type: PlanetType): Boolean {
        return PlanetRegistry.isAccessible(type, characterManager) && !isPlanetCompleted(type)
    }

    /** Select a planet to play. */
    fun selectPlanet(type: PlanetType): Boolean {
        if (!canSelectPlanet(type)) return false
        selectedPlanet = type
        currentAct = 1
        gamePhase = GamePhase.PLAYING_PLANET
        return true
    }

    val getSelectedPlanet: PlanetType?
        get() = selectedPlanet

    /** Get progress for a planet. */
    fun getPlanetProgress(type: PlanetType): PlanetProgress {
        return planetProgress[type] ?: PlanetProgress(type = type)
    }

    // ========================================================================
    // Act Progression
    // ========================================================================

    val getCurrentAct: Int
        get() = currentAct

    val getSelectedPlanetData: PlanetData?
        get() = selectedPlanet?.let { PlanetRegistry.get(it) }

    val getCurrentActData: ActData?
        get() = selectedPlanet?.let { ActRegistry.get(it, currentAct) }

    /** Complete the current act. */
    fun completeAct() {
        val planet = selectedPlanet ?: return
        val progress = planetProgress[planet]!!
        
        if (currentAct < progress.actsCompleted + 1) {
            // Don't go backwards
            return
        }
        
        progress.actsCompleted = maxOf(progress.actsCompleted, currentAct)
        
        if (currentAct < PlanetRegistry.get(planet).numActs) {
            currentAct++
        } else {
            // All acts complete, move to boss
            gamePhase = GamePhase.BOSS_FIGHT
        }
    }

    /** Complete the boss fight. */
    fun completeBoss() {
        val planet = selectedPlanet ?: return
        val progress = planetProgress[planet]!!
        progress.bossDefeated = true
        
        // Trigger crew rescue
        gamePhase = GamePhase.RESCUE_SEQUENCE
    }

    /** Complete the crew rescue. */
    fun completeRescue() {
        val planet = selectedPlanet ?: return
        val progress = planetProgress[planet]!!
        progress.crewRescued = true
        progress.planetCompleted = true
        
        // Rescue the crew member via character manager
        val planetData = PlanetRegistry.get(planet)
        val crewType = planetToCharacterType(planetData.crewMember)
        characterManager.rescue(crewType)
        
        // Check if all planets are complete
        if (allPlanetsCompleted()) {
            gamePhase = GamePhase.MOTHERSHIP
            // Start mothership from Cell stage
            mothershipProgress.currentStage = MothershipStage.CELL
        } else {
            gamePhase = GamePhase.PLANET_SELECTION
        }
    }

    /** Check if all planets are completed. */
    fun allPlanetsCompleted(): Boolean {
        return PlanetRegistry.allTypes.all { isPlanetCompleted(it) }
    }

    private fun isPlanetCompleted(type: PlanetType): Boolean {
        return planetProgress[type]?.planetCompleted ?: false
    }

    /** Get all completed planets. */
    val getCompletedPlanets: List<PlanetType>
        get() = PlanetRegistry.allTypes.filter { isPlanetCompleted(it) }

    /** Get remaining planets (not yet completed). */
    val getRemainingPlanets: List<PlanetType>
        get() = PlanetRegistry.allTypes.filter { !isPlanetCompleted(it) }

    // ========================================================================
    // Mothership Progression
    // ========================================================================

    val getMothershipProgress: MothershipProgress
        get() = mothershipProgress

    /** Complete the current mothership stage. */
    fun completeMothershipStage() {
        val current = mothershipProgress.currentStage
        val progress = mothershipProgress
        
        // Rescue crew member if this stage has one
        val stageData = MothershipRegistry.get(current)
        if (stageData.hasRescue && stageData.rescueTarget != null) {
            val crewType = planetToCharacterType(stageData.rescueTarget!!)
            characterManager.rescue(crewType)
            progress.crewRescuedInMothership++
        }
        
        progress.stagesCompleted = maxOf(progress.stagesCompleted, 
            MothershipRegistry.allStages.indexOf(current) + 1)
        
        // Move to next stage
        val allStages = MothershipRegistry.allStages
        val currentIndex = allStages.indexOf(current)
        
        if (currentIndex < allStages.size - 1) {
            mothershipProgress.currentStage = allStages[currentIndex + 1]
        } else {
            // All stages complete
            progress.mothershipCompleted = true
            gamePhase = GamePhase.GAME_COMPLETE
        }
    }

    val getCurrentMothershipStage: MothershipStage
        get() = mothershipProgress.currentStage

    val getCurrentMothershipData: MothershipData?
        get() = MothershipRegistry.get(mothershipProgress.currentStage)

    // ========================================================================
    // Helper: Map crew member name to CharacterType
    // ========================================================================

    private fun planetToCharacterType(crewMember: String): com.dincer552.hattrickssh.game.character.CharacterType {
        return when (crewMember) {
            "A.F.C. Blinky" -> com.dincer552.hattrickssh.game.character.CharacterType.BLINKY
            "Dead-Eye Duck" -> com.dincer552.hattrickssh.game.character.CharacterType.DEAD_EYE
            "Jenny" -> com.dincer552.hattrickssh.game.character.CharacterType.JENNY
            "Willy DuWitt" -> com.dincer552.hattrickssh.game.character.CharacterType.WILLY
            else -> com.dincer552.hattrickssh.game.character.CharacterType.BUCKY
        }
    }

    // ========================================================================
    // Save/Load Support
    // ========================================================================

    /** Get serializable state for saving. */
    fun saveState(): Map<String, Any> {
        return mapOf(
            "gamePhase" to gamePhase.name,
            "selectedPlanet" to selectedPlanet?.name,
            "currentAct" to currentAct,
            "planetProgress" to planetProgress.mapValues { it.value.actsCompleted to it.value.bossDefeated to it.value.crewRescued to it.value.planetCompleted },
            "mothershipProgress" to mapOf(
                "currentStage" to mothershipProgress.currentStage.name,
                "stagesCompleted" to mothershipProgress.stagesCompleted,
                "crewRescuedInMothership" to mothershipProgress.crewRescuedInMothership,
                "mothershipCompleted" to mothershipProgress.mothershipCompleted
            )
        )
    }

    /** Load state from saved data. */
    fun loadState(state: Map<String, Any>) {
        gamePhase = GamePhase.valueOf(state["gamePhase"] as String)
        selectedPlanet = state["selectedPlanet"]?.let { PlanetType.valueOf(it as String) }
        currentAct = state["currentAct"] as Int
        
        // Load planet progress
        val planetProgressMap = state["planetProgress"] as Map<*, *>
        for ((typeName, progressData) in planetProgressMap) {
            val type = PlanetType.valueOf(typeName as String)
            val progressArray = progressData as List<*>
            if (progressArray.size >= 4) {
                planetProgress[type] = PlanetProgress(
                    type = type,
                    currentAct = progressArray[0] as Int,
                    actsCompleted = progressArray[1] as Int,
                    bossDefeated = progressArray[2] as Boolean,
                    crewRescued = progressArray[3] as Boolean,
                    planetCompleted = progressArray.size > 4 && progressArray[4] as Boolean
                )
            }
        }
        
        // Load mothership progress
        val mothershipMap = state["mothershipProgress"] as Map<*, *>
        mothershipProgress.currentStage = MothershipStage.valueOf(mothershipMap["currentStage"] as String)
        mothershipProgress.stagesCompleted = mothershipMap["stagesCompleted"] as Int
        mothershipProgress.crewRescuedInMothership = mothershipMap["crewRescuedInMothership"] as Int
        mothershipProgress.mothershipCompleted = mothershipMap["mothershipCompleted"] as Boolean
    }
}
