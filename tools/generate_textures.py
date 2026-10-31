#!/usr/bin/env python3
"""Draws the mod's textures: the barbarian skins (64x64, standard humanoid layout) and
the build tool icon (16x16).

Output: src/main/resources/assets/hearthstead/textures/entity/barbarian/<kind>.png
        src/main/resources/assets/hearthstead/textures/item/build_tool.png
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


def main():
    os.makedirs(ITEM_OUT, exist_ok=True)
    build_tool().save(os.path.join(ITEM_OUT, "build_tool.png"))
    os.makedirs(OUT, exist_ok=True)
    for name, make in (("barbarian", barbarian), ("archer_barbarian", archer), ("chief_barbarian", chief)):
        make().img.save(os.path.join(OUT, name + ".png"))
    print("wrote barbarian textures to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
