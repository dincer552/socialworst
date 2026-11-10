package com.dincer552.hattrickssh.game

import android.content.Context
import android.content.SharedPreferences

class SaveManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bucky_ohare_save", Context.MODE_PRIVATE)

    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isScanlinesEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCANLINES, true)
        set(value) = prefs.edit().putBoolean(KEY_SCANLINES, value).apply()

    fun isPlanetCompleted(planetIndex: Int): Boolean {
        return prefs.getBoolean(KEY_PLANET_PREFIX + planetIndex, false)
    }

    fun setPlanetCompleted(planetIndex: Int, completed: Boolean) {
        prefs.edit().putBoolean(KEY_PLANET_PREFIX + planetIndex, completed).apply()
    }

    fun isCharacterUnlocked(charName: String): Boolean {
        if (charName == "BUCKY") return true // Bucky is always unlocked
        return prefs.getBoolean(KEY_CHAR_PREFIX + charName, false)
    }

    fun unlockCharacter(charName: String) {
        prefs.edit().putBoolean(KEY_CHAR_PREFIX + charName, true).apply()
    }

    fun resetProgress() {
        prefs.edit()
            .remove(KEY_PLANET_PREFIX + "0")
            .remove(KEY_PLANET_PREFIX + "1")
            .remove(KEY_PLANET_PREFIX + "2")
            .remove(KEY_PLANET_PREFIX + "3")
            .remove(KEY_PLANET_PREFIX + "4")
            .remove(KEY_CHAR_PREFIX + "JENNY")
            .remove(KEY_CHAR_PREFIX + "WILLY")
            .remove(KEY_CHAR_PREFIX + "DEAD_EYE")
            .remove(KEY_CHAR_PREFIX + "BLINKY")
            .apply()
    }

    companion object {
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SCANLINES = "scanlines"
        private const val KEY_PLANET_PREFIX = "planet_cleared_"
        private const val KEY_CHAR_PREFIX = "char_unlocked_"
    }
}
