package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.pipeline.SpawnSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides vanilla's spawn-point search so that Terrain Diffusion worlds spawn
 * on land near the origin. Vanilla's default would often dump the player in the
 * ocean, since the AI terrain can produce large seas around (0, 0).
 *
 * <p>26.2 migration: the spawn API changed from
 * {@code ServerLevelData.setSpawnPoint(GlobalPos, float)} to
 * {@code MinecraftServer.setRespawnData(LevelData.RespawnData)}. The
 * {@code setInitialSpawn} static method still exists (private static) but now
 * takes a {@link LevelLoadListener} (renamed from {@code ChunkProgressListener}).
 *
 * <p>This mixin injects at the HEAD of {@code setInitialSpawn}, computes a land
 * spawn via {@link SpawnSelector#findSpawnBlockPos()}, sets it on the server via
 * {@link MinecraftServer#setRespawnData(LevelData.RespawnData)}, and cancels the
 * vanilla spawn search.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void terrainDiffusionMc$overrideWorldSpawn(ServerLevel world, ServerLevelData worldProperties,
                                                              boolean bonusChest, boolean debugWorld,
                                                              LevelLoadListener loadProgress, CallbackInfo ci) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos spawnPos = SpawnSelector.findSpawnBlockPos();
        MinecraftServer server = world.getServer();
        if (server != null) {
            // 26.2 spawn model: set respawn data (GlobalPos + angle) on the server.
            // RespawnData.of(dimensionKey, pos, yaw, pitch) replaces the old
            // ServerLevelData.setSpawnPoint(GlobalPos, float).
            LevelData.RespawnData respawnData = LevelData.RespawnData.of(
                    Level.OVERWORLD, spawnPos, 0.0F, 0.0F);
            server.setRespawnData(respawnData);
        }
        ci.cancel();
    }
}
