#!/usr/bin/env python3
"""Generates the structure templates for world generation.

Output: src/main/resources/data/hearthstead/structure/abandoned_settlement/camp.nbt

An abandoned settlement: an unclaimed Town Hall on a small plaza, two collapsed hut
foundations, a cold campfire, broken rope fencing and a supply chest. The template is
17 x 17 blocks with the town hall in the middle; layer y = 0 replaces the ground surface.
Keep CENTER and CHEST in sync with worldgen/AbandonedSettlementPiece.java.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from generate_schematics import block, to_nbt, write_nbt  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "data", "hearthstead", "structure", "abandoned_settlement")
SIZE = 17
CENTER = (8, 1, 8)    # the town hall
CHEST = (3, 1, 3)     # filled with loot when the settlement generates


def camp():
    rng = random.Random(1203)
    blocks = {}

    def put(x, y, z, state):
        blocks[(x, y, z)] = state

    # Plaza: a worn 5 x 5 square of cobblestone, gravel and moss around the town hall.
    plaza = [block("cobblestone"), block("mossy_cobblestone"), block("gravel"), block("coarse_dirt")]
    for x in range(6, 11):
        for z in range(6, 11):
            put(x, 0, z, plaza[rng.choice([0, 0, 0, 1, 1, 2, 3])])
    put(*CENTER, block("hearthstead:blockhuttownhall", facing="south"))

    # Paths from the plaza to the ruins and the fire pit.
    for x, z in [(5, 7), (4, 6), (11, 7), (12, 7), (8, 11), (8, 12), (7, 13)]:
        put(x, 0, z, block("dirt_path"))

    # Ruin A (north west): cobblestone foundation, stumps of plank walls, one standing corner.
    for x in range(1, 6):
        for z in range(1, 6):
            edge = x in (1, 5) or z in (1, 5)
            put(x, 0, z, block("mossy_cobblestone" if rng.random() < 0.35 else "cobblestone") if edge
                else block("oak_planks" if rng.random() < 0.7 else "coarse_dirt"))
            if edge and (x, z) not in ((3, 5), (4, 5)) and rng.random() < 0.45:
                put(x, 1, z, block("oak_planks"))
    for y in (1, 2, 3):
        put(1, y, 1, block("oak_log"))
    put(1, 1, 5, block("oak_log"))
    put(*CHEST, block("chest", facing="south", type="single", waterlogged="false"))
    put(4, 1, 2, block("cobweb"))
    put(2, 1, 4, block("barrel", facing="up", open="false"))

    # Ruin B (east): stone brick foundation, cracked and partly overgrown.
    for x in range(11, 16):
        for z in range(2, 7):
            edge = x in (11, 15) or z in (2, 6)
            if edge:
                kind = rng.choice(["stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"])
                put(x, 0, z, block(kind))
                if (x, z) not in ((11, 4), (11, 5)) and rng.random() < 0.35:
                    put(x, 1, z, block(kind))
            else:
                put(x, 0, z, block("spruce_planks" if rng.random() < 0.6 else "gravel"))
    put(15, 1, 2, block("stripped_spruce_log"))
    put(15, 2, 2, block("stripped_spruce_log"))
    put(13, 1, 4, block("hay_block", axis="y"))
    put(14, 1, 5, block("composter", level="3"))

    # Fire pit with log benches, south of the town hall.
    put(8, 1, 13, block("campfire", facing="north", lit="false", signal_fire="false", waterlogged="false"))
    put(6, 1, 13, block("oak_log", axis="z"))
    put(10, 1, 13, block("oak_log", axis="z"))

    # What is left of the rope fence along the southern edge.
    rope = dict(north="false", south="false", waterlogged="false")
    for x in (3, 4, 5, 11, 12, 13):
        east = "true" if x + 1 in (4, 5, 12, 13) else "false"
        west = "true" if x - 1 in (3, 4, 11, 12) else "false"
        put(x, 1, 15, block("hearthstead:rope_fence", east=east, west=west, **rope))
    put(13, 2, 15, block("lantern", hanging="false", waterlogged="false"))

    # Old crop rows west of the fire pit, gone to seed.
    for x in (1, 2, 3):
        for z in (9, 10, 11, 12):
            put(x, 0, z, block("coarse_dirt" if rng.random() < 0.4 else "dirt"))
            if rng.random() < 0.5:
                put(x, 1, z, block("short_grass"))
            elif rng.random() < 0.3:
                put(x, 1, z, block("fern"))

    # Make sure the corners span the whole 17 x 17 area so the template has a fixed size.
    put(0, 0, 0, block("coarse_dirt"))
    put(SIZE - 1, 0, SIZE - 1, block("coarse_dirt"))
    return blocks


def main():
    blocks = camp()
    write_nbt(os.path.join(OUT, "camp.nbt"), to_nbt(None, blocks))
    print("wrote", len(blocks), "blocks to", os.path.normpath(os.path.join(OUT, "camp.nbt")))


if __name__ == "__main__":
    main()
