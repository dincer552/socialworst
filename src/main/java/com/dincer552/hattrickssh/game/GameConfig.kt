package com.dincer552.hattrickssh.game

/** Global game configuration constants.
 * Holds screen sizes, FPS, colors, and other settings
 * that may need to be adjusted for different devices or future tweaks.
 */
object GameConfig {
    const val TARGET_FPS = 60
    const val MIN_FRAME_INTERVAL_NS = 1_000_000_000 / TARGET_FPS

    // Viewport dimensions (game world size in pixels)
    const val VIEWPORT_WIDTH = 256
    const val VIEWPORT_HEIGHT = 224

    // UI dimensions
    const val UI_HIGHCONTRAST = true
    const val COLOR_BG = 0xFF0A0A20
    const val COLOR_PANEL = 0xFF121B24
    const val COLOR_ACCENT = 0xFF38D98A
    const val COLOR_MUTED = 0xFF91A1B2
    const val COLOR_RED = 0xFFFF0000
    const val COLOR_GREEN = 0xFF00FF00
    const val COLOR_ORANGE = 0xFFFFAA00

    // Touch input constants
    const val INPUT_DEAD_ZONE = 0.1f

    // Game state transition durations (ms)
    const val TRANSITION_FADE_MS = 200
    const val TRANSITION_SLIDE_MS = 150

    // Gameplay constants
    const val PLAYER_START_LIVES = 3
    const val POINTS_PER_STAR = 100
    const val POINTS_PER_ENEMY_DESTROYED = 50
}
