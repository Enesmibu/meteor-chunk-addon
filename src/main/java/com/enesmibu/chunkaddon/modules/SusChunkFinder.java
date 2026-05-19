package com.enesmibu.chunkaddon.modules;

import com.enesmibu.chunkaddon.ChunkAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThresholds = settings.createGroup("Thresholds");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius")
        .description("Kaç chunk yarıçapında aranacak.")
        .defaultValue(8)
        .min(1)
        .sliderMax(16)
        .build()
    );

    private final Setting<Boolean> showChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notifications")
        .description("Sus chunk bulunduğunda chat'e yazar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chestThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("chest-threshold")
        .description("Bir chunk'ı 'sus' yapan minimum sandık sayısı.")
        .defaultValue(3)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> spawnerThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("spawner-threshold")
        .description("Bir chunk'ı 'sus' yapan minimum spawner sayısı.")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> valuableOreThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("ore-threshold")
        .description("Bir chunk'ı 'sus' yapan minimum değerli cevher sayısı (diamond, emerald, ancient debris).")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> renderHighlight = sgRender.add(new BoolSetting.Builder()
        .name("render-highlight")
        .description("Sus chunk'ları render'da vurgular.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Chunk highlight dolgu rengi.")
        .defaultValue(new SettingColor(255, 0, 0, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Chunk highlight çizgi rengi.")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final ConcurrentHashMap<ChunkPos, SusInfo> susChunks = new ConcurrentHashMap<>();

    public SusChunkFinder() {
        super(ChunkAddon.CATEGORY, "sus-chunk-finder", "Anormal miktarda sandık, spawner veya değerli cevher içeren şüpheli chunk'ları bulur.");
    }

    @Override
    public void onActivate() {
        susChunks.clear();
        scanAllLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        susChunks.clear();
    }

    private void scanAllLoadedChunks() {
        if (mc.world == null || mc.player == null) return;
        ChunkPos playerChunk = mc.player.getChunkPos();
        int r = searchRadius.get();
        for (int cx = playerChunk.x - r; cx <= playerChunk.x + r; cx++) {
            for (int cz = playerChunk.z - r; cz <= playerChunk.z + r; cz++) {
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                analyzeChunk(chunk);
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        analyzeChunk(event.chunk());
    }

    private void analyzeChunk(WorldChunk chunk) {
        if (chunk == null || mc.world == null) return;

        int chests = 0;
        int spawners = 0;
        int valuableOres = 0;

        for (Map.Entry<BlockPos, net.minecraft.block.entity.BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            Block block = mc.world.getBlockState(entry.getKey()).getBlock();
            if (block instanceof ChestBlock || block instanceof TrappedChestBlock
                    || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
                chests++;
            }
            if (block == Blocks.SPAWNER) {
                spawners++;
            }
        }

        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int minY = mc.world.getBottomY();
        int maxY = minY + mc.world.getHeight();

        for (int x = startX; x < startX + 16; x += 2) {
            for (int z = startZ; z < startZ + 16; z += 2) {
                for (int y = minY; y < maxY; y++) {
                    Block b = mc.world.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
                            || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
                            || b == Blocks.ANCIENT_DEBRIS) {
                        valuableOres++;
                    }
                }
            }
        }

        boolean isSus = chests >= chestThreshold.get()
                || spawners >= spawnerThreshold.get()
                || valuableOres >= valuableOreThreshold.get();

        ChunkPos cp = chunk.getPos();

        if (isSus) {
            String reason = buildReason(chests, spawners, valuableOres);
            SusInfo info = new SusInfo(chests, spawners, valuableOres, reason);
            boolean isNew = !susChunks.containsKey(cp);
            susChunks.put(cp, info);
            if (isNew && showChat.get() && mc.player != null) {
                info("§cSus Chunk §f[" + cp.x + ", " + cp.z + "] §7-> " + reason);
            }
        } else {
            susChunks.remove(cp);
        }
    }

    private String buildReason(int chests, int spawners, int ores) {
        StringBuilder sb = new StringBuilder();
        if (chests >= chestThreshold.get()) sb.append("Sandik:").append(chests).append(" ");
        if (spawners >= spawnerThreshold.get()) sb.append("Spawner:").append(spawners).append(" ");
        if (ores >= valuableOreThreshold.get()) sb.append("Cevher:").append(ores);
        return sb.toString().trim();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderHighlight.get()) return;
        if (mc.world == null) return;

        int minY = mc.world.getBottomY();
        int maxY = minY + mc.world.getHeight();

        for (ChunkPos cp : susChunks.keySet()) {
            int x1 = cp.getStartX();
            int z1 = cp.getStartZ();
            int x2 = cp.getEndX() + 1;
            int z2 = cp.getEndZ() + 1;

            event.renderer.box(
                x1, minY, z1,
                x2, maxY, z2,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(susChunks.size());
    }

    private record SusInfo(int chests, int spawners, int ores, String reason) {}
}
