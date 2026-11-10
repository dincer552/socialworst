# SSH Client development and release checklist

## General SSH client

- [x] Preserve application ID `com.dincer552.hattrickssh` and the existing Android Keystore alias.
- [x] Use multiple server profiles; remove personal host/user defaults from the active UI.
- [x] Support key or password authentication per profile.
- [x] Encrypt profile records, passwords, private keys, and known-host data using Android Keystore AES-GCM.
- [x] Keep Cloudflare Access optional; allow direct SSH.
- [x] Add GitHub Release update check, in-app check button, signed APK download, and Android installer handoff.
- [ ] Configure GitHub signing secrets and verify existing APK certificate continuity.
- [ ] Run Actions unit tests and debug/release APK builds; inspect ARM64 payloads and release assets.

## Device acceptance

- [ ] Test profile create/edit/delete and persistence after restart.
- [ ] Test per-profile key isolation and SSH password authentication.
- [ ] Connect to a controlled SSH server; verify host-key prompt and changed-key rejection.
- [ ] Test PTY shell, ANSI, input, resize, scrolling, disconnect and reconnect on an ARM64 Android device.
- [ ] Test Cloudflare browser login and SSH tunnel separately from direct SSH.
- [ ] Install an older signed APK and test its in-app GitHub update path.
- [ ] Confirm credentials and key material do not appear in logs.
- [ ] Verify API 26+ and native 16 KB page-size compatibility.

## Release setup

- [ ] Set `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` in GitHub Actions secrets; do not commit or share signing material.
- [ ] Publish the privacy policy at a public HTTPS URL and add support contact details.
- [ ] Review third-party license notices and release notes.

## Known limitations

No real SSH server, Cloudflare Access flow, Android-device terminal, or update-install test is claimed until it has actually been performed. A successful compile/package alone does not verify these runtime features.

## Bucky O'Hare Game Development Roadmap

### Phase 0–5 (Complete)

- [x] Project setup: single-root Android app, app ID `com.dincer552.hattrickssh`
- [x] Project analysis: SSH client audit, existing code review
- [x] Game infrastructure: GameActivity, GameView, GameEngine, GameRenderer, GameInput, GameState, GameConfig
- [x] Visual reference analysis: NES disassembly research, mechanics documentation
- [x] Pixel art asset production: 69 PNG files, sprite sheets, character concepts, weapons, effects (validated)
- [x] Core gameplay mechanics: sprite sheet animation, physics, jumping, shooting, collision, touch controls, camera, HUD

### Phase 6: Multi-Character System (Next)

- [ ] Five playable characters: Bucky, Blinky, Dead-Eye, Jenny, Willy
- [ ] Unique weapons per character: straight shot, downward-arc ice breaker, three-direction spread, blue laser, piercing laser
- [ ] Unique special abilities: high jump, jetpack flight, wall climbing, guided orb, increased laser power
- [ ] Character switching UI (touch-friendly on Android)
- [ ] Shared team health + character-specific power gauges
- [ ] Charge-and-release special ability mechanic (hold to charge, release to activate)
- [ ] Character-specific animations for all five characters

### Phase 7: Planet System

- [ ] Four distinct planets: Green (forests), Red (volcanoes), Blue (ice caves), Yellow (futuristic)
- [ ] Multi-act levels per planet with different layouts
- [ ] Planet-specific hazards: insects, fireballs, ice blocks, laser cannons
- [ ] Horizontal and vertical scrolling sections
- [ ] Planet selection screen with dependency tracking (Blue Planet requires Blinky)

### Phase 8: Bosses and Crew Rescue

- [ ] Planet bosses for each of the four planets
- [ ] Boss defeat triggers crew rescue sequence
- [ ] Rescue animation, character unlock, introduction display
- [ ] Return to planet selection after rescue
- [ ] Character-ability dependency: Blinky required for Blue Planet ice blocks

### Phase 9: Toad Mothership (Second Half)

- [ ] Cell stage — Bucky and Blinky escape
- [ ] Salvage Chute stage
- [ ] Center of Magma Tanker stage
- [ ] Escape! stage — spacecraft control through collapsing mothership
- [ ] Rescue other crew members during mothership stages
- [ ] Final escape sequence

### Phase 10: Items and Progression

- [ ] 1-UP: extra life
- [ ] L-heart: restores health
- [ ] P-icon: increases special power gauge capacity
- [ ] Score bonuses
- [ ] Shared health system across all characters

### Phase 11: Android Integration

- [ ] Character switch button/interface on Android
- [ ] Charge-and-release touch controls (hold to charge, release to activate)
- [ ] Game button on main SSH screen
- [ ] Main application integration

### Phase 12: Polish and QA

- [ ] Visual QA against original NES references
- [ ] Bug fixes and balance tuning
- [ ] Device testing on ARM64 Android
