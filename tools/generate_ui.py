#!/usr/bin/env python3
"""Draws the hut block textures and the GUI theme (all original pixel art).

Output:
  src/main/resources/assets/hearthstead/textures/block/hut_sign_*.png   hut job signs
  src/main/resources/assets/hearthstead/textures/block/rope*.png        rope fence
  src/main/resources/assets/hearthstead/models/block/*.json             hut workbenches, rope fence
  src/main/resources/assets/hearthstead/textures/gui/sprites/*.png      window theme (nine-slice)
"""
import json
import os
import random
import zlib

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hearthstead", "textures")
BLOCK = os.path.join(ROOT, "block")
GUI = os.path.join(ROOT, "gui", "sprites")


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


def jitter(c, rng, amount):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)


# ----------------------------------------------------------------------------- hut blocks
PLAQUE = (196, 160, 106)
PLAQUE_EDGE = (110, 74, 40)


# Pictograms for the hut signs, 10x10. '.' is the sign background.
ICON_COLORS = {
    "k": (40, 30, 24), "w": (235, 230, 220), "g": (150, 150, 158), "G": (90, 90, 98),
    "y": (226, 186, 60), "Y": (170, 128, 30), "b": (120, 80, 44), "B": (84, 54, 28),
    "r": (196, 52, 44), "R": (130, 30, 26), "e": (70, 150, 60), "E": (40, 100, 40),
    "u": (70, 120, 200), "U": (40, 70, 140), "p": (232, 150, 160), "o": (230, 130, 40),
    "s": (190, 190, 170), "c": (150, 210, 230), "v": (140, 70, 200), "V": (200, 140, 240),
}
ICONS = {
    "townhall": ["....yy....", "...yyyy...", "...yYYy...", "..yyyyyy..", "..yYyyYy..",
                 "..yyyyyy..", ".yyyyyyyy.", ".YYYYYYYY.", "....kk....", "....kk...."],
    "residence": [".....r....", "....rrr...", "...rrrrr..", "..rrrrrrr.", ".rrrrrrrrr",
                  "..bbbbbbb.", "..bwbbbwb.", "..bbbkbbb.", "..bbbkbbb.", "..bbbkbbb."],
    "lumberjack": ["..ggg.....", ".gggggb...", ".ggggGb...", "..gGG.b...", "......b...",
                   ".....b....", ".....b....", "....b.....", "....b.....", "...b......"],
    "farmer": ["....y.....", "...yYy....", "....y.y...", "..y.yYy...", ".yYy.y....",
               "..y..e....", "....eE....", "...e.e....", "....e.....", "....E....."],
    "miner": [".gggggggg.", "gG.....Gg.", "g...bb..Gg", "...b......", "..b.......",
              ".b........", "b.........", "..........", "..........", ".........."],
    "guardtower": ["........gw", ".......gw.", "......gw..", ".....gw...", ".y..gw....",
                   "..ygw.....", "..by......", ".b..y.....", "b.........", ".........."],
    "warehouse": ["..........", ".BBBBBBBB.", ".BbbBbbbB.", ".BbBbbbBB.", ".BbbBbBbB.",
                  ".BbbbBbbB.", ".BBbbbBbB.", ".BbbbbbBB.", ".BBBBBBBB.", ".........."],
    "builder": ["..ggggg...", ".gggggG...", "..GGgG....", "....b.....", "....b.....",
                "....b.....", "....b.....", "....b.....", "....B.....", "....B....."],
    "deliveryman": ["....b.....", "...bBb....", "..bbbbb...", ".bbbbbbb..", ".bbBbbbb..",
                    ".bbbbbBb..", ".bbbbbbb..", "..bbbbb...", "..........", ".........."],
    "cook": ["...w.w....", "....w.w...", "..........", ".GGGGGGGG.", ".GgggggG..",
             ".GgggggG..", "..GgggG...", "...GGG....", "..o.o.o...", "..ooooo..."],
    "fisherman": ["..........", "......u...", "..uuuuuu..", ".uuuwuuuuu", "uuuuuuuuuU",
                  ".uuuuuuuu.", "..uuuuuu..", "......u...", "..........", ".........."],
    "baker": ["..........", "...yyyy...", "..yYyyYy..", ".yyyyyyyy.", ".yYyyYyyy.",
              ".yyyyyyYy.", "..YYYYYY..", "..........", "..........", ".........."],
    "smelter": ["..........", "..........", "...wwwww..", "..wsssssg.", ".wsssssgg.",
                ".gggggggG.", "..........", "...o.o....", "..ooooo...", ".ooroooo.."],
    "shepherd": ["...ww.....", "..wwwww...", ".wwwwwwww.", ".wwwwwwww.", "kkwwwwwww.",
                 "kwkwwwwww.", ".kk.ww.ww.", "....k..k..", "....k..k..", ".........."],
    "university": [".rrrrrrr..", ".rwwwwwwr.", ".rwkkwwwr.", ".rwwwwwwr.", ".rwkkkwwr.",
                   ".rwwwwwwr.", ".rwkkwwwr.", ".rrrrrrrr.", ".RRRRRRR..", ".........."],
    "composter": ["....e.....", "...eEe....", "..e.e.e...", ".BBBBBBBB.", ".BbbbbbbB.",
                  ".BbBbbBbB.", ".BbbbbbbB.", ".BbBbbBbB.", ".BbbbbbbB.", ".BBBBBBBB."],
    "sawmill": ["...gggg...", "..gGGGGg..", ".gG....Gg.", ".gG.bb.Gg.", ".gG.bb.Gg.",
                ".gG....Gg.", "..gGGGGg..", "...gggg...", "..........", ".........."],
    "cowhand": ["..........", "..gggggg..", ".gwwwwwwg.", ".gwwwwwwg.", ".gwwwwwwg.",
                ".ggwwwwgg.", "..gggggg..", "..g....g..", "...gggg...", ".........."],
    "chickenherder": ["....ww....", "...wwww...", "..wwwwww..", "..wwwwww..", ".wwwwwwww.",
                      ".wwwwwwww.", ".wwwwwwww.", "..wwwwww..", "...wwww...", ".........."],
    "swineherd": ["..........", ".pp....pp.", ".pppppppp.", "pppkppkppp", "pppppppppp",
                  "pp.pRRp.pp", "pp.RkkR.pp", "pp.pRRp.pp", ".pppppppp.", "..pppppp.."],
    "stonemason": ["..........", ".gggggggg.", ".gGggGggg.", ".GGGGGGGG.", ".ggGgggGg.",
                   ".gggGgggg.", ".GGGGGGGG.", ".gGgggGgg.", ".gggggggg.", ".........."],
    "florist": ["...pp.....", "..pyyp....", "..pyyp....", "...pp.....", "....e.....",
                "..e.e.....", "...ee..e..", "....e.ee..", "....ee....", "....e....."],
    "dyer": ["....u.....", "....u.....", "...uuu....", "..uuuuu...", ".uuuuuuu..",
             ".uuwuuuu..", ".uuuuuuu..", "..uuuuu...", "...UUU....", ".........."],
    "beekeeper": ["...ww.ww..", "...wwwww..", "..yykkyyk.", ".yykkyykkk", ".yykkyykk.",
                  "..yykkyy..", "...kk.....", "..........", "..........", ".........."],
    "rabbitherder": ["......E.E.", ".......E..", "......oo..", ".....ooo..", "....ooo...",
                     "...ooo....", "..ooo.....", ".oo.......", "o.........", ".........."],
    "glassblower": ["....cc....", "....cc....", "...c..c...", "..c....c..", ".c......c.",
                    ".c.cc...c.", ".c......c.", "..c....c..", "...cccc...", ".........."],
    "concretemixer": ["..........", ".GGGGGGGG.", ".GgGgGgGG.", ".GgggggGG.", ".GgGgGgGG.",
                      ".GGGGGGGG.", "..b....b..", "..b....b..", ".bb....bb.", ".........."],
    "planter": ["..e....e..", "..E..e.E..", "..e..E.e..", "..E..e.E..", "..e..E.e..", ".eE..e.E..", "..e..E.e.e", "..E..e.EE.", "..e..E.e..", "bbbbbbbbbb"],
    "alchemist": ["....ww....", "....bb....", "....cc....", "...c..c...", "..c....c..", ".cuuuuuuc.", ".cuwuuuuc.", ".cuuuuuuc.", "..cuuuuc..", "...cccc..."],
    "enchanter": ["y.......y.", "....y.....", "..........", ".RRRRRRRR.", ".Rwwwwwwr.", ".Rwkkkkwr.", ".Rwwwwwwr.", ".Rwkkkwwr.", ".Rwwwwwwr.", ".RRRRRRRR."],
    "crusher": ["..GGGG....", "..GGGG....", "...bb.....", "...bb.....", "...bb.....", "...bb.....", "..........", ".g.gg.g...", "gGggGggGg.", "gggggggggg"],
    "sifter": ["..........", "bbbbbbbbbb", "bGgGgGgGgb", "bgGgGgGgGb", "bbbbbbbbbb", "...y..o...", "....y...o.", "..o...y...", ".....o....", ".........."],
    "mechanic": ["....GG....", ".G.GGGG.G.", "..GGGGGG..", ".GGGrrGGG.", "GGGrRRrGGG", "GGGrRRrGGG", ".GGGrrGGG.", "..GGGGGG..", ".G.GGGG.G.", "....GG...."],
    "netherworker": ["kkkkkkkk..", "kvvvvvvk..", "kvVvvvvk..", "kvvvVvvk..", "kvvvvvvk..", "kvVvvvvk..", "kvvvvVvk..", "kvvvvvvk..", "kkkkkkkk..", ".........."],
    "blacksmith": ["..........", "..........", ".GGGGGGGG.", "GGGGGGGGGG", ".GGGGGGG..", "...GGG....", "...GGG....", "..GGGGG...", ".GGGGGGG..", ".........."],
    "fletcher": ["b........w", ".b......k.", "..b....k..", "..b...k...", "..b..k....", "..b.k.....", "..bk......", "..k.......", ".kbb......", "rr........"],
    "stonesmeltery": ["..........", ".gggggggg.", ".gGGGGGGg.", ".gG....Gg.", ".gG.oo.Gg.", ".gGoyyoGg.", ".gGGGGGGg.", ".gggggggg.", ".gggggggg.", ".........."],
}


