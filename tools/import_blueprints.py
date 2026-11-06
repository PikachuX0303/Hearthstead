#!/usr/bin/env python3
"""Imports the hut blueprints of the MineColonies style packs into Hearthstead.

The MineColonies blueprints (by the LDT Team and the style pack builders, GPL-3.0) are
Structurize ``.blueprint`` files. This script reads them from a MineColonies jar and writes
Hearthstead blueprints: the same compact format (a gzipped NBT palette and a packed block
array), with every block turned into a vanilla or Hearthstead block:

* MineColonies hut blocks become Hearthstead hut blocks. Only the main hut (Structurize's
  primary offset) is kept; other hut blocks in the same blueprint (barracks towers, kitchens)
  become air.
* Domum Ornamentum blocks take their material from the block entity's texture data and
  become the matching vanilla stairs, slab, wall, fence, gate, door, trapdoor, pane or block.
* Structurize substitution blocks become structure void: the builder leaves the terrain there.
* Racks and barrels become barrels, compost barrels become composters, and so on.

Usage: tools/import_blueprints.py <minecolonies jar> <minecraft client jar>

Output: src/main/resources/data/hearthstead/blueprints/huts/<hut>/level<N>.blueprint   ("original" pack)
        src/main/resources/data/hearthstead/blueprints/styles/<pack>/<hut>/level<N>.blueprint
        src/main/resources/data/hearthstead/blueprints/packs.json                    (names and authors)
"""
import collections
import gzip
import io
import json
import os
import re
import struct
import sys
import zipfile

DATA_VERSION = 4671  # Minecraft 1.21.11
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "hearthstead")
OUT = os.path.join(ROOT, "blueprints")
CLASSIC_PACK = "original"

# MineColonies hut name (after "blockhut") -> Hearthstead hut folder.
HUTS = {
    "townhall": "townhall", "citizen": "residence", "lumberjack": "lumberjack", "farmer": "farmer",
    "miner": "miner", "guardtower": "guardtower", "warehouse": "warehouse", "builder": "builder",
    "deliveryman": "deliveryman", "cook": "cook", "fisherman": "fisherman", "baker": "baker",
    "smeltery": "smelter", "shepherd": "shepherd", "university": "university", "composter": "composter",
    "sawmill": "sawmill", "cowboy": "cowhand", "chickenherder": "chickenherder", "swineherder": "swineherd",
    "stonemason": "stonemason", "florist": "florist", "dyer": "dyer", "beekeeper": "beekeeper",
    "rabbithutch": "rabbitherder", "glassblower": "glassblower", "concretemixer": "concretemixer",
    "plantation": "planter", "alchemist": "alchemist", "enchanter": "enchanter", "crusher": "crusher",
    "sifter": "sifter", "mechanic": "mechanic", "netherworker": "netherworker", "blacksmith": "blacksmith",
    "fletcher": "fletcher", "stonesmeltery": "stonesmeltery", "barracks": "barracks", "hospital": "hospital",
    "library": "library", "combatacademy": "combatacademy", "archery": "archery", "school": "school",
    "tavern": "tavern", "graveyard": "graveyard", "mysticalsite": "mysticalsite",
}
# The usual MineColonies file name of each hut, preferred when a pack has several blueprints for it.
STANDARD_NAMES = {
    "residence": "residence", "smelter": "smeltery", "cowhand": "cowboy", "swineherd": "swineherder",
    "rabbitherder": "rabbithutch", "planter": "plantation",
}

