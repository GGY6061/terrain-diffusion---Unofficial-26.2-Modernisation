package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;

/**
 * Biome source backed by the Terrain Diffusion elevation+climate heightmap.
 *
 * <p>26.2 confirmed signatures (via mcsrc.dev decompiled source):
 * <ul>
 *   <li>{@code protected abstract MapCodec<? extends BiomeSource> codec()}</li>
 *   <li>{@code protected abstract Stream<Holder<Biome>> collectPossibleBiomes()}
 *       — replaces Yarn {@code biomeStream()}.</li>
 *   <li>{@code public abstract Holder<Biome> getNoiseBiome(int, int, int, Climate.Sampler)}
 *       — replaces Yarn {@code getBiome(int, int, int, MultiNoiseSampler)}.</li>
 *   <li>{@code findBiomeHorizontal(...)} is concrete (not abstract), so the old
 *       {@code locateBiome} overrides are removed.</li>
 * </ul>
 * The codec uses {@link RegistryOps#retrieveGetter} — {@code RegistryOps} moved
 * from {@code net.minecraft.core} to {@code net.minecraft.resources} in 26.x.
 */
public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final ResourceKey<Biome> FOREST_SPARSE = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "forest_sparse"));
    private static final ResourceKey<Biome> TAIGA_SPARSE = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "taiga_sparse"));
    private static final ResourceKey<Biome> SNOWY_TAIGA_SPARSE = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "snowy_taiga_sparse"));

    public static final MapCodec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, instance.stable(TerrainDiffusionBiomeSource::new)));


    private final HolderGetter<Biome> biomeLookup;
    private Map<Short, Holder<Biome>> biomeIdMap = null;

    public TerrainDiffusionBiomeSource(HolderGetter<Biome> biomeLookup) {
        this.biomeLookup = biomeLookup;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    private void requireBiomeIdMap() {
        if (biomeIdMap == null) {
            biomeIdMap = Map.ofEntries(
                    entry((short) 1, this.biomeLookup.getOrThrow(Biomes.PLAINS)),
                    entry((short) 3, this.biomeLookup.getOrThrow(Biomes.SNOWY_PLAINS)),
                    entry((short) 5, this.biomeLookup.getOrThrow(Biomes.DESERT)),
                    entry((short) 6, this.biomeLookup.getOrThrow(Biomes.SWAMP)),
                    entry((short) 8, this.biomeLookup.getOrThrow(Biomes.FOREST)),
                    entry((short) 15, this.biomeLookup.getOrThrow(Biomes.TAIGA)),
                    entry((short) 16, this.biomeLookup.getOrThrow(Biomes.SNOWY_TAIGA)),
                    entry((short) 17, this.biomeLookup.getOrThrow(Biomes.SAVANNA)),
                    entry((short) 19, this.biomeLookup.getOrThrow(Biomes.WINDSWEPT_HILLS)),
                    entry((short) 23, this.biomeLookup.getOrThrow(Biomes.JUNGLE)),
                    entry((short) 26, this.biomeLookup.getOrThrow(Biomes.BADLANDS)),
                    entry((short) 29, this.biomeLookup.getOrThrow(Biomes.MEADOW)),
                    entry((short) 31, this.biomeLookup.getOrThrow(Biomes.GROVE)),
                    entry((short) 32, this.biomeLookup.getOrThrow(Biomes.SNOWY_SLOPES)),
                    entry((short) 33, this.biomeLookup.getOrThrow(Biomes.FROZEN_PEAKS)),
                    entry((short) 35, this.biomeLookup.getOrThrow(Biomes.STONY_PEAKS)),
                    entry((short) 41, this.biomeLookup.getOrThrow(Biomes.WARM_OCEAN)),
                    entry((short) 44, this.biomeLookup.getOrThrow(Biomes.OCEAN)),
                    entry((short) 46, this.biomeLookup.getOrThrow(Biomes.COLD_OCEAN)),
                    entry((short) 48, this.biomeLookup.getOrThrow(Biomes.FROZEN_OCEAN)),
                    entry((short) 108, this.biomeLookup.getOrThrow(FOREST_SPARSE)),
                    entry((short) 115, this.biomeLookup.getOrThrow(TAIGA_SPARSE)),
                    entry((short) 116, this.biomeLookup.getOrThrow(SNOWY_TAIGA_SPARSE))
            );
        }
    }

    /**
     * Returns the stream of all biomes this source can emit. Required by
     * {@link BiomeSource#collectPossibleBiomes()} (replaces the old Yarn
     * {@code biomeStream()}).
     */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        requireBiomeIdMap();
        return biomeIdMap.values().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
        requireBiomeIdMap();
        Holder<Biome> defaultEntry = biomeIdMap.get((short) 1);

        // x, y, z are in quart coordinates (block / 4). QuartPos.toBlock(q) == q << 2.
        int blockX = x << 2;
        int blockZ = z << 2;

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = blockX >> tileShift;
        int tileZ = blockZ >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data != null && data.biomeIds != null) {
            int localX = Math.max(0, Math.min(data.width  - 1, blockX - blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, blockZ - blockStartZ));
            Holder<Biome> entry = biomeIdMap.get(data.biomeIds[localZ][localX]);
            if (entry != null) return entry;
        }

        return defaultEntry;
    }

    /**
     * Biome location is not supported by this source (the terrain is generated
     * from a diffusion model, not searchable noise). {@code findBiomeHorizontal}
     * in the base class handles the {@code /locate biome} flow and will simply
     * find nothing — which is acceptable.
     */
    @SuppressWarnings("unused")
    private Pair<BlockPos, Holder<Biome>> locateBiomeDisabled(BlockPos origin, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval, Predicate<Holder<Biome>> predicate, Climate.Sampler noiseSampler, LevelReader world) {
        return null;
    }

    @SuppressWarnings("unused")
    private Pair<BlockPos, Holder<Biome>> locateBiomeDisabled(int x, int y, int z, int radius, int blockCheckInterval, Predicate<Holder<Biome>> predicate, RandomSource random, boolean bl, Climate.Sampler noiseSampler) {
        return null;
    }
}
