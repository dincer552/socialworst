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
    var currentAct: Int = 1,
    var actsCompleted: Int = 0,
    var bossDefeated: Boolean = false,
    var crewRescued: Boolean = false,
    var planetCompleted: Boolean = false
)

/** MothershipProgress - Tracks progress in the Toad Mothership second half. */
data class MothershipProgress(
    var currentStage: MothershipStage = MothershipStage.CELL,
    var stagesCompleted: Int = 0,
    var crewRescuedInMothership: Int = 0,
    var mothershipCompleted: Boolean = false
)

/** PlanetManager - Manages planet selection, progression, and Blinky dependency. */
class PlanetManager(private val characterManager: CharacterManager) {

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
        return PlanetRegistry.allTypes.map { it to PlanetRegistry.isAccessible(it, characterManager) }
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
            return
        }

        progress.actsCompleted = maxOf(progress.actsCompleted, currentAct)

        if (currentAct < PlanetRegistry.get(planet).numActs) {
            currentAct++
        } else {
            gamePhase = GamePhase.BOSS_FIGHT
        }
    }

    /** Complete the boss fight. */
    fun completeBoss() {
        val planet = selectedPlanet ?: return
        val progress = planetProgress[planet]!!
        progress.bossDefeated = true
        gamePhase = GamePhase.RESCUE_SEQUENCE
    }

    /** Complete the crew rescue. */
    fun completeRescue() {
        val planet = selectedPlanet ?: return
        val progress = planetProgress[planet]!!
        progress.crewRescued = true
        progress.planetCompleted = true

        val planetData = PlanetRegistry.get(planet)
        val crewType = planetToCharacterType(planetData.crewMember)
        characterManager.rescue(crewType)

        if (allPlanetsCompleted()) {
            gamePhase = GamePhase.MOTHERSHIP
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

        val stageData = MothershipRegistry.get(current)
        if (stageData.hasRescue && stageData.rescueTarget != null) {
            val crewType = planetToCharacterType(stageData.rescueTarget)
            characterManager.rescue(crewType)
            progress.crewRescuedInMothership++
        }

        progress.stagesCompleted = maxOf(
            progress.stagesCompleted,
            MothershipRegistry.allStages.indexOf(current) + 1
        )

        val allStages = MothershipRegistry.allStages
        val currentIndex = allStages.indexOf(current)

        if (currentIndex < allStages.size - 1) {
            mothershipProgress.currentStage = allStages[currentIndex + 1]
        } else {
            progress.mothershipCompleted = true
            gamePhase = GamePhase.GAME_COMPLETE
        }
    }

    val getCurrentMothershipStage: MothershipStage
        get() = mothershipProgress.currentStage

    val getCurrentMothershipData: MothershipData?
        get() = MothershipRegistry.get(mothershipProgress.currentStage)

    private fun planetToCharacterType(crewMember: String): CharacterType {
        return when (crewMember) {
            "A.F.C. Blinky" -> CharacterType.BLINKY
            "Dead-Eye Duck" -> CharacterType.DEAD_EYE
            "Jenny" -> CharacterType.JENNY
            "Willy DuWitt" -> CharacterType.WILLY
            else -> CharacterType.BUCKY
        }
    }

    fun saveState(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["gamePhase"] = gamePhase.name
        selectedPlanet?.let { result["selectedPlanet"] = it.name }
        result["currentAct"] = currentAct
        return result
    }

    fun loadState(state: Map<String, Any?>) {
        (state["gamePhase"] as? String)?.let {
            try { gamePhase = GamePhase.valueOf(it) } catch (_: Exception) {}
        }
        (state["selectedPlanet"] as? String)?.let {
            try { selectedPlanet = PlanetType.valueOf(it) } catch (_: Exception) {}
        }
        (state["currentAct"] as? Int)?.let { currentAct = it }
    }
}
