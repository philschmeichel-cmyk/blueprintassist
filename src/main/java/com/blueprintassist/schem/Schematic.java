package com.blueprintassist.schem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Eine geladene Blueprint. Enthaelt nur die Block-IDs (ohne Ausrichtung/Zustand) und keine Luft. */
public final class Schematic {
    public final String name;
    public final int sizeX, sizeY, sizeZ;
    private final Map<Long, String> blocks;

    private Schematic(String name, int sx, int sy, int sz, Map<Long, String> blocks) {
        this.name = name;
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        this.blocks = blocks;
    }

    static Schematic of(String name, int sx, int sy, int sz, List<Placed> placed) {
        Map<Long, String> map = new HashMap<>(Math.max(16, placed.size() * 2));
        for (Placed p : placed) {
            if (p.x() < 0 || p.y() < 0 || p.z() < 0) continue;
            map.put(key(p.x(), p.y(), p.z()), p.id());
        }
        return new Schematic(name, sx, sy, sz, map);
    }

    private static long key(int x, int y, int z) {
        return (long) x | ((long) y << 21) | ((long) z << 42);
    }

    /** Block-ID an dieser Stelle (relativ zur Ecke) oder null, wenn dort nichts (Luft) sein soll. */
    public String blockAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return null;
        return blocks.get(key(x, y, z));
    }

    public int blockCount() {
        return blocks.size();
    }
}
