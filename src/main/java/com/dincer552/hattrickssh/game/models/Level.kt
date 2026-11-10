package com.dincer552.hattrickssh.game.models

class Tile(
    val type: Int,
    val x: Int,
    val y: Int
) {
    var isDestroyed: Boolean = false

    companion object {
        const val EMPTY = 0
        const val SOLID = 1
        const val PLATFORM = 2
        const val LADDER = 3
        const val SPIKE = 4
        const val LAVA = 5
        const val ACID = 6
        const val DESTRUCTIBLE = 7
        const val DOOR_GOAL = 8
    }
}

data class PlanetInfo(
    val index: Int,
    val name: String,
    val subtitle: String,
    val themeColor: Int,
    val rescuedChar: BuckyCharacter?,
    val bossType: EnemyType
)

class LevelData(
    val planetInfo: PlanetInfo,
    val widthTiles: Int,
    val heightTiles: Int,
    val tileSize: Float = 48f,
    val tiles: Array<IntArray>,
    val spawnX: Float,
    val spawnY: Float,
    val enemySpawns: List<EnemySpawn>,
    val pickupSpawns: List<PickupSpawn>
)

data class EnemySpawn(val type: EnemyType, val x: Float, val y: Float)
data class PickupSpawn(val type: PickupType, val x: Float, val y: Float)
