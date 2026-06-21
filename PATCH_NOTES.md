# Terrain Diffusion MC — Patch Notes: 1.21.11 → 26.2

This documents the complete port of the Terrain Diffusion Fabric mod from
Minecraft Java Edition **1.21.11** to **26.2**. Every change below was verified
against the actual Minecraft 26.2 client jar (decompiled locally) and tested at
runtime with all three build variants (CUDA, DirectML, CPU).

---

## Background: what changed between 1.21.11 and 26.2

Mojang made three sweeping changes that broke every existing mod:

1. **Year-based versioning** — versions now start with the year (26.1, 26.2, ...)
2. **Deobfuscation** (26.1) — the game ships with Mojang's official source names.
   **Yarn mappings were dropped entirely.** Every mod must migrate to Mojang
   official mappings. No mod from 1.21.11 or earlier runs without recompilation.
3. **Java 25** required (was 21). Vanilla uses JEP 447 (statements before `super`).

Plus a SavedData refactor, World Clocks, GUI reorg, and dozens of API renames.

### Dependency coordinates (verified)
| Component | 1.21.11 | 26.2 |
|-----------|---------|------|
| Minecraft | 1.21.11 | **26.2** |
| Fabric Loader | 0.18.1 | **0.19.3** |
| Fabric API | 0.141.2+1.21.11 | **0.152.2+26.2** |
| Fabric Loom | 1.13-SNAPSHOT (remapping) | **1.17-SNAPSHOT** (non-remapping, `net.fabricmc.fabric-loom`) |
| Gradle | 8.14.1 | **9.5.1** |
| Java | 21 | **25** |
| Yarn mappings | 1.21.11+build.4 | **removed** (game is deobfuscated) |
| DataFixerUpper | (bundled) | **10.0.21** |

---

## Build system changes

### `gradle.properties`
- `minecraft_version=26.2`, `loader_version=0.19.3`, `fabric_version=0.152.2+26.2`
- Added `loom_version=1.17-SNAPSHOT` (Loom 1.17 is only published as a SNAPSHOT)
- Removed `yarn_mappings` (no longer used — game is deobfuscated)
- Bumped `mod_version` to `2.3.0`

### `build.gradle`
- Plugin: `id 'fabric-loom' version '1.13-SNAPSHOT'` → `id 'net.fabricmc.fabric-loom' version "${loom_version}"`
  - New plugin ID includes the group; old remapping plugin is gone
- Removed the `mappings` dependency line entirely
- `modImplementation` → `implementation` for Fabric Loader + Fabric API (Loom no longer remaps)
- Java target: 21 → 25
- `remapJar` → `jar` (Loom 1.17 outputs the plain jar)

### `gradle/wrapper/gradle-wrapper.properties`
- Gradle `8.14.1` → `9.5.1`

### `fabric.mod.json`
- Dependency `"fabric"` → `"fabric-api"` (the `fabric` mod-id was removed)
- Added `"java": ">=25"` dependency

### Mixin configs (`*.mixins.json`)
- `compatibilityLevel`: `JAVA_21` → `JAVA_25`

---

## Source changes (Yarn → Mojang official mappings)

14 files had Minecraft/Fabric imports; 24 pure-Java files (tensor math, ONNX
pipeline, biome classifier, explorer HTTP server) needed no changes.

### `TerrainDiffusionMc.java` (main initializer)
- `net.minecraft.registry.Registries` → `net.minecraft.core.registries.BuiltInRegistries`
  - `BIOME_SOURCE`, `DENSITY_FUNCTION_TYPE` fields confirmed present
- `Identifier.of(ns, path)` → `Identifier.fromNamespaceAndPath(ns, path)`
- `net.minecraft.world.World` → `net.minecraft.world.level.Level` (`World.OVERWORLD` → `Level.OVERWORLD`)
- `world.getRegistryKey()` → `world.dimension()`
- `net.minecraft.text.Text` → `net.minecraft.network.chat.Component`
- `ServerWorldEvents` → **`ServerLevelEvents`** (Fabric API 26.1 World→Level rename)
- `CommandSourceStack`, `Commands.literal`, `sendSuccess`/`sendFailure` (Mojang names)
- `MutableComponent.styled(...)` → `MutableComponent.withStyle(...)` (Mojang name)

