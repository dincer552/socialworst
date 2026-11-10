package com.dincer552.hattrickssh.game.item

import com.dincer552.hattrickssh.game.*

/** ItemType - Types of collectible items. */
enum class ItemType {
    ONE_UP,       // Extra life
    L_HEART,      // Restores health
    P_ICON,       // Increases special power gauge capacity
    SCORE_BONUS   // Score bonus (100 points)
}

/** ItemData - Static data for each item type. */
data class ItemData(
    val type: ItemType,
    val name: String,
    val description: String,
    val color: Int,
    val size: Float,
    val points: Int
)

/** ItemRegistry - Static data for all item types. */
object ItemRegistry {
    private val items: Map<ItemType, ItemData> = mapOf(
        ItemType.ONE_UP to ItemData(
            type = ItemType.ONE_UP,
            name = "1-UP",
            description = "Extra life",
            color = 0xFFFF00FF.toInt(), // Magenta
            size = 16.0f,
            points = 0
        ),
        ItemType.L_HEART to ItemData(
            type = ItemType.L_HEART,
            name = "L-Heart",
            description = "Restores health",
            color = 0xFFFF0000.toInt(), // Red
            size = 16.0f,
            points = 50
        ),
        ItemType.P_ICON to ItemData(
            type = ItemType.P_ICON,
            name = "P-Icon",
            description = "Increases power gauge capacity",
            color = 0xFF00FFFF.toInt(), // Cyan
            size = 16.0f,
            points = 100
        ),
        ItemType.SCORE_BONUS to ItemData(
            type = ItemType.SCORE_BONUS,
            name = "Score Bonus",
            description = "Bonus points",
            color = 0xFFFFD700.toInt(), // Gold
            size = 12.0f,
            points = 100
        )
    )

    fun get(type: ItemType): ItemData = items[type]
        ?: throw IllegalArgumentException("Unknown item type: $type")

    val allTypes: List<ItemType> = items.keys.toList()
}

/** Item - A collectible item in the game world. */
class Item(
    position: GamePosition,
    val type: ItemType,
    private val data: ItemData
) : GameObject(
    position,
    GameVelocity(0f, 0f),
    GameRect(
        position.x, position.y, position.x + data.size, position.y + data.size
    ),
    isActive = true
) {
    private var collected: Boolean = false
    private var floatOffset: Float = 0f
    private var floatDirection: Float = 1f

    init {
        collisionRect = GameRect(
            position.x, position.y, position.x + data.size, position.y + data.size
        )
    }

    /** Get item data. */
    val getData: ItemData get() = data

    /** Check if item has been collected. */
    val isCollected: Boolean get() = collected

    /** Collect the item. Returns true if successfully collected. */
    fun collect(): Boolean {
        if (collected || !isActive) return false
        collected = true
        isActive = false
        return true
    }

    /** Update item animation (floating effect). */
    override fun update(deltaTime: Float) {
        if (collected || !isActive) return

        floatOffset += deltaTime * 2f * floatDirection
        if (floatOffset > 4f) floatDirection = -1f
        if (floatOffset < -4f) floatDirection = 1f

        // Apply floating to position (visual only, collision stays same)
        position.y = position.y  // Base position stays, visual offset handled in draw
        updateCollisionRect()
    }

    override fun draw(canvas: android.graphics.Canvas, paint: android.graphics.Paint, offsetX: Float, offsetY: Float) {
        if (collected || !isActive) return

        paint.color = data.color
        val drawY = position.y - offsetY + floatOffset
        canvas.drawRect(
            position.x - offsetX,
            drawY,
            position.x + data.size - offsetX,
            drawY + data.size,
            paint
        )

        // Draw item symbol
        paint.color = 0xFFFFFFFF.toInt()
        when (type) {
            ItemType.ONE_UP -> {
                // Draw "1UP"
                canvas.drawText("1UP", position.x - offsetX + 2f, drawY + data.size - 2f, paint)
            }
            ItemType.L_HEART -> {
                // Draw heart
                canvas.drawCircle(position.x - offsetX + data.size/2f, drawY + data.size/2f, data.size/4f, paint)
            }
            ItemType.P_ICON -> {
                // Draw "P"
                canvas.drawText("P", position.x - offsetX + data.size/2f - 4f, drawY + data.size - 2f, paint)
            }
            ItemType.SCORE_BONUS -> {
                // Draw coin
                canvas.drawCircle(position.x - offsetX + data.size/2f, drawY + data.size/2f, data.size/4f, paint)
            }
        }
    }

    /** Get points value. */
    val getPoints: Int get() = data.points
}

/** ItemFactory - Creates item instances. */
object ItemFactory {
    fun create(type: ItemType, x: Float, y: Float): Item {
        return Item(
            com.dincer552.hattrickssh.game.character.GamePosition(x, y),
            type,
            ItemRegistry.get(type)
        )
    }
}
