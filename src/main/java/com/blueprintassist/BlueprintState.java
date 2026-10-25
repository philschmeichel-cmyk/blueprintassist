package com.blueprintassist;

import com.blueprintassist.schem.Schematic;
import net.minecraft.core.BlockPos;

/** Aktueller Zustand: welche Blueprint ist geladen und wo steht ihre Ecke (0,0,0) in der Welt. */
final class BlueprintState {
    private BlueprintState() {}

    static volatile Schematic schematic;
    static volatile BlockPos origin;
    static volatile boolean autoSwap = true;
}