### `world/TerrainDiffusionBiomeSource.java`
- Extends `net.minecraft.world.level.biome.BiomeSource` (confirmed: `Biome`/`Biomes`/`Climate.Sampler`/`Holder`/`HolderGetter`/`ResourceKey`)
- Codec: `RegistryOps.retrieveGetter(Registries.BIOME)` — `RegistryOps` moved from `net.minecraft.core` to **`net.minecraft.resources`**
- Abstract method overrides (verified against decompiled 26.2 `BiomeSource`):
  - `biomeStream()` → **`collectPossibleBiomes()`** (returns `Stream<Holder<Biome>>`)
  - `getBiome(int,int,int,MultiNoiseSampler)` → **`getNoiseBiome(int,int,int,Climate.Sampler)`**
  - `codec()` returns `MapCodec<? extends BiomeSource>` (protected)
- Removed `locateBiome` overrides (replaced by concrete `findBiomeHorizontal` in base class)
- Inlined `BiomeCoords.toBlock(q)` as `q << 2` (quart→block conversion)

### `world/TerrainDiffusionDensityFunction.java`
- Implements `net.minecraft.world.level.levelgen.DensityFunction`
- **`CodecHolder` removed in 26.2** — `codec()` now returns `KeyDispatchDataCodec`
- `KeyDispatchDataCodec` moved from `com.mojang.serialization` to **`net.minecraft.util`** (DFU 10.0.21 moved it into the MC codebase)
- `codec()` returns `KeyDispatchDataCodec.of(CODEC)` where `CODEC` is a `MapCodec`
- Nested type renames (verified against decompiled 26.2 `DensityFunction`):
  - `NoisePos` → **`FunctionContext`**
  - `sample(NoisePos)` → **`compute(FunctionContext)`**
  - `fill(double[], EachApplier)` → **`fillArray(double[], ContextProvider)`**
  - `EachApplier.at(int)` → **`ContextProvider.forIndex(int)`**
  - `apply(DensityFunctionVisitor)` → **`mapChildren(DensityFunction.Visitor)`**
- **Critical fix:** `mapChildren` must `return this;` for leaf functions (no children).
  Returning `visitor.apply(this)` causes infinite recursion via `mapAll`'s
  `RecursiveVisitor` → stack overflow crash during chunk generation.

### `world/WorldScaleSettingsState.java`
- `PersistentState` → **`SavedData`** (`net.minecraft.world.level.saveddata.SavedData`)
- `PersistentStateType` → **`SavedDataType`** (takes `Identifier` + `Codec` + data-fixer)
- `markDirty()` → **`setDirty()`**
- Codec is a plain `Codec<T>` (not `MapCodec<T>`) — `SavedData` serializes as a single NBT compound

### `world/WorldScaleManager.java`
- `ServerWorld` → **`ServerLevel`** (`net.minecraft.server.level.ServerLevel`)
- `getPersistentStateManager().getOrCreate(TYPE)` → **`getDataStorage().computeIfAbsent(TYPE)`**
  - `DimensionDataStorage` renamed to `SavedDataStorage`

### `mixin/BiomeMixin.java`
- `Biome` package: `net.minecraft.world.biome.Biome` → `net.minecraft.world.level.biome.Biome`
- `BlockPos` package: `net.minecraft.util.math` → `net.minecraft.core`
- `getTemperature()` → **`getBaseTemperature()`** (Mojang name)
- **`getPrecipitation(BlockPos, int)` → `getPrecipitationAt(BlockPos, int)`** — one-word rename that caused a mixin crash at bootstrap

### `mixin/MinecraftServerMixin.java` (spawn point — re-created after initially deleting)
- `setupSpawn` → **`setInitialSpawn`** (still `private static`, confirmed in 26.2 jar)
- Last parameter: `ChunkProgressListener` → **`LevelLoadListener`** (renamed)
- `ServerWorld` → `ServerLevel`; `ServerWorldProperties` → `ServerLevelData`
- Spawn API changed: `ServerLevelData.setSpawnPoint(GlobalPos, float)` no longer exists
- New spawn model: **`MinecraftServer.setRespawnData(LevelData.RespawnData)`**
  - `LevelData.RespawnData.of(dimensionKey, pos, yaw, pitch)` builds the spawn data
