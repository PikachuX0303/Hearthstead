#!/usr/bin/env python3
"""Generates the default hut schematics as vanilla structure (.nbt) files.

Output: src/main/resources/data/hearthstead/structure/huts/<hut>/level<N>.nbt          (classic style)
        src/main/resources/data/hearthstead/structure/styles/<style>/<hut>/level<N>.nbt (other styles)

Every template is authored with the hut block facing north. The hut block marks
the anchor: the template is rotated and positioned so that its hut block lines
up with the hut placed in the world.
"""
import gzip
import os
import struct

DATA_VERSION = 4671  # Minecraft 1.21.11
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "data", "hearthstead", "structure", "huts")

# ----------------------------------------------------------------------------- NBT writer
TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


class Int(int):
    pass


def _payload(value):
    if isinstance(value, Int):
        return TAG_INT, struct.pack(">i", value)
    if isinstance(value, str):
        data = value.encode("utf-8")
        return TAG_STRING, struct.pack(">H", len(data)) + data
    if isinstance(value, dict):
        out = b""
        for key, child in value.items():
            tag, data = _payload(child)
            name = key.encode("utf-8")
            out += bytes([tag]) + struct.pack(">H", len(name)) + name + data
        return TAG_COMPOUND, out + bytes([TAG_END])
    if isinstance(value, list):
        if not value:
            return TAG_LIST, bytes([TAG_END]) + struct.pack(">i", 0)
        tags = [_payload(v) for v in value]
        return TAG_LIST, bytes([tags[0][0]]) + struct.pack(">i", len(tags)) + b"".join(t[1] for t in tags)
    raise TypeError(value)


def write_nbt(path, root):
    _, data = _payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(bytes([TAG_COMPOUND]) + struct.pack(">H", 0) + data)


# ----------------------------------------------------------------------------- building model
def block(name, **props):
    if ":" not in name:
        name = "minecraft:" + name
    return (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))


AIR = block("air")

STYLES = {
    #               wall                pillar         floor                  roof wood
    "townhall":   ("stone_bricks",     "spruce_log",   "polished_andesite",   "spruce"),
    "residence":  ("oak_planks",       "oak_log",      "spruce_planks",       "dark_oak"),
    "lumberjack": ("spruce_planks",    "oak_log",      "oak_planks",          "spruce"),
    "farmer":     ("birch_planks",     "birch_log",    "oak_planks",          "oak"),
    "miner":      ("cobblestone",      "spruce_log",   "stone_bricks",        "spruce"),
    "guardtower": ("stone_bricks",     "spruce_log",   "stone_bricks",        "dark_oak"),
    "warehouse":  ("spruce_planks",    "dark_oak_log", "stone_bricks",        "dark_oak"),
    "builder":    ("oak_planks",       "spruce_log",   "cobblestone",         "oak"),
    "deliveryman": ("birch_planks",    "spruce_log",   "spruce_planks",       "spruce"),
    "cook":       ("bricks",           "oak_log",      "stone_bricks",        "dark_oak"),
    "fisherman":  ("spruce_planks",    "stripped_spruce_log", "oak_planks",   "oak"),
    "baker":      ("birch_planks",     "stripped_oak_log", "bricks",          "spruce"),
    "smelter":    ("stone_bricks",     "dark_oak_log", "cobblestone",         "dark_oak"),
    "shepherd":   ("oak_planks",       "birch_log",    "spruce_planks",       "birch"),
    "university": ("stone_bricks",     "dark_oak_log", "dark_oak_planks",     "dark_oak"),
    "composter":  ("oak_planks",       "spruce_log",   "coarse_dirt",         "spruce"),
    "sawmill":    ("stripped_oak_log", "oak_log",      "oak_planks",          "oak"),
    "cowhand":    ("spruce_planks",    "oak_log",      "spruce_planks",       "spruce"),
    "chickenherder": ("birch_planks",  "oak_log",      "oak_planks",          "birch"),
    "swineherd":  ("packed_mud",       "spruce_log",   "spruce_planks",       "spruce"),
    "stonemason": ("polished_andesite", "stripped_spruce_log", "stone_bricks", "spruce"),
    "florist":    ("birch_planks",     "stripped_birch_log", "oak_planks",    "birch"),
    "dyer":       ("white_terracotta", "oak_log",      "spruce_planks",       "oak"),
    "beekeeper":  ("oak_planks",       "birch_log",    "oak_planks",          "oak"),
    "rabbitherder": ("spruce_planks",  "birch_log",    "oak_planks",          "birch"),
    "glassblower": ("sandstone",       "stripped_birch_log", "smooth_stone",  "birch"),
    "concretemixer": ("light_gray_concrete", "spruce_log", "gray_concrete",   "spruce"),
    "planter":    ("jungle_planks",    "jungle_log",   "coarse_dirt",         "jungle"),
    "alchemist":  ("deepslate_bricks", "dark_oak_log", "polished_deepslate",  "dark_oak"),
    "enchanter":  ("stone_bricks",     "birch_log",    "dark_oak_planks",     "dark_oak"),
    "crusher":    ("cobblestone",      "spruce_log",   "andesite",            "spruce"),
    "sifter":     ("birch_planks",     "oak_log",      "sandstone",           "oak"),
    "mechanic":   ("bricks",           "dark_oak_log", "polished_andesite",   "dark_oak"),
    "netherworker": ("nether_bricks",  "crimson_stem", "blackstone",          "crimson"),
    "blacksmith": ("stone_bricks",     "spruce_log",   "cobblestone",         "spruce"),
    "fletcher":   ("birch_planks",     "spruce_log",   "oak_planks",          "birch"),
    "stonesmeltery": ("cobblestone",   "stripped_spruce_log", "stone_bricks", "spruce"),
    "barracks":   ("stone_bricks",     "dark_oak_log", "spruce_planks",       "dark_oak"),
    "hospital":   ("white_concrete",   "birch_log",    "smooth_quartz",       "birch"),
    "quarry":     ("cobblestone",      "oak_log",      "andesite",            "oak"),
    "stash":      ("oak_planks",       "oak_log",      "oak_planks",          "oak"),
}

