package com.dincer552.hattrickssh.game.planet

/** PlanetType - The four planets of the Aniverse. */
enum class PlanetType {
    GREEN,   // Green Planet - forests, trees, waterfalls, Blinky rescue
    RED,     // Red Planet - volcanoes, lava pits, Dead-Eye rescue
    BLUE,    // Blue Planet - ice caves, slippery surfaces, Jenny rescue (requires Blinky)
    YELLOW   // Yellow Planet - futuristic, laser cannons, floating asteroids, Willy rescue
}

/** PlanetEnvironment - Visual theme for each planet. */
enum class PlanetEnvironment {
    FOREST,   // Green Planet
    VOLCANO,  // Red Planet
    ICE,      // Blue Planet
    SPACE     // Yellow Planet
}

/** HazardType - Types of hazards in each planet. */
enum class HazardType {
    INSECT,      // Green Planet - insects and environmental creatures
    FIREBALL,    // Red Planet - fireballs from volcanoes
    ICE_BLOCK,   // Blue Planet - breakable ice blocks (requires Blinky)
    ROBOT_SNAKE, // Blue Planet - robotic snakes
    LASER_CANNON, // Yellow Planet - laser cannons
    FLOATING_ASTEROID, // Yellow Planet - floating asteroids
    LAVA,        // Red Planet - lava pits
    WATER        // Blue/Green Planet - water hazards
}

/** LevelType - Type of level layout. */
enum class LevelType {
    HORIZONTAL_SCROLL,  // Side-scrolling platforming
    VERTICAL_SCROLL,    // Vertical scrolling (climbing/rising)
    BOSS_ARENA,         // Boss fight arena
    ESCAPE_SEQUENCE     // Escape from collapsing area
}

/** PlanetData - Static data for each planet. */
data class PlanetData(
    val type: PlanetType,
    val name: String,
    val environment: PlanetEnvironment,
    val crewMember: String,        // Crew member rescued here
    val numActs: Int,              // Number of acts in this planet
    val bossName: String,          // Boss name
    val hazards: List<HazardType>, // Hazards present
    val backgroundColor: Int,      // Background color
    val platformColor: Int,        // Platform color
    val requiresBlinky: Boolean = false // Whether Blinky is required to access
)

/** PlanetRegistry - Static data for all four planets. */
object PlanetRegistry {
    private val planets: Map<PlanetType, PlanetData> = mapOf(
        PlanetType.GREEN to PlanetData(
            type = PlanetType.GREEN,
            name = "Green Planet",
            environment = PlanetEnvironment.FOREST,
            crewMember = "A.F.C. Blinky",
            numActs = 3,
            bossName = "Toad Commander",
            hazards = listOf(HazardType.INSECT, HazardType.WATER),
            backgroundColor = 0xFF2E4A2E.toInt(),  // Dark green
            platformColor = 0xFF8B4513.toInt(),    // Brown
            requiresBlinky = false
        ),
        PlanetType.RED to PlanetData(
            type = PlanetType.RED,
            name = "Red Planet",
            environment = PlanetEnvironment.VOLCANO,
            crewMember = "Dead-Eye Duck",
            numActs = 3,
            bossName = "Toad Stormtrooper",
            hazards = listOf(HazardType.FIREBALL, HazardType.LAVA),
            backgroundColor = 0xFF4A1A1A.toInt(),  // Dark red
            platformColor = 0xFF654321.toInt(),    // Dark brown
            requiresBlinky = false
        ),
        PlanetType.BLUE to PlanetData(
            type = PlanetType.BLUE,
            name = "Blue Planet",
            environment = PlanetEnvironment.ICE,
            crewMember = "Jenny",
            numActs = 3,
            bossName = "Toad Air Marshall",
            hazards = listOf(HazardType.ICE_BLOCK, HazardType.ROBOT_SNAKE, HazardType.WATER),
            backgroundColor = 0xFF1A2A4A.toInt(),  // Dark blue
            platformColor = 0xFFADD8E6.toInt(),    // Light blue ice
            requiresBlinky = true  // REQUIRES BLINKY
        ),
        PlanetType.YELLOW to PlanetData(
            type = PlanetType.YELLOW,
            name = "Yellow Planet",
            environment = PlanetEnvironment.SPACE,
            crewMember = "Willy DuWitt",
            numActs = 3,
            bossName = "Toad Borg",
            hazards = listOf(HazardType.LASER_CANNON, HazardType.FLOATING_ASTEROID),
            backgroundColor = 0xFF4A4A1A.toInt(),  // Dark yellow/brown
            platformColor = 0xFF808080.toInt(),    // Gray metal
            requiresBlinky = false
        )
    )

