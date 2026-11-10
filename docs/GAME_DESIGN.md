# Bucky O'Hare Android Game — Master Design Document

**Repository**: https://github.com/dincer552/socialworst  
**Branch**: main  
**Package**: `com.dincer552.hattrickssh` (game sub-package: `com.dincer552.hattrickssh.game`)  
**Document status**: Master design — all subsequent development phases reference this document

---

## 1. Project Overview

Bucky O'Hare is an Android platform game inspired by the 1992 NES title *Bucky O'Hare*. The player controls a crew of anthropomorphic space adventurers who must rescue their captured teammates across four planets, then assault the Toad Mothership to escape.

**Core philosophy**: Faithful recreation of the original NES gameplay structure — planet selection with character-ability dependencies, five distinct playable characters with real gameplay consequences, character switching during gameplay, four distinct planet environments with multi-act levels, boss encounters tied to rescues, and a mandatory second half aboard the Toad Mothership.

**Visual style**: Original pixel art in the NES 8-bit aesthetic. No ROM graphics, sprite sheets, or copyrighted audio are copied. All assets are independently created.

**Existing infrastructure preserved**: The Android SSH client (`com.dincer552.hattrickssh`) remains fully functional. The game is a separate module within the same single-root Gradle application. Application ID, signing configuration, SSH/Cloudflare functionality, and credential storage are untouched.

---

## 2. Game Structure

### Phase 1 — Four-Planet Rescue

The game begins on a **planet selection screen**. Four planets are available, each with a captured crew member:

| Planet | Crew Member Rescued | Location |
|--------|--------------------|----------|
| Green Planet | Blinky | Green Planet |
| Red Planet | Dead-Eye Duck | Red Planet |
| Blue Planet | Jenny | Blue Planet |
| Yellow Planet | Willy DuWitt | Yellow Planet |

**Dependency rule**: The Blue Planet requires Blinky because Blinky's weapon destroys ice blocks that block the route. The player cannot complete the Blue Planet without first rescuing Blinky from the Green Planet.

The player may choose planets in almost any order, subject to this dependency. Each planet contains multiple acts with distinct layouts and challenges.

### Phase 2 — Toad Mothership (Mandatory Second Half)

After all four crew members are rescued, the story continues:

1. **Cell** — Bucky and Blinky escape from their cell
2. **Salvage Chute** — Progress through the ship's salvage system
3. **Center of Magma Tanker** — Navigate the magma tanker core
4. **Escape!** — Control the spacecraft through the collapsing Toad Mothership

During the mothership stages, the player must rescue the other captured crew members again. The Escape section includes spacecraft control sequences.

---

## 3. Playable Characters (5)

Each character has unique weapons, special abilities, animations, and gameplay behavior. Characters are NOT cosmetic skins — their abilities have real gameplay consequences.

| Character | Unlock | Weapon | Special Ability (Charge + Release) |
|-----------|--------|--------|-----------------------------------|
| **Bucky O'Hare** | Starting character | Standard straight-firing blaster | Charged high jump |
| **A.F.C. Blinky** | Green Planet rescue | Short-range downward-arc shot (breaks ice/stone blocks) | Jetpack flight |
| **Dead-Eye Duck** | Red Planet rescue | Three-direction spread shot | Wall climbing |
| **Jenny** | Blue Planet rescue | Blue laser | Controllable guided energy orb (player directs orb while it travels) |
| **Willy DuWitt** | Yellow Planet rescue | Powerful piercing laser | Increased laser power (movement restriction while charging) |

### Character Switching

Once rescued, a crew member becomes playable. The player can switch between unlocked characters during gameplay using a dedicated character-switch control.

On Android: a clear touch-friendly character-switching interface.

**Preserved across all characters**:
- Shared team health (single health pool)
- Character-specific power gauges
- Individual weapon behavior
- Individual special abilities
- Unlock status

### Charged Special Ability Mechanic

Original NES control behavior: Hold B to charge, release B to activate. This is implemented on Android as a touch charge-and-release mechanic — not replaced with unrelated generic mobile-game buttons.

---

## 4. Planets and Environments

Each planet has a distinct visual identity, hazards, and level layout. Multi-act planets with both horizontal and vertical scrolling sections.

