package com.dincer552.hattrickssh.game

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.dincer552.hattrickssh.game.character.CharacterManager
import com.dincer552.hattrickssh.game.character.SpecialAbilityManager
import com.dincer552.hattrickssh.game.planet.PlanetManager
import com.dincer552.hattrickssh.game.rescue.RescueSystem
import com.dincer552.hattrickssh.game.mothership.MothershipManager

/** GameView - Custom SurfaceView for the Bucky O'Hare game.
 * 
 * Responsibilities:
 * - Hosts the game loop and rendering
 * - Provides SurfaceHolder for Canvas drawing
 * - Coordinates GameEngine, GameRenderer, GameWorld, CharacterManager, PlanetManager
 * - Handles touch input forwarding to GameInput
 * - Manages surface lifecycle tied to Activity
 * - Loads sprite sheets and initializes game objects
 * 
 * Design notes:
 * - Uses SurfaceView for optimal 60 FPS drawing
 * - SurfaceHolder.Callback handles surface creation/change/destruction
 * - Game loop runs in a dedicated render thread
 * - All game state managed by GameWorld, CharacterManager, PlanetManager
 */
class GameView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var surfaceHolder: SurfaceHolder? = null
    private val gameEngine = GameEngine()
    private val gameRenderer = GameRenderer(context)
    private val gameInput = GameInput()
    private val gameState = GameStateManager()
    private val characterManager = CharacterManager()
    private val specialAbilityManager = SpecialAbilityManager()
    private val planetManager = PlanetManager(characterManager)
    private val rescueSystem = RescueSystem(characterManager)
    private val mothershipManager = com.dincer552.hattrickssh.game.mothership.MothershipManager(characterManager)
    private var gameWorld: GameWorld? = null

    // Game world dimensions
    private var viewportWidth = GameConfig.VIEWPORT_WIDTH
    private var viewportHeight = GameConfig.VIEWPORT_HEIGHT

    // Touch control regions
    private var dpadLeftX: Float = 0f
    private var dpadRightX: Float = 0f
    private var jumpButtonX: Float = 0f
    private var fireButtonX: Float = 0f
    private var chargeButtonX: Float = 0f
    private var charSwitchButtonX: Float = 0f
    private var controlButtonY: Float = 0f
    private var controlButtonSize: Float = 0f

    // Render thread
    private var renderThread: Thread? = null
    private var isRendering: Boolean = false

    init {
        surfaceHolder = holder
        if (surfaceHolder != null) {
            surfaceHolder?.addCallback(this)
        }
        setFocusable(true)
        setFocusableInTouchMode(true)
    }

    // ------- SurfaceHolder.Callback implementation -------

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Surface is ready; initialize game
        updateScale(width, height)
        initializeGame()
        startRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Update viewport dimensions based on surface size
        viewportWidth = width
        viewportHeight = height
        updateScale(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Surface is being destroyed; stop game loop
        stopRenderThread()
        gameEngine.pause()
        gameEngine.stop()
    }

    // ------- Render Thread -------

    private fun startRenderThread() {
        if (isRendering) return
        isRendering = true
        renderThread = Thread({
            while (isRendering) {
                val canvas = surfaceHolder?.lockCanvas()
                if (canvas != null) {
                    try {
                        update()
                        draw(canvas)
                    } finally {
                        surfaceHolder?.unlockCanvasAndPost(canvas)
                    }
                }
            }
        }, "GameRenderThread")
        renderThread?.start()
    }

    private fun stopRenderThread() {
        isRendering = false
        renderThread?.join(1000)
        renderThread = null
    }

    // ------- Initialization -------

    private fun updateScale(viewWidth: Int, viewHeight: Int) {
        gameRenderer.updateScale(viewWidth, viewHeight)
    }

    private fun initializeGame() {
        // Load sprite sheet for player
        val spriteSheet = try {
            com.dincer552.hattrickssh.game.character.SpriteSheet(
                context, "game/characters/hero/hero_spritesheet.png", 8, 5
            )
        } catch (e: Exception) {
            null
        }

        // Create animations from sprite sheet
        val animations = mutableMapOf<String, com.dincer552.hattrickssh.game.character.AnimatedSprite>()
        if (spriteSheet != null && spriteSheet.isLoaded) {
            animations["idle"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 3, fps = 8, loop = true, startFrame = 0
            )
            animations["walk"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 4, fps = 10, loop = true, startFrame = 3
            )
            animations["run"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 4, fps = 14, loop = true, startFrame = 7
            )
            animations["jump_prep"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 10, loop = false, startFrame = 11
            )
            animations["jump_rising"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 12, loop = false, startFrame = 13
            )
            animations["jump_falling"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 12, loop = false, startFrame = 15
            )
            animations["land"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 12, loop = false, startFrame = 17
            )
            animations["shoot_stand"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 12, loop = false, startFrame = 19
            )
            animations["shoot_move"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 12, loop = false, startFrame = 21
            )
            animations["shoot_up"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 12, loop = false, startFrame = 23
            )
            animations["damage"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 10, loop = false, startFrame = 25
            )
            animations["death"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 4, fps = 8, loop = false, startFrame = 27
            )
            animations["special"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 3, fps = 10, loop = false, startFrame = 31
            )
            animations["victory"] = com.dincer552.hattrickssh.game.character.AnimatedSprite(
                spriteSheet, frames = 2, fps = 6, loop = false, startFrame = 34
            )
        }

        // Create player at center of screen
        val playerStartX = 50f
        val playerStartY = 100f
        val player = Player(
            com.dincer552.hattrickssh.game.character.GamePosition(playerStartX, playerStartY),
            characterManager,
            specialAbilityManager
        )

        // Create game world
        gameWorld = GameWorld(player)

        // Add platforms
        val platformWidth = 120f
        val platformHeight = 16f
        val groundY = viewportHeight.toFloat() - 40f
        gameWorld?.addPlatform(Platform(
            com.dincer552.hattrickssh.game.character.GamePosition(0f, groundY),
            viewportWidth.toFloat(), 40f
        )) // Ground
        gameWorld?.addPlatform(Platform(
            com.dincer552.hattrickssh.game.character.GamePosition(40f, groundY - 60f),
            platformWidth, platformHeight
        )) // Platform 1
        gameWorld?.addPlatform(Platform(
            com.dincer552.hattrickssh.game.character.GamePosition(160f, groundY - 120f),
            platformWidth, platformHeight
        )) // Platform 2
        gameWorld?.addPlatform(Platform(
            com.dincer552.hattrickssh.game.character.GamePosition(80f, groundY - 180f),
            platformWidth, platformHeight, isConveyor = true, conveyorSpeed = 1.0f
        )) // Conveyor

        // Add targets for shooting practice
        gameWorld?.addTarget(Target(
            com.dincer552.hattrickssh.game.character.GamePosition(200f, groundY - 80f)
        ))
        gameWorld?.addTarget(Target(
            com.dincer552.hattrickssh.game.character.GamePosition(300f, groundY - 140f), 2
        )) // Two-hit target

        // Setup touch control regions
        setupTouchControls()
    }

    private fun setupTouchControls() {
        val btnSize = 44f
        val margin = 10f
        controlButtonSize = btnSize
        controlButtonY = viewportHeight.toFloat() - btnSize - margin

        // D-pad (left side)
        dpadLeftX = margin
        dpadRightX = margin + btnSize

        // Action buttons (right side)
        jumpButtonX = viewportWidth.toFloat() - btnSize * 4 - margin
        fireButtonX = viewportWidth.toFloat() - btnSize * 3 - margin
        chargeButtonX = viewportWidth.toFloat() - btnSize * 2 - margin
        charSwitchButtonX = viewportWidth.toFloat() - btnSize - margin
    }

    // ------- Input forwarding -------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                handleTouchDown(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                handleTouchUp(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(x, y)
            }
        }

        return true
    }

    private fun handleTouchDown(x: Float, y: Float) {
        if (y > controlButtonY) {
            // Bottom control area
            if (x < dpadRightX) {
                // Left side - D-pad
                if (x < dpadLeftX + controlButtonSize / 2) {
                    gameInput.press(InputAction.LEFT)
                } else {
                    gameInput.press(InputAction.RIGHT)
                }
            } else if (x >= charSwitchButtonX && x < charSwitchButtonX + controlButtonSize) {
                gameInput.press(InputAction.CHAR_SWITCH)
            } else if (x >= chargeButtonX && x < chargeButtonX + controlButtonSize) {
                gameInput.press(InputAction.CHARGE)
            } else if (x >= fireButtonX && x < fireButtonX + controlButtonSize) {
                gameInput.press(InputAction.FIRE)
            } else if (x >= jumpButtonX && x < jumpButtonX + controlButtonSize) {
                gameInput.press(InputAction.JUMP)
            }
        } else {
            // Upper area - movement
            val centerX = viewportWidth.toFloat() / 2f
            if (x < centerX) {
                gameInput.press(InputAction.LEFT)
            } else {
                gameInput.press(InputAction.RIGHT)
            }
        }
    }

    private fun handleTouchUp(x: Float, y: Float) {
        gameInput.release(InputAction.LEFT)
        gameInput.release(InputAction.RIGHT)
        gameInput.release(InputAction.JUMP)
        gameInput.release(InputAction.FIRE)
        gameInput.release(InputAction.CHARGE)
        gameInput.release(InputAction.CHAR_SWITCH)
    }

    private fun handleTouchMove(x: Float, y: Float) {
        val centerX = viewportWidth.toFloat() / 2f
        if (y > controlButtonY) {
            if (x < centerX) {
                gameInput.press(InputAction.LEFT)
                gameInput.release(InputAction.RIGHT)
            } else {
                gameInput.press(InputAction.RIGHT)
                gameInput.release(InputAction.LEFT)
            }
        }
    }

    // ------- Game loop integration -------

    fun update() {
        val deltaTime = gameEngine.getDeltaTime()

        // Process input
        processInput()

        // Update rescue system
        rescueSystem.update(deltaTime)

        // Update game world based on game phase
        when (planetManager.getGamePhase) {
            com.dincer552.hattrickssh.game.planet.GamePhase.PLAYING_PLANET -> {
                gameWorld?.update(deltaTime)
            }
            com.dincer552.hattrickssh.game.planet.GamePhase.MOTHERSHIP -> {
                gameWorld?.update(deltaTime)
            }
            else -> {
                // Planet selection, boss fight, rescue sequence - minimal updates
            }
        }

        // Clear just-pressed flags
        gameInput.clearFrame()
    }

    private fun processInput() {
        val player = gameWorld?.getPlayer ?: return

        // Horizontal movement (only during gameplay)
        if (planetManager.isPlayingPlanet || planetManager.isInMothership) {
            if (gameInput.isPressed(InputAction.LEFT)) {
                player.startMove(-1, gameInput.isPressed(InputAction.SPECIAL))
            } else if (gameInput.isPressed(InputAction.RIGHT)) {
                player.startMove(1, gameInput.isPressed(InputAction.SPECIAL))
            } else {
                player.stopMove()
            }

            // Jump
            if (gameInput.wasJustPressed(InputAction.JUMP)) {
                player.startJump()
            }

            // Fire
            if (gameInput.wasJustPressed(InputAction.FIRE)) {
                val projectileData = player.fire()
                if (projectileData != null) {
                    val projectile = createProjectileFromData(projectileData)
                    gameWorld?.addProjectile(projectile)
                }
            }

            // Charge special ability
            if (gameInput.isCharging()) {
                player.startCharge()
            }

            // Release charge (activate special ability)
            if (gameInput.wasChargeReleased()) {
                if (player.releaseCharge()) {
                    handleSpecialAbilityActivation()
                }
            }

            // Cancel charge if released without full charge
            if (gameInput.wasJustReleased(InputAction.CHARGE) && !gameInput.wasChargeReleased()) {
                player.cancelCharge()
            }

            // Character switch
            if (gameInput.wasCharacterSwitch()) {
                player.cycleNextCharacter()
            }
            if (gameInput.wasCharacterSwitchPrev()) {
                player.cyclePreviousCharacter()
            }
        }
    }

    private fun handleSpecialAbilityActivation() {
        val player = gameWorld?.getPlayer ?: return
        val specialType = player.getActiveSpecialAbility

        when (specialType) {
            com.dincer552.hattrickssh.game.character.SpecialAbilityType.GUIDED_ORB -> {
                // Create guided orb at player position
                val projectile = specialAbilityManager.createGuidedOrb(
                    player.position.x + 16f,
                    player.position.y + 12f,
                    player.position.x + 50f,
                    player.position.y + 12f
                )
                gameWorld?.addProjectile(projectile)
            }
            else -> {
                // Other abilities are handled by state changes in SpecialAbilityManager
            }
        }
    }

    /** Called by Activity to draw frame. */
    fun draw(canvas: Canvas) {
        // Clear screen
        gameRenderer.clearScreen(canvas)

        // Update scale
        gameRenderer.updateScale(canvas.width, canvas.height)

        // Get camera offset
        val camX = gameWorld?.getCameraX ?: 0f
        val camY = gameWorld?.getCameraY ?: 0f

        // Draw based on game phase
        when (planetManager.getGamePhase) {
            com.dincer552.hattrickssh.game.planet.GamePhase.PLANET_SELECTION -> {
                drawPlanetSelection(canvas)
            }
            com.dincer552.hattrickssh.game.planet.GamePhase.PLAYING_PLANET,
            com.dincer552.hattrickssh.game.planet.GamePhase.BOSS_FIGHT,
            com.dincer552.hattrickssh.game.planet.GamePhase.MOTHERSHIP -> {
                drawGameplay(canvas, camX, camY)
            }
            com.dincer552.hattrickssh.game.planet.GamePhase.RESCUE_SEQUENCE -> {
                drawRescueSequence(canvas)
            }
            com.dincer552.hattrickssh.game.planet.GamePhase.GAME_COMPLETE -> {
                drawGameComplete(canvas)
            }
        }
    }

    private fun drawPlanetSelection(canvas: Canvas) {
        // Draw planet selection screen
        gameRenderer.drawText(canvas, "BUCKY O'HARE", 40f, 40f, 0xFFFFFFFF.toInt(), 24f)
        gameRenderer.drawText(canvas, "Select Planet", 40f, 80f, 0xFFFFFFFF.toInt(), 16f)

        val planets = planetManager.getPlanetStatuses()
        var y = 100f
        for ((planetType, accessible) in planets) {
            val planetData = com.dincer552.hattrickssh.game.planet.PlanetRegistry.get(planetType)
            val color = if (accessible) 0xFFFFFFFF.toInt() else 0xFF888888.toInt()
            gameRenderer.drawText(canvas, "${planetData.name} - ${planetData.crewMember}", 40f, y, color, 14f)
            y += 20f
        }

        // Draw controls hint
        gameRenderer.drawText(canvas, "Touch planet to select", 40f, viewportHeight.toFloat() - 40f, 0xFF888888.toInt(), 12f)
    }

    private fun drawGameplay(canvas: Canvas, camX: Float, camY: Float) {
        // Draw platforms
        gameWorld?.getPlatforms?.forEach { platform ->
            platform.draw(canvas, gameRenderer.getPaint, camX, camY)
        }

        // Draw targets
        gameWorld?.getTargets?.forEach { target ->
            target.draw(canvas, gameRenderer.getPaint, camX, camY)
        }

        // Draw projectiles
        gameWorld?.getProjectiles?.forEach { projectile ->
            projectile.draw(canvas, gameRenderer.getPaint, camX, camY)
        }

        // Draw player
        val player = gameWorld?.getPlayer
        if (player != null) {
            val frameRect = player.getCurrentFrameRect
            val spriteSheet = player.getSpriteSheet
            if (frameRect != null && spriteSheet != null && spriteSheet.isLoaded) {
                gameRenderer.drawSpriteFrame(
                    canvas,
                    spriteSheet,
                    frameRect,
                    player.position.x - camX,
                    player.position.y - camY,
                    player.isFacingRight,
                    if (player.isInvincible) 0.5f else 1f
                )
            } else {
                // Fallback: draw colored rectangle
                gameRenderer.drawRect(canvas, player.position.x - camX, player.position.y - camY, 24f, 24f, 0xFFFF00FF.toInt())
            }

            // Draw health indicator
            drawHealthBar(canvas, player, camX, camY)

            // Draw power gauge indicator
            drawPowerGauge(canvas, player, camX, camY)

            // Draw charge indicator
            drawChargeIndicator(canvas, player, camX, camY)
        }

        // Draw score
        gameWorld?.let { world ->
            gameRenderer.drawText(canvas, "SCORE: ${world.getScore}", 10f, 20f, 0xFFFFFFFF.toInt(), 14f)
            gameRenderer.drawText(canvas, "LIVES: ${player?.getHealth ?: 0}", 10f, 40f, 0xFFFFFFFF.toInt(), 14f)
        }

        // Draw touch control hints
        drawTouchControls(canvas)
    }

    private fun drawRescueSequence(canvas: Canvas) {
        val rescue = rescueSystem.getCurrentCrewMember
        val state = rescueSystem.getRescueState

        gameRenderer.drawText(canvas, "RESCUE", 40f, 40f, 0xFFFFFFFF.toInt(), 24f)

        when (state) {
            com.dincer552.hattrickssh.game.rescue.RescueState.RESCUING -> {
                gameRenderer.drawText(canvas, "Rescuing ${rescue?.name}...", 40f, 80f, 0xFFFFFFFF.toInt(), 16f)
            }
            com.dincer552.hattrickssh.game.rescue.RescueState.INTRODUCTION -> {
                gameRenderer.drawText(canvas, "${rescue?.name}", 40f, 80f, 0xFFFFFFFF.toInt(), 20f)
                gameRenderer.drawText(canvas, "${rescue?.description}", 40f, 100f, 0xFF888888.toInt(), 14f)
            }
            com.dincer552.hattrickssh.game.rescue.RescueState.COMPLETE -> {
                gameRenderer.drawText(canvas, "${rescue?.name} Joined the Team!", 40f, 80f, 0xFF00FF00.toInt(), 16f)
            }
            else -> {}
        }
    }

    private fun drawGameComplete(canvas: Canvas) {
        gameRenderer.drawText(canvas, "GAME COMPLETE", 40f, 40f, 0xFFFFFFFF.toInt(), 24f)
        gameRenderer.drawText(canvas, "All Crew Rescued!", 40f, 80f, 0xFF00FF00.toInt(), 16f)
        gameRenderer.drawText(canvas, "Toad Mothership Escaped", 40f, 100f, 0xFFFFFFFF.toInt(), 14f)
    }

    private fun drawHealthBar(canvas: Canvas, player: Player, camX: Float, camY: Float) {
        val barWidth = 24f
        val barHeight = 4f
        val x = player.position.x - camX
        val y = player.position.y - camY - 6f

        // Background
        gameRenderer.drawRect(canvas, x, y, barWidth, barHeight, 0xFF444444.toInt())
        // Health fill
        val healthWidth = (barWidth * player.getHealth / player.getMaxHealth).toFloat()
        gameRenderer.drawRect(canvas, x, y, healthWidth, barHeight, 0xFF00FF00.toInt())
    }

    private fun drawPowerGauge(canvas: Canvas, player: Player, camX: Float, camY: Float) {
        val barWidth = 30f
        val barHeight = 5f
        val x = player.position.x - camX - 3f
        val y = player.position.y - camY - 12f

        // Background
        gameRenderer.drawRect(canvas, x, y, barWidth, barHeight, 0xFF333333.toInt())
        // Power fill
        val powerWidth = (barWidth * player.getPowerGaugePercent).toFloat()
        gameRenderer.drawRect(canvas, x, y, powerWidth, barHeight, 0xFF00BFFF.toInt()) // Blue power gauge
    }

    private fun drawChargeIndicator(canvas: Canvas, player: Player, camX: Float, camY: Float) {
        val chargePct = player.getChargePercent
        if (chargePct <= 0f) return

        val indicatorSize = 12f * chargePct
        val x = player.position.x - camX + 12f - indicatorSize / 2f
        val y = player.position.y - camY - 18f

        gameRenderer.drawRect(canvas, x, y, indicatorSize, indicatorSize, 0xFFFFFF00.toInt()) // Yellow charge indicator
    }

    private fun drawTouchControls(canvas: Canvas) {
        val paint = gameRenderer.getPaint

        // Draw directional pad area (left side)
        paint.color = 0x44FFFFFF.toInt()
        canvas.drawRect(dpadLeftX, controlButtonY, dpadLeftX + controlButtonSize * 2, controlButtonY + controlButtonSize, paint)
        canvas.drawRect(dpadLeftX, controlButtonY, dpadLeftX + controlButtonSize, controlButtonY + controlButtonSize, paint)
        canvas.drawRect(dpadLeftX + controlButtonSize, controlButtonY, dpadLeftX + controlButtonSize * 2, controlButtonY + controlButtonSize, paint)

        // Draw jump button (right side)
        paint.color = 0x44FFFFFF.toInt()
        canvas.drawRect(jumpButtonX, controlButtonY, jumpButtonX + controlButtonSize, controlButtonY + controlButtonSize, paint)
        paint.color = 0xFFFFFFFF.toInt()
        gameRenderer.drawText(canvas, "J", jumpButtonX + controlButtonSize / 2 - 4f, controlButtonY + controlButtonSize / 2 + 6f, paint)

        // Draw fire button (right side)
        paint.color = 0x44FFFFFF.toInt()
        canvas.drawRect(fireButtonX, controlButtonY, fireButtonX + controlButtonSize, controlButtonY + controlButtonSize, paint)
        paint.color = 0xFFFFFFFF.toInt()
        gameRenderer.drawText(canvas, "F", fireButtonX + controlButtonSize / 2 - 4f, controlButtonY + controlButtonSize / 2 + 6f, paint)

        // Draw charge button (right side)
        paint.color = 0x44FFFFFF.toInt()
        canvas.drawRect(chargeButtonX, controlButtonY, chargeButtonX + controlButtonSize, controlButtonY + controlButtonSize, paint)
        paint.color = 0xFFFFFF00.toInt()
        gameRenderer.drawText(canvas, "C", chargeButtonX + controlButtonSize / 2 - 4f, controlButtonY + controlButtonSize / 2 + 6f, paint)

        // Draw character switch button (right side)
        paint.color = 0x44FFFFFF.toInt()
        canvas.drawRect(charSwitchButtonX, controlButtonY, charSwitchButtonX + controlButtonSize, controlButtonY + controlButtonSize, paint)
        paint.color = 0xFF00FFFF.toInt()
        gameRenderer.drawText(canvas, "S", charSwitchButtonX + controlButtonSize / 2 - 4f, controlButtonY + controlButtonSize / 2 + 6f, paint)
    }

    // ------- Accessors for external use -------

    val getGameEngine: GameEngine get() = gameEngine
    val getGameInput: GameInput get() = gameInput
    val getGameState: GameStateManager get() = gameState
    val getViewportWidth: Int get() = viewportWidth
    val getViewportHeight: Int get() = viewportHeight
    val getGameWorld: GameWorld? get() = gameWorld
    val getCharacterManager: CharacterManager get() = characterManager
    val getSpecialAbilityManager: SpecialAbilityManager get() = specialAbilityManager
    val getPlanetManager: PlanetManager get() = planetManager
    val getRescueSystem: RescueSystem get() = rescueSystem
    val getMothershipManager: com.dincer552.hattrickssh.game.mothership.MothershipManager get() = mothershipManager
}
