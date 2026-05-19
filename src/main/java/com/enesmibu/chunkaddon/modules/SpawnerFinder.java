package com.enesmibu.chunkaddon.modules;

import com.enesmibu.chunkaddon.ChunkAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius")
        .description("Kaç chunk yarıçapında aranacak (chunk cinsinden).")
        .defaultValue(5)
        .min(1)
        .sliderMax(16)
        .build()
    );

    private final Setting<Boolean> showChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notifications")
        .description("Spawner bulunduğunda chat'e mesaj yazar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderBox = sgRender.add(new BoolSetting.Builder()
        .name("render-box")
        .description("Spawner'ın etrafına kutu çizer.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Kutu dolgu rengi.")
        .defaultValue(new SettingColor(255, 100, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Kutu çizgi rengi.")
        .defaultValue(new SettingColor(255, 150, 0, 255))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Kutunun çizim modu.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final ConcurrentHashMap<BlockPos, Boolean> spawnerPositions = new ConcurrentHashMap<>();

    public SpawnerFinder() {
        super(ChunkAddon.CATEGORY, "spawner-finder", "Çevredeki chunk'larda spawner arar ve ESP ile gösterir.");
    }

    @Override
    public void onActivate() {
        spawnerPositions.clear();
        scanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        spawnerPositions.clear();
    }

    private void scanLoadedChunks() {
        if (mc.world == null || mc.player == null) return;
        ChunkPos playerChunk = mc.player.getChunkPos();
        int r = searchRadius.get();
        for (int cx = playerChunk.x - r; cx <= playerChunk.x + r; cx++) {
            for (int cz = playerChunk.z - r; cz <= playerChunk.z + r; cz++) {
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                scanChunkForSpawners(chunk);
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        scanChunkForSpawners(event.chunk());
    }

    private void scanChunkForSpawners(WorldChunk chunk) {
        if (chunk == null) return;
        chunk.getBlockEntities().forEach((pos, be) -> {
            if (be instanceof MobSpawnerBlockEntity) {
                if (!spawnerPositions.containsKey(pos)) {
                    spawnerPositions.put(pos.toImmutable(), true);
                    if (showChat.get() && mc.player != null) {
                        info("Spawner bulundu: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                    }
                }
            }
        });
        // Remove spawners from unloaded positions
        spawnerPositions.keySet().removeIf(pos -> {
            if (mc.world == null) return true;
            return mc.world.getBlockState(pos).getBlock() != Blocks.SPAWNER;
        });
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderBox.get()) return;
        for (BlockPos pos : spawnerPositions.keySet()) {
            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(spawnerPositions.size());
    }
}