### Green Planet
- **Theme**: Forests, giant trees, waterfalls
- **Platforms**: Branch platforms
- **Hazards**: Insects and environmental creatures
- **Rescue**: Blinky

### Red Planet
- **Theme**: Volcanoes, lava pits
- **Hazards**: Fireballs, rock platforms, volcanic hazards
- **Rescue**: Dead-Eye Duck

### Blue Planet
- **Theme**: Ice caves, slippery surfaces
- **Hazards**: Ice blocks (breakable by Blinky's weapon), robotic snakes, water hazards
- **Rescue**: Jenny (requires Blinky to access)

### Yellow Planet
- **Theme**: Futuristic space station
- **Hazards**: Laser cannons, floating asteroids, space hazards
- **Rescue**: Willy DuWitt

---

## 5. Bosses and Rescue Progression

Each planet contains boss encounters. Defeating a planet boss triggers the corresponding crew rescue.

**Rescue sequence**:
1. Show character rescue animation
2. Unlock the character
3. Display short character introduction
4. Return to planet selection screen
5. Allow the player to use the newly unlocked character

---

## 6. Items and Progression

| Item | Effect |
|------|--------|
| **1-UP** | Extra life |
| **L-heart** | Restores health |
| **P-icon** | Increases special power gauge capacity |
| **Score bonus** | Points |

**Shared health system**: One health pool shared across all characters.

**Character-specific power gauges**: Each character has their own power gauge for special abilities.

---

## 7. Control Scheme (NES → Android Adaptation)

| NES Control | Android Touch Equivalent |
|-------------|-------------------------|
| D-pad: movement | Left-side virtual D-pad |
| A: jump | Jump button (right side) |
| B: fire | Fire button (right side) |
| Hold B: charge special | Touch-and-hold charge, release to activate |
| Release B: activate charged ability | Touch release triggers charged ability |
| Select: switch character | Dedicated character-switch button/interface |
| Start: pause | Pause button |
| Down + A: drop through platform | Down + Jump |
| Up + B: fire upward | Up + Fire |

The charge-and-release mechanic is preserved as a core interaction — not replaced with unrelated generic mobile-game buttons.

---

## 8. Current Implementation Status (Phase 5 Complete)

### What Exists (Phase 5 — Complete)

| System | Status | Details |
|--------|--------|---------|
| Single-player platformer | Implemented | One character (Bucky-like hero), basic animations |
| Sprite sheet loading | Implemented | 69 PNG assets, 32x32 frame size, master sprite sheet 256x160 |
| Animation system | Implemented | 14 animations, fixed-timestep playback |
| Physics | Implemented | Gravity, walk, run, jump, fall, land, ground friction |
| Platform collision | Implemented | AABB detection, landing detection, conveyor belts |
| Shooting | Implemented | Projectiles with cooldown, lifetime, target destruction |
| Camera | Implemented | Smooth follow centered on player |
| Touch controls | Implemented | D-pad, Jump, Fire buttons (bottom of screen) |
| HUD | Implemented | Score, lives/health, health bar above player |
| Game loop | Implemented | SurfaceView render thread, 60 FPS fixed timestep |
| AndroidManifest | Updated | GameActivity registered with landscape orientation |

### What Is Missing (Phases 6+)

| System | Status | Required For |
|--------|--------|--------------|
| Five playable characters | NOT IMPLEMENTED | Only Bucky-like hero exists |
| Character switching | NOT IMPLEMENTED | No character select/switch UI |
| Character-specific power gauges | NOT IMPLEMENTED | Single health only |
| Charge-and-release special abilities | NOT IMPLEMENTED | No charge mechanic |
| Four planets with distinct themes | NOT IMPLEMENTED | Generic platforms only |
| Multi-act planet levels | NOT IMPLEMENTED | Single level layout |
| Boss encounters | NOT IMPLEMENTED | No bosses |
| Crew rescue sequence | NOT IMPLEMENTED | No rescue logic |
| Planet selection screen | NOT IMPLEMENTED | No planet menu |
| Toad Mothership second half | NOT IMPLEMENTED | No Cell, Salvage Chute, Magma Tanker, Escape |
| Ice blocks (breakable by Blinky) | NOT IMPLEMENTED | No breakable terrain |
| Items (1-UP, L-heart, P-icon) | NOT IMPLEMENTED | No collectibles |
| Character-ability dependencies | NOT IMPLEMENTED | Blinky required for Blue Planet |
| Spacecraft escape sequence | NOT IMPLEMENTED | No spacecraft control |

---

## 9. Development Roadmap

| Phase | Component | Description | Status |
|-------|-----------|-------------|--------|
| 0 | Project setup | Single-root Android app, app ID `com.dincer552.hattrickssh` | ✅ Complete |
| 1 | Project analysis | SSH client audit, existing code review | ✅ Complete |
| 2 | Game infrastructure | GameActivity, GameView, GameEngine, GameRenderer, GameInput, GameState, GameConfig | ✅ Complete |
| 3 | Visual reference analysis | NES disassembly research, mechanics documentation | ✅ Complete |
| 4 | Pixel art asset production | 69 PNG files, sprite sheets, character concepts, weapons, effects | ✅ Complete |
| 5 | Core gameplay mechanics | Sprite sheet animation, physics, jumping, shooting, collision, touch controls, camera | ✅ Complete |
| 6 | Multi-character system | Five playable characters with unique weapons/specials, character switching, shared health, power gauges | ⏳ Next |
| 7 | Planet system | Four planets (Green, Red, Blue, Yellow), multi-act levels, distinct environments | ⏳ Planned |
| 8 | Boss and rescue system | Planet bosses, crew rescue sequences, character unlock, return to planet selection | ⏳ Planned |
| 9 | Toad Mothership | Cell, Salvage Chute, Magma Tanker, Escape stages, spacecraft control | ⏳ Planned |
| 10 | Items and progression | 1-UP, L-heart, P-icon, score bonuses | ⏳ Planned |
| 11 | Android integration | Character switch UI, charge-and-release touch controls, main screen integration | ⏳ Planned |
| 12 | Polish and QA | Visual QA against NES references, bug fixes, balance tuning | ⏳ Planned |

---

## 9. Differences Between Current Implementation and Required Structure

| Requirement | Current Implementation | Gap |
|-------------|----------------------|-----|
| Five playable characters | One character (Bucky-like hero) | Need 4 more characters with unique weapons/specials |
| Character switching | Not implemented | Need character select UI and runtime switching |
| Shared team health + character power gauges | Single health only | Need per-character power gauges |
| Charge-and-release special abilities | Not implemented | Need hold-to-charge, release-to-activate mechanic |
| Four distinct planets | Generic platforms only | Need Green, Red, Blue, Yellow planet themes |
| Multi-act planet levels | Single level | Need multiple acts per planet |
| Boss encounters | None | Need bosses per planet |
| Crew rescue sequence | None | Need rescue animation, unlock, introduction |
| Planet selection screen | None | Need planet menu with dependency tracking |
| Toad Mothership second half | None | Need Cell, Salvage Chute, Magma Tanker, Escape |
| Ice blocks breakable by Blinky | None | Need breakable terrain |
| Items (1-UP, L-heart, P-icon) | None | Need collectible system |
| Character-ability dependencies | None | Blinky required for Blue Planet access |
| Spacecraft escape | None | Need spacecraft control sequence |
| Android character-switch UI | None | Need touch-friendly switch interface |

---

## 10. Project Protection (Invariant)

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
- `AndroidManifest.xml` SSH activities

The game module is additive only: new package `com.dincer552.hattrickssh.game`, new Activity `GameActivity`, new assets under `assets/game/`.

---

## 11. Remaining Work Summary

**Immediate next step (Phase 6)**: Implement multi-character system — five playable characters with unique weapons and special abilities, character switching UI, shared health, character-specific power gauges, charge-and-release mechanic.

**Major milestones remaining**:
1. Phase 6: Multi-character system
2. Phase 7: Four planets with multi-act levels
3. Phase 8: Bosses and crew rescue progression
4. Phase 9: Toad Mothership second half
5. Phase 10: Items and progression system
6. Phase 11: Android integration and main screen access
7. Phase 12: Polish and QA

**No Phase 6 work has been started yet.** This document serves as the specification for all remaining development.
