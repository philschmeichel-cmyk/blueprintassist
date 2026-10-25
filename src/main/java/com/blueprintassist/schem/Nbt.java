package com.blueprintassist.schem;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Kleiner eigener NBT-Leser (ohne Minecraft-Klassen). Compound = Map, Liste = List, Arrays = byte[]/int[]/long[]. */
final class Nbt {
    private Nbt() {}

    static Map<String, Object> read(InputStream raw) throws IOException {
        PushbackInputStream pb = new PushbackInputStream(new BufferedInputStream(raw), 2);
        int b1 = pb.read();
        int b2 = pb.read();
        if (b2 >= 0) pb.unread(b2);
        if (b1 >= 0) pb.unread(b1);
        InputStream src = (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(pb) : pb;
        DataInputStream in = new DataInputStream(new BufferedInputStream(src));
        int type = in.readUnsignedByte();
        if (type != 10) throw new IOException("Keine gueltige NBT-Datei");
        in.readUTF();
        return readCompound(in);
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new HashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
        return map;
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                int n = in.readInt();
                byte[] a = new byte[n];
                in.readFully(a);
                return a;
            }
            case 8: return in.readUTF();
            case 9: {
                int t = in.readUnsignedByte();
                int n = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, Math.min(n, 1 << 16)));
                for (int i = 0; i < n; i++) list.add(readPayload(in, t));
                return list;
            }
            case 10: return readCompound(in);
            case 11: {
                int n = in.readInt();
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                int n = in.readInt();
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IOException("Unbekannter NBT-Typ " + type);
        }
    }
}
