package com.dincer552.hattrickssh.game

/** Touch input actions for mobile gameplay.
 * 
 * Actions map to touch events and are independent of rendering logic.
 * Includes movement, actions, character switching, and charge mechanics.
 */
enum class InputAction {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    JUMP,
    FIRE,
    SPECIAL,
    CHARGE,        // Hold to charge special ability
    CHAR_RELEASE,  // Release to activate charged ability
    CHAR_SWITCH,   // Switch to next character
    CHAR_SWITCH_PREV, // Switch to previous character
    DROP_DOWN,     // Down + Jump: drop through platform
    FIRE_UP        // Up + Fire: fire upward
}

/** Touch input processor.
 * 
 * Maps raw touch events to InputAction enums.
 * Keeps input handling separate from rendering logic.
 * Supports charged abilities with hold-and-release semantics.
 */
class GameInput {

    // Input state tracking
    private val pressed = mutableSetOf<InputAction>()
    private val justPressed = mutableSetOf<InputAction>()
    private val justReleased = mutableSetOf<InputAction>()

    // Public query functions
    fun isPressed(action: InputAction): Boolean = pressed.contains(action)
    fun wasJustPressed(action: InputAction): Boolean = justPressed.contains(action)
    fun wasJustReleased(action: InputAction): Boolean = justReleased.contains(action)

    // Register input events (to be called from Activity/View)
    fun press(action: InputAction) {
        pressed.add(action)
        justPressed.add(action)
    }

    fun release(action: InputAction) {
        pressed.remove(action)
        justReleased.add(action)
    }

    // Clear just-pressed/released for next frame
    fun clearFrame() {
        justPressed.clear()
        justReleased.clear()
    }

    /** Check if charge is currently held down. */
    fun isCharging(): Boolean = isPressed(InputAction.CHARGE)

    /** Check if charge was just released (activate special ability). */
    fun wasChargeReleased(): Boolean = wasJustReleased(InputAction.CHARGE)

    /** Check if character switch was just triggered. */
    fun wasCharacterSwitch(): Boolean = wasJustPressed(InputAction.CHAR_SWITCH)

    /** Check if character switch previous was just triggered. */
    fun wasCharacterSwitchPrev(): Boolean = wasJustPressed(InputAction.CHAR_SWITCH_PREV)

    /** Check if drop through platform (down + jump). */
    fun isDropDown(): Boolean = isPressed(InputAction.DOWN) && isPressed(InputAction.JUMP)

    /** Check if fire upward (up + fire). */
    fun isFireUp(): Boolean = isPressed(InputAction.UP) && isPressed(InputAction.FIRE)
}
