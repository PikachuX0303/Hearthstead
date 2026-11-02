# Project notes

Hearthstead: a colony-building mod for Minecraft 1.21.11 (Mojang mappings, Fabric API),
inspired by MineColonies and not affiliated with it. Mod id `hearthstead`, package
`dev.pikachux.hearthstead`. It was called "MineColonies (Unofficial Fabric Port)" with mod id
`minecolonies` up to 0.16; `registry/LegacyIds.java` and the legacy saved-data name in
`ColonyManager` keep those worlds loading.

Credits: PikachuX0303, PerniciousPx and FallenStar114. Keep all of them credited in `fabric.mod.json` (authors),
the README "Credits" section, the release notes footer in `.github/workflows/release.yml`
and the chat welcome message (`event/WelcomeMessage.java`).

## Releasing (do this with every change that is pushed)

1. Bump `mod_version` in `gradle.properties` (`MAJOR.MINOR.PATCH-alpha+1.21.11`):
   new features bump MINOR, fixes only bump PATCH.
2. Add a section `## [<version without +1.21.11>] - <date>` at the top of `CHANGELOG.md`
   with Added / Changed / Fixed lists.
3. Push. `.github/workflows/release.yml` builds the jar, runs the game tests and publishes
   a GitHub release tagged `v<version>` with that changelog section as release notes.

## Checks

- `./gradlew build` compiles and runs the server game tests.
- `xvfb-run ./gradlew runClientGameTest` opens every screen in a real client and writes
  screenshots to `build/run/clientGameTest/screenshots`.

## Libraries

Existing Fabric mod libraries may be used (for example Mod Menu, Cloth Config or YACL for a
config screen). Prefer well-maintained libraries available for 1.21.11 on Modrinth, keep them
optional where that is practical (the mod should still run with only Fabric API), check that
their license is compatible with GPL-3.0-or-later, and list any required ones in the README
download section and in `fabric.mod.json` `depends` (optional ones in `suggests`).

## Generated assets

- `tools/generate_schematics.py`: hut and decoration blueprints for every style, and the
  decoration icons (`decoration_icons.json`).
- `tools/generate_textures.py`: barbarian skins and the Build Tool and Clipboard icons.
- `tools/generate_ui.py`: hut block textures, hut block models and the GUI theme sprites.
- `tools/generate_worldgen.py`: structure templates for world generation (abandoned settlement).

Hand-drawn art: the citizens' job outfits in `assets/hearthstead/textures/entity/citizen/`
(`<outfit>_male.png` and `<outfit>_female.png`) are by FallenStar114; keep them credited in the
README's "Job outfits" and "Credits" sections. New outfits go into `CitizenEntity.OUTFITS` and the
job mapping in `CitizenEntity.outfit`; skins with slim (3 pixel) arms also into `SLIM_OUTFITS`
(a game test checks that list against the textures).

All art is original; do not copy textures from MineColonies or other mods.

## Commits and GitHub posts

Never put a Claude session link (`claude.ai/code/session_...`, `Claude-Session:` trailer)
in commit messages, PR descriptions, comments, release notes or any file.

Commits are authored by `PikachuX0303 <136934106+PikachuX0303@users.noreply.github.com>`:
run `git config author.name PikachuX0303` and
`git config author.email 136934106+PikachuX0303@users.noreply.github.com` before committing
(leave the committer as configured, so commits stay signed and verified).

Push to `https://github.com/PikachuX0303/Hearthstead.git` (the session may reset `origin` to
the old MineColonies-Fabric-Port-UNOFFICIAL repo).
