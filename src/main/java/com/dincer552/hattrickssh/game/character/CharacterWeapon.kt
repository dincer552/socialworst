package com.dincer552.hattrickssh.game.character

/** WeaponStats - Per-shot statistics for a weapon. */
data class WeaponStats(
    val damage: Int = 1,
    val speed: Float = 6.0f,
    val size: Float = 8.0f,
    val color: Int = 0xFFFFF078.toInt(), // Yellow energy bolt
    val glowColor: Int = 0xFFC89628.toInt(), // Orange glow
    val lifetime: Float = 2.0f,
    val canPierce: Boolean = false,
    val canBreakIce: Boolean = false
)

/** WeaponPattern - Describes how projectiles are fired. */
enum class WeaponPattern {
    SINGLE,      // Single projectile straight ahead
    SPREAD_THREE, // Three projectiles at different angles
    DOWNWARD_ARC, // Single projectile that arcs downward
    PIERCING      // Single projectile that passes through enemies
}

/** WeaponConfig - Complete weapon configuration for a character. */
data class WeaponConfig(
    val pattern: WeaponPattern,
    val stats: WeaponStats,
    val cooldownSeconds: Float = 0.15f,
    val shotsPerCharge: Int = 1
)

/** WeaponRegistry - Weapon configurations for each character type. */
object WeaponRegistry {
    private val configs: Map<CharacterType, WeaponConfig> = mapOf(
        CharacterType.BUCKY to WeaponConfig(
            pattern = WeaponPattern.SINGLE,
            stats = WeaponStats(
                damage = 1,
                speed = 6.0f,
                size = 8.0f,
                color = 0xFFFFF078.toInt(),
                glowColor = 0xFFC89628.toInt(),
                lifetime = 2.0f,
                canPierce = false,
                canBreakIce = false
            ),
            cooldownSeconds = 0.15f
        ),
        CharacterType.BLINKY to WeaponConfig(
            pattern = WeaponPattern.DOWNWARD_ARC,
            stats = WeaponStats(
                damage = 2,
                speed = 4.0f,
                size = 10.0f,
                color = 0xFFFF8800.toInt(), // Orange
                glowColor = 0xFFFF4400.toInt(),
                lifetime = 1.5f,
                canPierce = false,
                canBreakIce = true
            ),
            cooldownSeconds = 0.35f
        ),
        CharacterType.DEAD_EYE to WeaponConfig(
            pattern = WeaponPattern.SPREAD_THREE,
            stats = WeaponStats(
                damage = 1,
                speed = 5.0f,
                size = 6.0f,
                color = 0xFFFFFF00.toInt(), // Yellow
                glowColor = 0xFFFFDD00.toInt(),
                lifetime = 1.8f,
                canPierce = false,
                canBreakIce = false
            ),
            cooldownSeconds = 0.4f
        ),
        CharacterType.JENNY to WeaponConfig(
            pattern = WeaponPattern.SINGLE,
            stats = WeaponStats(
                damage = 1,
                speed = 8.0f,
                size = 6.0f,
                color = 0xFF00FFFF.toInt(), // Cyan/blue
                glowColor = 0xFF88FFFF.toInt(),
                lifetime = 2.5f,
                canPierce = false,
                canBreakIce = false
            ),
            cooldownSeconds = 0.2f
        ),
        CharacterType.WILLY to WeaponConfig(
            pattern = WeaponPattern.PIERCING,
            stats = WeaponStats(
                damage = 2,
                speed = 7.0f,
                size = 6.0f,
                color = 0xFFFF00FF.toInt(), // Magenta
                glowColor = 0xFFFF66FF.toInt(),
                lifetime = 3.0f,
                canPierce = true,
                canBreakIce = false
            ),
            cooldownSeconds = 0.25f
        )
    )

    fun get(type: CharacterType): WeaponConfig = configs[type]
        ?: throw IllegalArgumentException("No weapon config for $type")
}

/** ProjectileData - Data for a fired projectile. */
data class ProjectileData(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val weaponType: WeaponType,
    val damage: Int,
    val size: Float,
    val color: Int,
    val glowColor: Int,
    val lifetime: Float,
    val canPierce: Boolean,
    val canBreakIce: Boolean,
    val ownerType: CharacterType
)

/** WeaponFactory - Creates projectile data for weapon firing. */
object WeaponFactory {

    /** Create projectiles for a weapon shot.
     * Returns a list of projectile data, one per projectile in the pattern.
     */
    fun createProjectiles(
        config: WeaponConfig,
        startX: Float,
        startY: Float,
        facingRight: Boolean
    ): List<ProjectileData> {
        val dir = if (facingRight) 1 else -1
        val stats = config.stats
        val projectiles = mutableListOf<ProjectileData>()

        when (config.pattern) {
            WeaponPattern.SINGLE -> {
                projectiles.add(ProjectileData(
                    x = startX + (if (facingRight) 16f else -8f),
                    y = startY + 8f,
                    vx = dir * stats.speed,
                    vy = 0f,
                    weaponType = config.stats.color.let { WeaponType.STRAIGHT }, // Fallback
                    damage = stats.damage,
                    size = stats.size,
                    color = stats.color,
                    glowColor = stats.glowColor,
                    lifetime = stats.lifetime,
                    canPierce = stats.canPierce,
                    canBreakIce = stats.canBreakIce,
                    ownerType = CharacterType.BUCKY // Will be overridden by caller
                ))
            }

            WeaponPattern.SPREAD_THREE -> {
                val angles = listOf(-0.3f, 0f, 0.3f) // Spread angles in radians
                for (angle in angles) {
                    val vx = dir * stats.speed * Math.cos(angle.toDouble()).toFloat()
                    val vy = -stats.speed * Math.sin(angle.toDouble()).toFloat()
                    projectiles.add(ProjectileData(
                        x = startX + (if (facingRight) 16f else -8f),
                        y = startY + 6f,
                        vx = vx,
                        vy = vy,
                        weaponType = WeaponType.SPREAD_THREE,
                        damage = stats.damage,
                        size = stats.size,
                        color = stats.color,
                        glowColor = stats.glowColor,
                        lifetime = stats.lifetime,
                        canPierce = stats.canPierce,
                        canBreakIce = stats.canBreakIce,
                        ownerType = CharacterType.DEAD_EYE
                    ))
                }
            }

            WeaponPattern.DOWNWARD_ARC -> {
                // Arc downward from the character
                val arcX = if (facingRight) 14f else -6f
                projectiles.add(ProjectileData(
                    x = startX + arcX,
                    y = startY + 10f,
                    vx = dir * stats.speed * 0.7f,
                    vy = 2.0f, // Start moving downward
                    weaponType = WeaponType.DOWNWARD_ARC,
                    damage = stats.damage,
                    size = stats.size,
                    color = stats.color,
                    glowColor = stats.glowColor,
                    lifetime = stats.lifetime,
                    canPierce = stats.canPierce,
                    canBreakIce = stats.canBreakIce,
                    ownerType = CharacterType.BLINKY
                ))
            }

            WeaponPattern.PIERCING -> {
                projectiles.add(ProjectileData(
                    x = startX + (if (facingRight) 16f else -8f),
                    y = startY + 8f,
                    vx = dir * stats.speed,
                    vy = 0f,
                    weaponType = WeaponType.PIERCING,
                    damage = stats.damage,
                    size = stats.size,
                    color = stats.color,
                    glowColor = stats.glowColor,
                    lifetime = stats.lifetime,
                    canPierce = true,
                    canBreakIce = false,
                    ownerType = CharacterType.WILLY
                ))
            }
        }

        return projectiles
    }
}