- `World.OVERWORLD` → `Level.OVERWORLD`; `world.getRegistryKey()` → `world.dimension()`

### `client/WorldScaleSettingsScreen.java`
- `Screen` → `net.minecraft.client.gui.screens.Screen`
- `CreateWorldScreen` → `net.minecraft.client.gui.screens.worldselection.CreateWorldScreen`
- `ButtonWidget` → **`Button`** (`builder().dimensions()` → `builder().bounds()`)
- `TextFieldWidget` → **`EditBox`** (`setText`/`getText` → `setValue`/`getValue`; `setChangedListener` → `setResponder`)
- `TextWidget` → **`StringWidget`**
- `Text`/`MutableText`/`Formatting` → `Component`/`MutableComponent`/`ChatFormatting`
- `this.textRenderer` → `this.font`; `this.client` → `this.minecraft`
- `addDrawableChild` → `addRenderableWidget`
- `Screen.close()` → **`Screen.onClose()`** (Mojang name)
- **26.2 GUI reorg:** `Minecraft.setScreen(s)` → **`Minecraft.gui.setScreen(s)`**
- `DimensionOptions` → **`LevelStem`** (`OVERWORLD`, `chunkGenerator()` → `generator()`)
- `DimensionOptionsRegistryHolder` → **`WorldDimensions`** (in `net.minecraft.world.level.levelgen`, not `.dimension`)
- `getWorldCreator().applyModifier(BiFunction)` → **`getUiState().updateDimensions(DimensionsUpdater)`**
  - `DimensionsUpdater` = `BiFunction<RegistryAccess.Frozen, WorldDimensions, WorldDimensions>`
- `WorldCreator` → **`WorldCreationUiState`** + `WorldCreationContext`

### `client/mixin/client/CreateWorldScreenWorldTabMixin.java`
- Target: `net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab` (confirmed exists in 26.2)
- **Intermediary names don't exist in deobfuscated jar** — all `field_42182`/`method_48676`/etc. replaced with real Mojang names:
  - Field `field_42182` → **`this$0`** (synthetic outer reference, type `CreateWorldScreen`)
  - Field `field_42184` → **`customizeTypeButton`** (type `Button`)
  - Method `openCustomizeScreen` → **`openPresetEditor`** (private, no args)
- Removed 2 injections (`method_48680`/`method_48681`) — they targeted now-inline lambdas (`lambda$new$5`/`lambda$new$6`) that can't be mixin-targeted
- `WorldCreator.WorldType` → `WorldCreationUiState.WorldTypeEntry`
- `worldType.preset()` returns `Holder<WorldPreset>` (use `holder.is(key)` to compare)
- `worldType.getName()` → `worldType.describePreset()`

### `client/TerrainDiffusionMcClient.java` (new — Customize button registration)
- 26.2 only enables the "Customize" button for presets in `PresetEditor.EDITORS`
- `EDITORS` is a `static final Map` in an interface, initialized via `Map.of(...)` (immutable)
- Java 25 blocks `Field.set` and `MethodHandles.findStaticSetter` on static final fields
- **Solution:** use `sun.misc.Unsafe.putObjectVolatile` with `staticFieldBase`/`staticFieldOffset` to replace the map with a mutable `HashMap` copy containing our preset editor entry
- Registered editor returns `WorldScaleSettingsScreen` for the Terrain Diffusion preset

### `pipeline/SpawnSelector.java`
- Only import change: `net.minecraft.util.math.BlockPos` → `net.minecraft.core.BlockPos`

### `pipeline/OnnxModel.java` (runtime stability fix)
- Added **CPU fallback** in `run()`: if a GPU session fails (either `gpuSession` or `claimGpuSlot`), the mod creates a fresh CPU session and continues instead of crashing
- `createFallbackCpuSession()` builds a CPU-only `OrtSession` from cached model bytes
- Prevents world-generation crashes when DirectML sessions die mid-game

