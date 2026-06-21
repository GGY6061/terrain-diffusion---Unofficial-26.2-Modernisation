package com.github.xandergos.terraindiffusionmc.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents altitude-based snow from falling in non-snowy biomes, so that tall
 * Terrain Diffusion mountains in temperate biomes don't suddenly become snowy.
 *
 * <p>26.1 migration: types remapped to Mojang official names
 * ({@code net.minecraft.core.BlockPos}, {@code net.minecraft.world.level.biome.Biome}).
 * The shadowed temperature accessor is Mojang's {@code getBaseTemperature()}.
 *
 * <p>VERIFY against the 26.2 jar: Minecraft has been gradually moving biome
 * precipitation from a positional computation ({@code getPrecipitation(BlockPos, int)})
 * to a flat biome property. If 26.2 no longer exposes the two-arg
 * {@code getPrecipitation}, this mixin is no longer needed and should be removed
 * (together with its entry in {@code terrain-diffusion-mc.mixins.json}); altitude
 * snow would then already be gone from vanilla.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {

    @Shadow
    public abstract float getBaseTemperature();

    @Shadow
    public abstract boolean hasPrecipitation();

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void preventHighAltitudeSnow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (!this.hasPrecipitation()) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }

        // Base temperature >= 0.15 means this is NOT a snowy biome.
        // Always return RAIN to prevent altitude-based snow in non-snowy biomes.
        if (this.getBaseTemperature() >= 0.15F) {
            cir.setReturnValue(Biome.Precipitation.RAIN);
        }
        // For snowy biomes (base temp < 0.15), let vanilla handle it
    }
}
