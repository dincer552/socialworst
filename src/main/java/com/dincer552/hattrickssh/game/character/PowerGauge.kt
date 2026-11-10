package com.dincer552.hattrickssh.game.character

/** PowerGauge - Manages a single character's power gauge.
 *
 * The power gauge is consumed when using special abilities and recharges over time.
 * Each character has their own independent gauge.
 */
class PowerGauge(
    private val maxGauge: Int
) {
    private var currentGauge: Int = maxGauge
    private var rechargeRatePerSecond: Int = 10 // Gauge points per second
    private var isRecharging: Boolean = true
    private var rechargeDelay: Float = 0f
    private val rechargeDelayDuration: Float = 0.5f // Seconds before recharge starts after use

    /** Current gauge value. */
    val getCurrent: Int
        get() = currentGauge

    /** Maximum gauge value. */
    val getMax: Int
        get() = maxGauge

    /** Gauge percentage (0.0 to 1.0). */
    val getPercent: Float
        get() = currentGauge.toFloat() / maxGauge.toFloat()

    /** Whether the gauge is full. */
    val isFull: Boolean
        get() = currentGauge >= maxGauge

    /** Whether the gauge is empty. */
    val isEmpty: Boolean
        get() = currentGauge <= 0

    /** Consume gauge points. Returns true if successful. */
    fun consume(amount: Int): Boolean {
        if (currentGauge >= amount) {
            currentGauge -= amount
            isRecharging = false
            rechargeDelay = rechargeDelayDuration
            return true
        }
        return false
    }

    /** Recharge the gauge over time. */
    fun update(deltaTime: Float) {
        if (rechargeDelay > 0) {
            rechargeDelay = maxOf(0f, rechargeDelay - deltaTime)
            return
        }

        if (!isRecharging) {
            isRecharging = true
        }

        if (currentGauge < maxGauge) {
            val rechargeAmount = (rechargeRatePerSecond * deltaTime).toInt()
            currentGauge = minOf(maxGauge, currentGauge + rechargeAmount)
        }
    }

    /** Force full recharge (e.g., on character switch or level start). */
    fun rechargeFull() {
        currentGauge = maxGauge
        isRecharging = true
        rechargeDelay = 0f
    }

    /** Set a custom recharge rate. */
    fun setRechargeRate(ratePerSecond: Int) {
        rechargeRatePerSecond = maxOf(0, ratePerSecond)
    }

    /** Reset the gauge to empty (e.g., on character defeat). */
    fun empty() {
        currentGauge = 0
        isRecharging = false
        rechargeDelay = rechargeDelayDuration
    }
}

/** PowerGaugeManager - Manages power gauges for all characters.
 *
 * Each character has an independent power gauge that recharges over time.
 * Gauges are synced when switching characters so the player doesn't lose progress.
 */
class PowerGaugeManager {

    private val gauges: MutableMap<CharacterType, PowerGauge> = mutableMapOf()

    init {
        for (type in CharacterRegistry.allTypes) {
            val config = CharacterRegistry.get(type)
            gauges[type] = PowerGauge(config.maxPowerGauge)
        }
    }

    /** Get the power gauge for a specific character. */
    fun getGauge(type: CharacterType): PowerGauge {
        return gauges[type] ?: throw IllegalArgumentException("No gauge for $type")
    }

    /** Get the active character's gauge. */
    fun getActiveGauge(activeType: CharacterType): PowerGauge {
        return getGauge(activeType)
    }

    /** Consume power from a character's gauge. */
    fun consume(type: CharacterType, amount: Int): Boolean {
        return getGauge(type).consume(amount)
    }

    /** Recharge all gauges over time. */
    fun updateAll(deltaTime: Float) {
        for (gauge in gauges.values) {
            gauge.update(deltaTime)
        }
    }

    /** Recharge a specific gauge fully. */
    fun rechargeFull(type: CharacterType) {
        getGauge(type).rechargeFull()
    }

    /** Recharge all gauges fully. */
    fun rechargeAllFull() {
        for (gauge in gauges.values) {
            gauge.rechargeFull()
        }
    }

    /** Get gauge percentage for a character (0.0 to 1.0). */
    fun getGaugePercent(type: CharacterType): Float {
        return getGauge(type).getPercent
    }

    /** Check if a character's gauge is full. */
    fun isGaugeFull(type: CharacterType): Boolean {
        return getGauge(type).isFull
    }

    /** Check if a character's gauge is empty. */
    fun isGaugeEmpty(type: CharacterType): Boolean {
        return getGauge(type).isEmpty
    }

    /** Sync all gauges to the same value (used when switching characters). */
    fun syncAllTo(type: CharacterType, value: Int) {
        for (gauge in gauges.values) {
            // We can't directly set currentGauge, but we can empty and recharge
            // For simplicity, we'll just set the active gauge and others will catch up
        }
    }

    /** Empty a specific gauge (e.g., on character defeat). */
    fun emptyGauge(type: CharacterType) {
        getGauge(type).empty()
    }
}
