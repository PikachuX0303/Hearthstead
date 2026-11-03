#!/usr/bin/env python3
"""Draws the mod's textures: the barbarian and pirate skins (64x64, standard humanoid layout) and
the build tool and clipboard icons (16x16), and the mod icon shown by Mod Menu (128x128).

Output: src/main/resources/assets/hearthstead/icon.png
        src/main/resources/assets/hearthstead/textures/entity/barbarian/<kind>.png
        src/main/resources/assets/hearthstead/textures/item/build_tool.png
        src/main/resources/assets/hearthstead/textures/item/clipboard.png
"""
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "hearthstead", "textures", "entity", "barbarian")


def shade(color, rng, amount=14):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + d)) for c in color) + (255,)


class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def rect(self, x0, y0, x1, y1, color, amount=14):
        for x in range(x0, x1):
            for y in range(y0, y1):
                self.img.putpixel((x, y), shade(color, self.rng, amount))

    def px(self, x, y, color):
        self.img.putpixel((x, y), tuple(color) + (255,))

    # box helpers: u, v = texture offset, w/h/d = box size
    def box(self, u, v, w, h, d, color, amount=14):
        self.rect(u + d, v, u + d + w * 2, v + d, color, amount)        # top and bottom
        self.rect(u, v + d, u + 2 * (d + w), v + d + h, color, amount)  # sides, front, back

    def front(self, u, v, w, h, d):
        """Top-left corner of a box's front face."""
        return u + d, v + d


SKIN = (196, 142, 104)
SKIN_DARK = (160, 108, 76)
HAIR = (122, 46, 24)
FUR = (112, 92, 70)
FUR_LIGHT = (150, 130, 104)
LEATHER = (96, 60, 34)
LEATHER_DARK = (66, 40, 22)
TROUSERS = (58, 46, 40)
BOOTS = (44, 34, 28)
PAINT = (52, 92, 170)
EYE = (240, 240, 240)
PUPIL = (40, 30, 30)


def base(seed, hair=HAIR):
    s = Skin(seed)
    # head
    s.box(0, 0, 8, 8, 8, SKIN, 8)
    fx, fy = s.front(0, 0, 8, 8, 8)
    s.rect(0, 0, 32, 10, hair)                       # hair on top and around
    s.rect(24, 8, 32, 16, hair)                      # back of the head
    s.rect(fx, fy, fx + 8, fy + 2, hair)             # fringe
    s.rect(fx, fy + 2, fx + 8, fy + 4, PAINT, 6)     # war paint band across the eyes
    for ex in (1, 5):
        s.px(fx + ex, fy + 3, EYE)
        s.px(fx + ex + 1, fy + 3, PUPIL)
    s.rect(fx + 3, fy + 4, fx + 5, fy + 5, SKIN_DARK, 4)   # nose
    s.rect(fx, fy + 5, fx + 8, fy + 8, hair)         # beard
    s.rect(fx + 3, fy + 5, fx + 5, fy + 6, (80, 30, 20), 4)  # mouth
    s.px(fx + 2, fy + 7, (200, 170, 60))             # beard bead
    s.px(fx + 5, fy + 7, (200, 170, 60))
    s.rect(0, 12, 8, 16, hair)                       # side beard
    s.rect(16, 12, 24, 16, hair)
    # body: leather tunic with a fur collar
    s.box(16, 16, 8, 12, 4, LEATHER)
    s.rect(16, 20, 40, 23, FUR, 18)
    s.rect(20, 16, 28, 20, FUR, 18)
    s.rect(16, 28, 40, 30, LEATHER_DARK, 6)          # belt
    s.rect(23, 28, 25, 30, (170, 150, 90), 6)        # buckle
    # arms: bare skin with fur bracers
    for u, v in ((40, 16), (32, 48)):
        s.box(u, v, 4, 12, 4, SKIN, 8)
        s.rect(u, v + 4, u + 16, v + 7, FUR_LIGHT, 16)
        s.rect(u, v + 12, u + 16, v + 16, LEATHER, 10)
    # legs: trousers and fur boots
    for u, v in ((0, 16), (16, 48)):
        s.box(u, v, 4, 12, 4, TROUSERS, 8)
        s.rect(u, v + 12, u + 16, v + 16, BOOTS, 8)
        s.rect(u, v + 11, u + 16, v + 12, FUR_LIGHT, 16)
    return s


def barbarian():
    return base(1)