def hut_sign(hut):
    rng = random.Random(zlib.crc32(hut.encode()))
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            edge = x in (0, 15) or y in (0, 15)
            img.putpixel((x, y), jitter(PLAQUE_EDGE if edge else PLAQUE, rng, 6))
    for y, row in enumerate(ICONS[hut]):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((3 + x, 3 + y), rgba(ICON_COLORS[ch]))
    return img


# Prop on top of each hut's workbench: (from, to, side texture, top texture), using
# vanilla block textures. A hut may have more than one prop.
def box(x0, y0, z0, x1, y1, z1, side, top=None):
    return ([x0, y0, z0], [x1, y1, z1], side, top or side)


V = "minecraft:block/"
PROPS = {
    "townhall": [box(5, 12, 6, 11, 17, 10, V + "gold_block")],
    "residence": [box(3, 12, 4, 13, 14, 13, V + "red_wool"), box(3, 14, 4, 13, 15, 7, V + "white_wool")],
    "lumberjack": [box(4, 12, 4, 12, 18, 12, V + "oak_log", V + "oak_log_top")],
    "farmer": [box(3, 12, 3, 13, 18, 13, V + "hay_block_side", V + "hay_block_top")],
    "miner": [box(4, 12, 4, 12, 17, 12, V + "coal_ore")],
    "guardtower": [box(5, 12, 5, 11, 18, 11, V + "iron_block")],
    "warehouse": [box(3, 12, 3, 13, 21, 13, V + "barrel_side", V + "barrel_top")],
    "builder": [box(4, 12, 4, 12, 18, 12, V + "crafting_table_front", V + "crafting_table_top")],
    "deliveryman": [box(4, 12, 4, 12, 18, 12, V + "brown_wool"), box(6, 18, 6, 10, 19, 10, V + "white_wool")],
    "cook": [box(4, 12, 4, 12, 17, 12, V + "cauldron_side", V + "cauldron_top")],
    "fisherman": [box(5, 12, 5, 11, 17, 11, V + "blue_terracotta")],
    "baker": [box(3, 12, 3, 13, 16, 13, V + "cake_side", V + "cake_top")],
    "smelter": [box(4, 12, 4, 12, 19, 12, V + "blast_furnace_side", V + "blast_furnace_top")],
    "shepherd": [box(4, 12, 4, 12, 18, 12, V + "white_wool")],
    "university": [box(3, 12, 4, 13, 20, 12, V + "bookshelf", V + "oak_planks")],
    "composter": [box(3, 12, 3, 13, 20, 13, V + "composter_side", V + "composter_top")],
    "sawmill": [box(3, 12, 3, 13, 17, 13, V + "stonecutter_side", V + "stonecutter_top")],
    "cowhand": [box(5, 12, 5, 11, 19, 11, V + "quartz_block_side", V + "quartz_block_top")],
    "chickenherder": [box(3, 12, 3, 13, 15, 13, V + "hay_block_side", V + "hay_block_top"),
                      box(6, 15, 6, 10, 19, 10, V + "white_wool")],
    "swineherd": [box(4, 12, 4, 12, 17, 12, V + "pink_terracotta")],
    "stonemason": [box(4, 12, 4, 12, 18, 12, V + "stone_bricks")],
    "florist": [box(5, 12, 5, 11, 16, 11, V + "terracotta"), box(4, 16, 4, 12, 21, 12, V + "moss_block")],
    "dyer": [box(2, 12, 4, 6, 16, 8, V + "red_wool"), box(6, 12, 8, 10, 16, 12, V + "yellow_wool"),
             box(10, 12, 4, 14, 16, 8, V + "blue_wool")],
    "beekeeper": [box(4, 12, 4, 12, 18, 12, V + "beehive_side", V + "beehive_end")],
    "rabbitherder": [box(3, 12, 3, 13, 15, 13, V + "hay_block_side", V + "hay_block_top"),
                     box(6, 15, 6, 10, 20, 10, V + "orange_terracotta")],
    "glassblower": [box(4, 12, 4, 12, 18, 12, V + "furnace_side", V + "furnace_top")],
    "concretemixer": [box(3, 12, 3, 13, 15, 13, V + "gray_concrete_powder"), box(5, 15, 5, 11, 18, 11, V + "white_concrete")],
    "planter": [box(3, 12, 3, 13, 15, 13, V + "coarse_dirt"), box(5, 15, 5, 11, 22, 11, V + "bamboo_block", V + "bamboo_block_top")],
    "alchemist": [box(4, 12, 4, 12, 14, 12, V + "polished_blackstone"), box(6, 14, 6, 10, 19, 10, V + "amethyst_block")],
    "enchanter": [box(3, 12, 3, 13, 15, 13, V + "obsidian"), box(5, 15, 5, 11, 17, 11, V + "lapis_block")],
    "crusher": [box(3, 12, 3, 13, 15, 13, V + "cobblestone"), box(5, 15, 5, 11, 18, 11, V + "gravel")],
    "sifter": [box(3, 12, 3, 13, 14, 13, V + "oak_planks"), box(4, 14, 4, 12, 16, 12, V + "sand")],
    "mechanic": [box(4, 12, 4, 12, 15, 12, V + "iron_block"), box(6, 15, 6, 10, 18, 10, V + "redstone_block")],
    "netherworker": [box(3, 12, 3, 13, 15, 13, V + "netherrack"), box(5, 15, 5, 11, 19, 11, V + "nether_bricks")],
    "blacksmith": [box(4, 12, 5, 12, 14, 11, V + "iron_block"), box(6, 14, 6, 10, 17, 10, V + "smooth_stone")],
    "fletcher": [box(3, 12, 3, 13, 14, 13, V + "white_wool"), box(5, 14, 5, 11, 18, 11, V + "birch_log", V + "birch_log_top")],
    "stonesmeltery": [box(4, 12, 4, 12, 19, 12, V + "furnace_front", V + "furnace_top")],
}

