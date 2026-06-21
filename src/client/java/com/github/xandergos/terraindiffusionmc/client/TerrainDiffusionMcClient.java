package com.github.xandergos.terraindiffusionmc.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side initialization for Terrain Diffusion.
 *
 * <p>Registers a {@link PresetEditor} for the Terrain Diffusion world preset so
 * that the "Customize" button on the world-creation World tab becomes active and
 * opens {@link WorldScaleSettingsScreen} when clicked.
 *
 * <p>26.2: {@code PresetEditor.EDITORS} is a {@code static final} map in an
 * interface, initialised by vanilla via {@code Map.of(...)} (immutable). Java 25
 * blocks both {@code Field.set} and {@code MethodHandles.findStaticSetter} on
 * static final fields. We use {@code sun.misc.Unsafe.putObjectVolatile} with the
 * field's raw offset to bypass the final-field restriction and replace the map
 * with a mutable copy containing our editor entry. If that fails too, the mod
 * continues to work but the "Customize" button stays inactive.
 */
public class TerrainDiffusionMcClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMcClient.class);

    public static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    @Override
    public void onInitializeClient() {
        PresetEditor editor = (createWorldScreen, worldCreationContext) ->
                new WorldScaleSettingsScreen(createWorldScreen);
        registerPresetEditor(editor);
    }

    /**
     * Replaces the immutable {@link PresetEditor#EDITORS} map with a mutable copy
     * containing our editor entry, using {@code sun.misc.Unsafe} to bypass the
     * static-final field write restriction in Java 25.
     */
    @SuppressWarnings("unchecked")
    private void registerPresetEditor(PresetEditor editor) {
        try {
            Field editorsField = PresetEditor.class.getDeclaredField("EDITORS");
            editorsField.setAccessible(true);

            Map<Optional<ResourceKey<WorldPreset>>, PresetEditor> original =
                    (Map<Optional<ResourceKey<WorldPreset>>, PresetEditor>) editorsField.get(null);

            Map<Optional<ResourceKey<WorldPreset>>, PresetEditor> mutable = new HashMap<>(original);
            mutable.put(Optional.of(TERRAIN_DIFFUSION_PRESET_KEY), editor);

            // sun.misc.Unsafe bypasses the static-final field write restriction.
            // This is the same mechanism used by serialization frameworks and
            // works reliably across Java versions including 25.
            Object unsafe = getUnsafe();
            if (unsafe != null) {
                Object base = unsafe.getClass().getMethod("staticFieldBase", Field.class).invoke(unsafe, editorsField);
                long offset = (long) unsafe.getClass().getMethod("staticFieldOffset", Field.class).invoke(unsafe, editorsField);
                unsafe.getClass().getMethod("putObjectVolatile", Object.class, long.class, Object.class)
                      .invoke(unsafe, base, offset, mutable);
                LOG.info("Registered Terrain Diffusion PresetEditor (customize button enabled)");
            } else {
                LOG.warn("sun.misc.Unsafe unavailable; customize button will be inactive");
            }
        } catch (Throwable t) {
            LOG.warn("Could not register Terrain Diffusion PresetEditor; customize button will be inactive", t);
        }
    }

    /**
     * Obtains the {@code sun.misc.Unsafe} singleton via reflection.
     */
    private Object getUnsafe() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return theUnsafe.get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