def archer():
    s = base(2, hair=(70, 46, 30))
    hood = (54, 92, 46)
    # hood on the hat layer (32, 0): everything but the face
    s.box(32, 0, 8, 8, 8, hood, 12)
    hx, hy = s.front(32, 0, 8, 8, 8)
    for x in range(hx + 1, hx + 7):
        for y in range(hy + 2, hy + 8):
            s.img.putpixel((x, y), (0, 0, 0, 0))
    s.rect(16, 20, 40, 23, hood, 10)                 # mantle instead of fur
    s.rect(20, 16, 28, 20, hood, 10)
    s.rect(32, 20, 36, 32, (120, 90, 50), 8)          # quiver strap on the back
    return s


def chief():
    s = base(3, hair=(150, 150, 150))
    iron = (150, 150, 158)
    gold = (220, 180, 60)
    # horned helmet on the hat layer
    s.rect(32, 0, 64, 10, iron, 10)
    s.rect(32, 9, 64, 10, gold, 6)
    s.rect(40, 0, 48, 8, iron, 10)
    for x in (33, 34, 61, 62):
        for y in range(10, 13):
            s.px(x, y, (230, 220, 200))
    # crimson tunic with a golden belt
    s.box(16, 16, 8, 12, 4, (128, 30, 30))
    s.rect(16, 20, 40, 23, (60, 52, 48), 18)          # dark fur collar
    s.rect(20, 16, 28, 20, (60, 52, 48), 18)
    s.rect(16, 28, 40, 30, gold, 8)
    for u, v in ((40, 16), (32, 48)):
        s.rect(u, v + 4, u + 16, v + 7, iron, 8)       # iron bracers
    return s


def pirate_base(seed, hair, shirt_a, shirt_b):
    """A pirate: no war paint, a striped shirt with a sash, dark trousers and boots."""
    s = base(seed, hair=hair)
    fx, fy = s.front(0, 0, 8, 8, 8)
    s.rect(fx, fy + 2, fx + 8, fy + 4, SKIN, 6)      # wash off the war paint
    for ex in (1, 5):
        s.px(fx + ex, fy + 3, EYE)
        s.px(fx + ex + 1, fy + 3, PUPIL)
    s.rect(fx + 1, fy + 5, fx + 7, fy + 6, hair)     # moustache over a shorter beard
    s.rect(fx + 2, fy + 6, fx + 6, fy + 8, SKIN, 6)
    s.rect(fx + 3, fy + 6, fx + 5, fy + 8, hair)
    # striped shirt
    s.box(16, 16, 8, 12, 4, shirt_a, 6)
    for y in range(20, 28, 2):
        s.rect(16, y, 40, y + 1, shirt_b, 6)
    s.rect(16, 28, 40, 30, (150, 40, 36), 8)         # red sash instead of a belt
    # sleeves to the elbow, then bare arms
    for u, v in ((40, 16), (32, 48)):
        s.box(u, v, 4, 12, 4, SKIN, 8)
        s.rect(u, v + 4, u + 16, v + 10, shirt_a, 6)
        for y in range(v + 5, v + 10, 2):
            s.rect(u, y, u + 16, y + 1, shirt_b, 6)
        s.rect(u, v + 12, u + 16, v + 16, SKIN_DARK, 6)
    for u, v in ((0, 16), (16, 48)):
        s.box(u, v, 4, 12, 4, (44, 48, 70), 8)       # navy trousers
        s.rect(u, v + 10, u + 16, v + 16, (30, 26, 24), 6)   # tall boots
    return s


def pirate():
    s = pirate_base(4, (60, 40, 28), (232, 228, 214), (40, 60, 120))
    red = (176, 36, 32)
    s.rect(32, 0, 64, 10, red, 10)                   # bandana on the hat layer
    s.rect(40, 0, 48, 8, red, 10)
    hx, hy = s.front(32, 0, 8, 8, 8)
    for x in range(hx, hx + 8):                      # leave the face free below the bandana
        for y in range(hy + 2, hy + 8):
            s.img.putpixel((x, y), (0, 0, 0, 0))
    for x, y in ((hx + 1, hy), (hx + 4, hy + 1), (hx + 6, hy)):
        s.px(x, y, (236, 230, 220))                  # white dots
    return s


