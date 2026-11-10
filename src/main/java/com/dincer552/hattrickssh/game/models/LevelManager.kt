package com.dincer552.hattrickssh.game.models

class LevelManager {

    val planetInfos = listOf(
        PlanetInfo(0, "PLANET GREEN", "CELLULAR PLANET", 0xFF22C55E.toInt(), BuckyCharacter.JENNY, EnemyType.BOSS_TOAD),
        PlanetInfo(1, "PLANET RED", "VOLCANO PLANET", 0xFFEF4444.toInt(), BuckyCharacter.WILLY, EnemyType.BOSS_TOAD),
        PlanetInfo(2, "PLANET BLUE", "WATER PLANET", 0xFF3B82F6.toInt(), BuckyCharacter.DEAD_EYE, EnemyType.BOSS_AIR_MARSHAL),
        PlanetInfo(3, "PLANET YELLOW", "CLIMATE PLANET", 0xFFEAB308.toInt(), BuckyCharacter.BLINKY, EnemyType.BOSS_AIR_MARSHAL),
        PlanetInfo(4, "TOAD FLAGSHIP", "KOMPLEX FORTRESS", 0xFFA855F7.toInt(), null, EnemyType.BOSS_KOMPLEX)
    )

    fun createLevel(planetIndex: Int): LevelData {
        val info = planetInfos.getOrElse(planetIndex) { planetInfos[0] }
        val width = 120
        val height = 16
        val map = Array(height) { IntArray(width) { Tile.EMPTY } }
        val enemySpawns = mutableListOf<EnemySpawn>()
        val pickupSpawns = mutableListOf<PickupSpawn>()

        // Base ground
        for (x in 0 until width) {
            map[height - 1][x] = Tile.SOLID
            map[height - 2][x] = Tile.SOLID
        }

        val hazardType = when (planetIndex) {
            0 -> Tile.ACID
            1 -> Tile.LAVA
            else -> Tile.SPIKE
        }

        // Generate platforms, pits, ladders, destructible blocks, hazards
        for (x in 8 until width - 15 step 12) {
            // Hazard pits
            map[height - 1][x] = hazardType
            map[height - 1][x + 1] = hazardType
            map[height - 2][x] = hazardType
            map[height - 2][x + 1] = hazardType

            // Floating platforms above pit
            map[height - 6][x - 1] = Tile.PLATFORM
            map[height - 6][x] = Tile.PLATFORM
            map[height - 6][x + 1] = Tile.PLATFORM

            // Solid obstacle blocks & destructibles
            map[height - 3][x + 4] = Tile.SOLID
            map[height - 4][x + 4] = Tile.DESTRUCTIBLE
            map[height - 5][x + 4] = Tile.SOLID

            // Ladders
            map[height - 3][x + 8] = Tile.LADDER
            map[height - 4][x + 8] = Tile.LADDER
            map[height - 5][x + 8] = Tile.LADDER
            map[height - 6][x + 8] = Tile.LADDER

            // Higher ledge
            map[height - 7][x + 6] = Tile.SOLID
            map[height - 7][x + 7] = Tile.SOLID
            map[height - 7][x + 8] = Tile.SOLID

            // Pickups
            pickupSpawns.add(PickupSpawn(PickupType.ENERGY, (x + 7) * 48f, (height - 8) * 48f))
            if (x % 24 == 0) {
                pickupSpawns.add(PickupSpawn(PickupType.HEALTH, (x + 4) * 48f, (height - 6) * 48f))
            }
        }

        // Enemy spawns along the map
        for (x in 15 until width - 20 step 10) {
            val enemyType = when ((x / 10) % 4) {
                0 -> EnemyType.TOAD_SOLDIER
                1 -> EnemyType.FLYING_COPTER
                2 -> EnemyType.MECH_WALKER
                else -> EnemyType.LAVA_SLUG
            }
            enemySpawns.add(EnemySpawn(enemyType, x * 48f, (height - 4) * 48f))
        }

        // Boss arena at the end of stage
        val bossX = (width - 10) * 48f
        val bossY = (height - 5) * 48f
        enemySpawns.add(EnemySpawn(info.bossType, bossX, bossY))

        // Stage goal door
        map[height - 3][width - 3] = Tile.DOOR_GOAL
        map[height - 4][width - 3] = Tile.DOOR_GOAL

        return LevelData(
            planetInfo = info,
            widthTiles = width,
            heightTiles = height,
            tileSize = 48f,
            tiles = map,
            spawnX = 3 * 48f,
            spawnY = (height - 4) * 48f,
            enemySpawns = enemySpawns,
            pickupSpawns = pickupSpawns
        )
    }
}