# Domum Ornamentum's own materials -> the closest vanilla block that has stairs and slabs.
DO_MATERIALS = {
    "brick_extra": "bricks", "paper_extra": "white_terracotta", "white_paper_extra": "white_concrete",
    "wheat_extra": "bamboo_mosaic", "cobblestone_extra": "cobblestone", "cyan_brick_extra": "prismarine_bricks",
    "red_brick_extra": "red_nether_bricks", "sand_stone_bricks": "smooth_sandstone", "orange_brick_extra": "red_sandstone",
    "cream_stone_bricks": "end_stone_bricks", "blue_cobblestone_extra": "cobbled_deepslate", "cactus_extra": "bamboo_planks",
    "green_cactus_extra": "bamboo_planks", "black_brick_extra": "polished_blackstone_bricks",
    "green_brick_extra": "mossy_stone_bricks", "brown_bricks": "mud_bricks", "brown_brick_extra": "mud_bricks",
    "green_cobblestone_extra": "mossy_cobblestone", "light_gray_brick_extra": "stone_bricks",
    "cream_bricks": "smooth_sandstone", "gray_brick_extra": "polished_andesite", "beige_bricks": "sandstone",
    "beige_stone_bricks": "smooth_sandstone", "light_blue_brick_extra": "prismarine", "purple_brick_extra": "purpur_block",
    "mossy_cobblestone_extra": "mossy_cobblestone", "blue_brick_extra": "dark_prismarine", "brown_stone_bricks": "mud_bricks",
    "roan_stone_bricks": "polished_granite", "roan_bricks": "granite", "purple_cobblestone_extra": "purpur_block",
    "sand_bricks": "sandstone", "white_brick_extra": "polished_diorite", "lime_brick_extra": "mossy_stone_bricks",
    "magenta_brick_extra": "purpur_block", "yellow_brick_extra": "end_stone_bricks", "dark_brick": "deepslate_bricks",
    "dark_stone_bricks": "deepslate_bricks",
}
RENAMED = {"chain": "iron_chain", "grass": "short_grass"}

# Which texture slot holds the main material, per Domum Ornamentum block.
SLOT_ORDER = {
    "shingle": ["clay"], "shingle_flat": ["clay"], "shingle_flat_lower": ["clay"],
    "shingle_slab": ["oak_planks", "acacia_planks"],
    # Timber frames: dark_oak_planks is the infill, oak_planks the frame.
    "plain": ["dark_oak_planks"], "horizontal_plain": ["dark_oak_planks"], "framed": ["dark_oak_planks"],
    "blockpaperwall": ["dark_oak_planks"], "blocktiledpaperwall": ["dark_oak_planks"],
    "fancy_light": ["glowstone"], "center_light": ["glowstone"], "crossed_light": ["glowstone"],
    "framed_light": ["glowstone"], "horizontal_light": ["glowstone"], "vertical_light": ["glowstone"],
}
DEFAULT_SLOTS = ["oak_planks", "clay", "dark_oak_planks", "acacia_planks"]

WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo",
         "crimson", "warped")
COLORS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black")


# ----------------------------------------------------------------------------- NBT
def _read(f, tag):
    if tag == 1: return struct.unpack(">b", f.read(1))[0]
    if tag == 2: return struct.unpack(">h", f.read(2))[0]
    if tag == 3: return struct.unpack(">i", f.read(4))[0]
    if tag == 4: return struct.unpack(">q", f.read(8))[0]
    if tag == 5: return struct.unpack(">f", f.read(4))[0]
    if tag == 6: return struct.unpack(">d", f.read(8))[0]
    if tag == 7:
        n = struct.unpack(">i", f.read(4))[0]
        return f.read(n)
    if tag == 8:
        n = struct.unpack(">H", f.read(2))[0]
        return f.read(n).decode("utf-8", "replace")
    if tag == 9:
        sub = f.read(1)[0]
        n = struct.unpack(">i", f.read(4))[0]
        return [_read(f, sub) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            t = f.read(1)[0]
            if t == 0:
                return out
            n = struct.unpack(">H", f.read(2))[0]
            name = f.read(n).decode("utf-8", "replace")  # read the name before the value
            out[name] = _read(f, t)
    if tag == 11:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}i", f.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(f"unknown NBT tag {tag}")


def read_nbt(data):
    try:
        data = gzip.decompress(data)
    except OSError:
        pass
    f = io.BytesIO(data)
    tag = f.read(1)[0]
    f.read(struct.unpack(">H", f.read(2))[0])
    return _read(f, tag)


class IntArray(list):
    pass


