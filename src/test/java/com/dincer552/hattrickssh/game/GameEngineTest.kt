package com.dincer552.hattrickssh.game

import com.dincer552.hattrickssh.game.models.*
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {

    @Test
    fun testBuckyCharactersDefinition() {
        val chars = BuckyCharacter.values()
        assertEquals(5, chars.size)
        assertEquals("BUCKY O'HARE", BuckyCharacter.BUCKY.displayName)
        assertEquals("JENNY", BuckyCharacter.JENNY.displayName)
        assertEquals("WILLY DUWITT", BuckyCharacter.WILLY.displayName)
        assertEquals("DEAD-EYE DUCK", BuckyCharacter.DEAD_EYE.displayName)
        assertEquals("BLINKY", BuckyCharacter.BLINKY.displayName)
    }

    @Test
    fun testLevelManagerPlanets() {
        val levelManager = LevelManager()
        val planets = levelManager.planetInfos
        assertEquals(5, planets.size)

        for (i in 0 until 5) {
            val level = levelManager.createLevel(i)
            assertNotNull(level)
            assertEquals(120, level.widthTiles)
            assertEquals(16, level.heightTiles)
            assertTrue(level.enemySpawns.isNotEmpty())
            assertTrue(level.pickupSpawns.isNotEmpty())
        }
    }

    @Test
    fun testPlayerEntityInitialization() {
        val player = Player()
        assertEquals(100, player.health)
        assertEquals(100, player.maxHealth)
        assertEquals(100, player.energy)
        assertEquals(3, player.lives)
        assertEquals(BuckyCharacter.BUCKY, player.activeCharacter)
    }

    @Test
    fun testEnemyDamageTaking() {
        val enemy = Enemy(EnemyType.TOAD_SOLDIER, 100f, 100f)
        assertTrue(enemy.isAlive)
        val initialHp = enemy.hp
        enemy.takeDamage(10)
        assertEquals(initialHp - 10, enemy.hp)
        assertTrue(enemy.isAlive)

        enemy.takeDamage(100)
        assertFalse(enemy.isAlive)
        assertEquals(0, enemy.hp)
    }
}