    fun get(type: PlanetType): PlanetData = planets[type]
        ?: throw IllegalArgumentException("Unknown planet type: $type")

    val allTypes: List<PlanetType> = planets.keys.toList()

    /** Check if a planet is accessible given the current character unlock status. */
    fun isAccessible(type: PlanetType, characterManager: com.dincer552.hattrickssh.game.character.CharacterManager): Boolean {
        val data = get(type)
        if (data.requiresBlinky) {
            return characterManager.isUnlocked(com.dincer552.hattrickssh.game.character.CharacterType.BLINKY)
        }
        return true
    }
}

/** ActData - Data for a single act within a planet. */
data class ActData(
    val planetType: PlanetType,
    val actNumber: Int,
    val levelType: LevelType,
    val name: String,
    val viewportWidth: Int = 256,
    val viewportHeight: Int = 224,
    val backgroundColor: Int = 0xFF0A0A20.toInt(),
    val hasBoss: Boolean = false,
    val hasRescue: Boolean = false
)

/** ActRegistry - Static data for all acts across all planets. */
object ActRegistry {
    private val acts: Map<Pair<PlanetType, Int>, ActData> = mapOf(
        // Green Planet acts
        Pair(PlanetType.GREEN, 1) to ActData(PlanetType.GREEN, 1, LevelType.HORIZONTAL_SCROLL, "Forest Canopy"),
        Pair(PlanetType.GREEN, 2) to ActData(PlanetType.GREEN, 2, LevelType.VERTICAL_SCROLL, "Waterfall Climb"),
        Pair(PlanetType.GREEN, 3) to ActData(PlanetType.GREEN, 3, LevelType.BOSS_ARENA, "Toad Commander Lair", hasBoss = true, hasRescue = true),
        
        // Red Planet acts
        Pair(PlanetType.RED, 1) to ActData(PlanetType.RED, 1, LevelType.HORIZONTAL_SCROLL, "Volcano Slopes"),
        Pair(PlanetType.RED, 2) to ActData(PlanetType.RED, 2, LevelType.HORIZONTAL_SCROLL, "Lava Pits"),
        Pair(PlanetType.RED, 3) to ActData(PlanetType.RED, 3, LevelType.BOSS_ARENA, "Toad Stormtrooper Base", hasBoss = true, hasRescue = true),
        
        // Blue Planet acts
        Pair(PlanetType.BLUE, 1) to ActData(PlanetType.BLUE, 1, LevelType.HORIZONTAL_SCROLL, "Ice Caves"),
        Pair(PlanetType.BLUE, 2) to ActData(PlanetType.BLUE, 2, LevelType.VERTICAL_SCROLL, "Slippery Cliffs"),
        Pair(PlanetType.BLUE, 3) to ActData(PlanetType.BLUE, 3, LevelType.BOSS_ARENA, "Toad Air Marshall Ship", hasBoss = true, hasRescue = true),
        
        // Yellow Planet acts
        Pair(PlanetType.YELLOW, 1) to ActData(PlanetType.YELLOW, 1, LevelType.HORIZONTAL_SCROLL, "Laser Corridors"),
        Pair(PlanetType.YELLOW, 2) to ActData(PlanetType.YELLOW, 2, LevelType.HORIZONTAL_SCROLL, "Floating Asteroids"),
        Pair(PlanetType.YELLOW, 3) to ActData(PlanetType.YELLOW, 3, LevelType.BOSS_ARENA, "Toad Borg Fortress", hasBoss = true, hasRescue = true)
    )

