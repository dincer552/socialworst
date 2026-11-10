package com.dincer552.hattrickssh.game

import com.dincer552.hattrickssh.game.character.*
import com.dincer552.hattrickssh.game.planet.*
import com.dincer552.hattrickssh.game.mothership.*
import com.dincer552.hattrickssh.game.rescue.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameLogicTest {

    private lateinit var characterManager: CharacterManager
    private lateinit var planetManager: PlanetManager
    private lateinit var mothershipManager: MothershipManager
    private lateinit var rescueSystem: RescueSystem

    @Before
    fun setUp() {
        characterManager = CharacterManager()
        planetManager = PlanetManager(characterManager)
        mothershipManager = MothershipManager(characterManager)
        rescueSystem = RescueSystem(characterManager)
    }

    @Test
    fun testInitialCharacterIsBucky() {
        assertEquals(CharacterType.BUCKY, characterManager.getActiveCharacterType)
        assertTrue(characterManager.isUnlocked(CharacterType.BUCKY))
        assertFalse(characterManager.isUnlocked(CharacterType.BLINKY))
        assertFalse(characterManager.isUnlocked(CharacterType.DEAD_EYE))
        assertFalse(characterManager.isUnlocked(CharacterType.JENNY))
        assertFalse(characterManager.isUnlocked(CharacterType.WILLY))
    }

    @Test
    fun testUnlockAndSwitchCharacters() {
        characterManager.rescue(CharacterType.BLINKY)
        assertTrue(characterManager.isUnlocked(CharacterType.BLINKY))

        // Switch to Blinky
        val switched = characterManager.switchTo(CharacterType.BLINKY)
        assertTrue(switched)
        assertEquals(CharacterType.BLINKY, characterManager.getActiveCharacterType)

        // Cannot switch to locked character
        val switchedToJenny = characterManager.switchTo(CharacterType.JENNY)
        assertFalse(switchedToJenny)
        assertEquals(CharacterType.BLINKY, characterManager.getActiveCharacterType)
    }

    @Test
    fun testNextCharacterCycle() {
        characterManager.rescue(CharacterType.DEAD_EYE)
        assertEquals(CharacterType.BUCKY, characterManager.getActiveCharacterType)

        characterManager.cycleNext()
        assertEquals(CharacterType.DEAD_EYE, characterManager.getActiveCharacterType)

        characterManager.cycleNext()
        assertEquals(CharacterType.BUCKY, characterManager.getActiveCharacterType)
    }

    @Test
    fun testPlanetAccessAndBlinkyDependency() {
        // Initially, Green, Red, Yellow are accessible, but Blue requires Blinky
        val greenAccessible = PlanetRegistry.isAccessible(PlanetType.GREEN, characterManager)
        val blueAccessibleBefore = PlanetRegistry.isAccessible(PlanetType.BLUE, characterManager)
        assertTrue("Green planet should be accessible initially", greenAccessible)
        assertFalse("Blue planet requires Blinky", blueAccessibleBefore)

        // Rescue Blinky
        characterManager.rescue(CharacterType.BLINKY)
        val blueAccessibleAfter = PlanetRegistry.isAccessible(PlanetType.BLUE, characterManager)
        assertTrue("Blue planet should be accessible once Blinky is rescued", blueAccessibleAfter)
    }

    @Test
    fun testPlanetProgressionAndCrewRescue() {
        planetManager.selectPlanet(PlanetType.GREEN)
        assertEquals(1, planetManager.getCurrentAct)

        // Complete Act 1
        planetManager.completeAct()
        assertEquals(2, planetManager.getCurrentAct)

        // Complete remaining acts to reach boss
        val totalActs = PlanetRegistry.get(PlanetType.GREEN).numActs
        for (i in 2..totalActs) {
            planetManager.completeAct()
        }
        assertEquals(GamePhase.BOSS_FIGHT, planetManager.getGamePhase)

        // Complete boss
        planetManager.completeBoss()
        assertEquals(GamePhase.RESCUE_SEQUENCE, planetManager.getGamePhase)

        // Complete rescue -> Blinky rescued
        planetManager.completeRescue()
        assertTrue("Blinky should be unlocked after Green Planet", characterManager.isUnlocked(CharacterType.BLINKY))
    }

    @Test
    fun testMothershipTriggerWhenAllPlanetsComplete() {
        // Complete Green
        planetManager.selectPlanet(PlanetType.GREEN)
        planetManager.completeBoss()
        planetManager.completeRescue()

        // Complete Red
        planetManager.selectPlanet(PlanetType.RED)
        planetManager.completeBoss()
        planetManager.completeRescue()

        // Complete Blue
        planetManager.selectPlanet(PlanetType.BLUE)
        planetManager.completeBoss()
        planetManager.completeRescue()

        // Complete Yellow
        planetManager.selectPlanet(PlanetType.YELLOW)
        planetManager.completeBoss()
        planetManager.completeRescue()

        // All 4 planets complete -> transitions to Mothership!
        assertEquals(GamePhase.MOTHERSHIP, planetManager.getGamePhase)
        assertEquals(MothershipStage.CELL, planetManager.getMothershipProgress.currentStage)
    }

    @Test
    fun testMothershipStageProgression() {
        assertEquals(MothershipStage.CELL, mothershipManager.getCurrentStage.stage)

        mothershipManager.completeStage()
        assertEquals(MothershipPhase.SALVAGE_CHUTE, mothershipManager.getPhase)

        mothershipManager.completeStage()
        assertEquals(MothershipPhase.MAGMA_TANKER, mothershipManager.getPhase)

        mothershipManager.completeStage()
        assertEquals(MothershipPhase.FINAL_ESCAPE, mothershipManager.getPhase)

        mothershipManager.completeStage()
        assertEquals(MothershipPhase.COMPLETE, mothershipManager.getPhase)
        assertTrue(mothershipManager.isMothershipComplete)
    }

    @Test
    fun testPhysicsAABBCollision() {
        val rect1 = GameRect(10f, 10f, 30f, 30f)
        val rect2 = GameRect(20f, 20f, 40f, 40f)
        val rect3 = GameRect(50f, 50f, 70f, 70f)

        assertTrue(rect1.intersects(rect2))
        assertTrue(rect2.intersects(rect1))
        assertFalse(rect1.intersects(rect3))
    }

    @Test
    fun testWeaponConfigurations() {
        for (type in CharacterType.values()) {
            val weapon = WeaponRegistry.get(type)
            assertNotNull("Weapon config should exist for $type", weapon)
            assertTrue("Weapon speed should be positive", weapon.stats.speed > 0f)
            assertTrue("Weapon damage should be positive", weapon.stats.damage > 0)
        }
    }

    @Test
    fun testSpecialAbilities() {
        for (type in SpecialAbilityType.values()) {
            val config = SpecialAbilityRegistry.get(type)
            assertNotNull("Special ability config should exist for $type", config)
            assertTrue("Power cost should be positive", config.powerCost > 0)
        }
    }
}
