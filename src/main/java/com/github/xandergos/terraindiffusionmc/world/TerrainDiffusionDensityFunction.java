package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function that drives the Terrain Diffusion final-density +
 * preliminary-surface-level noise router entries.
 *
 * <p>26.2 confirmed signatures (via mcsrc.dev decompiled source of
 * {@code net.minecraft.world.level.levelgen.DensityFunction}):
 * <ul>
 *   <li>{@code double compute(DensityFunction.FunctionContext)} — replaces Yarn
 *       {@code sample(NoisePos)}.</li>
 *   <li>{@code void fillArray(double[], DensityFunction.ContextProvider)} — replaces
 *       Yarn {@code fill(double[], EachApplier)}; {@code ContextProvider#forIndex(int)}
 *       returns a {@code FunctionContext} (replaces {@code EachApplier#at(int)}).</li>
 *   <li>{@code DensityFunction mapChildren(DensityFunction.Visitor)} — replaces
 *       Yarn {@code apply(DensityFunctionVisitor)}.</li>
 *   <li>{@code KeyDispatchDataCodec<? extends DensityFunction> codec()} — the
 *       registered registry value is still a {@link MapCodec} (loot-type
 *       unrolling), but the instance method now returns a
 *       {@link KeyDispatchDataCodec} (moved from DFU's
 *       {@code com.mojang.serialization} to {@code net.minecraft.util} in 26.2)
 *       wrapping it via {@link KeyDispatchDataCodec#of(MapCodec)}.</li>
 * </ul>
 */
public class TerrainDiffusionDensityFunction implements DensityFunction {
    /** Registered to {@code BuiltInRegistries.DENSITY_FUNCTION_TYPE}. */
    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    @Override
    public double compute(DensityFunction.FunctionContext pos) {
        int x = pos.blockX();
        int z = pos.blockZ();
        int y = pos.blockY();

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = x >> tileShift;
        int tileZ = z >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data == null || data.heightmap == null) {
            return -y;
        }

        int localX = Math.max(0, Math.min(data.width  - 1, x - blockStartX));
        int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));

        int targetHeight = HeightConverter.convertToMinecraftHeight(data.heightmap[localZ][localX]);
        return targetHeight - y;
    }

    private static final class FillContext {
        int blockStartX, blockStartZ, blockEndX, blockEndZ;
        HeightmapData data;

        void update(int x, int z) {
            if (x < blockStartX || x >= blockEndX) this.init(x, z);
            if (z < blockStartZ || z >= blockEndZ) this.init(x, z);
        }

        void init(int x, int z) {
            int tileSize = TerrainDiffusionConfig.tileSize();
            int tileShift = Integer.numberOfTrailingZeros(tileSize);

            int tileX = x >> tileShift;
            int tileZ = z >> tileShift;

            this.blockStartX = tileX << tileShift;
            this.blockStartZ = tileZ << tileShift;
            this.blockEndX = blockStartX + tileSize;
            this.blockEndZ = blockStartZ + tileSize;

            this.data = LocalTerrainProvider.getInstance()
                .fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        }
    }

    @Override
    public void fillArray(double[] densities, DensityFunction.ContextProvider contextProvider) {
        if (densities.length == 0) return;

        FillContext ctx = new FillContext();
        DensityFunction.FunctionContext pos = contextProvider.forIndex(0);
        int x = pos.blockX();
        int z = pos.blockZ();
        int y = pos.blockY();
        ctx.init(x, z);

        for (int i = 0; i < densities.length; i++) {
            pos = contextProvider.forIndex(i);
            x = pos.blockX();
            z = pos.blockZ();
            y = pos.blockY();
            ctx.update(x, z);

            HeightmapData data = ctx.data;
            if (data == null || data.heightmap == null) {
                densities[i] = -y;
                continue;
            }

            int localX = Math.max(0, Math.min(data.width  - 1, x - ctx.blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, z - ctx.blockStartZ));

            int targetHeight = HeightConverter
                .convertToMinecraftHeight(data.heightmap[localZ][localX]);
            densities[i] = targetHeight - y;
        }
    }

    @Override
    public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
        // This is a LEAF density function (no child DensityFunction arguments),
        // so there are no children to map. Returning `this` matches vanilla's
        // SimpleFunction#mapChildren contract. Calling visitor.apply(this) here
        // would cause infinite recursion: vanilla's mapAll() default impl creates
        // a RecursiveVisitor whose apply(input) calls input.mapChildren(this),
        // so a self-referential mapChildren → visitor.apply(this) → mapChildren
        // loop would stack-overflow (verified: crash log shows exactly that).
        return this;
    }

    @Override
    public double minValue() {
        return -64;
    }

    @Override
    public double maxValue() {
        return 1024;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(CODEC);
    }
}