def _payload(value):
    if isinstance(value, IntArray):
        return 11, struct.pack(">i", len(value)) + struct.pack(f">{len(value)}i", *value)
    if isinstance(value, bool):
        return 1, struct.pack(">b", int(value))
    if isinstance(value, int):
        return 3, struct.pack(">i", value)
    if isinstance(value, str):
        data = value.encode("utf-8")
        return 8, struct.pack(">H", len(data)) + data
    if isinstance(value, dict):
        out = bytearray()
        for key, child in value.items():
            tag, data = _payload(child)
            name = key.encode("utf-8")
            out += bytes([tag]) + struct.pack(">H", len(name)) + name + data
        return 10, bytes(out) + b"\0"
    if isinstance(value, list):
        if not value:
            return 9, b"\0" + struct.pack(">i", 0)
        parts = [_payload(v) for v in value]
        return 9, bytes([parts[0][0]]) + struct.pack(">i", len(parts)) + b"".join(p[1] for p in parts)
    raise TypeError(value)


def write_nbt(path, root):
    _, data = _payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(b"\x0a\0\0" + data)


# ----------------------------------------------------------------------------- blocks
class Blocks:
    """Knows which vanilla blocks exist and finds shaped variants of a material."""

    def __init__(self, names, hearthstead):
        self.names = names
        self.hearthstead = hearthstead

    def exists(self, name):
        return name in self.names

    def material(self, value):
        """A texture-data value ("minecraft:spruce_planks", "domum_ornamentum:wheat_extra") as a vanilla block."""
        ns, _, path = value.partition(":")
        if ns == "domum_ornamentum":
            return DO_MATERIALS.get(path, "oak_planks")
        path = RENAMED.get(path, path)
        return path if self.exists(path) else "oak_planks"

    def wood_of(self, material):
        for wood in sorted(WOODS, key=len, reverse=True):
            if re.fullmatch(rf"(stripped_)?{wood}_(planks|log|wood|stem|hyphae|block)", material) or material == f"{wood}_mosaic":
                return wood
        return None

    def bases(self, material):
        """Names a shaped variant could be built from, best first ("stone_bricks" -> "stone_brick")."""
        wood = self.wood_of(material)
        if wood:
            return ["bamboo_mosaic" if material == "bamboo_mosaic" else wood]
        out = []
        m = material
        for prefix in ("chiseled_", "cracked_", "cut_"):
            if m.startswith(prefix) and m not in ("cut_copper",):
                out.append(m)
                m = m[len(prefix):]
        if m.endswith("_block"):
            m = m[:-6]
        if m.endswith("_pillar"):
            m = m[:-7]
        out += [m[:-1] if m.endswith("bricks") or m.endswith("tiles") else m, m]
        if m in ("smooth_stone", "stone"):
            out.append("stone")
        if "sandstone" in m:
            out.append(m.replace("smooth_", "").replace("cut_", ""))
        return out

    def shaped(self, material, shape):
        """The material's stairs/slab/wall/fence/fence_gate, or None."""
        for base in self.bases(material):
            name = f"{base}_{shape}"
            if self.exists(name):
                return name
        return None

    def door(self, material, kind):
        """A wooden door or trapdoor of the material's wood, iron for metals, spruce otherwise."""
        wood = self.wood_of(material)
        if wood and self.exists(f"{wood}_{kind}"):
            return f"{wood}_{kind}"
        if "iron" in material or "copper" in material:
            return f"iron_{kind}"
        return f"spruce_{kind}"

    def pane(self, material):
        for color in COLORS:
            if material.startswith(color + "_stained_glass"):
                return f"{color}_stained_glass_pane"
        if material == "glass" or "glass" in material:
            return "glass_pane"
        if material in ("white_concrete", "white_terracotta"):
            return "white_stained_glass_pane"
        return "glass_pane"


def keep(props, *names):
    return {k: v for k, v in props.items() if k in names}


