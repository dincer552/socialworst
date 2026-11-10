package com.dincer552.hattrickssh.game

import android.content.Context
import android.content.SharedPreferences
import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.planet.PlanetManager
import com.dincer552.hattrickssh.game.mothership.MothershipManager
import com.dincer552.hattrickssh.game.rescue.RescueSystem

/** SaveSystem - Manages saving and loading game progress. */
class SaveSystem(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Save the complete game state. */
    fun saveGame(
        characterManager: CharacterManager,
        planetManager: PlanetManager,
        mothershipManager: MothershipManager,
        rescueSystem: RescueSystem,
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
        val planetProgressMap = mutableMapOf<String, String>()
        for (type in com.dincer552.hattrickssh.game.planet.PlanetType.values()) {
            val progress = planetManager.getPlanetProgress(type)
            planetProgressMap[type.name] = "${progress.actsCompleted}:${progress.bossDefeated}:${progress.crewRescued}:${progress.planetCompleted}"
        }
        editor.putString(KEY_PLANET_PROGRESS, planetProgressMap.toString())

        // Save mothership progress
        val mProgress = planetManager.getMothershipProgress
        editor.putString(KEY_MOTHERSHIP_PROGRESS, "${mProgress.stagesCompleted}:${mProgress.mothershipCompleted}")

        editor.apply()
    }

    /** Load the complete game state. */
    fun loadGame(
        characterManager: CharacterManager,
        planetManager: PlanetManager,
        mothershipManager: MothershipManager,
        rescueSystem: RescueSystem
    ): Int {
        // Load game phase
        val phaseName = prefs.getString(KEY_GAME_PHASE, null)
        if (phaseName != null) {
            try {
                planetManager.setGamePhase(com.dincer552.hattrickssh.game.planet.GamePhase.valueOf(phaseName))
            } catch (_: Exception) {}
        }

        // Load team health
        val teamHealth = prefs.getInt(KEY_TEAM_HEALTH, 3)
        characterManager.setTeamHealth(teamHealth)

        // Load character unlocks
        val unlocksStr = prefs.getString(KEY_CHARACTER_UNLOCKS, "") ?: ""
        if (unlocksStr.isNotEmpty()) {
            val unlockTypes = unlocksStr.split(",").filter { it.isNotEmpty() }
            for (typeName in unlockTypes) {
                try {
                    val type = com.dincer552.hattrickssh.game.character.CharacterType.valueOf(typeName)
                    characterManager.rescue(type)
                } catch (_: Exception) {}
            }
        }

        return prefs.getInt(KEY_SCORE, 0)
    }

    fun clearSave() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "bucky_ohare_game_save"
        private const val KEY_GAME_PHASE = "game_phase"
        private const val KEY_TEAM_HEALTH = "team_health"
        private const val KEY_PLANET_PROGRESS = "planet_progress"
        private const val KEY_MOTHERSHIP_PROGRESS = "mothership_progress"
        private const val KEY_CHARACTER_UNLOCKS = "character_unlocks"
        private const val KEY_SCORE = "score"
    }
}
