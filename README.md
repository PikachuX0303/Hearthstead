# Hearthstead (Minecraft 1.21.11, Fabric)

**Hearthstead** is a colony-building mod for Minecraft **1.21.11** on Fabric. Found a town,
place huts, and watch citizens move in, take jobs, gather resources, research, and defend
the town against barbarian raids.

> Hearthstead is inspired by [MineColonies](https://github.com/ldtteam/minecolonies) and started
> out as an unofficial Fabric port of it. It is **not affiliated with or endorsed by** the
> MineColonies team (ldtteam). It is written from scratch against the 1.21.11 API and contains
> no code or assets from MineColonies; its textures are either vanilla Minecraft or original
> art made for this mod.

## Download

Get the latest jar from the [Releases page](https://github.com/PikachuX0303/Hearthstead/releases).
Put it into your `mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api).
See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

### Automatic updates

On the first start Hearthstead asks whether it may update itself. If you turn this on, it
checks the Releases page each time the game starts. When there is a newer release for your
Minecraft version, it downloads the jar into your `mods` folder (checking its size and SHA-256
checksum) and shows a message. The new version replaces the old jar when you close the game
and is used from the next start. Turn it on or off later in `config/hearthstead-updates.json`
(`{"automaticUpdates": true}` or `false`); delete that file to be asked again.

## Status: early alpha

Hearthstead has the core colony loop, 27 huts and jobs, five building styles, research and
raids. Things that are **not there yet** (some of which MineColonies has):

- Decoration blueprints and hand-built style packs. The five styles are generated.
- Hut-specific settings pages (for example, choosing which crops a farmer plants).
- Some jobs: no Enchanter, Alchemist, Mechanic, Planter, Netherworker, and so on.
- Most of the original research tree, pirates and other raider cultures, request system, colony events, permissions GUI, config file.

## Features

![Hut blocks](docs/screenshots/hut_blocks.png)

Every hut is a wooden workbench with its job sign on the front and a fitting prop on top
(a log stump for the Lumberjack, a hay bale for the Farmer, a cake for the Baker, and so on).

### Windows

| Action | Opens |
|---|---|
| Right-click a hut | The **hut window**. **Overview** shows level, residents, workers and their status. **Construction** shows the work order, a progress bar and the materials list, with how many of each the Builder's Huts already hold. Buttons: **Inventory**, **Build** / **Upgrade** / **Cancel Order**. |
| Right-click the Town Hall | The hut window plus a **Citizens** tab (every citizen with job, happiness and status) and a **Colony** tab (owner, statistics, renaming the colony, open work orders). |
| Sneak + right-click a hut | The hut's inventory directly. |
| Right-click a citizen | The **citizen window**: a model preview, health, food and happiness bars, status, home, workplace and carried items. |

Open windows refresh every two seconds, so you can watch construction progress live.

When you join a world or server, a short welcome message in chat shows the mod version and
links to the releases and the changelog.

| | |
|---|---|
| ![Town Hall overview](docs/screenshots/townhall_overview.png) | ![Citizens tab](docs/screenshots/townhall_citizens.png) |
| ![Construction tab](docs/screenshots/residence_construction.png) | ![Citizen window](docs/screenshots/citizen.png) |

### Gameplay

| Feature | Details |
|---|---|
| **Colonies** | Place a **Town Hall** to found a colony (one per player). It claims a 129×129 area. Colonies must be at least 144 blocks apart. |
| **Protection** | Non-members can't break blocks or interact with blocks inside a colony. Creative-mode operators can bypass this. |
| **Citizens** | New citizens arrive every minute while there is free housing. Each one has a name, a skin, saturation and happiness. If a citizen goes missing (for example, unloaded), the colony respawns them at the town hall. If one dies, the owner is notified. |
| **Housing** | Town Hall: 4 settlers per level (also 4 before it is built). Residence: 2 beds per level. |
| **Food** | Citizens get hungry. They eat food stored in their home, the Town Hall, a Residence or a Warehouse. Starving citizens become unhappy and take damage. |
| **Happiness** | Housing, employment and food raise happiness. Happiness scales work speed. |
| **Day/night** | Workers go home to sleep at night. Guards stay on duty. |
| **Lumberjack** | Needs an **axe** in the hut. Fells nearby trees from the top down, picks up drops, replants saplings and brings the wood back. |
| **Farmer** | Needs a **hoe** in the hut. Tills, sows and harvests a field that surrounds the farmer's building, at floor level. Takes seeds from the hut, and uses bone meal from the hut on growing crops. Field width = 2 + level blocks. |
| **Miner** | Needs a **pickaxe** in the hut. Digs a 2-high tunnel out of the front of the miner's building (16 blocks per level), mines exposed ores and places torches from the hut. Place the hut underground. |
| **Guard Tower** | Employs one guard per level. Guards patrol the tower and attack hostile mobs, including barbarians. They use a sword if one is stored in the tower, and have +20 health, +6 armour and +2 attack. Other citizens flee from monsters. |
| **Raids** | See [Raids](#raids) below. |
| **Research** | See [Research](#research) below. |
| **Warehouse** | The colony's central storage. Its building is lined with barrels, and the hut plus every chest and barrel inside the building count as storage. Citizens can eat from it. |
| **Deliveryman** | Carries items between the Warehouse and the huts. Brings builders their missing materials, workers their tools (axe, hoe, pickaxe, sword, fishing rod), seeds and torches, homes their food and the cook raw food and coal. Also empties worker huts into the Warehouse. Needs a Warehouse. The hut window shows what each hut is waiting for. |
| **Cook** | Loads the smokers inside the kitchen (one per hut level) with raw food (beef, pork, chicken, mutton, rabbit, cod, salmon, potatoes, kelp) and coal or charcoal from the hut, and stores the meals in the hut. Citizens eat there too, and prefer cooked meals. |
| **Baker** | Turns wheat stored in the Bakery into bread: 3 wheat per loaf, one loaf per hut level each batch. Citizens eat there too. |
| **Smelter** | Runs the furnaces inside the Smeltery (one per hut level), smelting ores, raw metals and sand into ingots and glass with coal or charcoal from the hut. |
| **Shepherd** | Needs **shears** in the hut. Shears the sheep around the hut and breeds them with wheat while the flock is smaller than 2 + 2 × hut level. Build a pen near the hut so the sheep stay in range. |
| **Composter** | Fills the composters in its building (two per hut level) with plant waste such as leaves and saplings, and collects the bone meal. Farmers use bone meal on growing crops. |
| **Sawmill** | Saws logs into what builders are waiting for and the warehouse can't supply: planks, sticks, slabs, stairs, doors, trapdoors, fences and fence gates of every wood type. Otherwise it keeps 64 planks in the warehouse. |
| **Cowhand** | Needs **buckets** in the hut. Milks the cows around the hut (each cow every few minutes) and breeds them with wheat while the herd is smaller than 2 + 2 × hut level. Keep the cows in a pen near the hut. |
| **Chicken Herder** | Breeds the chickens around the hut with seeds and gathers the eggs and feathers they leave lying around. |
| **Swineherd** | Needs a **sword or axe** in the hut. Breeds the pigs around the hut with carrots, potatoes or beetroot; once the herd reaches 2 + 2 × hut level, it butchers an adult pig for pork. |
| **Stonemason** | Cuts stone, cobblestone, sandstone, deepslate, bricks and more into the stone parts builders are waiting for and the warehouse can't supply (stone bricks, stairs, slabs, walls, cut and polished blocks). |
| **Florist** | Needs **bone meal** in the hut. Uses it on the grass around its building so flowers grow, and picks the flowers. |
| **Dyer** | Makes the dyes, coloured wool and beds builders are waiting for (flowers into dyes, white wool into coloured wool, wool and planks into beds). Otherwise turns flowers into dyes. |
| **Beekeeper** | Harvests the full beehives in the Apiary (one per level) and bee nests nearby: honey bottles when it has glass bottles, honeycomb with **shears** otherwise. Bees are not angered. Bring your own bees. |
| **Rabbit Herder** | Needs a **sword or axe** in the hut. Breeds the rabbits around the hutch with carrots or dandelions; once the warren reaches 2 + 2 × hut level, takes an adult rabbit for meat and hide. |
| **Glassblower** | Smelts sand into glass in the furnaces of its workshop (one per hut level) with coal or charcoal, and makes the glass panes and stained glass builders are waiting for. |
| **Concrete Mixer** | Mixes 4 sand, 4 gravel and a dye into 8 concrete powder and sets the powder into concrete. |
| **Fisherman** | Needs a **fishing rod** in the hut. Finds open water near the hut, fishes from the shore using the vanilla fishing loot, and brings the catch home. |
| **Builder** | Every hut is built from a blueprint (schematic) by a **Builder**. A placed hut starts at level 0 and gets a work order for level 1. The builder takes building materials from the **Builder's Hut** inventory and places the blueprint block by block, clearing whatever is in the way. Open a hut to see progress and the materials still needed. |
| **Upgrades** | Huts go up to level 5, and each level has a bigger building. Open a hut and click **Upgrade** to create a work order. Other huts can't exceed the Town Hall's level, and a builder can only build up to its own hut's level (level 1 is always possible). |

### Recipes

Each hut is 8 planks around a center item:

| Hut | Center item |
|---|---|
| Town Hall | book (cobblestone in the edge slots) |
| Residence | any bed |
| Lumberjack | wooden axe |
| Farmer | wooden hoe |
| Miner | wooden pickaxe |
| Guard Tower | wooden sword |
| Warehouse | chest |
| Builder's Hut | wooden shovel |
| Deliveryman's Hut | leather |
| Cook's Hut | smoker |
| Fisherman's Hut | fishing rod |
| Bakery | wheat |
| Smeltery | furnace |
| Shepherd's Hut | shears |
| University | book |
| Composter's Hut | composter |
| Sawmill | iron axe |
| Cowhand's Hut | bucket |
| Chicken Herder's Hut | egg |
| Swineherd's Hut | carrot |
| Stonemason's Hut | stonecutter |
| Florist's Hut | poppy |
| Dyer's Hut | loom |
| Apiary | beehive |
| Rabbit Hutch | dandelion |
| Glassblower's Hut | glass |
| Concrete Mixer's Hut | cauldron |

The **Rope Fence** is crafted from 4 sticks and 2 string (sticks on the sides, string in the
middle column) and makes 4 fences.

All huts are also in the **Hearthstead** creative tab.

### Commands (`/hearthstead` or `/hs`)

- `info`: overview of your colony and its buildings
- `citizens`: lists citizens with job, food, happiness and status
- `list`: lists all colonies on the server
- `rename <name>`: renames your colony (owner only)
- `addofficer <player>` / `removeofficer <player>`: officers can build in and manage the colony
- `upgrade <x y z>`: creates a work order to build or upgrade a hut (same as the **Build** / **Upgrade** button in the hut window)
- `cancel <x y z>`: cancels a hut's work order
- `workorders`: lists the colony's open work orders
- `build <x y z>`: builds a hut's next level instantly, without materials (operators only)
- `raids <on|off>`: allows or prevents raids on your colony (owner only)
- `raid <start|stop>`: starts or stops a raid on the colony you are in (operators only)
- `research complete <research>`: finishes a research immediately (operators only)
- `delete <id>`: deletes a colony (operators only)

## Building styles

Every hut can be built in one of five styles: **Classic**, **Medieval** (stone brick and
spruce), **Desert** (sandstone with flat roofs), **Nordic** (spruce and cobblestone) and
**Timber** (half-timbered: stone base, white plaster in a dark oak frame). Buildings keep the
same size and interior in every style, and each has a rope-fenced front yard with lanterns
by the entrance.

![A timber-style residence with its front yard](docs/screenshots/timber_yard.png)

- **Colony default:** new huts get the colony's default style. Change it with **Change Style** on
  the Town Hall's Colony tab.
- **Per building:** the Build Tool's style switcher (◀ Style ▶) previews a hut in each style
  before placing it, and picks the style for an upgrade. Each building remembers its style.

![Build Tool preview in the desert style](docs/screenshots/build_tool_desert.png)

## Build Tool

Craft the **Build Tool** from two sticks and a sheet of paper (diagonally, paper on top), or
take it from the creative tab.

1. Right-click a block with the Build Tool and choose a hut from your inventory.
2. The hut's building appears as a see-through ghost, with an outline of the area the
   builder will use. Blocks in red show where something is in the way; the builder will
   clear it.
3. Move it with the arrow buttons or arrow keys, raise or lower it with PgUp/PgDn and
   rotate it with R.
4. Press **Place Hut** (or Enter). The hut is placed and the builder gets a work order.

Esc hides the controls so you can look around. Right-click with the tool to show them
again, or right-click a block to move the preview there. Right-clicking an existing hut
previews its next level, with an **Order Upgrade** button.

| | |
|---|---|
| ![Choosing a hut](docs/screenshots/build_tool_select.png) | ![Building preview](docs/screenshots/build_tool_preview.png) |

## Research

Build a **University** (8 planks around a book). It employs one **Researcher** per level,
who studies during the day and adds research points to the colony's active research.

Open the University and go to the **Research** tab. Select a research to see what it does
and what it costs, put the materials into the University's inventory and press
**Start Research**. One research runs at a time. A research needs its parent research and a
University of at least its tier.

| Branch | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Civilian | **Community Spirit**: +1 happiness (16 bread) | **Comfortable Homes**: +1 resident per Residence (16 white wool, 4 books) | **Feast Days**: hunger 30% slower (32 bread, 1 cake, 8 books) |
| Technology | **Apprenticeship**: +10% work speed (8 paper) | **Swift Construction**: builders ~40% faster (16 iron ingots, 4 books) | **Master Craftsmen**: +15% work speed (8 gold ingots, 1 diamond, 8 books) |
| Combat | **Militia Drills**: guards +2 damage (8 iron ingots) | **Chainmail Training**: guards +4 armour (24 iron ingots, 4 books) | **Stone Walls**: raids 25% smaller (64 stone bricks, 8 books) |

Research takes 300, 900 and 2400 points for tiers 1, 2 and 3 (about 5, 15 and 40 minutes
with one researcher).

| | |
|---|---|
| ![Research tab](docs/screenshots/university_research.png) | ![Research details](docs/screenshots/university_research_details.png) |

## Raids

At nightfall, a colony with at least 4 citizens may be raided by barbarians. The chance grows
with every night without a raid.

- **Warning:** members get a chat warning with the direction of the attack, a raid horn
  sounds, and a boss bar shows the raiders left to everyone inside the colony.
- **Attack:** the raiders gather at the edge of the colony, march on the Town Hall and attack
  citizens and players on the way. Guards fight back; everyone else flees.
- **End:** the raid ends when all raiders are dead, or at sunrise when the survivors retreat.
- **Size:** 2 raiders plus 1 for every 2 citizens, up to 16. Barbarian Archers join from 6
  citizens, and a Barbarian Chief from 10.
- **Loot:** leather, iron nuggets and arrows. Chiefs drop iron ingots and emeralds, and
  sometimes a golden apple.
- **Controls:** owners can switch raids off on the Town Hall's Colony tab or with
  `/hs raids off`. There are no raids on Peaceful.

| | |
|---|---|
| ![Barbarians](docs/screenshots/barbarians.png) | ![A raid starts](docs/screenshots/raid_started.png) |

## Getting started

1. Place a **Town Hall** on open, fairly flat ground. Up to four settlers will arrive.
   Use the **Build Tool** to see how much room each building needs before placing it.
2. Put some food (bread, cooked meat, and so on) into the Town Hall's inventory.
3. Place a **Builder's Hut** and fill it with building materials. Open any hut
   to see which materials its blueprint still needs. A level 1 hut needs about 250-300 blocks:
   planks, logs, cobblestone or stone bricks, glass, stairs, a slab, a door and torches.
4. The builder builds its own hut first, then the Town Hall, then the other huts in order.
5. Huts need room: a level 1 building is 7×7, and level 5 is 13×13. The builder clears
   anything in the way, so leave space between huts.
6. Add a **Residence** for more citizens, and work huts such as a Lumberjack with an axe inside.
7. Add a **Warehouse** and a **Deliveryman's Hut**. From then on, stock the Warehouse instead
   of individual huts: the deliveryman brings materials, tools and food to wherever they
   are needed, and collects what the workers produce.

## Schematics (blueprints)

Blueprints are vanilla structure files. The built-in ones are at
`data/hearthstead/structure/huts/<hut>/level<1-5>.nbt`, where `<hut>` is one of
`townhall`, `residence`, `lumberjack`, `farmer`, `miner`, `guardtower`, `warehouse`, `builder`,
`deliveryman`, `cook`, `fisherman`, `baker`, `smelter`, `shepherd`, `university`, `composter`,
`sawmill`, `cowhand`, `chickenherder`, `swineherd`, `stonemason`, `florist`, `dyer`, `beekeeper`, `rabbitherder`, `glassblower` and `concretemixer`.

To use your own design:
1. Build it in a creative world and put the matching hut block where the hut should sit.
   The way the hut block faces is the building's front.
2. Save it with a **structure block** named `hearthstead:huts/<hut>/level<N>`, for example
   `hearthstead:huts/residence/level2`.
3. Run `/reload`. From then on, builders in that world use your design. To share a design
   between worlds, put the `.nbt` file into a data pack at the same path.

Other styles live at `data/hearthstead/structure/styles/<style>/<hut>/level<1-5>.nbt`
(structure name `hearthstead:styles/<style>/<hut>/level<N>`). A data pack can add a whole new
style by adding such a folder; it shows up in the style list automatically, and any hut or level
it lacks falls back to the classic blueprint.

Air in the structure is cleared by the builder. Structure void is left untouched.
Block entity contents (for example, chest items) are not copied.

The default blueprints are generated by `tools/generate_schematics.py`.

## Building

Requires Java 21.

```sh
./gradlew build          # jar in build/libs/
./gradlew runClient      # dev client
./gradlew runServer      # dev server
./gradlew runGameTest    # automated in-game tests (server)
xvfb-run ./gradlew runClientGameTest   # opens every window in a real client and saves screenshots
```

Dependencies: Fabric Loader ≥ 0.20.1 and Fabric API (0.141.6+1.21.11). The project uses
official Mojang mappings.

## Credits

- **PikachuX0303**
- **PerniciousPx**
- **FallenStar114**

In collaboration with claude.

Inspired by [MineColonies](https://github.com/ldtteam/minecolonies) by the ldtteam; Hearthstead
is not affiliated with or endorsed by them.

## License

GPL-3.0 (see `LICENSE`), the same license MineColonies uses.

## Coming from the old name

Hearthstead was called "MineColonies (Unofficial Fabric Port)" up to version 0.16. Worlds from
those versions keep working: colonies, huts, items and citizens are carried over to the new
`hearthstead` ids automatically. Custom blueprints in data packs must move from
`data/minecolonies/` to `data/hearthstead/`, and the command is now `/hearthstead` (or `/hs`).
