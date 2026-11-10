# Game Art Direction Specification
## Bucky O'Hare-Inspired Android NES-Style Platform Game

### Project Overview
- **Project**: Bucky O'Hare-inspired Android platform game
- **Repository**: https://github.com/dincer552/socialworst
- **Branch**: main
- **Phase**: 4 - Pixel Art Character and Animation Asset Production

---

## 1. Pixel-Art Resolution and Scale

| Parameter | Value |
|-----------|-------|
| Base sprite frame size | 24x24 pixels |
| Virtual viewport | 256x224 pixels (NES resolution) |
| Canvas scaling | Nearest-neighbor only |
| Display scaling | Integer multiples (1x, 2x, 3x, 4x) |
| Frame alignment | All frames anchored to consistent origin |

**Rule**: All character sprites use 24x24 pixel frames. No sub-pixel positioning. All movement snaps to integer pixel coordinates.

---

## 2. Pixel Grid Rules

- **Grid**: Every asset drawn on a strict pixel grid
- **Anti-aliasing**: Prohibited - all edges must be crisp pixel boundaries
- **Sub-pixel**: Prohibited - no fractional coordinates
- **Padding**: No extra padding around sprite frames that would break animation alignment
- **Transparency**: Full alpha (0) for transparent pixels, full alpha (255) for opaque pixels

---

## 3. Color Palette

### Primary Palette (16 colors)

| Name | HEX | RGB | Usage |
|------|-----|-----|-------|
| OUTLINE | #282828 | 40,40,40 | Character and object outlines |
| FUR_LIGHT | #FFE0B4 | 255,224,180 | Main fur highlights |
| FUR_MEDIUM | #D2AA78 | 210,170,120 | Main fur mid-tones |
| FUR_DARK | #8C6446 | 140,100,70 | Fur shadows and nose |
| SUIT_LIGHT | #508CFF | 80,140,255 | Suit highlights |
| SUIT_MEDIUM | #2864DC | 40,100,220 | Suit base color |
| SUIT_DARK | #143C8C | 20,60,140 | Suit shadows |
| GLOVE_LIGHT | #DCDCDC | 220,220,220 | Glove highlights |
| GLOVE_DARK | #787878 | 120,120,120 | Glove shadows |
| BOOT_LIGHT | #B43C3C | 180,60,60 | Boot highlights |
| BOOT_DARK | #641E1E | 100,30,30 | Boot shadows |
| WEAPON | #FFF078 | 255,240,120 | Weapon body |
| WEAPON_DARK | #C89628 | 200,150,40 | Weapon shadows |
| EYE | #FFFFFF | 255,255,255 | Eye whites |
| PUPIL | #000000 | 0,0,0 | Eye pupils |
| TRANSPARENT | #00000000 | 0,0,0,0 | Background transparency |

### Effect Palette

| Name | HEX | RGB | Usage |
|------|-----|-----|-------|
| MUZZLE_FLASH | #FFF966 | 255,249,102 | Muzzle flash core |
| MUZZLE_GLOW | #FFC832 | 255,200,50 | Muzzle flash glow |
| IMPACT_CORE | #FFFFFF | 255,255,255 | Impact center |
| IMPACT_RING | #FFA0FF | 255,160,255 | Impact ring |
| EXPLOSION_CORE | #FF9632 | 255,150,50 | Explosion center |
| EXPLOSION_OUTER | #FFC896 | 255,200,150 | Explosion outer |
| DAMAGE_FLASH | #FF6464 | 255,100,100 | Damage tint |
| ENERGY_ITEM | #FFB464 | 255,180,100 | Collectible item |
| ENERGY_ITEM_DARK | #DC6428 | 220,100,40 | Collectible shadow |

---

## 4. Outline Style