def pirate_archer():
    s = pirate_base(5, (120, 80, 40), (210, 196, 160), (130, 100, 60))
    green = (60, 96, 70)
    s.rect(32, 0, 64, 10, green, 10)                 # green headscarf
    s.rect(40, 0, 48, 8, green, 10)
    hx, hy = s.front(32, 0, 8, 8, 8)
    for x in range(hx, hx + 8):
        for y in range(hy + 2, hy + 8):
            s.img.putpixel((x, y), (0, 0, 0, 0))
    s.rect(32, 20, 36, 32, (110, 80, 44), 8)         # quiver strap on the back
    return s


def pirate_captain():
    s = pirate_base(6, (30, 26, 24), (236, 232, 222), (236, 232, 222))
    coat, gold, black = (150, 28, 34), (220, 180, 60), (26, 24, 28)
    # long red coat with gold buttons over the white shirt
    s.box(16, 16, 8, 12, 4, coat, 8)
    s.rect(23, 20, 25, 28, (236, 232, 222), 4)       # shirt showing at the front
    for y in (21, 24, 27):
        s.px(22, y, gold)
        s.px(25, y, gold)
    s.rect(16, 28, 40, 30, black, 4)                 # black belt with a gold buckle
    s.rect(23, 28, 25, 30, gold, 4)
    for u, v in ((40, 16), (32, 48)):
        s.box(u, v, 4, 12, 4, coat, 8)               # coat sleeves with gold cuffs
        s.rect(u, v + 10, u + 16, v + 12, gold, 6)
        s.rect(u, v + 12, u + 16, v + 16, SKIN, 6)
    # black tricorn hat with gold trim on the hat layer
    s.rect(32, 0, 64, 10, black, 4)
    s.rect(40, 0, 48, 8, black, 4)
    s.rect(32, 9, 64, 10, gold, 4)
    hx, hy = s.front(32, 0, 8, 8, 8)
    for x in range(hx, hx + 8):
        for y in range(hy + 2, hy + 8):
            s.img.putpixel((x, y), (0, 0, 0, 0))
    s.px(hx + 3, hy + 1, (236, 232, 222))            # small white skull on the hat
    s.px(hx + 4, hy + 1, (236, 232, 222))
    fx, fy = s.front(0, 0, 8, 8, 8)
    s.rect(fx + 5, fy + 2, fx + 8, fy + 4, black, 2)  # eyepatch and its strap
    s.rect(fx, fy + 2, fx + 1, fy + 3, black, 2)
    return s


ITEM_OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                        "assets", "hearthstead", "textures", "item")


def build_tool():
    """16x16 item icon: a wooden handle carrying a small blueprint sheet."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random(4)
    wood, wood_dark = (150, 104, 58), (98, 66, 34)
    for i in range(9):                      # diagonal handle, bottom left to the middle
        x, y = 1 + i, 14 - i
        img.putpixel((x, y), shade(wood, rng, 10))
        img.putpixel((x + 1, y), shade(wood_dark, rng, 6))
    paper, line, edge = (58, 110, 196), (210, 230, 255), (30, 60, 120)
    for x in range(8, 16):                  # blueprint sheet in the top right
        for y in range(0, 8):
            border = x in (8, 15) or y in (0, 7)
            color = edge if border else paper
            if not border and (x == 11 or y == 3):
                color = line                # grid lines
            img.putpixel((x, y), shade(color, rng, 6))
    for x, y in ((10, 5), (12, 2), (13, 5)):  # little building sketch
        img.putpixel((x, y), (255, 255, 255, 255))
    img.putpixel((9, 8), (220, 180, 60, 255))  # brass fitting where the handle meets the sheet
    img.putpixel((8, 8), (190, 150, 50, 255))
    return img


def clipboard():
    """16x16 item icon: a wooden board with a sheet of notes held by a metal clip."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random(7)
    wood, wood_dark = (156, 108, 62), (104, 70, 38)
    for x in range(2, 14):                  # the board
        for y in range(2, 16):
            edge = x in (2, 13) or y == 15
            img.putpixel((x, y), shade(wood_dark if edge else wood, rng, 8))
    paper, paper_shadow, ink = (236, 230, 214), (200, 192, 172), (96, 88, 84)
    for x in range(4, 12):                  # the sheet
        for y in range(4, 14):
            img.putpixel((x, y), shade(paper_shadow if y == 13 or x == 11 else paper, rng, 4))
    for y in (6, 8, 10, 12):                # lines of writing, one of them ticked off
        length = 5 if y != 10 else 3
        for x in range(5, 5 + length):
            img.putpixel((x, y), shade(ink, rng, 10))
    img.putpixel((9, 10), (70, 150, 60, 255))
    img.putpixel((10, 9), (70, 150, 60, 255))
    metal, metal_dark = (196, 200, 206), (120, 124, 132)
    for x in range(5, 11):                  # the clip
        img.putpixel((x, 2), shade(metal, rng, 6))
        img.putpixel((x, 3), shade(metal_dark, rng, 6))
    for x in (6, 9):
        img.putpixel((x, 1), shade(metal_dark, rng, 6))
    for x in (7, 8):
        img.putpixel((x, 1), shade(metal, rng, 6))
    return img