# Architectural styles. "classic" uses the per-hut palette above; the others give every
# hut the same look. roof is a wood type (pyramid roofs) or a stone block (flat roofs).
ARCH_STYLES = {
    "medieval": dict(wall="stone_bricks", pillar="stripped_spruce_log", floor="spruce_planks",
                     roof="dark_oak", roof_type="pyramid", door="spruce"),
    "desert":   dict(wall="sandstone", pillar="cut_sandstone", floor="smooth_sandstone",
                     roof="smooth_sandstone", roof_type="flat", door="acacia", trim="cut_sandstone"),
    "nordic":   dict(wall="spruce_planks", pillar="stripped_dark_oak_log", floor="cobblestone",
                     roof="spruce", roof_type="pyramid", door="spruce"),
    # Half-timbered: stone base course, white plaster between dark oak beams.
    "timber":   dict(wall="white_concrete", pillar="stripped_dark_oak_log", floor="spruce_planks",
                     roof="dark_oak", roof_type="pyramid", door="dark_oak",
                     base="stone_bricks", beam="stripped_dark_oak_log"),
}
STYLE_OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                         "data", "hearthstead", "structure", "styles")


def palette(hut, style):
    if style == "classic":
        wall, pillar, floor, wood = STYLES[hut]
        return dict(wall=wall, pillar=pillar, floor=floor, roof=wood, roof_type="pyramid", door=wood)
    return ARCH_STYLES[style]


SIZES = [7, 9, 11, 11, 13]
HEIGHTS = [3, 3, 4, 4, 5]


