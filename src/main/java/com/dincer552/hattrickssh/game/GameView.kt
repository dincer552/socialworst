package com.dincer552.hattrickssh.game

import android.content.Context
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.dincer552.hattrickssh.game.render.GameRenderer

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val engine = GameEngine(context)
    val renderer = GameRenderer()

    private var gameThread: Thread? = null
    @Volatile
    private var isRunning = false
    private var lastTimeNanos: Long = System.nanoTime()

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        lastTimeNanos = System.nanoTime()
        gameThread = Thread(this, "GameLoopThread").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (_: InterruptedException) {}
        }
        gameThread = null
    }

    override fun run() {
        while (isRunning) {
            val now = System.nanoTime()
            val deltaTime = ((now - lastTimeNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
            lastTimeNanos = now

            engine.update(deltaTime)
            drawFrame()

            val elapsedMs = (System.nanoTime() - now) / 1_000_000
            val sleepMs = 16 - elapsedMs
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {}
            }
        }
    }

    private fun drawFrame() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            val w = width
            val h = height

            when (engine.state) {
                GameState.TITLE -> {
                    renderer.renderTitle(canvas, w, h, engine.frameTimer, engine.saveManager.highScore)
                }
                GameState.PLANET_SELECT -> {
                    renderer.renderPlanetSelect(
                        canvas, w, h,
                        engine.level.planetInfo.let { listOf(
                            com.dincer552.hattrickssh.game.models.PlanetInfo(0, "PLANET GREEN", "CELLULAR PLANET", 0xFF22C55E.toInt(), com.dincer552.hattrickssh.game.models.BuckyCharacter.JENNY, com.dincer552.hattrickssh.game.models.EnemyType.BOSS_TOAD),
                            com.dincer552.hattrickssh.game.models.PlanetInfo(1, "PLANET RED", "VOLCANO PLANET", 0xFFEF4444.toInt(), com.dincer552.hattrickssh.game.models.BuckyCharacter.WILLY, com.dincer552.hattrickssh.game.models.EnemyType.BOSS_TOAD),
                            com.dincer552.hattrickssh.game.models.PlanetInfo(2, "PLANET BLUE", "WATER PLANET", 0xFF3B82F6.toInt(), com.dincer552.hattrickssh.game.models.BuckyCharacter.DEAD_EYE, com.dincer552.hattrickssh.game.models.EnemyType.BOSS_AIR_MARSHAL),
                            com.dincer552.hattrickssh.game.models.PlanetInfo(3, "PLANET YELLOW", "CLIMATE PLANET", 0xFFEAB308.toInt(), com.dincer552.hattrickssh.game.models.BuckyCharacter.BLINKY, com.dincer552.hattrickssh.game.models.EnemyType.BOSS_AIR_MARSHAL),
                            com.dincer552.hattrickssh.game.models.PlanetInfo(4, "TOAD FLAGSHIP", "KOMPLEX FORTRESS", 0xFFA855F7.toInt(), null, com.dincer552.hattrickssh.game.models.EnemyType.BOSS_KOMPLEX)
                        ) },
                        engine.currentPlanetIndex,
                        engine.saveManager
                    )
                }
                GameState.PLAYING -> {
                    val boss = engine.enemies.firstOrNull { it.type.isBoss && it.isAlive }
                    renderer.renderGame(
                        canvas, w, h,
                        engine.player,
                        engine.level,
                        engine.enemies,
                        engine.projectiles,
                        engine.particles,
                        engine.pickups,
                        engine.saveManager,
                        boss,
                        engine.frameTimer
                    )
                }
                GameState.PAUSED -> {
                    val boss = engine.enemies.firstOrNull { it.type.isBoss && it.isAlive }
                    renderer.renderGame(
                        canvas, w, h,
                        engine.player,
                        engine.level,
                        engine.enemies,
                        engine.projectiles,
                        engine.particles,
                        engine.pickups,
                        engine.saveManager,
                        boss,
                        engine.frameTimer
                    )
                    renderer.renderOverlayMessage(canvas, w, h, "PAUSED", "TAP TO RESUME GAME")
                }
                GameState.STAGE_CLEAR -> {
                    renderer.renderOverlayMessage(
                        canvas, w, h,
                        "STAGE CLEARED!",
                        "CREW MEMBER RESCUED! TAP TO CONTINUE"
                    )
                }
                GameState.GAME_OVER -> {
                    renderer.renderOverlayMessage(
                        canvas, w, h,
                        "GAME OVER",
                        "TAP TO RETURN TO TITLE"
                    )
                }
                GameState.VICTORY -> {
                    renderer.renderOverlayMessage(
                        canvas, w, h,
                        "VICTORY!",
                        "KOMPLEX DEFEATED! BUCKY SAVED THE GALAXY!"
                    )
                }
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (engine.state) {
            GameState.TITLE -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    engine.state = GameState.PLANET_SELECT
                    engine.soundSynth.playSfx(com.dincer552.hattrickssh.game.audio.SoundSynth.SfxType.SELECT)
                }
            }
            GameState.PLANET_SELECT -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    val planetSpacing = width / 6f
                    val planetRadius = height * 0.15f
                    var clickedPlanet = -1
                    for (i in 0 until 5) {
                        val px = planetSpacing * (i + 1)
                        val py = height * 0.5f
                        if (Math.hypot((x - px).toDouble(), (y - py).toDouble()) <= planetRadius) {
                            clickedPlanet = i
                            break
                        }
                    }

                    if (clickedPlanet != -1) {
                        if (clickedPlanet == 4 && !engine.saveManager.isPlanetCompleted(3)) {
                            // Locked
                            engine.soundSynth.playSfx(com.dincer552.hattrickssh.game.audio.SoundSynth.SfxType.HIT)
                        } else {
                            engine.currentPlanetIndex = clickedPlanet
                            engine.soundSynth.playSfx(com.dincer552.hattrickssh.game.audio.SoundSynth.SfxType.SELECT)
                            engine.startPlanet(clickedPlanet)
                        }
                    } else {
                        // Tap anywhere else to start current selected planet
                        engine.startPlanet(engine.currentPlanetIndex)
                    }
                }
            }
            GameState.PLAYING -> {
                when (action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        processTouchInput(x, y, true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        processTouchInput(x, y, false)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        for (i in 0 until event.pointerCount) {
                            processTouchInput(event.getX(i), event.getY(i), true)
                        }
                    }
                }
            }
            GameState.PAUSED -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    engine.state = GameState.PLAYING
                }
            }
            GameState.STAGE_CLEAR -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    engine.state = GameState.PLANET_SELECT
                    engine.soundSynth.startMusic("TITLE")
                }
            }
            GameState.GAME_OVER -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    engine.state = GameState.TITLE
                    engine.soundSynth.startMusic("TITLE")
                }
            }
            GameState.VICTORY -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    engine.state = GameState.TITLE
                    engine.soundSynth.startMusic("TITLE")
                }
            }
        }
        return true
    }

    private fun processTouchInput(tx: Float, ty: Float, isPressed: Boolean) {
        if (renderer.dpadLeftRect.contains(tx, ty)) engine.inputLeft = isPressed
        if (renderer.dpadRightRect.contains(tx, ty)) engine.inputRight = isPressed
        if (renderer.dpadUpRect.contains(tx, ty)) engine.inputUp = isPressed
        if (renderer.dpadDownRect.contains(tx, ty)) engine.inputDown = isPressed

        if (renderer.btnJumpRect.contains(tx, ty)) engine.inputJump = isPressed
        if (renderer.btnShootRect.contains(tx, ty)) engine.inputShoot = isPressed
        if (renderer.btnSpecialRect.contains(tx, ty)) engine.inputSpecial = isPressed

        if (isPressed && renderer.btnSwapRect.contains(tx, ty)) {
            engine.swapCharacter()
        }
        if (isPressed && renderer.btnPauseRect.contains(tx, ty)) {
            engine.state = GameState.PAUSED
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> { engine.inputLeft = true; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> { engine.inputRight = true; return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { engine.inputUp = true; return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> { engine.inputDown = true; return true }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_Z -> { engine.inputJump = true; return true }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_X -> { engine.inputShoot = true; return true }
            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_C -> { engine.inputSpecial = true; return true }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_V -> { engine.swapCharacter(); return true }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> {
                engine.state = if (engine.state == GameState.PLAYING) GameState.PAUSED else GameState.PLAYING
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> { engine.inputLeft = false; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> { engine.inputRight = false; return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { engine.inputUp = false; return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> { engine.inputDown = false; return true }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_Z -> { engine.inputJump = false; return true }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_X -> { engine.inputShoot = false; return true }
            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_C -> { engine.inputSpecial = false; return true }
        }
        return super.onKeyUp(keyCode, event)
    }

    fun release() {
        engine.release()
    }
}