# Workbench wood per hut: (planks for the top and shelf, stripped log for the legs).
WOODS = {"townhall": ("dark_oak", "dark_oak"), "university": ("dark_oak", "dark_oak"),
         "guardtower": ("spruce", "spruce"), "miner": ("spruce", "spruce"), "smelter": ("spruce", "spruce"),
         "planter": ("jungle", "jungle"), "alchemist": ("dark_oak", "dark_oak"), "enchanter": ("dark_oak", "dark_oak"),
         "mechanic": ("spruce", "spruce"), "netherworker": ("dark_oak", "dark_oak"),
         "blacksmith": ("spruce", "spruce"), "stonesmeltery": ("spruce", "spruce")}


def cube(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all_faces(texture, uv=None, top=None):
    faces = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        faces[face] = {"texture": top if face in ("up", "down") and top else texture}
        if uv:
            faces[face]["uv"] = uv
    return faces


def write_models():
    models = os.path.join(ROOT, "..", "models", "block")
    os.makedirs(models, exist_ok=True)
    # A workbench: top, four legs, a lower shelf and the job sign on the front apron.
    leg = lambda x, z: cube([x, 0, z], [x + 2, 10, z + 2], all_faces("#leg", [0, 0, 2, 16]))
    parent = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#top"},
        "elements": [
            cube([0, 10, 0], [16, 12, 16], all_faces("#top")),
            leg(1, 1), leg(13, 1), leg(1, 13), leg(13, 13),
            cube([2, 3, 2], [14, 4, 14], all_faces("#top")),
            cube([3, 4, 1], [13, 10, 2], {
                "north": {"uv": [0, 0, 16, 16], "texture": "#sign"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "up": {"uv": [0, 0, 16, 1], "texture": "#top"},
                "down": {"uv": [0, 0, 16, 1], "texture": "#top"},
                "east": {"uv": [0, 0, 1, 16], "texture": "#top"},
                "west": {"uv": [0, 0, 1, 16], "texture": "#top"}}),
        ],
    }
    with open(os.path.join(models, "hut_base.json"), "w") as f:
        json.dump(parent, f, indent=2)
        f.write("\n")
    for hut in ICONS:
        planks, log = WOODS.get(hut, ("oak", "oak"))
        elements = list(parent["elements"])
        textures = {"top": V + planks + "_planks", "leg": V + "stripped_" + log + "_log",
                    "sign": "hearthstead:block/hut_sign_" + hut, "particle": V + planks + "_planks"}
        for i, (frm, to, side, top) in enumerate(PROPS[hut]):
            textures["prop%d" % i] = side
            textures["prop%dtop" % i] = top
            elements.append(cube(frm, to, all_faces("#prop%d" % i, top="#prop%dtop" % i)))
        model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
        with open(os.path.join(models, "blockhut" + hut + ".json"), "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")


# ----------------------------------------------------------------------------- rope fence
def rope_fence_textures():
    rng = random.Random(30)
    post = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            color = (122, 86, 50) if x % 4 else (96, 66, 38)          # wood grain
            if y in (3, 4, 11, 12):
                color = (196, 170, 120)                                # rope lashings
            post.putpixel((x, y), jitter(color, rng, 7))
    post.save(os.path.join(BLOCK, "rope_fence_post.png"))
    rope = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            twist = (x + y) % 4 < 2                                    # twisted strands
            rope.putpixel((x, y), jitter((214, 190, 140) if twist else (176, 150, 104), rng, 5))
    rope.save(os.path.join(BLOCK, "rope.png"))


def rope_fence_models():
    root = os.path.join(ROOT, "..")
    post = {"parent": "minecraft:block/block", "textures": {"post": "hearthstead:block/rope_fence_post",
                                                           "particle": "hearthstead:block/rope_fence_post"},
            "elements": [cube([6, 0, 6], [10, 16, 10], {
                f: {"texture": "#post", "uv": [6, 0, 10, 16] if f not in ("up", "down") else [6, 6, 10, 10]}
                for f in ("north", "east", "south", "west", "up", "down")})]}
    rope = lambda y: cube([7.5, y, 0], [8.5, y + 1, 6], all_faces("#rope"))
    side = {"textures": {"rope": "hearthstead:block/rope", "particle": "hearthstead:block/rope"},
            "elements": [rope(5), rope(12)]}
    inventory = {"parent": "minecraft:block/block", "display": {"gui": {"rotation": [30, 135, 0], "scale": [0.625] * 3}},
                 "textures": {"post": "hearthstead:block/rope_fence_post", "rope": "hearthstead:block/rope",
                              "particle": "hearthstead:block/rope_fence_post"},
                 "elements": [cube([6, 0, 0], [10, 16, 4], all_faces("#post")),
                              cube([6, 0, 12], [10, 16, 16], all_faces("#post")),
                              cube([7.5, 5, 4], [8.5, 6, 12], all_faces("#rope")),
                              cube([7.5, 12, 4], [8.5, 13, 12], all_faces("#rope"))]}
    for name, model in (("rope_fence_post", post), ("rope_fence_side", side), ("rope_fence_inventory", inventory)):
        with open(os.path.join(root, "models", "block", name + ".json"), "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")
    parts = [{"apply": {"model": "hearthstead:block/rope_fence_post"}}]
    for direction, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        apply = {"model": "hearthstead:block/rope_fence_side", "uvlock": True}
        if y:
            apply["y"] = y
        parts.append({"when": {direction: "true"}, "apply": apply})
    os.makedirs(os.path.join(root, "blockstates"), exist_ok=True)
    with open(os.path.join(root, "blockstates", "rope_fence.json"), "w") as f:
        json.dump({"multipart": parts}, f, indent=2)
        f.write("\n")
    os.makedirs(os.path.join(root, "items"), exist_ok=True)
    with open(os.path.join(root, "items", "rope_fence.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "hearthstead:block/rope_fence_inventory"}}, f, indent=2)
        f.write("\n")


# ----------------------------------------------------------------------------- GUI theme
PARCHMENT = (232, 214, 172)
PARCHMENT_DARK = (204, 182, 134)
FRAME = (98, 64, 36)
FRAME_LIGHT = (140, 96, 56)
FRAME_DARK = (52, 32, 18)
LEATHER = (66, 48, 34)
LEATHER_DARK = (40, 28, 20)


def nine_slice(name, img, border):
    os.makedirs(GUI, exist_ok=True)
    img.save(os.path.join(GUI, name + ".png"))
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": img.width, "height": img.height, "border": border}}}
    with open(os.path.join(GUI, name + ".png.mcmeta"), "w") as f:
        json.dump(meta, f, indent=2)
        f.write("\n")


def window():
    rng = random.Random(20)
    size, border = 48, 7
    img = Image.new("RGBA", (size, size))
    for x in range(size):
        for y in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d == 0:
                color = FRAME_DARK
            elif d < border - 2:
                color = FRAME_LIGHT if (x + y) % 7 == 0 else FRAME   # wood grain
                if d == 1:
                    color = FRAME_LIGHT
            elif d == border - 2:
                color = FRAME_DARK
            elif d == border - 1:
                color = PARCHMENT_DARK
            else:
                color = PARCHMENT
            img.putpixel((x, y), jitter(color, rng, 6 if d >= border - 1 else 10))
    # brass corner studs
    for cx, cy in ((3, 3), (size - 4, 3), (3, size - 4), (size - 4, size - 4)):
        img.putpixel((cx, cy), (226, 186, 90, 255))
    nine_slice("window", img, border)


def inset():
    rng = random.Random(21)
    size = 16
    img = Image.new("RGBA", (size, size))
    for x in range(size):
        for y in range(size):
            if x == 0 or y == 0:
                color = LEATHER_DARK
            elif x == size - 1 or y == size - 1:
                color = PARCHMENT_DARK
            else:
                color = LEATHER
            img.putpixel((x, y), jitter(color, rng, 4))
    nine_slice("inset", img, 2)


def slot():
    img = Image.new("RGBA", (18, 18))
    for x in range(18):
        for y in range(18):
            if x == 0 or y == 0:
                color = LEATHER_DARK
            elif x == 17 or y == 17:
                color = PARCHMENT_DARK
            else:
                color = (92, 70, 50)
            img.putpixel((x, y), rgba(color))
    nine_slice("slot", img, 1)


def button(name, base, light, dark, outline):
    rng = random.Random(zlib.crc32(name.encode()))
    w, h = 64, 20
    img = Image.new("RGBA", (w, h))
    for x in range(w):
        for y in range(h):
            if x in (0, w - 1) or y in (0, h - 1):
                color = outline
            elif x == 1 or y == 1:
                color = light
            elif x == w - 2 or y == h - 2:
                color = dark
            else:
                color = base if (y // 5) % 2 == 0 else tuple(max(0, c - 8) for c in base)
            img.putpixel((x, y), jitter(color, rng, 5))
    nine_slice(name, img, 3)


def main():
    os.makedirs(BLOCK, exist_ok=True)
    for hut in ICONS:
        hut_sign(hut).save(os.path.join(BLOCK, "hut_sign_" + hut + ".png"))
    write_models()
    rope_fence_textures()
    rope_fence_models()
    window()
    inset()
    slot()
    button("button", (128, 86, 48), (168, 120, 72), (82, 54, 30), (40, 26, 14))
    button("button_highlighted", (156, 108, 62), (206, 160, 96), (98, 66, 36), (226, 186, 90))
    button("button_disabled", (110, 96, 82), (130, 116, 100), (80, 70, 60), (52, 44, 36))
    button("tab_selected", PARCHMENT, (246, 232, 198), PARCHMENT_DARK, FRAME_DARK)
    print("wrote hut textures, models and GUI sprites")


if __name__ == "__main__":
    main()