- **Outline color**: OUTLINE (#282828)
- **Outline thickness**: 1 pixel on all external edges
- **Internal outlines**: Used sparingly for major form separation only
- **No gradient outlines**: All outlines are solid flat color

---

## 5. Shading Technique

- **Shading style**: Flat cel shading with 3 tones per material (light, medium, dark)
- **No gradients**: No smooth color transitions
- **Dithering**: Prohibited
- **Light direction**: Top-left consistent across all assets
- **Highlight placement**: Upper-left of each form
- **Shadow placement**: Lower-right of each form

---

## 6. Character Proportions

### Hero (Main Playable Character)

| Feature | Proportion | Pixels (24x24 frame) |
|---------|-----------|---------------------|
| Head width | 33% of body | 8px |
| Head height | 29% of body | 7px |
| Torso width | 42% of body | 10px |
| Torso height | 33% of body | 8px |
| Leg length | 4% of body | 1px per frame (compressed) |
| Arm length | 21% of body | 5px |
| Ear height | 21% of body | 5px |
| Eye size | 1px | 1x1 pixel |

### Overall Silhouette
- Anthropomorphic space-adventurer (rabbit/hare-inspired but original)
- Distinctive ears (two, upright)
- Compact body, expressive face
- Readable at 24x24 pixels

---

## 7. Animation Frame Conventions

| Animation | Frame Count | FPS | Loop | Sheet File |
|-----------|------------|-----|------|------------|
| idle | 3 | 8 | Yes | hero_idle.png |
| walk | 4 | 10 | Yes | hero_walk.png |
| run | 4 | 14 | Yes | hero_run.png |
| jump_prep | 2 | 10 | No | hero_jump.png |
| jump_rising | 2 | 12 | No | hero_jump.png |
| jump_falling | 2 | 12 | No | hero_jump.png |
| land | 2 | 12 | No | hero_land.png |
| shoot_stand | 2 | 12 | No | hero_shoot.png |
| shoot_move | 2 | 12 | No | hero_shoot_move.png |
| shoot_up | 2 | 12 | No | hero_shoot_up.png |
| damage | 2 | 10 | No | hero_damage.png |
| death | 4 | 8 | No | hero_death.png |
| special | 3 | 10 | No | hero_special.png |
| victory | 2 | 6 | No | hero_victory.png |

**Frame alignment**: All frames anchored at bottom-center of character. No drifting between frames.

**Facing**: All animations drawn facing right (faceDir=1). Left-facing achieved by horizontal flip at runtime.

---

## 8. Transparency Rules

- **Background**: Fully transparent (alpha = 0)
- **Opaque pixels**: Alpha = 255
- **Semi-transparent**: Prohibited in character sprites (allowed only in effect particles)
- **Alpha channel**: 8-bit per pixel PNG

---

## 9. Sprite Sheet Layout

### Individual Animation Sheets
- Each animation has its own PNG file
- Frames arranged horizontally in a single row
- No spacing between frames
- Consistent frame width (24px) and height (24px)

### Master Sprite Sheet
- File: `hero_spritesheet.png`
- Layout: 8 columns x 3 rows
- Contains all 36 animation frames in sequence
- Used for texture atlas optimization

---

## 10. Background Transparency

- All character and effect assets use transparent backgrounds (PNG alpha channel)
- No background color baked into sprite frames
- GameRenderer composites sprites over game background at runtime

---

## 11. Export Formats

| Asset Type | Format | Compression | Transparency |
|------------|--------|-------------|--------------|
| Character sprites | PNG | None (zlib level 9) | Alpha channel |
| Effect sprites | PNG | None (zlib level 9) | Alpha channel |
| Concept sheets | PNG | None (zlib level 9) | Alpha channel |
| Reference sheet | PNG | None (zlib level 9) | Alpha channel |
| Animation metadata | JSON | UTF-8 text | N/A |

**Prohibited formats**: JPEG, WebP, GIF, BMP (no lossy compression, no unintended artifacts)

---

## 12. Asset Directory Structure

```
assets/game/
├── characters/
│   ├── hero/
│   │   ├── hero_animations.json
│   │   ├── hero_reference.png
│   │   ├── hero_spritesheet.png
│   │   ├── hero_idle.png (3 frames)
│   │   ├── hero_walk.png (4 frames)
│   │   ├── hero_run.png (4 frames)
│   │   ├── hero_jump.png (8 frames combined)
│   │   ├── hero_land.png (2 frames)
│   │   ├── hero_shoot.png (2 frames)
│   │   ├── hero_shoot_move.png (2 frames)
│   │   ├── hero_shoot_up.png (2 frames)
│   │   ├── hero_damage.png (2 frames)
│   │   ├── hero_death.png (4 frames)
│   │   ├── hero_special.png (3 frames)
│   │   ├── hero_victory.png (2 frames)
│   │   └── hero_*_*.png (individual frames)
│   └── concepts/
│       ├── concept_scout.png
│       ├── concept_heavy.png
│       ├── concept_tech.png
│       └── concept_striker.png
└── effects/
    ├── weapon_blaster.png
    ├── projectile_bolt.png
    ├── muzzle_flash.png (3 frames)
    ├── impact_energy.png
    ├── explosion.png (4 frames)
    ├── damage_flash.png
    ├── special_attack.png
    └── item_energy.png
```

---

## 13. Animation Metadata Specification

File: `assets/game/characters/hero/hero_animations.json`

Defines:
- `frameWidth`: 24
- `frameHeight`: 24
- `animations`: Object mapping animation name to {frames, fps, loop, sheet, startFrame}
- `totalFrames`: 36
- `palette`: Complete color reference with HEX values

---

## 14. Visual Style Principles

1. **NES-authentic**: 8-bit color limitations, pixel grid, no anti-aliasing
2. **Readable**: Clear silhouettes at 24x24 pixels
3. **Consistent**: Same palette, same outline weight, same light direction across all assets
4. **Original**: Independently created, not extracted from any ROM
5. **Game-ready**: Suitable for Android Canvas rendering with nearest-neighbor scaling

---

## 15. Integration Notes

- All assets designed for `GameRenderer` Canvas-based rendering
- Sprite sheets optimized for single-texture binding
- Animation metadata drives frame timing in `GameEngine`
- No integration into live game yet (Phase 5+)