package com.dincer552.hattrickssh.game.models

enum class BuckyCharacter(
    val displayName: String,
    val role: String,
    val primaryColor: Int,
    val speedMultiplier: Float,
    val jumpMultiplier: Float,
    val specialName: String,
    val specialEnergyCost: Int,
    val description: String
) {
    BUCKY(
        displayName = "BUCKY O'HARE",
        role = "CAPTAIN",
        primaryColor = 0xFF22C55E.toInt(),
        speedMultiplier = 1.15f,
        jumpMultiplier = 1.2f,
        specialName = "SUPER JUMP & TRIPLE LASER",
        specialEnergyCost = 10,
        description = "High agility green rabbit with triple laser burst and super jump."
    ),
    JENNY(
        displayName = "JENNY",
        role = "FIRST MATE",
        primaryColor = 0xFFEC4899.toInt(),
        speedMultiplier = 1.0f,
        jumpMultiplier = 1.05f,
        specialName = "HOMING MAGIC ORB",
        specialEnergyCost = 15,
        description = "Aldebaran cat with telepathic homing magic spheres and levitation."
    ),
    WILLY(
        displayName = "WILLY DUWITT",
        role = "GENIUS ENGINEER",
        primaryColor = 0xFF3B82F6.toInt(),
        speedMultiplier = 0.95f,
        jumpMultiplier = 1.0f,
        specialName = "MEGA CHARGED BLASTER",
        specialEnergyCost = 20,
        description = "Human genius armed with ultra high-damage charged mega laser."
    ),
    DEAD_EYE(
        displayName = "DEAD-EYE DUCK",
        role = "GUNNER",
        primaryColor = 0xFFEAB308.toInt(),
        speedMultiplier = 1.05f,
        jumpMultiplier = 1.0f,
        specialName = "3-WAY SPREAD & WALL CLIMB",
        specialEnergyCost = 12,
        description = "Four-armed duck gunner who shoots 3-way spread lasers and climbs walls."
    ),
    BLINKY(
        displayName = "BLINKY",
        role = "ANDROID",
        primaryColor = 0xFFF97316.toInt(),
        speedMultiplier = 0.9f,
        jumpMultiplier = 0.9f,
        specialName = "JETPACK HOVER & BOMB",
        specialEnergyCost = 25,
        description = "One-eyed Android capable of flying with jetpack and launching block-destroying bombs."
    )
}
