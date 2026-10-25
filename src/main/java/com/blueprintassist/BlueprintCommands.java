package com.blueprintassist;

import com.blueprintassist.schem.Schematic;
import com.blueprintassist.schem.SchematicReader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

final class BlueprintCommands {
    private BlueprintCommands() {}

    private static final List<String> EXTENSIONS = List.of(".litematic", ".schem", ".nbt");

    static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bp")
                .executes(ctx -> help())
                .then(Commands.literal("help").executes(ctx -> help()))
                .then(Commands.literal("list").executes(ctx -> list()))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> load(StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("origin")
                        .executes(ctx -> originAtCrosshair())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> originAt(ctx))))))
                .then(Commands.literal("move")
                        .then(Commands.argument("dx", IntegerArgumentType.integer())
                                .then(Commands.argument("dy", IntegerArgumentType.integer())
                                        .then(Commands.argument("dz", IntegerArgumentType.integer())
                                                .executes(ctx -> move(ctx))))))
                .then(Commands.literal("swap").executes(ctx -> toggleSwap()))
                .then(Commands.literal("status").executes(ctx -> status()))
                .then(Commands.literal("off").executes(ctx -> off())));
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }

    private static Path schematicsDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
    }

    private static int help() {
        msg("Blueprint Assist:");
        msg("/bp list - Dateien in .minecraft/schematics anzeigen");
        msg("/bp load <name> - Blueprint laden");
        msg("/bp origin - Ecke der Blueprint dorthin setzen, wo du hinschaust (Block-Seite, die du anschaust)");
        msg("/bp origin <x> <y> <z> - Ecke auf feste Koordinaten setzen");
        msg("/bp move <dx> <dy> <dz> - Blueprint verschieben");
        msg("/bp swap - automatischen Hotbar-Wechsel an/aus");
        msg("/bp status, /bp off");
        return 1;
    }

    private static int list() {
        Path dir = schematicsDir();
        if (!Files.isDirectory(dir)) {
            msg("Ordner nicht gefunden: " + dir);
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString())
                    .filter(n -> EXTENSIONS.stream().anyMatch(e -> n.toLowerCase(Locale.ROOT).endsWith(e)))
                    .sorted().toList();
            if (names.isEmpty()) msg("Keine Blueprints im Ordner " + dir);
            else names.forEach(n -> msg(" - " + n));
        } catch (IOException e) {
            msg("Fehler beim Lesen des Ordners: " + e.getMessage());
        }
        return 1;
    }

    private static Path findFile(String name) {
        Path dir = schematicsDir();
        Path exact = dir.resolve(name);
        if (Files.isRegularFile(exact)) return exact;
        for (String ext : EXTENSIONS) {
            Path p = dir.resolve(name + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static int load(String name) {
        Path file = findFile(name.trim());
        if (file == null) {
            msg("Nicht gefunden: " + name + " (mit /bp list siehst du alle Dateien)");
            return 0;
        }
        msg("Lade " + file.getFileName() + " ...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return SchematicReader.read(file);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((schematic, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                msg("Fehler beim Laden: " + cause.getMessage());
                return;
            }
            BlueprintState.schematic = schematic;
            msg("Geladen: " + schematic.name + " (" + schematic.sizeX + " x " + schematic.sizeY + " x "
                    + schematic.sizeZ + ", " + schematic.blockCount() + " Bloecke)");
            if (BlueprintState.origin == null) {
                msg("Schau jetzt auf einen Block und gib /bp origin ein, um die Ecke festzulegen.");
            }
        }));
        return 1;
    }

    private static int originAtCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) {
            msg("Schau zuerst auf einen Block.");
            return 0;
        }
        BlockPos target = hit.getBlockPos();
        BlockPos pos = mc.level.getBlockState(target).canBeReplaced() ? target : target.relative(hit.getDirection());
        BlueprintState.origin = pos;
        msg("Ecke (0,0,0) der Blueprint liegt jetzt bei " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    private static int originAt(CommandContext<CommandSourceStack> ctx) {
        BlueprintState.origin = new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
        BlockPos o = BlueprintState.origin;
        msg("Ecke gesetzt auf " + o.getX() + " " + o.getY() + " " + o.getZ());
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> ctx) {
        BlockPos o = BlueprintState.origin;
        if (o == null) {
            msg("Setze zuerst die Ecke mit /bp origin.");
            return 0;
        }
        BlueprintState.origin = o.offset(IntegerArgumentType.getInteger(ctx, "dx"),
                IntegerArgumentType.getInteger(ctx, "dy"), IntegerArgumentType.getInteger(ctx, "dz"));
        o = BlueprintState.origin;
        msg("Ecke jetzt bei " + o.getX() + " " + o.getY() + " " + o.getZ());
        return 1;
    }

    private static int toggleSwap() {
        BlueprintState.autoSwap = !BlueprintState.autoSwap;
        msg("Automatischer Hotbar-Wechsel: " + (BlueprintState.autoSwap ? "AN" : "AUS"));
        return 1;
    }

    private static int status() {
        Schematic s = BlueprintState.schematic;
        BlockPos o = BlueprintState.origin;
        msg("Blueprint: " + (s == null ? "keine geladen" : s.name));
        msg("Ecke: " + (o == null ? "nicht gesetzt" : o.getX() + " " + o.getY() + " " + o.getZ()));
        msg("Hotbar-Wechsel: " + (BlueprintState.autoSwap ? "AN" : "AUS"));
        return 1;
    }

    private static int off() {
        BlueprintState.schematic = null;
        BlueprintState.origin = null;
        msg("Blueprint entladen.");
        return 1;
    }
}