---

## Data file changes

### `dimension_type/*.json` (7 files)
All dimension type JSONs were missing **required fields** added in 26.1/26.2,
causing the world preset to silently fail to load (no crash, just a missing
"Terrain Diffusion" world type option). Added to all 7 files:

- **`"default_clock": "minecraft:overworld"`** — 26.1 World Clocks refactor (every dimension type must specify its clock)
- **`"has_ender_dragon_fight": false`** — 26.1 DimensionType now takes a boolean for dragon fight support
- **`"minecraft:visual/ambient_light_color": "#0a0a0a"`** — present in vanilla overworld

### `terrain-diffusion-mc.properties` (config)
- **`inference.offload_models=false`** (was `true`) — DirectML has a known issue where session re-creation after eviction fails ("no GPU provider available"). Keeping all model sessions alive in VRAM avoids this. With `offload_models=false`, DirectML sessions persist and generation stays stable. CUDA users can set it back to `true` if VRAM is tight.

### Unchanged (verified compatible)
- `worldgen/noise_settings/terrain_diffusion.json` — schema unchanged in 26.2
- `worldgen/biome/*.json` — schema unchanged
- `worldgen/world_preset/terrain_diffusion.json` — format unchanged
- `tags/worldgen/world_preset/normal.json` — tag path unchanged

---

## Runtime issues fixed (beyond compile errors)

| Issue | Symptom | Fix |
|-------|---------|-----|
| Density function recursion | `StackOverflowError` during chunk gen | `mapChildren` returns `this` for leaf functions |
| Biome precipitation mixin crash | `ExceptionInInitializerError` at bootstrap | `getPrecipitation` → `getPrecipitationAt` |
| DirectML session death | `RuntimeException: Terrain tile failed` after ~30s | `offload_models=false` + CPU fallback in `OnnxModel.run()` |
| Missing Customize button | Button inactive for Terrain Diffusion | `sun.misc.Unsafe`-based `PresetEditor.EDITORS` registration |
| Missing world type | Preset didn't appear in world creation | Required `default_clock`/`has_ender_dragon_fight` fields in dimension_type JSONs |
| Spawn in ocean | Player spawns at sea | Re-created `MinecraftServerMixin` with 26.2 spawn API (`setRespawnData`) |

---

## Verification

All changes verified against:
- **Minecraft 26.2 client jar** (downloaded from `piston-data.mojang.com`, decompiled locally with a custom Python class-file parser)
- **DFU 10.0.21 jar** (from `libraries.minecraft.net`)
- **NeoForged 1.21.11→26.1 migration primer** (by ChampionAsh5357)
- **Fabric blog posts** for 26.1 and 26.2
- **Fabric API 26.1 porting guide** (rename list)
- **Runtime testing** on Minecraft 26.2 with all three build variants

### Test results (DirectML build, RTX 5070 Ti)
- ✅ Mod loads with Fabric API 0.152.2+26.2
- ✅ All 3 ONNX models load on GPU (coarse 11MB + base 991MB + decoder 109MB)
- ✅ DirectML inference stable for full session (471+ regions generated, zero errors)
- ✅ Customize button enabled (PresetEditor registered via Unsafe)
- ✅ Spawn finder locates land (-15360, 87, 15360 after 24×24 coarse search)
- ✅ Clean shutdown (all chunks saved)

---

## Known notes for users

1. **Delete `config/terrain-diffusion-mc.properties`** before first launch — old config files from 1.21.11 have `offload_models=true` which causes DirectML crashes
2. **Worlds from 1.21.11 are not compatible** — Minecraft's 26.x SavedData format changed (`Overworld settings missing` error if you try to load old saves)
3. **CPU build is very slow** — only use if no GPU is available
4. **CUDA build requires CUDA 12.x + cuDNN 9.x** — see `CUDA_INSTALL.md`

---

## Credits

- **Original mod + diffusion research:** xandergos (SIGGRAPH 2026)
- **26.2 port:** GLM 5.2 assistance, iterative debugging against the live 26.2 jar
- **Migration references:** NeoForged primer by ChampionAsh5357, Fabric blog posts, Fabric porting docs
