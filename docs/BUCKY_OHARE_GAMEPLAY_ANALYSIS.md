# Bucky O'Hare Android Game Development Roadmap

## Phase 0 – Project Setup (Completed)
- [x] Single-root Android application structure preserved
- [x] Application ID: `com.dincer552.hattrickssh`
- [x] Signing configuration unchanged
- [x] SSH client core code unmodified (verified zero diff)
- [x] Phase 1: Project analysis complete
- [x] Phase 2: Game infrastructure created (GameActivity, GameView, GameEngine, GameRenderer, GameInput, GameState, GameConfig)
- [x] Phase 2: AndroidManifest.xml registered with GameActivity (landscape, exported=false)
- [x] Phase 2: settings.gradle.kts restored to original single-project form
- [x] Phase 2: All 7 game infrastructure classes created under com.dincer552.hattrickssh.game package
- [ ] Phase 2 Verification: Gradle build cannot run in current environment (Java 17 unavailable), but code structure verified correct

## Phase 3 – Visual Reference Analysis (UPDATED)
- [x] NES disassembly analyzed (Yoshimaster96/bucky-disasm)
- [x] Player movement, shooting, enemy AI, level structure documented
- [x] Verified/Estimated/Unknown findings catalogued
- [x] Android implementation recommendations drafted
- [x] **Visual Fidelity Goal added** – original NES palette, sprite proportions, pixel-art style documented
- [ ] Screen composition and visual scale recorded against original references
- [ ] Original visual references distinguished from implementation estimates
- [ ] Documentation: `docs/BUCKY_OHARE_GAMEPLAY_ANALYSIS.md` created

**Key Visual References**:
- Original NES color palette: 3-bit RGB per sprite/background tile
- Sprite proportions: 16x16 pixel base, 24x24 with animation extensions
- Pixel-art style: limited color palette, no anti-aliasing, visible pixel grid
- HUD: compact score/lives/power display at top-left
- Backgrounds: sci-fi lab/ship environments with parallax scrolling
- Animation: 12-16 frame cycles, limited frames per action

## Phase 4 – Character and Animation Design (UPDATED)
- [ ] Create original pixel-art characters preserving recognizable silhouettes
- [ ] Design animation cycles: idle (4 frames), walking (4 frames), running (4 frames), jumping (5 frames), falling (4 frames), shooting (3 frames), damage (3 frames), special-action (4 frames)
- [ ] Maintain consistent pixel scale: 16x16 base, 24x24 with weapon/extension
- [ ] Color palette: NES-limited 3-bit RGB, matching Bucky O'Hare aesthetic
- [ ] Character silhouettes: Bucky (rabbit/hare), Willy (cat), Dead-Eye (owl), Jenny (mouse), Hydron (dog)
- [ ] Animations to implement:
  - Idle loop (12 frames @ 12 FPS)
  - Walk cycle (8 frames loop)
  - Run cycle (8 frames, faster than walk)
  - Jump pose + fall arc
  - Shoot: arm extension + projectile spawn
  - Damage: flash + recoil animation
  - Special ability: Jenny light ball, Blinky flash, Dead-Eye power drain
- [ ] Animation timing: match NES 60 FPS frame rates where possible
- [ ] **Restriction**: Do not copy or redistribute original NES sprite sheets or copyrighted graphics
- [ ] **Restriction**: Do not replace pixel-art with generic modern 3D graphics

## Phase 5 – Gameplay Visuals (UPDATED)
- [ ] Movement animations feel close to NES experience
- [ ] Combat animations with appropriate sprite sizes
- [ ] Projectile effects: 8x8 to 16x16 sprites, trail effect
- [ ] Visual feedback on hit: sprite flash, frame skip
- [ ] Avoid modern-looking 3D graphics or generic mobile-game visuals
- [ ] Pixel scale preservation: 1 Android pixel ≡ 1 NES pixel at reference resolution
- [ ] Animation timing: 12 FPS minimum, 60 FPS cap matching original
- [ ] Sprite rendering: nearest-neighbor scaling, no bilinear filtering

