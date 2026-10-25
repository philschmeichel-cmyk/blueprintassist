package com.blueprintassist;

import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Blueprint Assist: reine Client-Mod. Laedt .litematic/.schem/.nbt aus .minecraft/schematics,
 * markiert fehlende Bloecke mit Partikeln und waehlt beim Anschauen automatisch den passenden
 * Block in der Hotbar aus. Platziert wird danach ganz normal per Rechtsklick.
 */
@Mod(BlueprintAssist.MODID)
public final class BlueprintAssist {
    public static final String MODID = "blueprintassist";

    public BlueprintAssist(FMLJavaModLoadingContext context) {
        // UNSICHERE STELLE 1: Event-Namen/Bus-Schreibweise fuer Forge 26.2
        RegisterClientCommandsEvent.BUS.addListener(BlueprintCommands::register);
        // UNSICHERE STELLE 2: Name des Client-Tick-Events in Forge 26.2
        TickEvent.ClientTickEvent.Post.BUS.addListener(BlueprintClient::onClientTick);
    }
}
