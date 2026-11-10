package com.dincer552.hattrickssh.game

/** Controlled game loop targeting 60 FPS.
 * 
 * Responsibilities:
 * - Fixed timestep game updates separate from rendering
 * - Frame pacing to avoid busy-waiting/unstable frame rates
 * - Pause/resume support via flag
 * - Single active loop instance
 * - Suitable for platform game architecture
 * 
 * Design:
 * - Uses system nanoTime for accurate timing
 * - Updates at fixed interval; renders as fast as possible
 * - Loop control flag managed by Activity lifecycle
 */
class GameEngine(
    private var shouldRun: Boolean = true
) {
    private var lastNanoTime = System.nanoTime()
    private var lastUpdateNanoTime = lastNanoTime
    private var updateAccumulator = 0L
    private var deltaTime: Float = 0f

    // Fixed timestep parameters
    private val nsPerUpdate = 1_000_000_000L / GameConfig.TARGET_FPS // 16,666,667 ns ≈ 60 FPS
    private val updatesPerFrameLimit = 5 // maximum accumulated updates to catch up

    var isPaused: Boolean = false

    /** Called from the hosting View's render loop.
     * Should be called before drawing each frame.
     * Returns true if an update was performed this frame.
     */
    fun update(): Boolean {
        if (isPaused) return false

        val now = System.nanoTime()
        val elapsed = now - lastNanoTime
        lastNanoTime = now

        // Convert nanoseconds to seconds
        deltaTime = elapsed / 1_000_000_000f

        updateAccumulator += elapsed
        lastUpdateNanoTime = now

        // Cap accumulated updates to prevent spiral of death
        if (updateAccumulator > nsPerUpdate * updatesPerFrameLimit) {
            updateAccumulator = nsPerUpdate * updatesPerFrameLimit
        }

        var didUpdate = false

        // Process as many fixed updates as needed
        while (updateAccumulator >= nsPerUpdate && shouldRun) {
            updateAccumulator -= nsPerUpdate
            didUpdate = true
            onUpdate()
        }

        return didUpdate
    }

    /** Called each fixed timestep.
     * Override or extend in subclasses for actual game logic.
     */
    protected fun onUpdate() {
        // Fixed update step - game logic goes here
    }

    /** Request the game loop to stop.
     * Called from Activity onStop/onDestroy.
     */
    fun stop() {
        shouldRun = false
    }

    /** Pause the game loop.
     * Called from Activity onPause.
     */
    fun pause() {
        isPaused = true
    }

    /** Resume the game loop.
     * Called from Activity onResume.
     */
    fun resume() {
        isPaused = false
    }

    /** Get the delta time in seconds since the last update. */
    fun getDeltaTime(): Float = deltaTime
}
