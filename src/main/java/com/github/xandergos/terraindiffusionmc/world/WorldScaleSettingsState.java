package com.github.xandergos.terraindiffusionmc.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persisted per-world settings for terrain diffusion.
 *
 * <p>This is stored in the world save via Minecraft's saved-data manager.
 *
 * <p>26.1 migration: {@code PersistentState} → {@link SavedData},
 * {@code PersistentStateType} → {@link SavedDataType}. The type now takes an
 * {@link Identifier} (resolved against the data folder, allowing subdirectories)
 * and a {@link MapCodec} rather than a {@code Codec}. {@code markDirty()} is
 * renamed to {@code setDirty()}.
 */
public final class WorldScaleSettingsState extends SavedData {
    // 26.2: SavedDataType takes a Codec<T> (not MapCodec<T>). SavedData is serialized
    // as a single NBT compound, so a plain Codec is correct here.
    private static final Codec<WorldScaleSettingsState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("scale", WorldScaleManager.DEFAULT_SCALE).forGetter(WorldScaleSettingsState::getScale),
            Codec.BOOL.optionalFieldOf("explicit_scale", false).forGetter(WorldScaleSettingsState::hasExplicitScale)
    ).apply(instance, WorldScaleSettingsState::new));

    private int scale;
    private boolean explicitScale;

    private WorldScaleSettingsState(int configuredScale, boolean hasExplicitScale) {
        this.scale = WorldScaleManager.clampScale(configuredScale);
        this.explicitScale = hasExplicitScale;
    }

    /**
     * Creates a default state for worlds that do not yet have saved terrain diffusion settings.
     */
    public static WorldScaleSettingsState createDefault() {
        return new WorldScaleSettingsState(WorldScaleManager.DEFAULT_SCALE, false);
    }

    /**
     * Type descriptor used by the saved-data manager.
     *
     * <p>The identifier resolves against the world's {@code data/} folder, so this
     * state is stored at {@code data/terrain-diffusion-mc/terrain_diffusion_world_settings.dat}.
     */
    public static final SavedDataType<WorldScaleSettingsState> TYPE =
            new SavedDataType<>(
                    Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion_world_settings"),
                    WorldScaleSettingsState::createDefault,
                    CODEC,
                    null);

    /**
     * Returns the currently persisted world scale.
     */
    public int getScale() {
        return scale;
    }

    /**
     * Returns whether this world has an explicitly chosen scale.
     */
    public boolean hasExplicitScale() {
        return explicitScale;
    }

    /**
     * Applies a new persisted world scale and marks the state dirty.
     */
    public void setScale(int configuredScale) {
        this.scale = WorldScaleManager.clampScale(configuredScale);
        this.explicitScale = true;
        setDirty();
    }
}