    fun get(planetType: PlanetType, actNumber: Int): ActData = acts[Pair(planetType, actNumber)]
        ?: throw IllegalArgumentException("No act data for $planetType act $actNumber")

    val allActs: List<ActData> = acts.values.toList()
    
    fun getActsForPlanet(planetType: PlanetType): List<ActData> {
        return allActs.filter { it.planetType == planetType }.sortedBy { it.actNumber }
    }
}

/** MothershipStage - Stages of the Toad Mothership second half. */
enum class MothershipStage {
    CELL,              // Bucky and Blinky escape from cell
    SALVAGE_CHUTE,     // Progress through salvage system
    MAGMA_TANKER,      // Navigate magma tanker core
    ESCAPE             // Spacecraft escape through collapsing mothership
}

/** MothershipData - Data for each mothership stage. */
data class MothershipData(
    val stage: MothershipStage,
    val name: String,
    val levelType: LevelType,
    val backgroundColor: Int,
    val platformColor: Int,
    val hazards: List<HazardType>,
    val hasBoss: Boolean,
    val hasRescue: Boolean,
    val rescueTarget: String? = null
)

/** MothershipRegistry - Data for all mothership stages. */
object MothershipRegistry {
    private val stages: Map<MothershipStage, MothershipData> = mapOf(
        MothershipStage.CELL to MothershipData(
            stage = MothershipStage.CELL,
            name = "Cell Block",
            levelType = LevelType.HORIZONTAL_SCROLL,
            backgroundColor = 0xFF1A1A2E.toInt(),
            platformColor = 0xFF4A4A6A.toInt(),
            hazards = listOf(HazardType.LASER_CANNON),
            hasBoss = false,
            hasRescue = true,
            rescueTarget = "Dead-Eye Duck"
        ),
        MothershipStage.SALVAGE_CHUTE to MothershipData(
            stage = MothershipStage.SALVAGE_CHUTE,
            name = "Salvage Chute",
            levelType = LevelType.VERTICAL_SCROLL,
            backgroundColor = 0xFF2E2A1A.toInt(),
            platformColor = 0xFF6A5A4A.toInt(),
            hazards = listOf(HazardType.FLOATING_ASTEROID, HazardType.LASER_CANNON),
            hasBoss = false,
            hasRescue = true,
            rescueTarget = "Jenny"
        ),
        MothershipStage.MAGMA_TANKER to MothershipData(
            stage = MothershipStage.MAGMA_TANKER,
            name = "Magma Tanker Core",
            levelType = LevelType.HORIZONTAL_SCROLL,
            backgroundColor = 0xFF4A1A1A.toInt(),
            platformColor = 0xFF6A4A2A.toInt(),
            hazards = listOf(HazardType.LAVA, HazardType.FIREBALL),
            hasBoss = true,
            hasRescue = true,
            rescueTarget = "Willy DuWitt"
        ),
        MothershipStage.ESCAPE to MothershipData(
            stage = MothershipStage.ESCAPE,
            name = "Escape!",
            levelType = LevelType.ESCAPE_SEQUENCE,
            backgroundColor = 0xFF2A1A2E.toInt(),
            platformColor = 0xFF4A2A4A.toInt(),
            hazards = listOf(HazardType.FLOATING_ASTEROID, HazardType.LASER_CANNON),
            hasBoss = true,
            hasRescue = false,
            rescueTarget = null
        )
    )

    fun get(stage: MothershipStage): MothershipData = stages[stage]
        ?: throw IllegalArgumentException("Unknown mothership stage: $stage")

    val allStages: List<MothershipStage> = stages.keys.toList()
    val allStageData: List<MothershipData> = stages.values.toList()
}