## Phase 6 – Levels, Enemies and Bosses (UPDATED)
- [ ] Environments inspired by original game level atmosphere
- [ ] Detailed pixel-art backgrounds: 256x224 virtual viewport
- [ ] Platforms, hazards (spikes, conveyors, moving platforms)
- [ ] Foreground elements: destructible objects, collectibles
- [ ] Visually distinctive enemies per type:
  - Landen, Trooper, Blob, Suicide, etc.
  - Bosses: Haggos, Primid, Dr. Cyclops, etc.
- [ ] Colorful science-fiction atmosphere: bright blues, purples, cyans, with warm accent colors
- [ ] Enemy sprite sizes: 16x16 to 32x32 typical
- [ ] Boss phases with visual changes
- [ ] Level transitions with screen-fade effects

## Phase 7 – Menus and Interface (UPDATED)
- [ ] NES-inspired title screen with game logo
- [ ] Character selection screen with portraits
- [ ] HUD: score, lives, power indicator at top-left
- [ ] Health indicators: heart/sprite-based matching NES style
- [ ] Score display: 5-digit format (00010 = 1,000 points)
- [ ] Pause menu: simple list-based interface
- [ ] Pixel-style typography: 5x8 or 8x8 pixel font
- [ ] Interface elements: matching rectangle/capsule buttons
- [ ] Consistent color palette across all menus
- [ ] Touch navigation indicators where needed

## Phase 8 – Android Integration (UPDATED)
- [ ] Preserve pixel-art appearance on different screen sizes
- [ ] `android:scalingMode="matrix"` or nearest-neighbor in View configuration
- [ ] Avoid blurred or distorted sprites: `android:filterTouchesWhenScrolling="false"`
- [ ] Touch controls positioned to avoid obscuring gameplay area (bottom corners)
- [ ] Virtual D-pad: left side, action buttons: right side
- [ ] Resolution independence: virtual 256x224 viewport scaled to screen
- [ ] Multiple screen size support: mdpi, hdpi, xhdpi, xxhdpi asset buckets
- [ ] Landscape orientation locked (matching NES horizontal aspect)

## Phase 9 – Visual Quality Assurance (UPDATED)
- [ ] Compare Android screenshots with original NES gameplay references
- [ ] Check sprite proportions against documented palette
- [ ] Check colors match NES 3-bit RGB references
- [ ] Check animation smoothness: no stutter, consistent frame timing
- [ ] Check scrolling: side-to-side, vertical, parallax layers
- [ ] Check HUD placement: top-left, within safe zone
- [ ] Fix visual inconsistencies before final release
- [ ] QA checklist completed for all screen sizes

### Visual Fidelity Goal (MANDATORY)
**The Android game must visually and stylistically resemble the original Bucky O'Hare NES game as closely as reasonably possible.**

This is a core project requirement, not an optional enhancement.

**Palette Reference** (NES 3-bit RGB):
- Primary colors: various blues, cyans, greens, purples, warm reds/oranges/ yellows
- Background layers: multiple with different scroll rates
- Sprite colors: limited per sprite, color emphasis possible

**Sprite Proportions**:
- Base: 16x16 pixels
- With weapon/extension: up to 24x24 or 32x32
- Animation frames: 3-16 depending on action

**Screen Composition**:
- Virtual viewport: 256x224 pixels (NES resolution)
- HUD: top-left corner, compact design
- Score: 5-digit display (00010 = 1,000 points)
- Lives: icon-based, 3 maximum typically

### Documents Updated
1. `docs/BUCKY_OHARE_GAMEPLAY_ANALYSIS.md` - Updated with Phase 3-9 roadmap and visual fidelity goal
2. Project roadmap incorporated visual requirements across all development phases

### Restrictions (Reiterated)
- ✅ Do not copy or redistribute original NES ROM graphics, sprite sheets, copyrighted audio or extracted assets
- ✅ Create original assets that faithfully reproduce the visual style and atmosphere
- ✅ Do not replace the intended pixel-art style with generic modern graphics
- ✅ Do not modify SSH functionality
- ✅ Do not change Gradle configuration, application ID or signing settings
- ✅ Do not start Phase 4 until Phase 3 visual reference analysis is fully documented

### Next Steps
- Phase 3 completion: Visual reference analysis documented
- Phase 4 initiation: Original pixel-art character design beginning
- Phase 5-9: Sequential progression with visual fidelity checks at each stage
- Build verification when Java 17 environment available