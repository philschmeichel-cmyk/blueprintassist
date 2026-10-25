package com.blueprintassist;

import com.blueprintassist.schem.Schematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

final class BlueprintClient {
    private BlueprintClient() {}

    private static final int MARKER_RADIUS = 3;
    private static final int MARKER_LIMIT = 80;
    private static final Map<String, Block> BLOCK_CACHE = new HashMap<>();
    private static int tickCounter;
    private static int lastSwapTick = -100;

    static Block resolve(String id) {
        return BLOCK_CACHE.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key);
            if (identifier == null) return Blocks.AIR;
            Block block = ForgeRegistries.BLOCKS.getValue(identifier);
            return block == null ? Blocks.AIR : block;
        });
    }

    /** Welcher Block soll laut Blueprint an dieser Weltposition stehen? null = nichts/unbekannt. */
    private static Block expectedAt(Schematic s, BlockPos origin, BlockPos p) {
        String id = s.blockAt(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ());
        if (id == null) return null;
        Block block = resolve(id);
        return block == Blocks.AIR ? null : block;
    }

    static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Schematic schematic = BlueprintState.schematic;
        BlockPos origin = BlueprintState.origin;
        if (schematic == null || origin == null) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        tickCounter++;

        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        // Dort, wo beim Rechtsklick der neue Block landen wuerde:
        BlockPos target = hit.getBlockPos();
        BlockPos placePos = mc.level.getBlockState(target).canBeReplaced()
                ? target
                : target.relative(hit.getDirection());

        if (tickCounter % 5 == 0) {
            spawnMarkers(mc, schematic, origin, placePos);
        }

        Block expected = expectedAt(schematic, origin, placePos);
        if (expected == null) return;

        BlockState current = mc.level.getBlockState(placePos);
        if (!current.isAir() && !current.canBeReplaced()) return; // dort steht schon etwas

        mc.gui.hud.setOverlayMessage(Component.literal("Blueprint: ").append(expected.getName()), false);

        if (!BlueprintState.autoSwap) return;
        Item item = expected.asItem();
        if (item == Items.AIR) return;
        if (mc.player.getMainHandItem().is(item)) return;

        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).is(item)) {
                // UNSICHERE STELLE 3: setSelectedSlot (seit 1.21.5 der Weg, den Hotbar-Slot zu waehlen)
                inventory.setSelectedSlot(slot);
                return;
            }
        }

        // Nicht in der Hotbar: im restlichen Inventar (Slots 9-35) suchen und mit dem gewaehlten Hotbar-Slot tauschen.
        for (int slot = 9; slot < 36; slot++) {
            if (inventory.getItem(slot).is(item)) {
                if (tickCounter - lastSwapTick < 4) return;
                lastSwapTick = tickCounter;
                // UNSICHERE STELLE 4: handleContainerInput / ContainerInput.SWAP (26.2). Button = Hotbar-Slot 0-8.
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot,
                        inventory.getSelectedSlot(), ContainerInput.SWAP, mc.player);
                return;
            }
        }
        mc.gui.hud.setOverlayMessage(Component.literal("Nicht im Inventar: ").append(expected.getName()), false);
    }

    private static void spawnMarkers(Minecraft mc, Schematic s, BlockPos origin, BlockPos center) {
        int spawned = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -MARKER_RADIUS; dx <= MARKER_RADIUS; dx++) {
            for (int dy = -MARKER_RADIUS; dy <= MARKER_RADIUS; dy++) {
                for (int dz = -MARKER_RADIUS; dz <= MARKER_RADIUS; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (expectedAt(s, origin, pos) == null) continue;
                    if (!mc.level.getBlockState(pos).isAir()) continue;
                    boolean isCurrent = dx == 0 && dy == 0 && dz == 0;
                    mc.level.addParticle(isCurrent ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
                    if (++spawned >= MARKER_LIMIT) return;
                }
            }
        }
    }
}
