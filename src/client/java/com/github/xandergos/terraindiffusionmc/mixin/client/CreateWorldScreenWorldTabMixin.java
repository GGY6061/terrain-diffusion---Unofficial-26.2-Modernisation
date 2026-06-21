package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.client.WorldScaleSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reuses vanilla's World tab "Customize" button for Terrain Diffusion worlds.
 *
 * <p><b>26.2 confirmed names</b> (via decompiled MC 26.2 jar of
 * {@code CreateWorldScreen$WorldTab}):
 * <ul>
 *   <li>Outer reference: {@code this$0} (package-final, type {@link CreateWorldScreen})
 *       — the Yarn {@code field_42182}.</li>
 *   <li>Customize button: {@code customizeTypeButton} (private final, type
 *       {@link Button}) — the Yarn {@code field_42184}.</li>
 *   <li>Customize-screen opener: {@code openPresetEditor()} (private, no args) —
 *       replaces Yarn {@code openCustomizeScreen}.</li>
 * </ul>
 * The old injections into {@code method_48680}/{@code method_48681} (customize
 * available/visible) have been <b>removed</b> — in 26.2 those are inline lambdas
 * ({@code lambda$new$5}, {@code lambda$new$6}) that can't be mixin-targeted. The
 * button's active state is now controlled by whether a {@code PresetEditor} is
 * registered for the world preset; if the button is inactive, the user can still
 * use the world-type cycle to select Terrain Diffusion and the preset editor will
 * fire when vanilla makes the button available.
 *
 * <p>Other renames: {@code MinecraftClient}→{@link Minecraft}; {@code ButtonWidget}
 * →{@link Button}; {@code WorldCreator}→{@link WorldCreationUiState};
 * {@code WorldCreator.WorldType}→{@code WorldCreationUiState.WorldTypeEntry};
 * {@code worldType.preset()}→{@code WorldTypeEntry.preset()} returns
 * {@code Holder<WorldPreset>} (use {@link Holder#is(ResourceKey)} to compare);
 * {@code worldType.getName()}→{@code WorldTypeEntry.describePreset()};
 * 26.2 GUI reorg: {@code Minecraft.setScreen}→{@code Minecraft.gui.setScreen}.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class CreateWorldScreenWorldTabMixin {

    @Shadow(aliases = {"this$0"})
    @Final
    CreateWorldScreen parent;

    @Shadow(aliases = {"customizeTypeButton", "customizeButton"})
    private Button customizeTypeButton;

    private static final ResourceKey<WorldPreset> TERRAIN_DIFFUSION_PRESET_KEY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("terrain-diffusion-mc", "terrain_diffusion"));

    /**
     * Opens the Terrain Diffusion scale-selection screen instead of vanilla's
     * preset editor when a Terrain Diffusion world type is selected.
     */
    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void terrainDiffusionMc$openTerrainScaleScreen(CallbackInfo callbackInfo) {
        if (!isTerrainDiffusionWorldTypeSelected()) {
            return;
        }
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient != null) {
            minecraftClient.gui.setScreen(new WorldScaleSettingsScreen(parent));
            callbackInfo.cancel();
        }
    }

    private boolean isTerrainDiffusionWorldTypeSelected() {
        WorldCreationUiState uiState = parent.getUiState();
        if (uiState == null) {
            return false;
        }
        WorldCreationUiState.WorldTypeEntry worldType = uiState.getWorldType();
        if (worldType == null) {
            return false;
        }
        Holder<WorldPreset> presetHolder = worldType.preset();
        if (presetHolder != null && presetHolder.is(TERRAIN_DIFFUSION_PRESET_KEY)) {
            return true;
        }
        // Fallback: match by display name (covers custom preset entries without a key)
        return "terrain diffusion".equalsIgnoreCase(worldType.describePreset().getString());
    }
}