def build(hut, level, style="classic"):
    p = palette(hut, style)
    wall, pillar, floor, wood = p["wall"], p["pillar"], p["floor"], p["roof"]
    s = SIZES[level - 1]
    h = HEIGHTS[level - 1] + (2 if hut == "guardtower" else 0)
    c = s // 2
    blocks = {}

    def put(x, y, z, state):
        blocks[(x, y, z)] = state

    # floor and walls
    for x in range(s):
        for z in range(s):
            put(x, 0, z, block(floor))
            edge = x in (0, s - 1) or z in (0, s - 1)
            corner = x in (0, s - 1) and z in (0, s - 1)
            for y in range(1, h + 1):
                if corner:
                    put(x, y, z, block(pillar, axis="y") if pillar.endswith("_log") else block(pillar))
                elif edge:
                    along = x if z in (0, s - 1) else z
                    window = y == 2 and (along - c) % 2 == 0
                    if "beam" in p:
                        # timber frame: base course, uprights every third block and, on taller
                        # buildings, a beam along the top
                        axis = "x" if z in (0, s - 1) else "z"
                        if y == 1:
                            put(x, y, z, block(p["base"]))
                        elif along % 3 == 0:
                            put(x, y, z, block(p["beam"], axis="y"))
                        elif y == h and h >= 4:
                            put(x, y, z, block(p["beam"], axis=axis))
                        else:
                            put(x, y, z, block("glass") if window else block(wall))
                    else:
                        put(x, y, z, block("glass") if window else block(wall))
                else:
                    put(x, y, z, AIR)

    # flat roof: a rim of full blocks around a layer of slabs
    if p["roof_type"] == "flat":
        for x in range(s):
            for z in range(s):
                rim = x in (0, s - 1) or z in (0, s - 1)
                put(x, h + 1, z, block(p["trim"]) if rim else block(wood + "_slab", type="bottom", waterlogged=False))

    # pyramid roof made of stairs, with planks on the corners and a slab on top
    k = 0
    while p["roof_type"] == "pyramid":
        lo, hi = k, s - 1 - k
        y = h + 1 + k
        if lo > hi:
            break
        if lo == hi:
            put(lo, y, lo, block(wood + "_slab", type="bottom", waterlogged=False))
            break
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x in (lo, hi) and z in (lo, hi):
                    state = block(wood + "_planks")
                elif z == lo:
                    state = block(wood + "_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
                elif z == hi:
                    state = block(wood + "_stairs", facing="north", half="bottom", shape="straight", waterlogged=False)
                elif x == lo:
                    state = block(wood + "_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
                elif x == hi:
                    state = block(wood + "_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
                else:
                    state = AIR
                put(x, y, z, state)
        k += 1

    # front door (north wall)
    door = dict(facing="south", hinge="left", open=False, powered=False)
    put(c, 1, 0, block(p["door"] + "_door", half="lower", **door))
    put(c, 2, 0, block(p["door"] + "_door", half="upper", **door))

    # front yard: a rope fence two blocks in front of the wall, open in front of the door,
    # with a lantern on each post beside the entrance
    yard = [x for x in range(s) if abs(x - c) > 1]
    for x in yard:
        put(x, 1, -2, block("hearthstead:rope_fence", east=(x + 1) in yard, west=(x - 1) in yard,
                            north=False, south=False, waterlogged=False))
    for x in (c - 2, c + 2):
        put(x, 2, -2, block("lantern", hanging=False, waterlogged=False))

    # lighting
    put(1, 2, 1, block("wall_torch", facing="east"))
    put(s - 2, 2, s - 2, block("wall_torch", facing="west"))
    put(s - 2, 2, 1, block("wall_torch", facing="west"))
    put(1, 2, s - 2, block("wall_torch", facing="east"))

    # beds for residences: two per level along the side walls
    if hut == "residence":
        beds = 0
        for z in range(1, s - 1, 2):
            for head_x, foot_x, facing in ((s - 2, s - 3, "east"), (1, 2, "west")):
                if beds >= 2 * level:
                    break
                put(head_x, 1, z, block("red_bed", facing=facing, part="head", occupied=False))
                put(foot_x, 1, z, block("red_bed", facing=facing, part="foot", occupied=False))
                beds += 1

    # smokers along the back wall of the kitchen: one per level
    if hut == "cook":
        for i in range(level):
            put(2 + i, 1, s - 2, block("smoker", facing="north", lit=False))

    # furnaces along the back wall of the smelteries and the glassblower's workshop: one per level
    if hut in ("smelter", "glassblower", "stonesmeltery"):
        for i in range(level):
            put(2 + i, 1, s - 2, block("furnace", facing="north", lit=False))

    # composters along the back wall: two per level
    if hut == "composter":
        for i in range(min(2 * level, s - 4)):
            put(2 + i, 1, s - 2, block("composter", level=0))

    # beehives along the back wall of the apiary: one per level
    if hut == "beekeeper":
        for i in range(level):
            put(2 + i, 1, s - 2, block("beehive", facing="north", honey_level=0))

    # bookshelves along the side walls of the university: more shelves per level
    if hut == "university":
        for z in range(2, min(s - 2, 2 + 2 * level)):
            for x in (1, s - 2):
                put(x, 1, z, block("bookshelf"))
                if level >= 2:
                    put(x, 2, z, block("bookshelf"))

    # warehouse racks: barrels along both side walls, two high from level 3
    if hut == "warehouse":
        for z in range(1, s - 1):
            for x in (1, s - 2):
                put(x, 1, z, block("barrel", facing="up", open=False))
                if level >= 3 and z not in (1, s - 2):
                    put(x, 2, z, block("barrel", facing="up", open=False))

    # small job specific touches next to the hut
    decor = {"farmer": block("composter", level=0), "builder": block("crafting_table"),
             "lumberjack": block("oak_log", axis="y"), "miner": block("stonecutter", facing="north"),
             "townhall": block("lectern", facing="north", has_book=False, powered=False),
             "warehouse": block("barrel", facing="up", open=False),
             "deliveryman": block("barrel", facing="up", open=False),
             "fisherman": block("barrel", facing="up", open=False),
             "cook": block("crafting_table"),
             "baker": block("crafting_table"),
             "smelter": block("anvil", facing="north"),
             "shepherd": block("hay_block", axis="y"),
             "university": block("lectern", facing="north", has_book=False, powered=False),
             "composter": block("composter", level=0),
             "sawmill": block("stonecutter", facing="north"),
             "cowhand": block("hay_block", axis="y"),
             "chickenherder": block("hay_block", axis="y"),
             "swineherd": block("composter", level=0),
             "stonemason": block("stonecutter", facing="north"),
             "florist": block("potted_poppy"),
             "dyer": block("loom", facing="north"),
             "beekeeper": block("campfire", facing="north", lit=False, signal_fire=False, waterlogged=False),
             "rabbitherder": block("hay_block", axis="y"),
             "glassblower": block("crafting_table"),
             "concretemixer": block("water_cauldron", level=3),
             "planter": block("potted_cactus"),
             "alchemist": block("brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False),
             "enchanter": block("enchanting_table"),
             "crusher": block("grindstone", face="floor", facing="north"),
             "sifter": block("barrel", facing="up", open=False),
             "mechanic": block("smithing_table"),
             "netherworker": block("crying_obsidian"),
             "blacksmith": block("anvil", facing="north"),
             "fletcher": block("fletching_table"),
             "stonesmeltery": block("crafting_table"),
             "barracks": block("target", power=0),
             "hospital": block("white_carpet"),
             "quarry": block("stonecutter", facing="north"),
             "stash": block("barrel", facing="up", open=False)}
    if hut in decor:
        put(c + 1, 1, c + 1, decor[hut])

    # the hut itself (anchor)
    put(c, 1, c, block("hearthstead:blockhut" + hut, facing="north"))
    height = h + 2 if p["roof_type"] == "flat" else h + 1 + (s + 1) // 2
    return (s, height, s), blocks


def to_nbt(size, blocks):
    # Shift everything so the smallest coordinate is 0 (the yard lies in front of z = 0).
    low = [min(p[i] for p in blocks) for i in range(3)]
    size = [max(p[i] for p in blocks) - low[i] + 1 for i in range(3)]
    blocks = {(p[0] - low[0], p[1] - low[1], p[2] - low[2]): state for p, state in blocks.items()}
    palette, index, entries = [], {}, []
    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        state = blocks[pos]
        if state not in index:
            index[state] = len(palette)
            entry = {"Name": state[0]}
            if state[1]:
                entry["Properties"] = {k: v for k, v in state[1]}
            palette.append(entry)
        entries.append({"pos": [Int(pos[0]), Int(pos[1]), Int(pos[2])], "state": Int(index[state])})
    return {
        "DataVersion": Int(DATA_VERSION),
        "size": [Int(v) for v in size],
        "palette": palette,
        "blocks": entries,
        "entities": [],
    }


def main():
    count = 0
    for style in ["classic"] + list(ARCH_STYLES):
        for hut in STYLES:
            for level in range(1, 6):
                size, blocks = build(hut, level, style)
                folder = os.path.join(OUT, hut) if style == "classic" else os.path.join(STYLE_OUT, style, hut)
                write_nbt(os.path.join(folder, f"level{level}.nbt"), to_nbt(size, blocks))
                count += 1
    print("wrote", count, "schematics")


if __name__ == "__main__":
    main()
