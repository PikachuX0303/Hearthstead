# Changelog

All notable changes to Hearthstead (called "MineColonies (Unofficial Fabric Port)" up to 0.16)
are listed here.
Versions follow [Semantic Versioning](https://semver.org/). The Minecraft version is
part of the build metadata (for example, `0.45.0-beta+1.21.11`). Versions up to 0.44.0 were
alpha releases.

## [0.46.0-beta] - 2026-11-05

### Added
- **Settings for every hut with a worker**, plus the Tavern, Combat Academy and Archery:
  - Builder's Hut: which work orders to take (all, building only, repairs & removals only).
  - Deliveryman's Hut: empty worker huts early or only when they are full.
  - Fisherman's Hut: keep everything or only fish.
  - Bakery: bake bread, cookies (wheat and cocoa beans) or pumpkin pie (pumpkin, sugar, egg).
  - University: start the next research automatically once its materials are in.
  - Composter: plant waste or only leaves and saplings.
  - Sawmill and Stonemason: how many spare planks or stone bricks to keep in the warehouse.
  - Florist: pick flowers or only grow them. Dyer: make dyes when idle or not.
  - Apiary: honey bottles or honeycomb. Glassblower: make panes and stained glass or only glass.
  - Concrete Mixer: set concrete or leave the powder dry. Planter: which plants to spread.
  - Alchemist: splash potions with gunpowder. Enchanter: strongest enchantments or save lapis.
  - Mechanic, Blacksmith and Fletcher: make goods for huts or only taught recipes; the Blacksmith
    also chooses its material (best, no diamond, stone first).
  - Nether Mine: expeditions on or off. Stone Smeltery: what to smelt.
  - Hospital: heal wounds, cure illness or both. Quarry: a smaller pit.
  - Library: save books. School: faster growing on or off. Graveyard: revival on or off.
  - Tavern: visitors on or off. Combat Academy and Archery: training on or off.
- Game tests for the new settings, and a client screenshot of the Blacksmith's Settings tab.

### Changed
- **Citizen status lines are translated** into all 13 languages, in the hut, town hall and
  citizen windows and in `/hs citizens`.
- **Command output is translated**: `/hs info`, `list`, `citizens`, `rename`, `addofficer`,
  `removeofficer`, `workorders`, `research complete` and `delete`.
- Settings rows in a hut window are as tall as their description needs, so long translations no
  longer overlap the next setting.

## [0.45.0-beta] - 2026-11-03

### Changed
- **Hearthstead is now in beta.** Its main systems are in place and covered by game tests, and
  worlds stay compatible between versions. Keep backups and report problems on the issue tracker.
- The README is up to date: the beta status and what is still missing, requirements (Minecraft
  1.21.11, Fabric Loader 0.16.0 or newer, Java 21, on server and clients), every tab of the hut
  and town hall windows, the Clipboard, the blueprint folders of all 49 huts and how the colony
  distance depends on the radius.
- The chat welcome message credits PikachuX0303 as well as PerniciousPx and FallenStar114.

### Added
- A mod icon (a cottage with a lit window at dusk), shown in Mod Menu and the README.
- Homepage and issue tracker links in the mod list.

## [0.44.0-alpha] - 2026-11-03

### Added
- **Translations:** German, French, Spanish, Italian, Brazilian Portuguese, Russian, Polish,
  Dutch, Turkish, Simplified Chinese, Japanese and Korean.
- A game test that checks every language has all keys, with the same arguments as English.

### Changed
- Job and hut names in citizens' name tags, hut and citizen windows and chat messages are
  translated instead of always being English. So are the citizen window's home and workplace and
  the town hall's list of work orders.
- Lines that are too long for their space (common in translations) are drawn smaller instead of
  running out of the window: the build tool's hints, hut names in the build tool and the permission
  columns.

### Fixed
- The Colony tab of the town hall cut off the decorations, style and raids lines behind its
  buttons. They now sit in the left column.

## [0.43.0-alpha] - 2026-11-02

### Added
- **Teaching recipes:** the Sawmill, Mechanic's Workshop, Blacksmith's Hut and Fletcher's Hut have
  a Recipes tab. Teach them anything a crafting table makes (two recipes per hut level); they
  make it when a hut or postbox asks for it, and keep a stock in the warehouse if you set one.
  The hut asks for the ingredients it lacks.

### Changed
- Postbox orders and taught recipes are listed in two columns, so all of them fit.

## [0.42.0-alpha] - 2026-11-02

### Added
- Grown-up female citizens have a small chest shape, textured from their own skin (shirt and
  jacket layer) so it matches every outfit; it follows the body's pose. Children don't have it.

### Changed
- Citizens without a job outfit get a default skin that fits them: Alex, Ari, Efe, Makena or Noor
  for women, Steve, Kai, Sunny or Zuri for men.

## [0.41.0-alpha] - 2026-11-02

### Added
- More job outfits by FallenStar114: cowhands, deliverymen, farmers, fishermen, miners, researchers
  and shepherds now wear their own, in a male and a female version.

### Changed
- Outfits drawn with slim arms are shown on the slim player model, so their arms have no gaps.

## [0.40.0-alpha] - 2026-11-01

### Added
- **Postbox:** a hut without a worker where you order items by name or id (up to 8 orders); the
  deliveryman brings them from the warehouse, and members get a message when an order is
  complete.
- **Mystical Site:** a hut without a worker; the best one in the colony makes everyone happier
  (+0.25 per level).
- **Scarecrows and fields:** a scarecrow marks the 9 by 9 field around it, which the nearest
  farmer with room works (one field per Farmer's Hut level).
- **Tavern expeditions:** send a visitor away for a night with 6 meals (and a sword) from the
  tavern; they usually come back the next morning with loot.

### Changed
- The hut row in the README shows the newest huts and the scarecrow.

### Fixed
- A Netherworker's trip carries on after the world is reloaded instead of being lost.

## [0.39.0-alpha] - 2026-11-01

### Added
- **Repair:** a Repair button on the hut's Construction tab orders a builder to put back what is
  missing from the hut's current level.
- **Deconstruct:** a Deconstruct button (click twice) orders a builder to take the hut down; its
  blocks, contents and the hut block go to the Builder's Hut. Not for the town hall.
- **Move huts** with the Build Tool: Move Hut on an existing hut's preview, then Move Here. The
  hut moves with its contents, workers and residents, and builders clear the old site and
  rebuild it at the new spot.

### Fixed
- Game tests keep the weather clear, so citizens don't go to bed during a test's thunderstorm.

## [0.38.0-alpha] - 2026-11-01

### Added
- **Job outfits** by FallenStar114: alchemists, bakers, beekeepers, blacksmiths, builders, sawmill
  workers (carpenter), cooks (chef), knights (cavalry) and rangers (archer) wear their job's
  outfit, in a male or a female version to match the citizen.

### Changed
- Citizens are drawn with the player model, so skins show their outer layer (jackets, sleeves,
  trousers) too.

## [0.37.0-alpha] - 2026-11-01

### Added
- **Illness.** Citizens may catch a cold, a fever or a stomach bug in the morning (more likely
  when hungry or homeless, less with Herbal Remedies) and pass it on to the people they live
  with. Sick citizens stop working, are slower and less happy, and go to a hospital, or rest at
  home without one.
- Healers cure the sick with a honey bottle, golden carrot or milk bucket from the hospital,
  which asks for one per sick citizen. Without a cure, illnesses pass after two to four days.
- Config: `illnessChance` and `contagionChance` in `[citizens]`, also in the settings screen.

## [0.36.0-alpha] - 2026-11-01

### Added
- **Guard classes.** Guard Towers and Barracks have a Guard class setting:
  - **Knights** (the default) fight with a sword and shield; they get +4 armour and the shield
    takes 40% of each hit.
  - **Rangers** keep their distance and shoot arrows with a bow.
  - **Druids** throw harming potions at enemies (healing ones at the undead) and regeneration
    potions at hurt colonists, brewed from nether wart.
  Each building asks for its class's gear, and windows and name tags show the class.
- **Guard orders.** Patrol around the building, patrol the whole colony, hold the building, or
  follow the nearest colony member and defend them.

### Changed
- Rangers train at the Archery; druids and guards on hold or following someone don't train.
- Guards only walk back to their building for gear that is actually there.

## [0.35.0-alpha] - 2026-11-01

### Added
- **Settings screen.** With Mod Menu and Cloth Config installed (both optional), Mods →
  Hearthstead → ⚙ opens every option of `config/hearthstead.toml` in game: a tab per section,
  descriptions as tooltips, sliders, percentages for chances and Reset buttons. Saving writes
  the file and applies the values right away in singleplayer and LAN worlds.

## [0.34.0-alpha] - 2026-11-01

### Added
- **Decorations.** A new Decorations tab in the Build Tool: a well, a fountain, a lamppost, a
  bench, a flower bed, a market stall, a pavilion, a campfire circle, a banner pole, a hay pile,
  a path, a path crossing, a wall and a gate, each in every building style. Ordering one gives
  the builders a work order; they clear the area and build it from the Builder's Hut's
  materials.
- Every decoration that is still mostly standing gives all citizens +0.25 happiness (up to +1);
  torn-down decorations are forgotten. The town hall's Colony tab shows the bonus, and
  decoration orders can be cancelled from its work order list.
- Data packs can add decorations at `structure/decorations/<name>` (and per style), with icons
  in `decoration_icons.json`.

### Changed
- Builders lay dirt for dirt paths, farmland and grass blocks in blueprints.

## [0.33.0-alpha] - 2026-10-31

### Added
- **Children.** A child may be born each morning in a residence where two adults live (with a
  free bed). Children are half size, don't work, and grow up after an in-game day.
- **School.** With a teacher, children learn there during the day and grow up twice as fast.
- **Tavern.** Visitors arrive (one room per level), more skilled than settlers; right-click one
  holding what they ask for (bread, iron or emeralds) to recruit them, or they leave after three
  days.
- **Graveyard.** The colony mourns its dead for a day (-1 happiness); an undertaker buries them,
  ending the mourning, and sometimes brings one back to life.
- Config: `birthChance` and `visitorChance` in `[events]`.

### Changed
- Name tags and windows show "Child" or "Visitor" for children and visitors.

## [0.32.0-alpha] - 2026-10-31

### Added
- **Citizen skills.** Seven skills (Strength, Dexterity, Agility, Knowledge, Endurance, Combat,
  Marksmanship), shown in the citizen window. Each job trains its skill while working, up to
  level 50, and every level makes the citizen 1% faster in that job. Settlers arrive with a few
  random points; citizens from older worlds start at 5.
- **Library.** A librarian studies books; while it is staffed and has books, every citizen learns
  25% faster per Library level.
- **Combat Academy** and **Archery.** Guards train there every other stretch of a calm day:
  Combat (more damage and health) at the academy, Marksmanship at the archery.
- Guards with Marksmanship 5 or more shoot arrows at enemies out of sword reach, using arrows from
  their tower; towers and barracks ask for arrows once the colony has an Archery.

## [0.31.0-alpha] - 2026-10-31

### Added
- **Barracks.** Employs 2 guards per level (up to 10), who patrol around it.
- **Hospital.** A healer heals hurt citizens nearby; citizens below half health go to a staffed
  hospital until they have mostly recovered.
- **Quarry.** The quarrier digs an open pit behind the hut, layer by layer, for stone and ores;
  the pit grows wider and deeper with the hut level.
- **Stash.** A drop-off box without a worker: the deliveryman carries whatever you put in it to
  the warehouse.
- Blueprints in all five styles, workbench models, job signs and recipes for the four huts.

## [0.30.0-alpha] - 2026-10-31

### Added
- **Blacksmith's Hut.** Forges the tools and weapons huts ask for, plus iron armour and shields;
  prefers iron, then stone, with diamond gear from hut level 3.
- **Fletcher's Hut.** Makes bows, crossbows, arrows, fishing rods and leads, such as the
  fisherman's rod.
- **Stone Smeltery.** Smelts cobblestone into stone, clay into bricks and terracotta, netherrack
  into nether bricks and more in its furnaces.
- All three join the crafting chain, so tool and stone requests can be filled inside the colony.
- Blueprints in all five styles, workbench models, job signs and recipes for the three huts.

## [0.29.0-alpha] - 2026-10-31

### Added
- **Permissions screen**, opened from the town hall's Colony tab. Add online players as
  **friends** or officers, switch or remove members, and choose what friends and visitors may do:
  break blocks, place and change blocks, use doors and levers, open containers, hurt citizens and
  animals, and view hut windows.
- Friends may by default use doors, open containers and view hut windows; visitors may do nothing,
  as before. Owners and officers may always do everything.

### Changed
- Colony protection now checks each kind of action separately instead of only membership, and
  also protects citizens and animals from players who may not hurt them.
- Friends (and visitors, if allowed) can open hut windows and hand in items for requests;
  changing anything in a hut still needs an officer.

## [0.28.0-alpha] - 2026-10-31

### Added
- **Crusher's Hut.** Crushes cobblestone into gravel or sand (hut setting) and makes the sand
  other huts are short of.
- **Sifter's Hut.** Sifts gravel, sand or dirt (hut setting) for flint, nuggets, clay, seeds,
  saplings and more; rarer finds (raw metals, emeralds, diamonds, glowstone, prismarine) need a
  higher hut level.
- **Mechanic's Workshop.** Crafts redstone parts, rails, iron doors, trapdoors and bars,
  lanterns, torches, buckets, shears and flint and steel with the game's recipes, for whatever
  huts are short of, such as the miners' torches.
- **Nether Mine.** The netherworker goes on expeditions to the Nether with a sword and food, and
  comes back with netherrack, quartz, soul sand and gold, plus nether wart, glowstone, magma
  cream, blaze rods and ghast tears at higher levels.
- Blueprints in all five styles, workbench models, job signs and recipes for the four huts.
- The Crusher and Mechanic join the crafting chain ("Being crafted at the ...").

## [0.27.0-alpha] - 2026-10-31

### Added
- **15 new research**, growing the tree to 24 with two paths per branch up to tier 5 (a level 5
  University):
  - Civilian: Spacious Homes (+1 resident per Residence), Golden Age (+1 happiness), Herbal
    Remedies (citizens heal twice as fast), Welcome Wagons (settlers arrive twice as often),
    Market Days (merchants visit three times as often).
  - Technology: Delivery Carts (deliverymen 30% faster), Masterwork (+20% work speed),
    Bountiful Harvest (extra crops), Skilled Lumberjacks (faster felling), Prospectors (extra
    ore drops).
  - Combat: Plate Armour (+6 guard armour), Fortified Colony (smaller, rarer raids), First Aid
    (+10 guard health), Swift Patrols (guards 20% faster), Veteran Guards (+3 guard damage).
- The README lists the whole tree with effects and costs.

## [0.26.0-alpha] - 2026-10-31

### Added
- **Crafting chain.** The Sawmill, Stonemason, Dyer and Glassblower now make what any hut is
  waiting for, not only builders (builders still come first): for example the Dyer makes the
  Concrete Mixer's dyes and the Sawmill cuts planks for the Dyer.
- New request status **Being crafted at the ...** when the warehouse has none but a crafting hut
  with a worker makes it.

### Changed
- The deliveryman fetches crafted goods another hut is waiting for right away, instead of
  waiting until the crafting hut holds a full load.
- Crafters no longer make another batch of something while their own last batch is still
  waiting to be picked up.
- The Stonemason only takes on what the stonecutter can really make from its stone.

## [0.25.0-alpha] - 2026-10-31

### Added
- **Pirates.** Colonies with water near their edge may be raided by pirates instead of
  barbarians (half the time by default). They come ashore between the water and the town hall
  with two boats, and fight with cutlasses and bows; the Pirate Captain carries a diamond
  sword. Original skins for the Pirate, Pirate Archer and Pirate Captain.
- **Colony events**, rolled each morning for colonies with at least 3 citizens:
  - A **wandering merchant** visits the town hall for a day (10% chance).
  - A **harvest festival** every 7 days: citizens are happier until nightfall and fireworks go
    up over the town hall; the town hall window shows it.
- Config: `pirates` and `pirateChance` in `[raids]`, and a new `[events]` section
  (`wanderingMerchantChance`, `festivalInterval`).
- Commands: `/hs raid start barbarians|pirates` and `/hs event merchant|festival` (operators).

### Changed
- The Stone Walls research now makes every kind of raid smaller, not only barbarian ones.

### Fixed
- Raiders no longer spawn in water when the gathering point is next to it.

## [0.24.0-alpha] - 2026-10-31

### Added
- **Planter's Hut.** The planter cuts the sugar cane, cactus and bamboo you plant around the
  hut down to their base (so they grow back) and spreads each kind to free spots next to it,
  up to 8 plants at level 1 and 24 at level 5.
- **Alchemist's Hut.** The alchemist fills glass bottles with water, brews awkward potions with
  nether wart and finishes them with the brewing ingredients you put in the hut, three bottles at
  a time; blaze powder fuels the brewing. Asks for bottles, nether wart and blaze powder.
- **Enchanter's Hut.** The enchanter enchants books with lapis lazuli; higher hut levels give
  stronger enchantments and use more lapis. Asks for books and lapis lazuli.
- Blueprints for the three huts in all five styles, original workbench models and job signs,
  and recipes (8 planks around sugar cane, a brewing stand or lapis lazuli).

## [0.23.0-alpha] - 2026-10-31

### Added
- **Hut settings.** A Settings tab in the window of these huts; click a setting to switch it:
  - Farmer: which crop to sow (any seeds, wheat, carrots, potatoes, beetroot).
  - Lumberjack: replant saplings or not.
  - Miner: place torches or not.
  - Smeltery: smelt everything, only ores and raw metals, or only sand.
  - Cook's Hut: cook all raw food, only meat, only fish or only vegetables.
  - Shepherd, Cowhand, Chicken Herder, Swineherd and Rabbit Hutch: herd size (auto or 2 to 24)
    and whether to breed.
- What a hut requests follows its settings (for example, a farm set to carrots asks for carrots,
  and a mine without torches stops asking for them). Settings are saved with the colony.

## [0.22.0-alpha] - 2026-10-31

### Added
- **Abandoned settlements** generate in new chunks of plains, meadows, forests, taiga, savanna,
  snowy plains and cherry groves: a ruined camp around an unclaimed Town Hall, with collapsed
  hut foundations, a fire pit and a chest of starter supplies (sometimes a hut block, a Build
  Tool or a Clipboard). About as rare as pillager outposts, and never right next to villages.
- Right-click a town hall that belongs to no colony to claim it and found your colony there
  (same rules as placing one).
- Config section `[worldgen]`: `abandonedSettlements` (on/off) and `settlementChance`.

## [0.21.0-alpha] - 2026-10-31

### Added
- **Clipboard.** Use it to see every open request of your colony from anywhere, and hand items
  in with **Give** without walking back to the town hall. Crafted from sticks, paper and an
  iron nugget; also in the creative tab.

## [0.20.0-alpha] - 2026-10-31

### Added
- **Requests.** A new **Requests** tab in every hut window lists what the hut is waiting for,
  with item icons and who the request is waiting on: *Being delivered*, *In the warehouse - needs
  a deliveryman* or *Needs you*. The town hall lists every open request in the colony, with the
  hut that asked, requests that need you first.
- **Give** button on each request you carry matching items for: hands them in from your
  inventory, up to the amount asked for. The town hall can hand in items for any hut.

### Changed
- Hut window tabs share the width according to their names, so five tabs fit in the town hall.

## [0.19.0-alpha] - 2026-10-31

### Added
- **Config file** `config/hearthstead.toml`, created on the first start with a description,
  allowed range and default for every option. Settings: automatic updates, welcome message,
  colony radius, settler arrival and citizen respawn times, hunger rate, work speed, research
  speed, and raids (on/off, minimum citizens, largest raid, nightly chance and how it grows).
  Invalid values are reported in the log and replaced by their defaults; options added in
  later versions are filled in automatically.
- `/hearthstead config reload` (operators) reads the file again without restarting and lists
  any invalid values.

### Changed
- The automatic updates choice moved from `config/hearthstead-updates.json` into
  `hearthstead.toml` (`automaticUpdates = "on"`, `"off"` or `"ask"`). An existing answer is
  carried over and the old file is removed.

### Fixed
- The swineherd and rabbit herder game tests failed at random (and could stop a release from
  being published): the animals walked out of the test pen and the herd no longer counted as full.

## [0.18.0-alpha] - 2026-10-31

### Added
- **Automatic updates.** On the first start Hearthstead asks whether it may update itself.
  When turned on, it checks the GitHub releases at every start, downloads a newer jar for the
  same Minecraft version into the `mods` folder (verifying its size and SHA-256 checksum) and
  shows a message. The old jar is replaced when you close the game. The choice is saved in
  `config/hearthstead-updates.json`.

## [0.17.1-alpha] - 2026-10-31

### Changed
- The mod's source code and releases now live at
  [github.com/PikachuX0303/Hearthstead](https://github.com/PikachuX0303/Hearthstead). The
  README, the mod list "Sources" link and the chat welcome message point there.

## [0.17.0-alpha] - 2026-10-31

### Changed
- **The mod is now called Hearthstead.** New mod id `hearthstead` (was `minecolonies`), new
  package `dev.pikachux.hearthstead`, and the name is used everywhere: mod list, creative
  tab, welcome message, README, jar file (`hearthstead-<version>.jar`) and release titles.
  Hearthstead remains inspired by MineColonies and is not affiliated with its team.
- The command is now `/hearthstead`, with the short alias `/hs` (was `/minecolonies` and `/mc`).
- Blueprints, textures and other resources moved to the `hearthstead` namespace: custom
  blueprints go in `data/hearthstead/structure/`.

### Fixed
- Worlds from earlier versions keep working: old `minecolonies:` blocks, items, entities and
  the hut block entity are mapped to the new ids, and saved colonies are carried over.

## [0.16.0-alpha] - 2026-10-31

### Added
- **Rope Fence** (4 from 4 sticks and 2 string): wooden posts joined by two ropes. It
  connects to itself and other wooden fences. The sawmill makes them for builders when it
  has string.
- **Front yards.** Every building blueprint now has a rope fence two blocks in front of the
  building, open in front of the door, with a lantern on each post beside the entrance.
- **Timber style:** half-timbered buildings with a stone brick base, white plaster walls in a
  dark oak frame, and dark oak roofs and doors (675 blueprints in total).

### Changed
- **Hut blocks are workstations.** Each hut is now a wooden workbench with its job sign on
  the front and a fitting prop on top: a log stump for the lumberjack, a hay bale for the
  farmer, a cake for the baker, a bookshelf for the university, a mini beehive for the
  apiary, and so on. The town hall, university and some workshops use darker wood.

## [0.15.0-alpha] - 2026-10-31

### Changed
- **New look for hut blocks.** Every hut is a small timber house with a shingled roof and
  a wooden sign showing its job (a bell for the Town Hall, an axe for the lumberjack, a
  pickaxe for the miner, and so on). The Town Hall has a golden roof. All textures are new
  pixel art drawn by `tools/generate_ui.py`.
- **New window theme.** All mod windows use parchment panels in a dark wooden frame, leather
  list areas, and wooden buttons and tabs. The selected tab now looks like part of the page.
- **New hut selection window** for the Build Tool: a scrollable grid of tiles showing each
  hut's sign and name, instead of a list of buttons. Every hut fits, even in creative mode.

### Fixed
- Hut blocks no longer hide the faces of neighbouring blocks.

## [0.14.0-alpha] - 2026-10-30

### Added
- **Rabbit Herder** and Rabbit Hutch (8 planks around a dandelion). The rabbit herder
  breeds the rabbits around the hutch with carrots or dandelions, and once the warren is
  full (2 + 2 × hut level) takes an adult rabbit for meat and hide with a sword or axe.
- **Glassblower** and Glassblower's Hut (8 planks around glass). The glassblower smelts
  sand into glass in the furnaces of its workshop (one per level) and makes the glass
  panes and stained glass builders are waiting for, with the game's recipes.
- **Concrete Mixer** and Concrete Mixer's Hut (8 planks around a cauldron). The concrete
  mixer mixes four sand, four gravel and a dye into eight concrete powder and sets the
  powder into concrete.
- The deliveryman supplies the new huts (rabbit feed, swords, sand, gravel, dyes, coal)
  and collects their output.
- Blueprints for the three new huts in all four styles (540 in total).
- Game tests for the rabbit herder, glassblower and concrete mixer.

### Changed
- Crafting jobs share one helper for looking up the game's crafting recipes.

## [0.13.0-alpha] - 2026-10-30

### Added
- **Florist** and Florist's Hut (8 planks around a poppy). The florist keeps a flower
  garden on the grass around the building: it uses bone meal from the hut so flowers grow,
  and picks the flowers.
- **Dyer** and Dyer's Hut (8 planks around a loom). The dyer makes the dyes, coloured
  wool and beds builders are waiting for, with the game's crafting recipes: flowers into
  dyes, white wool into coloured wool, and three wool and three planks into a bed (for
  example the red beds in residences). With nothing requested, it turns flowers into dyes.
- **Beekeeper** and Apiary (8 planks around a beehive). The apiary has one beehive per
  level. The beekeeper harvests the full beehives and bee nests nearby, as honey bottles
  when it has glass bottles and as honeycomb with shears otherwise. Bees are not angered.
  Bring the bees yourself.
- The deliveryman supplies the new huts (bone meal, flowers, white wool, planks, shears)
  and collects their output.
- Blueprints for the three new huts in all four styles (480 in total).
- Game tests for the florist, dyer and beekeeper.

### Changed
- The sawmill, stonemason and dyer share one check for what builders are missing.

## [0.12.0-alpha] - 2026-10-30

### Added
- **Chicken Herder** and Chicken Herder's Hut (8 planks around an egg). The chicken
  herder breeds the chickens around the hut with seeds and gathers the eggs and feathers
  they leave lying around.
- **Swineherd** and Swineherd's Hut (8 planks around a carrot). The swineherd breeds the
  pigs around the hut with carrots, potatoes or beetroot. Once the herd reaches its limit
  (2 + 2 × hut level), it butchers an adult pig for pork with a sword or axe from the hut.
- **Stonemason** and Stonemason's Hut (8 planks around a stonecutter). The stonemason cuts
  stone, cobblestone, sandstone, deepslate, bricks and more into the stone parts builders
  are waiting for and the warehouse cannot supply (stone bricks, stairs, slabs, walls,
  cut and polished blocks), using the game's stonecutter recipes.
- Herders pick up their produce anywhere in their working area; the shepherd also
  collects stray wool.
- The deliveryman supplies the new huts (seeds, pig feed, swords, stone) and collects
  their output.
- Blueprints for the three new huts in all four styles (420 in total).
- Game tests for the chicken herder, swineherd and stonemason.

### Changed
- Each herder breeds its animals with the right food (seeds for chickens, carrots,
  potatoes or beetroot for pigs, wheat for cows and sheep).
- Herding tests retry up to three times, since animals move at random.

## [0.11.0-alpha] - 2026-10-30

### Added
- **Building styles.** Every hut can be built in one of four styles, each with all 18 huts
  and 5 levels (360 blueprints):
  - **Classic**: the original buildings.
  - **Medieval**: stone brick walls, spruce timber, dark oak roofs.
  - **Desert**: sandstone walls and flat sandstone roofs.
  - **Nordic**: spruce walls, cobblestone floors, spruce roofs.
- The colony has a default style for new huts, set with **Change Style** on the Town Hall's
  Colony tab.
- The Build Tool has a style switcher (◀ Style ▶) to preview a hut in each style before
  placing it, or to pick the style of an upgrade.
- Each building remembers its style; upgrades use it. The hut window shows the style.
- Data packs can add styles: blueprints in `data/minecolonies/structure/styles/<style>/`
  appear in the style list automatically. A style missing a hut or level falls back to
  classic.
- Game tests: every style has every blueprint, and a desert-style hut is built from
  sandstone. The client test places a desert-style hut with the Build Tool.

### Fixed
- Game tests that shared a test environment could remove each other's colony and fail
  at random; every colony test now has its own environment.

## [0.10.0-alpha] - 2026-10-30

### Added
- **Composter** and Composter's Hut (8 planks around a composter). The composter fills
  the composters in the building (two per level) with plant waste such as leaves and
  saplings, using the vanilla composting rules, and collects the bone meal.
- **Sawmill** (8 planks around an iron axe). The sawmill worker saws logs into what
  builders are waiting for and the warehouse cannot supply: planks, sticks, slabs, stairs,
  doors, trapdoors, fences and fence gates of every wood type. Results come from the
  game's own crafting recipes. With nothing requested, it keeps 64 planks in stock.
- **Cowhand** and Cowhand's Hut (8 planks around a bucket). The cowhand milks the cows
  near the hut into buckets from the hut (each cow every few minutes) and breeds them
  with wheat while the herd is smaller than 2 + 2 × hut level.
- Farmers use bone meal from their hut on growing crops when there is nothing else to do.
- The deliveryman supplies the new huts (plant waste, logs, buckets, wheat) and the
  farmer's bone meal, and collects their output.
- Blueprints for the three new huts (90 in total). The composter's has two composters
  per level.
- Game tests for the composter, the sawmill (crafting and planks) and the cowhand, and
  for farmers using bone meal.

### Changed
- The shepherd and the cowhand share the same herding code.

### Fixed
- Herders no longer get stuck outside a pen whose way in they cannot find; like other
  workers, they step over to the animal.

## [0.9.0-alpha] - 2026-10-30

### Added
- **University** (8 planks around a book) and the **Researcher** job. The University
  employs one researcher per level. Researchers study during the day and add about one
  research point per second (more when happy) to the colony's active research.
- **Research tree** with three branches of three tiers each. A research needs its
  parent, a University of at least its tier, and materials stored in the University,
  which are used up when it starts:
  - Civilian: Community Spirit (+1 happiness), Comfortable Homes (+1 resident per
    Residence), Feast Days (30% slower hunger).
  - Technology: Apprenticeship (+10% work speed), Swift Construction (builders about
    40% faster), Master Craftsmen (+15% work speed).
  - Combat: Militia Drills (+2 guard damage), Chainmail Training (+4 guard armour),
    Stone Walls (raids 25% smaller).
- **Research** tab in the University window: the tree with each research's state and
  progress. Select one to see what it does, why it is locked, its cost (green when the
  University has enough) and a **Start Research** button.
- A chat message for the colony when a research finishes.
- `/mc research complete <research>` for operators.
- University blueprints lined with bookshelves (75 blueprints in total).
- Game test for research and screenshots of the Research tab.

## [0.8.0-alpha] - 2026-10-30

### Added
- **Baker** and Bakery (8 planks around wheat). The baker turns wheat stored in the
  bakery into bread: three wheat per loaf, one loaf per hut level each batch. Citizens
  can eat at the bakery.
- **Smelter** and Smeltery (8 planks around a furnace). The smelter runs the furnaces in
  the smeltery (one per level), smelting ores, raw metals and sand into ingots and glass
  with coal or charcoal. Miners' finds can now go from the warehouse to the smelter and
  on to the builder.
- **Shepherd** and Shepherd's Hut (8 planks around shears). The shepherd shears the
  sheep near the hut with shears from the hut, and breeds them with wheat while the
  flock is smaller than 2 + 2 × hut level.
- The deliveryman supplies the new huts (wheat, ores and sand, coal, shears) and brings
  their bread, ingots, glass and wool to the warehouse.
- Blueprints for the three new huts (70 in total). The smeltery has a furnace per level.
- Game tests for the baker, smelter and shepherd.

### Changed
- The Build Tool's hut list uses three columns, so all huts fit.
- The cook and the smelter share the same furnace logic.

## [0.7.1-alpha] - 2026-10-30

### Changed
- Added FallenStar114 to the credits: mod authors (in-game mod list), README, release
  notes and the chat welcome message, which now reads "Created with ❤ by PerniciousPx &
  FallenStar114".

## [0.7.0-alpha] - 2026-10-30

### Added
- **Build Tool** (crafted from two sticks and a sheet of paper, diagonally). Previews
  buildings before you commit to them:
  - Right-click a block to pick a hut from your inventory (every hut in creative). Its
    level 1 building appears as a see-through ghost with an outline of the area the
    builder will use.
  - Blocks in red show where something else is in the way; the builder will clear it.
  - Move the preview with the arrow buttons or arrow keys, raise or lower it with
    PgUp/PgDn, rotate it with R, and press **Place Hut** (or Enter) to place the hut
    there. A work order is created as usual.
  - Esc hides the panel so you can look around. Right-click again with the tool to bring
    it back, or right-click a block to move the preview there.
  - Right-click an existing hut to preview its next level, with an **Order Upgrade**
    button.
  - Previews use the server's blueprints, so custom data pack and structure block
    designs show up too.
- Build Tool item icon, drawn by `tools/generate_textures.py`.
- Client test that places a hut through the Build Tool.

## [0.6.0-alpha] - 2026-10-30

### Added
- **Barbarian raids.** At nightfall, a colony with at least 4 citizens may be raided. The
  chance grows with every quiet night.
  - Raiders gather at the edge of the colony and march on the Town Hall, attacking
    citizens and colony members on the way.
  - Members get a warning in chat with the direction of the attack, and a raid horn
    sounds.
  - A boss bar shows the raiders left to anyone inside the colony.
  - The raid ends when every raider is dead, or at sunrise when the survivors retreat.
  - Raid size grows with the colony (up to 16). Archers join from 6 citizens, and a
    chief from 10.
- Three new hostile mobs with their own skins, drawn by `tools/generate_textures.py`:
  - **Barbarian**: stone or iron axe.
  - **Barbarian Archer**: bow.
  - **Barbarian Chief**: 60 health, armour, iron sword, resistant to knockback.
- Loot from barbarians: leather, iron nuggets and arrows. Chiefs drop iron ingots,
  emeralds and sometimes a golden apple.
- Raid controls:
  - Owners can switch raids off with the **Toggle Raids** button on the Town Hall's
    Colony tab or with `/mc raids on|off`.
  - Operators can start or stop a raid with `/mc raid start|stop`.
  - No raids happen on Peaceful.
- The Town Hall window shows a running raid and whether raids are enabled.
- Game tests for raids and for a guard fighting a barbarian.

### Changed
- Guards are trained fighters: +20 health, +6 armour and +2 attack while they hold the job.
- Non-guard citizens flee from barbarians.

## [0.5.0-alpha] - 2026-10-30

### Added
- A welcome message in chat whenever a player joins a world or server with the mod:
  the mod name and version, "Created with ❤ by PerniciousPx", and clickable
  **Releases** and **Changelog** links.

## [0.4.1-alpha] - 2026-10-30

### Changed
- Added PerniciousPx to the credits: mod authors (shown in the in-game mod list),
  README and release notes.

## [0.4.0-alpha] - 2026-10-30

### Added
- **Deliveryman** and Deliveryman's Hut (8 planks around leather). The deliveryman
  carries items between the Warehouse and the huts:
  - brings builders the materials their current work order still needs,
  - brings tools (axe, hoe, pickaxe, sword, fishing rod), seeds and torches to workers,
  - brings food to the Town Hall and Residences, and raw food and coal to the Cook,
  - empties worker huts (lumberjack, farmer, miner, fisherman, cook) into the Warehouse.
- **Cook** and Cook's Hut (8 planks around a smoker). The cook loads the smokers in the
  kitchen (one per level) with raw food and coal or charcoal, and stores the meals in
  the hut.
- **Fisherman** and Fisherman's Hut (8 planks around a fishing rod). The fisherman fishes
  in open water near the hut with a rod from the hut, using the vanilla fishing loot.
- The Warehouse building now has barrels along its walls (two rows high from level 3),
  and they all count as warehouse storage.
- The hut window shows what a hut is waiting for, for example "Waiting for: 16x Food".
- Blueprints for the three new huts (15 more, 55 in total).
- Game tests for the cook, fisherman and deliveryman.

### Changed
- Citizens also eat at the Cook's Hut and from the Warehouse, and prefer cooked meals
  over raw food.

### Fixed
- Workers no longer pick a spot in water as a place to stand.

## [0.3.0-alpha] - 2026-10-30

### Added
- Hut window, opened by right-clicking a hut:
  - **Overview** tab: level, residents, workers and their current status.
  - **Construction** tab: work order, progress bar and a materials list showing how
    many of each material the Builder's Huts already hold.
  - **Inventory**, **Build** / **Upgrade** / **Cancel Order** buttons. When an upgrade
    isn't possible, the button is disabled and its tooltip says why.
- Town Hall **Citizens** tab (every citizen with job, happiness and status) and
  **Colony** tab (statistics, renaming the colony, open work orders).
- Citizen window, opened by right-clicking a citizen: model preview, health, food and
  happiness bars, status, home, workplace and carried items.
- Open hut windows refresh every two seconds.
- Client game test that opens every window and saves screenshots.

### Changed
- Sneak + right-click on a hut now opens its inventory directly.
- Hut information is shown in the hut window instead of in chat.

## [0.2.0-alpha] - 2026-10-30

### Added
- Schematic-based building. Huts are built from blueprints (vanilla structure `.nbt`
  files) by a Builder.
- Builder's Hut and the Builder job. Builders take work orders, fetch materials from
  their hut and place blueprints block by block.
- 40 default blueprints (8 huts × 5 levels), generated by `tools/generate_schematics.py`.
- Custom blueprints through data packs or structure blocks saved as
  `minecolonies:huts/<hut>/level<N>`.
- Commands `/mc cancel`, `/mc workorders` and `/mc build` (operators only, instant).
- Server game tests: all blueprints load, and a builder builds its own hut.

### Changed
- Placed huts start at level 0 and get a work order for level 1.
- Upgrades now create work orders for the Builder instead of consuming materials
  stored in the hut.
- The farmer's field and the miner's tunnel are outside the building. Lumberjacks no
  longer chop logs that belong to buildings.

### Fixed
- New citizens spawn next to the Town Hall instead of on its roof.
- Workers that can't find a path step over to their target instead of getting stuck.

## [0.1.1-alpha] - 2026-10-30

### Fixed
- Lowered the required Fabric Loader version to 0.16.0, so launchers with an older
  loader can load the mod.

## [0.1.0-alpha] - 2026-10-30

### Added
- First release for Minecraft 1.21.11.
- Colonies founded with a Town Hall, with land protection.
- Citizens with names, skins, hunger, happiness, homes and automatic job assignment.
- Huts: Town Hall, Residence, Lumberjack, Farmer, Miner, Guard Tower and Warehouse.
- `/minecolonies` (`/mc`) commands.
