# Bucky O'Hare Game Development Roadmap

**Repository**: https://github.com/dincer552/socialworst  
**Branch**: main  
**Document**: Development roadmap — all game development phases

---

## Phase Status Summary

| Phase | Component | Status |
|-------|-----------|--------|
| 0 | Project setup | ✅ Complete |
| 1 | Project analysis | ✅ Complete |
| 2 | Game infrastructure | ✅ Complete |
| 3 | Visual reference analysis | ✅ Complete |
| 4 | Pixel art asset production | ✅ Complete |
| 5 | Core gameplay mechanics | ✅ Complete |
| 6 | Multi-character system | ⏳ Next |
| 7 | Planet system | ⏳ Planned |
| 8 | Bosses and crew rescue | ⏳ Planned |
| 9 | Toad Mothership second half | ⏳ Planned |
| 10 | Items and progression | ⏳ Planned |
| 11 | Android integration | ⏳ Planned |
| 12 | Polish and QA | ⏳ Planned |

---

## Phase 6: Multi-Character System (Immediate Next Step)

**Goal**: Implement five distinct playable characters with unique weapons and special abilities, character switching, shared health, character-specific power gauges, and charge-and-release mechanics.

### Deliverables

1. **Character data model** — Character class with weapon type, special ability type, power gauge, unlock status
2. **Five characters**:
   - Bucky O'Hare: straight shot, charged high jump
   - Blinky: downward-arc ice-breaking shot, jetpack flight
   - Dead-Eye: three-direction spread shot, wall climbing
   - Jenny: blue laser, controllable guided orb
   - Willy: piercing laser, increased laser power (movement restriction while charging)
3. **Character switching UI** — Touch-friendly Android interface
4. **Charge-and-release mechanic** — Hold touch to charge, release to activate
5. **Shared team health** — Single health pool across all characters
6. **Character-specific power gauges** — Each character tracks their own special power

### Files to Create

- `src/main/java/com/dincer552/hattrickssh/game/character/Character.kt` — Character base class and data
- `src/main/java/com/dincer552/hattrickssh/game/character/CharacterManager.kt` — Unlock status, switching logic
- `src/main/java/com/dincer552/hattrickssh/game/character/CharacterWeapon.kt` — Weapon behavior per character
- `src/main/java/com/dincer552/hattrickssh/game/character/CharacterSpecial.kt` — Special ability behavior per character
- `src/main/java/com/dincer552/hattrickssh/game/CharacterSwitchUI.kt` — Android touch interface
- `src/main/java/com/dincer552/hattrickssh/game/ChargeInput.kt` — Hold-to-charge, release-to-activate input

### Verification

- All five characters have distinct weapons with different gameplay behavior
- Character switching works during gameplay
- Charge-and-release functions correctly on touch
- Shared health and per-character power gauges function

---

## Phase 7: Planet System

**Goal**: Four distinct planets with multi-act levels, distinct environments, and planet selection with ability dependencies.

### Deliverables

1. **Planet data model** — Planet class with theme, acts, hazards, rescue target
2. **Four planets**:
   - Green Planet: forests, giant trees, waterfalls, branch platforms, insects
   - Red Planet: volcanoes, lava pits, fireballs, rock platforms
   - Blue Planet: ice caves, slippery surfaces, ice blocks (breakable by Blinky), robotic snakes
   - Yellow Planet: laser cannons, floating asteroids, space hazards, futuristic platforms
3. **Planet selection screen** — With dependency tracking (Blue Planet requires Blinky)
4. **Multi-act levels** — Each planet has multiple acts with different layouts
5. **Horizontal and vertical scrolling sections**

### Files to Create

- `src/main/java/com/dincer552/hattrickssh/game/planet/Planet.kt` — Planet data model
- `src/main/java/com/dincer552/hattrickssh/game/planet/PlanetSelectionUI.kt` — Planet selection screen
- `src/main/java/com/dincer552/hattrickssh/game/level/Level.kt` — Level data and layout
- `src/main/java/com/dincer552/hattrickssh/game/level/LevelLoader.kt` — Level loading from assets

### Verification

- Four planets are visually distinct (not recolored copies)
- Blue Planet requires Blinky (ice blocks block route without him)
- Planet selection screen functions correctly

---

## Phase 8: Bosses and Crew Rescue

**Goal**: Planet bosses, rescue sequences, character unlock, and return to planet selection.

### Deliverables

1. **Boss data model** — Boss class with health, patterns, phases
2. **Four planet bosses** — One per planet
3. **Rescue sequence** — Animation, character unlock, introduction display
4. **Return to planet selection** — After rescue, player returns to selection screen
5. **Character-ability dependency** — Blinky required for Blue Planet ice blocks

### Files to Create

