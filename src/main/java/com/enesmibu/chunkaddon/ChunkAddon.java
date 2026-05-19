package com.enesmibu.chunkaddon;

import com.enesmibu.chunkaddon.modules.SpawnerFinder;
import com.enesmibu.chunkaddon.modules.SusChunkFinder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("meteor-chunk-addon");
    public static final Category CATEGORY = new Category("Chunk Addon", Items.ENDER_EYE.getDefaultStack());

    @Override
    public void onInitialize() {
        LOG.info("Meteor Chunk Addon initialized!");
        Modules.get().add(new SpawnerFinder());
        Modules.get().add(new SusChunkFinder());
    }

    @Override
    public String getPackage() {
        return "com.enesmibu.chunkaddon";
    }
}