def mod_icon():
    """128x128 mod icon (drawn at 32x32 and scaled up): a cottage with a lit window and a smoking
    chimney on a grassy hill at dusk."""
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rng = random.Random(11)
    top, bottom = (40, 44, 92), (226, 128, 74)
    for y in range(size):                   # dusk sky, rounded corners
        t = y / (size - 1)
        sky = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
        for x in range(size):
            corner = min(x, size - 1 - x) + min(y, size - 1 - y)
            if corner >= 2:
                img.putpixel((x, y), sky + (255,))
    for x, y in ((4, 4), (9, 2), (26, 3), (21, 6), (29, 9)):  # first stars
        img.putpixel((x, y), (250, 240, 200, 255))
    grass, grass_dark, dirt = (92, 150, 62), (66, 116, 46), (120, 84, 52)
    for x in range(size):                   # the hill
        crest = 23 + round(2.2 * ((x - 15.5) / 16) ** 2 * 2)
        for y in range(crest, size):
            corner = min(x, size - 1 - x) + min(y, size - 1 - y)
            if corner < 2:
                continue
            color = grass if y - crest < 2 else (grass_dark if y - crest < 3 else dirt)
            img.putpixel((x, y), shade(color, rng, 10))
    wall, wall_dark, beam = (214, 196, 160), (176, 156, 120), (98, 64, 38)
    for x in range(9, 23):                  # walls with a timber frame
        for y in range(15, 24):
            frame = x in (9, 22) or y in (15, 23) or x == 15
            img.putpixel((x, y), shade(beam if frame else (wall_dark if x > 18 else wall), rng, 6))
    roof, roof_dark = (168, 62, 44), (120, 40, 30)
    for row in range(8):                    # gable roof
        y = 7 + row
        for x in range(15 - row - 1, 17 + row + 1):
            if 7 <= x <= 24:
                edge = x in (15 - row - 1, 16 + row)
                img.putpixel((x, y), shade(roof_dark if edge or row == 7 else roof, rng, 8))
    stone = (128, 124, 120)
    for x in range(19, 22):                 # chimney
        for y in range(6, 11):
            img.putpixel((x, y), shade(stone, rng, 12))
    for x, y, a in ((20, 4, 200), (21, 3, 170), (22, 1, 130), (23, 2, 110), (21, 0, 90)):  # smoke
        img.putpixel((x, y), (214, 214, 220, a))
    door = (110, 70, 40)
    for x in range(11, 14):                 # door
        for y in range(18, 23):
            img.putpixel((x, y), shade(door, rng, 6))
    img.putpixel((13, 20), (230, 190, 80, 255))
    glow, glow_bright = (250, 186, 72), (255, 232, 140)
    for x in range(17, 21):                 # warm window
        for y in range(17, 21):
            bright = x in (18, 19) and y in (18, 19)
            img.putpixel((x, y), (glow_bright if bright else glow) + (255,))
    for x in range(17, 21):
        img.putpixel((x, 21), (beam[0], beam[1], beam[2], 255))
    return img.resize((128, 128), Image.NEAREST)


def main():
    mod_icon().save(os.path.join(ITEM_OUT, "..", "..", "icon.png"))
    os.makedirs(ITEM_OUT, exist_ok=True)
    build_tool().save(os.path.join(ITEM_OUT, "build_tool.png"))
    clipboard().save(os.path.join(ITEM_OUT, "clipboard.png"))
    os.makedirs(OUT, exist_ok=True)
    for name, make in (("barbarian", barbarian), ("archer_barbarian", archer), ("chief_barbarian", chief),
                       ("pirate", pirate), ("pirate_archer", pirate_archer), ("pirate_captain", pirate_captain)):
        make().img.save(os.path.join(OUT, name + ".png"))
    print("wrote barbarian textures to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