- `src/main/java/com/dincer552/hattrickssh/game/boss/Boss.kt` — Boss base class
- `src/main/java/com/dincer552/hattrickssh/game/boss/PlanetBoss.kt` — Per-planet boss implementations
- `src/main/java/com/dincer552/hattrickssh/game/rescue/RescueSequence.kt` — Rescue animation and unlock logic

### Verification

- Defeating a planet boss triggers the corresponding crew rescue
- Rescue sequence plays correctly
- Newly unlocked character is playable immediately
- Return to planet selection works correctly

---

## Phase 9: Toad Mothership Second Half

**Goal**: Mandatory second half — Cell, Salvage Chute, Center of Magma Tanker, Escape!

### Deliverables

1. **Cell stage** — Bucky and Blinky escape from cell
2. **Salvage Chute stage** — Progress through salvage system
3. **Center of Magma Tanker stage** — Navigate magma tanker core
4. **Escape! stage** — Spacecraft control through collapsing mothership
5. **Rescue other crew members** during mothership stages
6. **Boss encounters** in mothership stages
7. **Final escape sequence**

### Files to Create

- `src/main/java/com/dincer552/hattrickssh/game/mothership/MothershipLevel.kt` — Mothership level data
- `src/main/java/com/dincer552/hattrickssh/game/mothership/MothershipBoss.kt` — Mothership bosses
- `src/main/java/com/dincer552/hattrickssh/game/mothership/SpacecraftControl.kt` — Escape spacecraft control

### Verification

- All four mothership stages are playable
- Other crew members are rescued during mothership stages
- Escape sequence includes spacecraft control
- Game does not end after four planets

---

## Phase 10: Items and Progression

**Goal**: Original-style items and progression system.

### Deliverables

1. **1-UP item** — Extra life
2. **L-heart item** — Restores health
3. **P-icon item** — Increases special power gauge capacity
4. **Score bonus items** — Points
5. **Shared health system** — Single health pool across all characters
6. **Character-specific power gauges** — Each character tracks their own power

### Files to Create

- `src/main/java/com/dincer552/hattrickssh/game/item/Item.kt` — Item base class
- `src/main/java/com/dincer552/hattrickssh/game/item/ItemType.kt` — Item type enum
- `src/main/java/com/dincer552/hattrickssh/game/item/ItemManager.kt` — Item spawning and collection logic

### Verification

- All four item types are collectible
- Shared health system works across all characters
- Character-specific power gauges function independently

---

## Phase 11: Android Integration

**Goal**: Integrate game into main Android application with proper touch controls.

### Deliverables

1. **Character switch button/interface** on Android — Touch-friendly
2. **Charge-and-release touch controls** — Hold to charge, release to activate
3. **Game button on main SSH screen** — Access game from main app
4. **Main application integration** — GameActivity accessible from main app

### Files to Create

- `src/main/java/com/dincer552/hattrickssh/game/CharacterSwitchButton.kt` — Android switch UI
- `src/main/java/com/dincer552/hattrickssh/game/ChargeButton.kt` — Charge-and-release button
- **Modify**: `MainActivity.kt` — Add game button (Phase 8 of original task)

### Verification

- Game button appears on main SSH screen
- Character switching works on Android touch interface
- Charge-and-release works on touch

---

## Phase 12: Polish and QA

**Goal**: Visual QA, bug fixes, balance tuning.

### Deliverables

1. **Visual QA** — Compare Android screenshots with original NES references
2. **Bug fixes** — Fix all identified issues
3. **Balance tuning** — Adjust difficulty, damage, speed
4. **Device testing** — ARM64 Android device testing

### Files to Modify

- Various — based on QA findings

### Verification

- Game feels like original NES title
- All mechanics work correctly on device
- No crashes or critical bugs

---

## Project Protection (Invariant)

The following are NEVER modified by game development:

- `MainActivity.kt` — SSH client main activity
- `TerminalActivity.kt` — SSH terminal
- SSH connection logic (`ssh/` package)
- Cloudflare functionality (`cloudflare/` package)
- SSH profile storage and credential encryption (`security/` package)
- GitHub update functionality (`AppUpdateManager.kt`)
- APK signing configuration (`build.gradle.kts`)
- Application ID (`com.dincer552.hattrickssh`)
- Gradle architecture (single-root application)

The game module is additive only: new package `com.dincer552.hattrickssh.game`, new Activity `GameActivity`, new assets under `assets/game/`.

---

## Build and Test Instructions

**Local build** (requires Java 17 + Android SDK 36):

```bash
./gradlew assembleDebug
```

**GitHub Actions** — Push to `main` triggers `.github/workflows/build-apk.yml`:
- Java 17 (Temurin)
- Android SDK 36, Build Tools 36.0.0, NDK 26.1.10909125
- Gradle 8.11.1
- Builds debug APK and signed release APK (when signing secrets configured)

**Build cannot be executed in this sandbox** because Java 17/Android SDK is not installed.
