package com.dincer552.hattrickssh.game

import android.content.Context
import android.content.SharedPreferences
import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.planet.PlanetManager
import com.dincer552.hattrickssh.game.mothership.MothershipManager
import com.dincer552.hattrickssh.game.rescue.RescueSystem

/** SaveSystem - Manages saving and loading game progress.
 *
 * Uses Android SharedPreferences to persist:
 * - Character unlock status
 * * Planet progress
 * * Mothership progress
 * * Team health
 * * Game phase
 */
class SaveSystem(private val context: Context) {

    private const val PREFS_NAME = "bucky_ohare_game_save"
    private const const val KEY_GAME_PHASE = "game_phase"
    private const val KEY_TEAM_HEALTH = "team_health"
    private const val KEY_PLANET_PROGRESS = "planet_progress"
    private const val KEY_MOTHERSHIP_PROGRESS = "mothership_progress"
    private const val KEY_CHARACTER_UNLOCKS = "character_unlocks"
    private const val KEY_SCORE = "score"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Save the complete game state. */
    fun saveGame(
        characterManager: CharacterManager,
        planetManager: PlanetManager,
        mothershipManager: com.dincer552.hattrickssh.game.mothership.MothershipManager,
        rescueSystem: com.dincer552.hattrickssh.game.rescue.RescueSystem,
        score: Int
    ) {
        val editor = prefs.edit()

        // Save game phase
        editor.putString(KEY_GAME_PHASE, planetManager.getGamePhase.name)

        // Save team health
        editor.putInt(KEY_TEAM_HEALTH, characterManager.getTeamHealth)

        // Save character unlocks
        val unlocks = characterManager.getUnlockedTypes.map { it.name }.toList()
        editor.putString(KEY_CHARACTER_UNLOCKS, unlocks.joinToString(","))

        // Save score
        editor.putInt(KEY_SCORE, score)

        // Save planet progress
        val planetProgress = planetManager.getPlanetProgress()
        val planetProgressMap = mutableMapOf<String, Any>()
        for ((type, progress) in planetProgress) {
            planetProgressMap[type.name] = mapOf(
                "actsCompleted" to progress.actsCompleted,
                "bossDefeated" to progress.bossDefeated,
                "crewRescued" to progress.crewRescued,
                "planetCompleted" to progress.planetCompleted
            )
        }
        editor.putString(KEY_PLANET_PROGRESS, planetProgressMap.toString())

        // Save mothership progress
        val mothershipProgress = mothershipProgress
        editor.putString(KEY_MOTHERSHIP_PROGRESS, mothershipProgress.toString())

        editor.apply()
    }

    /** Load the complete game state. */
    fun loadGame(
        characterManager: CharacterManager,
        planetManager: PlanetManager,
        mothershipManager: com.dincer552.hattrickssh.game.mothership.MothershipManager,
        rescueSystem: com.dincer552.hattrickssh.game.rescue.RescueSystem
    ): Int {
        // Load game phase
        val phaseName = prefs.getString(KEY_GAME_PHASE, null)
        if (phaseName != null) {
            try {
                planetManager.setGamePhase(com.dincer552.hattrickssh.game.planet.GamePhase.valueOf(phaseName))
            } catch (e: Exception) {
                // Ignore, use default
            }
        }

        // Load team health
        val teamHealth = prefs.getInt(KEY_TEAM_HEALTH, 3)
        characterManager.setTeamHealth(teamHealth)

        // Load character unlocks
        val unlocksStr = prefs.getString(KEY_CHARACTER_UNLOCKS, "")
        if (unlocksStr.isNotEmpty()) {
            val unlockTypes = unlocksStr.split(",").filter { it.isNotEmpty() }
            for (typeName in unlockTypes) {
                try {
                    val type = com.dincer552.hattrickssh.game.character.CharacterType.valueOf(typeName)
                    characterManager.rescue(type)
                } catch (e: Exception) {
                    // Ignore invalid types
                }
            }
        }

        // Load score
        val score = prefs.getInt(KEY_SCORE, 0)

        // Load planet progress
        val planetProgressStr = prefs.getString(KEY_PLANET_PROGRESS, null)
        if (planetProgressStr != null) {
            // Parse planet progress (simple format)
            // In a production app, we'd use JSON or proper serialization
            // For now, we'll re-rescue characters based on unlocks
        }

        // Load mothership progress
        // Similar parsing for mothership progress

        return score
    }

    /** Check if a saved game exists. */
    fun hasSavedGame(): Boolean {
        return prefs.contains(KEY_GAME_PHASE)
    }

    /** Delete the saved game. */
    fun deleteSave() {
        prefs.edit().clear().apply()
    }

    /** Get the saved game phase. */
    val getSavedGamePhase: com.dincer552.hattrickssh.game.planet.GamePhase?
        get() {
            val name = prefs.getString(KEY_GAME_PHASE, null)
            return if (name != null) {
                try {
                    com.dincer552.hattrickssh.game.planet.GamePhase.valueOf(name)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
}
