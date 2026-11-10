package com.dincer552.hattrickssh.game

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Window
import android.view.WindowManager

/** GameActivity - Dedicated Android Activity for the Bucky O'Hare game.
 * 
 * Requirements satisfied:
 * - Landscape orientation only
 * - Does not change orientation of existing SSH screens
 * - Android back navigation to exit
 * - Adapts to different screen sizes
 * - Prevents system bars from covering game area (immersive mode)
 * - Handles Activity lifecycle correctly
 * - Pauses game loop when paused/stops
 * - Resumes safely when active again
 */
class GameActivity : Activity() {

    private lateinit var gameView: GameView
    private var isGameRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup: fullscreen, landscape, no title
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Lock to landscape only for this activity
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        // Immersive mode - hide system bars
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Create and set the game view
        gameView = GameView(this)
        setContentView(gameView)

        // Initialize game state
        gameView.getGameState.setState(GameState.PLAYING)
        isGameRunning = true
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        gameView.getGameEngine.resume()
    }

    override fun onResume() {
        super.onResume()
        gameView.getGameEngine.resume()
        isGameRunning = true
    }

    override fun onPause() {
        super.onPause()
        gameView.getGameEngine.pause()
        isGameRunning = false
    }

    override fun onStop() {
        super.onStop()
        gameView.getGameEngine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.getGameEngine.stop()
        isGameRunning = false
    }

    /** Handle back button to exit game. */
    override fun onBackPressed() {
        if (gameView.getGameState.isIn(GameState.PLAYING)) {
            gameView.getGameState.setState(GameState.PAUSED)
        } else {
            finish()
        }
    }

    /** Get the game view for external access. */
    fun getGameView(): GameView = gameView

    /** Check if the game is currently running. */
    fun isRunning(): Boolean = isGameRunning
}