def convert_do(block, path, props, materials, blocks):
    """A Domum Ornamentum block as (name, properties)."""
    material = blocks.material(materials[0]) if materials else blocks.material(f"domum_ornamentum:{path}")
    if path in DO_MATERIALS:  # full DO bricks
        return DO_MATERIALS[path], {}
    if path.startswith("shingle_slab"):
        slab = blocks.shaped(material, "slab")
        return (slab, {"type": "bottom"}) if slab else (material, {})
    if path.startswith("shingle") or path in ("vanilla_stairs_compat", "dark_brick_stair"):
        if path == "dark_brick_stair":
            material = "deepslate_bricks"
        stairs = blocks.shaped(material, "stairs")
        if stairs:
            state = keep(props, "facing", "half", "shape", "waterlogged")
            state.setdefault("half", "bottom")
            return stairs, state
        return material, {}
    if path == "vanilla_slab_compat":
        slab = blocks.shaped(material, "slab")
        return (slab, keep(props, "type", "waterlogged")) if slab else (material, {})
    if path == "vanilla_wall_compat":
        wall = blocks.shaped(material, "wall") or "cobblestone_wall"
        state = keep(props, "up", "waterlogged")
        for side in ("north", "south", "east", "west"):
            if props.get(side) in ("true", "low", "tall"):
                state[side] = "low"
        return wall, state
    if path == "vanilla_fence_compat":
        fence = blocks.shaped(material, "fence")
        if not fence and blocks.wood_of(material) is None:
            fence = blocks.shaped(material, "wall")
            if fence:
                state = {s: ("low" if props.get(s) == "true" else "none") for s in ("north", "south", "east", "west")}
                state["up"] = "true"
                return fence, state
        return fence or "spruce_fence", keep(props, "north", "south", "east", "west", "waterlogged")
    if path == "vanilla_fence_gate_compat":
        wood = blocks.wood_of(material) or "spruce"
        return f"{wood}_fence_gate", keep(props, "facing", "in_wall", "open", "powered")
    if path in ("fancy_door", "vanilla_doors_compat"):
        return blocks.door(material, "door"), keep(props, "facing", "half", "hinge", "open", "powered")
    if path in ("fancy_trapdoors", "vanilla_trapdoors_compat", "panel"):
        if path == "panel" and "glass" in material and props.get("open") == "true":
            facing = props.get("facing", "north")
            axis = ("east", "west") if facing in ("north", "south") else ("north", "south")
            return blocks.pane(material), {axis[0]: "true", axis[1]: "true"}
        if path == "panel" and "glass" in material:
            return material, {}
        state = keep(props, "facing", "half", "open", "waterlogged")
        state.setdefault("half", "bottom")
        return blocks.door(material, "trapdoor"), state
    if path in ("blockpaperwall", "blocktiledpaperwall"):
        return blocks.pane(material), keep(props, "north", "south", "east", "west", "waterlogged")
    if path in ("squarepillar", "blockpillar", "blockypillar", "post"):
        if blocks.wood_of(material) and material.endswith(("_log", "_wood", "_stem", "_hyphae")):
            return material, {"axis": "y"}
        if path == "post":
            if blocks.wood_of(material):
                return f"{blocks.wood_of(material)}_fence", {}
            wall = blocks.shaped(material, "wall")
            if wall:
                return wall, {"up": "true"}
        if material in ("quartz_block", "quartz_pillar", "purpur_block", "purpur_pillar"):
            return material.replace("_block", "_pillar"), {"axis": "y"}
        return material, {}
    if path.endswith("_light"):
        return material, {}
    if path.endswith("_floating_carpet"):
        color = path[: -len("_floating_carpet")]
        return (f"{color}_carpet" if blocks.exists(f"{color}_carpet") else "white_carpet"), {}
    if path == "blockbarreldeco_standing":
        return "barrel", {"facing": "up"}
    if path == "blockbarreldeco_onside":
        return "barrel", {"facing": props.get("facing", "north")}
    if path == "architectscutter":
        return "stonecutter", keep(props, "facing")
    if path == "dynamic_timberframe":
        return material, {}
    # Timber frames and other full retexturable blocks. Crossed patterns show mostly the frame
    # (their first slot, oak_planks); plain ones mostly the infill (see SLOT_ORDER).
    return material, {}


