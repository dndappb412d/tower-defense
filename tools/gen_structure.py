#!/usr/bin/env python3
"""
Writes Minecraft structure templates (.nbt) from a plain description, so a map can be reviewed as text and rebuilt
byte for byte. Needs no Minecraft and no third-party packages.

    python tools/gen_structure.py spike   src/main/resources/data/heartguard/structure/spike.nbt
    python tools/gen_structure.py meadow  src/main/resources/data/heartguard/structure/meadow.nbt

A template is a gzipped NBT compound: DataVersion, size, palette (block states) and blocks (position and palette index).
DATA_VERSION must match the game version the template is made for, so the game does not run its data fixers on it.
"""
import gzip
import struct
import sys

DATA_VERSION = 4790  # Minecraft 26.1.2 (world_version in version.json)

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY = 7, 8, 9, 10, 11


class Int(int):
    pass


class Str(str):
    pass


def _name(name):
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(value):
    if isinstance(value, Int):
        return TAG_INT, struct.pack(">i", value)
    if isinstance(value, Str):
        raw = value.encode("utf-8")
        return TAG_STRING, struct.pack(">H", len(raw)) + raw
    if isinstance(value, dict):
        body = b""
        for key, item in value.items():
            tag, data = _payload(item)
            body += bytes([tag]) + _name(key) + data
        return TAG_COMPOUND, body + bytes([TAG_END])
    if isinstance(value, list):
        if not value:
            return TAG_LIST, bytes([TAG_END]) + struct.pack(">i", 0)
        tag = _payload(value[0])[0]
        body = bytes([tag]) + struct.pack(">i", len(value))
        for item in value:
            item_tag, data = _payload(item)
            assert item_tag == tag, "a list holds one kind of tag"
            body += data
        return TAG_LIST, body
    raise TypeError(type(value))


def write_nbt(path, root):
    tag, data = _payload(root)
    assert tag == TAG_COMPOUND
    raw = bytes([TAG_COMPOUND]) + _name("") + data
    with gzip.GzipFile(path, "wb", mtime=0) as f:  # mtime=0 so the same input gives the same bytes
        f.write(raw)


class Template:
    def __init__(self, size_x, size_y, size_z):
        self.size = (size_x, size_y, size_z)
        self.palette = []
        self.blocks = []

    def _index(self, name, properties):
        key = (name, tuple(sorted((properties or {}).items())))
        for i, existing in enumerate(self.palette):
            if existing == key:
                return i
        self.palette.append(key)
        return len(self.palette) - 1

    def set(self, x, y, z, name, properties=None):
        assert 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2], (x, y, z)
        index = self._index(name, properties)
        self.blocks = [b for b in self.blocks if b[0] != (x, y, z)]
        self.blocks.append(((x, y, z), index))

    def fill(self, x0, y0, z0, x1, y1, z1, name, properties=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, properties)

    def compound(self):
        palette = []
        for name, properties in self.palette:
            entry = {"Name": Str(name)}
            if properties:
                entry["Properties"] = {k: Str(v) for k, v in properties}
            palette.append(entry)
        blocks = [{"pos": [Int(c) for c in pos], "state": Int(i)} for pos, i in sorted(self.blocks)]
        return {
            "DataVersion": Int(DATA_VERSION),
            "size": [Int(s) for s in self.size],
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        }

    def write(self, path):
        write_nbt(path, self.compound())


def spike():
    """A 7x3x7 stone platform with a gold block in the middle and a barrier-free air column above it."""
    t = Template(7, 3, 7)
    t.fill(0, 0, 0, 6, 0, 6, "minecraft:stone")
    t.set(3, 1, 3, "minecraft:gold_block")
    return t


def meadow():
    """
    The first map: a 64x8x40 meadow. Mobs enter at the west (spawn pads at x=4, z=12 and z=28), cross open grass and
    squeeze through a five-wide gap in a wall at x=44, and reach the Heart pad at the east end (x=56, z=20). Everything
    the match needs on top (Heart, veins, Command Post, Quartermaster) is placed from data/heartguard/maps/meadow.json,
    not baked in here, so this file is terrain only.
    """
    t = Template(64, 8, 40)
    t.fill(0, 0, 0, 63, 0, 39, "minecraft:grass_block")

    # Border wall, three high, so nothing wanders off the map.
    t.fill(0, 1, 0, 63, 3, 1, "minecraft:stone_bricks")
    t.fill(0, 1, 38, 63, 3, 39, "minecraft:stone_bricks")
    t.fill(0, 1, 2, 1, 3, 37, "minecraft:stone_bricks")
    t.fill(62, 1, 2, 63, 3, 37, "minecraft:stone_bricks")

    # Lanes, as dirt paths (decoration: mobs walk anywhere), converging on the Heart.
    for z in (12, 28):
        t.fill(2, 0, z - 1, 40, 0, z + 1, "minecraft:dirt_path")
    t.fill(39, 0, 12, 41, 0, 28, "minecraft:dirt_path")
    t.fill(39, 0, 19, 56, 0, 21, "minecraft:dirt_path")

    # Spawn pads and the Heart pad.
    for z in (12, 28):
        t.fill(3, 0, z - 1, 5, 0, z + 1, "minecraft:cobblestone")
    t.fill(53, 0, 17, 59, 0, 23, "minecraft:polished_andesite")

    # The choke: a wall across the field with a five-wide gap on the middle lane.
    t.fill(44, 1, 2, 44, 2, 17, "minecraft:stone_bricks")
    t.fill(44, 1, 23, 44, 2, 37, "minecraft:stone_bricks")

    # A few pillars in the open to break up the field.
    for x, z in ((20, 15), (20, 24), (30, 19)):
        t.fill(x, 1, z, x + 1, 3, z + 1, "minecraft:stone_bricks")
    return t


LAYOUTS = {"spike": spike, "meadow": meadow}

if __name__ == "__main__":
    name, out = sys.argv[1], sys.argv[2]
    LAYOUTS[name]().write(out)
    print("wrote", out)
