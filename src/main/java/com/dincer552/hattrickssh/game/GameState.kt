package com.dincer552.hattrickssh.game

/** Basic game state enumeration.
 * Defines the possible states the game can be in.
 * State management is kept simple and centralized.
 */
enum class GameState {
    MENU,
    PLAYING,
    PAUSED,
    GAME_OVER,
    LEVEL_COMPLETE
}

/** Simple state holder.
 * Holds the current game state and provides transition logic.
 * All UI and engine code should query this state rather than managing it independently.
 */
class GameStateManager {
    private var currentState = GameState.MENU

    val current: GameState get() = currentState

    fun setState(newState: GameState) {
        currentState = newState
    }

    fun transitionTo(newState: GameState) {
        currentState = newState
    }

    fun isIn(state: GameState): Boolean = currentState == state
    fun isNot(state: GameState): Boolean = currentState != state
}