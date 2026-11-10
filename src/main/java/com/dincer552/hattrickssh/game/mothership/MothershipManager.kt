package com.dincer552.hattrickssh.game.mothership

import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.character.CharacterType
import com.dincer552.hattrickssh.game.planet.MothershipRegistry
import com.dincer552.hattrickssh.game.planet.MothershipStage
import com.dincer552.hattrickssh.game.planet.MothershipData

/** MothershipPhase - Which part of the mothership escape. */
enum class MothershipPhase {
    CELL_ESCAPE,        // Bucky and Blinky escape from cell
    SALVAGE_CHUTE,      // Progress through salvage system
    MAGMA_TANKER,       // Navigate magma tanker core
    FINAL_ESCAPE,       // Spacecraft escape through collapsing mothership
    COMPLETE            // Mothership escape complete
}

/** MothershipManager - Manages the Toad Mothership second half.
 *
 * This is the mandatory second half of the game after all four crew members
 * are rescued. Bucky and Blinky escape from their cell, then rescue the other
 * crew members while progressing through the mothership.
 */
class MothershipManager(private val characterManager: CharacterManager) {

    private var phase: MothershipPhase = MothershipPhase.CELL_ESCAPE
    private var currentStageIndex: Int = 0
    private var stageCompleted: Boolean = false
    private var crewRescuedInMothership: Int = 0

    // Stages in order
    private val stages = MothershipRegistry.allStages

    // ========================================================================
    // Phase Management
    // ========================================================================

    val getPhase: MothershipPhase get() = phase

    fun setPhase(phase: MothershipPhase) {
        this.phase = phase
    }

    val isInMothership: Boolean
        get() = phase != MothershipPhase.COMPLETE

    val isMothershipComplete: Boolean
        get() = phase == MothershipPhase.COMPLETE

    // ========================================================================
    // Stage Management
    // ========================================================================

    /** Get the current mothership stage data. */
    val getCurrentStage: MothershipData
        get() = MothershipRegistry.get(currentStage())

    private fun currentStage(): MothershipStage {
        return when (phase) {
            MothershipPhase.CELL_ESCAPE -> MothershipStage.CELL
            MothershipPhase.SALVAGE_CHUTE -> MothershipStage.SALVAGE_CHUTE
            MothershipPhase.MAGMA_TANKER -> MothershipStage.MAGMA_TANKER
            MothershipPhase.FINAL_ESCAPE -> MothershipStage.ESCAPE
            MothershipPhase.COMPLETE -> MothershipStage.ESCAPE
        }
    }

    /** Check if the current stage is completed. */
    val isStageCompleted: Boolean get() = stageCompleted

    /** Complete the current mothership stage. */
    fun completeStage() {
        stageCompleted = true

        // Rescue crew member if this stage has one
        val stageData = MothershipRegistry.get(currentStage())
        if (stageData.hasRescue && stageData.rescueTarget != null) {
            val crewType = crewMemberToCharacterType(stageData.rescueTarget!!)
            characterManager.rescue(crewType)
            crewRescuedInMothership++
        }

        // Move to next stage
        val allStages = MothershipRegistry.allStages
        val currentIndex = allStages.indexOf(currentStage())

        if (currentIndex < allStages.size - 1) {
            // Move to next stage
            when (phase) {
                MothershipPhase.CELL_ESCAPE -> phase = MothershipPhase.SALVAGE_CHUTE
                MothershipPhase.SALVAGE_CHUTE -> phase = MothershipPhase.MAGMA_TANKER
                MothershipPhase.MAGMA_TANKER -> phase = MothershipPhase.FINAL_ESCAPE
                MothershipPhase.FINAL_ESCAPE -> phase = MothershipPhase.COMPLETE
                MothershipPhase.COMPLETE -> { /* already complete */ }
            }
            stageCompleted = false
        } else {
            // All stages complete
            phase = MothershipPhase.COMPLETE
        }
    }

    /** Get crew members rescued in the mothership. */
    val getCrewRescuedCount: Int get() = crewRescuedInMothership

    /** Get the current stage name. */
    val getCurrentStageName: String
        get() = getCurrentStage.name

    /** Get the current stage hazards. */
    val getCurrentStageHazards: List<com.dincer552.hattrickssh.game.planet.HazardType>
        get() = getCurrentStage.hazards

    /** Check if the current stage has a boss. */
    val currentStageHasBoss: Boolean
        get() = getCurrentStage.hasBoss

    /** Check if the current stage has a rescue. */
    val currentStageHasRescue: Boolean
        get() = getCurrentStage.hasRescue

    /** Get the rescue target for the current stage. */
    val currentStageRescueTarget: String?
        get() = getCurrentStage.rescueTarget

    // ========================================================================
    // Helper: Map rescue target name to CharacterType
    // ========================================================================

    private fun crewMemberToCharacterType(crewMember: String): CharacterType {
        return when (crewMember) {
            "Dead-Eye Duck" -> CharacterType.DEAD_EYE
            "Jenny" -> CharacterType.JENNY
            "Willy DuWitt" -> CharacterType.WILLY
            "A.F.C. Blinky" -> CharacterType.BLINKY
            else -> CharacterType.BUCKY
        }
    }

    /** Check if Bucky and Blinky are both available (required for Cell escape). */
    val canStartMothership: Boolean
        get() = characterManager.isUnlocked(CharacterType.BUCKY) &&
            characterManager.isUnlocked(CharacterType.BLINKY)
}
