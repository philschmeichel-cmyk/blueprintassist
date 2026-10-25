package com.blueprintassist.schem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Liest .litematic, .schem (Sponge v1-v3) und .nbt (Structure-Block). Liest nur Block-IDs, keine Zustaende. */
public final class SchematicReader {
    private SchematicReader() {}

    public static Schematic read(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            root = Nbt.read(in);
        }
        String name = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        try {
            if (lower.endsWith(".litematic")) return litematic(name, root);
            if (lower.endsWith(".schem")) return sponge(name, root);
            if (lower.endsWith(".nbt")) return structure(name, root);
        } catch (IndexOutOfBoundsException | ClassCastException | NullPointerException e) {
            throw new IOException("Datei ist beschaedigt oder hat ein unbekanntes Layout: " + fileName, e);
        }
        throw new IOException("Unbekanntes Format: " + fileName);
    }

    // ---------- .nbt (Structure-Block) ----------

    private static Schematic structure(String name, Map<String, Object> root) throws IOException {
        List<Object> size = list(root.get("size"), "size");
        int sx = num(size.get(0), "size"), sy = num(size.get(1), "size"), sz = num(size.get(2), "size");

        Object paletteObj = root.get("palette");
        if (paletteObj == null) {
            paletteObj = list(root.get("palettes"), "palettes").get(0);
        }
        List<Object> palette = list(paletteObj, "palette");
        String[] ids = new String[palette.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = cleanId(str(compound(palette.get(i), "palette").get("Name"), "Name"));
        }

        List<Placed> out = new ArrayList<>();
        for (Object bo : list(root.get("blocks"), "blocks")) {
            Map<String, Object> b = compound(bo, "blocks");
            List<Object> pos = list(b.get("pos"), "pos");
            String id = ids[num(b.get("state"), "state")];
            if (isAir(id)) continue;
            out.add(new Placed(num(pos.get(0), "pos"), num(pos.get(1), "pos"), num(pos.get(2), "pos"), id));
        }
        return Schematic.of(name, sx, sy, sz, out);
    }

    // ---------- .schem (Sponge) ----------

    private static Schematic sponge(String name, Map<String, Object> root) throws IOException {
        if (root.get("Schematic") instanceof Map) {
            root = compound(root.get("Schematic"), "Schematic");
        }
        int version = root.get("Version") instanceof Number n ? n.intValue() : 2;
        int w = num(root.get("Width"), "Width");
        int h = num(root.get("Height"), "Height");
        int l = num(root.get("Length"), "Length");

        Map<String, Object> paletteMap;
        byte[] data;
        if (version >= 3) {
            Map<String, Object> blocks = compound(root.get("Blocks"), "Blocks");
            paletteMap = compound(blocks.get("Palette"), "Palette");
            data = (byte[]) blocks.get("Data");
        } else {
            paletteMap = compound(root.get("Palette"), "Palette");
            data = (byte[]) root.get("BlockData");
        }
        if (data == null) throw new IOException("Block-Daten fehlen");

        Map<Integer, String> ids = new HashMap<>();
        for (Map.Entry<String, Object> e : paletteMap.entrySet()) {
            ids.put(num(e.getValue(), "Palette"), cleanId(e.getKey()));
        }

        List<Placed> out = new ArrayList<>();
        int pos = 0;
        long total = (long) w * h * l;
        for (int i = 0; i < total; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (pos >= data.length) throw new IOException("Block-Daten zu kurz");
                int b = data[pos++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            String id = ids.get(value);
            if (id == null || isAir(id)) continue;
            int x = i % w;
            int z = (i / w) % l;
            int y = i / (w * l);
            out.add(new Placed(x, y, z, id));
        }
        return Schematic.of(name, w, h, l, out);
    }

    // ---------- .litematic (Litematica) ----------

    private record Region(int minX, int minY, int minZ, int sx, int sy, int sz, String[] palette, long[] states) {}

    private static Schematic litematic(String name, Map<String, Object> root) throws IOException {
        Map<String, Object> regions = compound(root.get("Regions"), "Regions");
        List<Region> list = new ArrayList<>();
        int gMinX = Integer.MAX_VALUE, gMinY = Integer.MAX_VALUE, gMinZ = Integer.MAX_VALUE;
        int gMaxX = Integer.MIN_VALUE, gMaxY = Integer.MIN_VALUE, gMaxZ = Integer.MIN_VALUE;

        for (Object ro : regions.values()) {
            Map<String, Object> r = compound(ro, "Region");
            Map<String, Object> p = compound(r.get("Position"), "Position");
            Map<String, Object> s = compound(r.get("Size"), "Size");
            int px = num(p.get("x"), "x"), py = num(p.get("y"), "y"), pz = num(p.get("z"), "z");
            int rx = num(s.get("x"), "x"), ry = num(s.get("y"), "y"), rz = num(s.get("z"), "z");
            // Negative Groessen bedeuten: die Region reicht von Position rueckwaerts.
            int minX = px + (rx < 0 ? rx + 1 : 0);
            int minY = py + (ry < 0 ? ry + 1 : 0);
            int minZ = pz + (rz < 0 ? rz + 1 : 0);
            int ax = Math.abs(rx), ay = Math.abs(ry), az = Math.abs(rz);

            List<Object> pal = list(r.get("BlockStatePalette"), "BlockStatePalette");
            String[] palette = new String[pal.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = cleanId(str(compound(pal.get(i), "BlockStatePalette").get("Name"), "Name"));
            }
            long[] states = (long[]) r.get("BlockStates");
            if (states == null) throw new IOException("BlockStates fehlt");

            list.add(new Region(minX, minY, minZ, ax, ay, az, palette, states));
            gMinX = Math.min(gMinX, minX);
            gMinY = Math.min(gMinY, minY);
            gMinZ = Math.min(gMinZ, minZ);
            gMaxX = Math.max(gMaxX, minX + ax);
            gMaxY = Math.max(gMaxY, minY + ay);
            gMaxZ = Math.max(gMaxZ, minZ + az);
        }
        if (list.isEmpty()) throw new IOException("Keine Region in der Datei");

        List<Placed> out = new ArrayList<>();
        for (Region r : list) {
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(r.palette().length - 1));
            long mask = (1L << bits) - 1L;
            long total = (long) r.sx() * r.sy() * r.sz();
            for (long i = 0; i < total; i++) {
                long startBit = i * bits;
                int word = (int) (startBit >>> 6);
                int offset = (int) (startBit & 63);
                long value = r.states()[word] >>> offset;
                if (offset + bits > 64) {
                    value |= r.states()[word + 1] << (64 - offset);
                }
                int idx = (int) (value & mask);
                if (idx >= r.palette().length) continue;
                String id = r.palette()[idx];
                if (isAir(id)) continue;
                int x = (int) (i % r.sx());
                int z = (int) ((i / r.sx()) % r.sz());
                int y = (int) (i / ((long) r.sx() * r.sz()));
                out.add(new Placed(r.minX() + x - gMinX, r.minY() + y - gMinY, r.minZ() + z - gMinZ, id));
            }
        }
        return Schematic.of(name, gMaxX - gMinX, gMaxY - gMinY, gMaxZ - gMinZ, out);
    }

    // ---------- Hilfsfunktionen ----------

    private static boolean isAir(String id) {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air") || id.equals("minecraft:structure_void");
    }

    /** Entfernt Zustaende wie [facing=north] und ergaenzt "minecraft:". */
    private static String cleanId(String s) {
        int i = s.indexOf('[');
        if (i >= 0) s = s.substring(0, i);
        return s.contains(":") ? s : "minecraft:" + s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object o, String what) throws IOException {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IOException("Feld fehlt oder ist falsch: " + what);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o, String what) throws IOException {
        if (o instanceof List<?> l) return (List<Object>) l;
        throw new IOException("Feld fehlt oder ist falsch: " + what);
    }

    private static int num(Object o, String what) throws IOException {
        if (o instanceof Number n) return n.intValue();
        throw new IOException("Feld fehlt oder ist falsch: " + what);
    }

    private static String str(Object o, String what) throws IOException {
        if (o instanceof String s) return s;
        throw new IOException("Feld fehlt oder ist falsch: " + what);
    }
}