def convert_minecolonies(path, props):
    """A MineColonies block (other than a hut) as (name, properties)."""
    if path in ("blockminecoloniesrack", "barrel_block") or path in ("blockpostbox", "blockstash"):
        return ("composter", {}) if path == "barrel_block" else ("barrel", {"facing": "up"})
    if path == "colony_wall_banner":
        return "white_wall_banner", keep(props, "facing")
    if path == "colony_banner":
        return "white_banner", keep(props, "rotation")
    if path == "blockminecoloniesnamedgrave" or path == "blockminecoloniesgrave":
        return "coarse_dirt", {}
    if path == "composted_dirt":
        return "podzol", {}
    if path in ("gate_iron", "gate_wood"):
        return ("iron_door" if path == "gate_iron" else "spruce_door"), keep(props, "facing", "half", "hinge", "open", "powered")
    if path.startswith("blockhut"):
        return "air", {}
    return "air", {}


class Converter:
    def __init__(self, blocks):
        self.blocks = blocks
        self.unknown = collections.Counter()

    def convert(self, root, hut):
        """A Structurize blueprint as a Hearthstead blueprint for the given hut, or None."""
        sx, sy, sz = root["size_x"], root["size_y"], root["size_z"]
        palette = root["palette"]
        packed = root["blocks"]
        indices = []
        for v in packed:
            indices.append((v >> 16) & 0xFFFF)
            indices.append(v & 0xFFFF)
        indices = indices[: sx * sy * sz]
        offset = root.get("optional_data", {}).get("structurize", {}).get("primary_offset")
        main = None
        if offset:
            main = (offset["y"] * sz + offset["z"]) * sx + offset["x"]
            if main >= len(indices) or not palette[indices[main]]["Name"].startswith("minecolonies:blockhut"):
                main = None
        if main is None:
            return None

        textures = {}
        for te in root.get("tile_entities", []):
            data = te.get("textureData") or te.get("originalTextureData")
            if data or "primaryBlock" in te:
                i = (te["y"] * sz + te["z"]) * sx + te["x"]
                textures[i] = (data or {}, te.get("primaryBlock"), te.get("secondaryBlock"))

        out_palette, out_index = [], {}

        def state_index(name, props):
            key = (name, tuple(sorted(props.items())))
            if key not in out_index:
                out_index[key] = len(out_palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(props)
                out_palette.append(entry)
            return out_index[key]

        cache = {}
        result = []
        for i, p in enumerate(indices):
            if i == main:
                facing = palette[p].get("Properties", {}).get("facing", "north")
                result.append(state_index(f"hearthstead:blockhut{hut}", {"facing": facing}))
                continue
            texture = textures.get(i)
            key = (p, None if texture is None else repr(texture))
            if key not in cache:
                name, props = self.convert_state(palette[p], texture)
                cache[key] = state_index(name if ":" in name else "minecraft:" + name, props)
            result.append(cache[key])

        packed_out = IntArray()
        if len(result) % 2:
            result.append(0)
        for a, b in zip(result[0::2], result[1::2]):
            v = (a << 16) | b
            packed_out.append(v - (1 << 32) if v >= 1 << 31 else v)
        return {
            "DataVersion": DATA_VERSION,
            "size": [sx, sy, sz],
            "palette": out_palette,
            "blocks": packed_out,
        }

    def convert_state(self, entry, texture):
        ns, _, path = entry["Name"].partition(":")
        props = {k: str(v) for k, v in entry.get("Properties", {}).items()}
        if ns == "minecraft":
            path = RENAMED.get(path, path)
            if self.blocks.exists(path):
                return path, props
            self.unknown[entry["Name"]] += 1
            return "air", {}
        if ns == "structurize":
            return "structure_void", {}
        if ns == "domum_ornamentum":
            materials = []
            if texture:
                data, primary, secondary = texture
                order = SLOT_ORDER.get(path, DEFAULT_SLOTS)
                slots = {k.split("/")[-1]: v for k, v in data.items()}
                materials = [slots[s] for s in order if s in slots]
                materials += [v for s, v in sorted(slots.items()) if s not in order]
                if primary:
                    materials = [primary] + ([secondary] if secondary else []) + materials
            name, state = convert_do(entry["Name"], path, props, materials, self.blocks)
            if not self.blocks.exists(name):
                self.unknown[f"{entry['Name']} -> {name}"] += 1
                return "oak_planks", {}
            return name, state
        if ns == "minecolonies":
            name, state = convert_minecolonies(path, props)
            return name, state
        self.unknown[entry["Name"]] += 1
        return "air", {}


# ----------------------------------------------------------------------------- packs
def level_of(name):
    m = re.search(r"(\d+)\.blueprint$", name)
    return int(m.group(1)) if m else None


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    jar = zipfile.ZipFile(sys.argv[1])
    client = zipfile.ZipFile(sys.argv[2])
    names = {n[len("assets/minecraft/blockstates/"):-5] for n in client.namelist()
             if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")}
    converter = Converter(Blocks(names, set(HUTS.values())))

    packs = sorted({n.split("/")[2] for n in jar.namelist() if n.startswith("blueprints/minecolonies/") and n.endswith("/pack.json")})
    skip = ("/decorations/", "/infrastructure/", "/walls/", "/roads/")
    if os.path.isdir(OUT):
        for dirpath, _, files in os.walk(OUT):
            for f in files:
                if f.endswith(".blueprint"):
                    os.remove(os.path.join(dirpath, f))
    info = {}
    total = 0
    for pack in packs:
        meta = json.loads(jar.read(f"blueprints/minecolonies/{pack}/pack.json"))
        info[pack] = {"name": meta.get("name", pack), "authors": meta.get("authors", [])}
        # (hut, base name) -> {level: (path, root)}
        found = collections.defaultdict(dict)
        for name in jar.namelist():
            if not name.startswith(f"blueprints/minecolonies/{pack}/") or not name.endswith(".blueprint"):
                continue
            if any(s in name for s in skip):
                continue
            level = level_of(name)
            if level is None or not 1 <= level <= 5:
                continue
            try:
                root = read_nbt(jar.read(name))
            except (ValueError, IndexError, struct.error) as e:
                print(f"  skipped {name}: {e}")
                continue
            offset = root.get("optional_data", {}).get("structurize", {}).get("primary_offset")
            if not offset:
                continue
            sx, sz = root["size_x"], root["size_z"]
            i = (offset["y"] * sz + offset["z"]) * sx + offset["x"]
            packed = root["blocks"]
            if i // 2 >= len(packed):
                continue
            v = packed[i // 2]
            index = ((v >> 16) & 0xFFFF) if i % 2 == 0 else (v & 0xFFFF)
            hut_block = root["palette"][index]["Name"]
            if not hut_block.startswith("minecolonies:blockhut"):
                continue
            hut = HUTS.get(hut_block[len("minecolonies:blockhut"):])
            if hut is None:
                continue
            base = re.sub(r"\d+\.blueprint$", "", name.rsplit("/", 1)[1])
            # Several folders can hold the same name (cavern's default and megahall): keep the first.
            found[(hut, base)].setdefault(level, (name, root))
        before = total
        by_hut = collections.defaultdict(list)
        for (hut, base), levels in found.items():
            by_hut[hut].append((base, levels))
        for hut, options in by_hut.items():
            standard = STANDARD_NAMES.get(hut, hut)

            def rank(option):
                base, levels = option
                return (-len(levels), base != standard, "alt" in base, len(base), base)

            base, levels = sorted(options, key=rank)[0]
            for level, (name, root) in sorted(levels.items()):
                converted = converter.convert(root, hut)
                if converted is None:
                    continue
                folder = os.path.join(OUT, "huts", hut) if pack == CLASSIC_PACK else os.path.join(OUT, "styles", pack, hut)
                write_nbt(os.path.join(folder, f"level{level}.blueprint"), converted)
                total += 1
        print(f"{pack}: {total - before} blueprints for {len(by_hut)} huts")
    with open(os.path.join(OUT, "packs.json"), "w") as f:
        json.dump(info, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote", total, "blueprints")
    if converter.unknown:
        print("unmapped blocks:", converter.unknown.most_common(20))


if __name__ == "__main__":
    main()
