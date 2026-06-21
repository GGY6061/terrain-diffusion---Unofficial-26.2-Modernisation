# Terrain Diffusion MC — Patch Notes: Minecraft 1.21.11 → 26.2

This document records the research and the patch applied to make the **Terrain
Diffusion** Fabric mod build and run on **Minecraft Java Edition 26.2**, starting
from its previous target of **1.21.11**.

---

## 1. Research findings (what actually changed)

### 1.1 Version numbering
Mojang switched to **year-based versioning** in 2026 ("Minecraft new version
numbering system"). Versions in 2026 begin with `26`. The relevant releases:

| Version | Codename | Released | Notes |
|--------|----------|----------|-------|
| 1.21.11 | — | Dec 2025 | Last `1.21.x` drop; the mod's previous target. |
| **26.1** | "Tiny Takeover" | Mar 24 2026 | **The big breaking update.** |
| **26.2** | "Chaos Cubed" | Jun 16 2026 | Target of this patch. Data pack v107.1, resource pack v88.0. |

So "26.2" is a real, current Minecraft version, not a typo.

### 1.2 The 26.1 breakage (deobfuscation + toolchain)
Sources: Fabric blog "Fabric for Minecraft 26.1" (2026-03-14), Fabric docs
"Porting to 26.1", NeoForged primer "1.21.11 -> 26.1" by ChampionAsh5357.

- **The game is no longer obfuscated.** Minecraft 26.1+ ships with Mojang's
  official source names. **No mod from 1.21.11 or earlier runs without
  recompilation.**
- **Yarn mappings are no longer provided by Fabric.** Every mod must migrate to
  Mojang official mappings.
- **Java 25** is required (was 21). Vanilla uses JEP 447 (statements before
  `super`), etc.
- **Loom 1.15+** with a new non-remapping plugin id: `net.fabricmc.fabric-loom`
  (the old `fabric-loom` remapping plugin is gone).
  - `modImplementation`/`modCompileOnly`/`modApi` → `implementation`/`compileOnly`/`api`.
  - `remapJar` → `jar`.
  - The `mappings` dependency line is removed entirely.
- **`fabric` mod-id dependency removed.** `fabric.mod.json` must depend on
  `fabric-api`, not `fabric`.
- **Fabric API renames** to match Mojang names where applicable
  (e.g. `ItemGroupEvents` → `CreativeModeTabEvents`).

### 1.3 Vanilla API renames relevant to this mod (Yarn → Mojang)
From the NeoForged primer's "Saved Data", "Loot Type Unrolling", and class lists:

- `PersistentState` → `SavedData`; `PersistentStateType` → `SavedDataType`
  (now takes an `Identifier` + `MapCodec` + data-fixer, not a `String` + `Codec`).
- `DimensionDataStorage` → `SavedDataStorage`; `level.getPersistentStateManager().getOrCreate(TYPE)`
  → `level.getDataStorage().computeIfAbsent(TYPE)`. `markDirty()` → `setDirty()`.
- Loot-type unrolling pattern applies to **all** `*Type`-registered objects
  (including density functions): `getType()`/`getCodecHolder()` → `codec()`
  returning a `MapCodec`; `CodecHolder` is removed; registries hold `MapCodec`s
  directly.
- `Identifier.of(ns, path)` (Yarn) → `Identifier.fromNamespaceAndPath(ns, path)`
  (Mojang deobfuscated).
- `RegistryKey` → `ResourceKey` (`RegistryKey.of(...)` → `ResourceKey.create(...)`).
- `Registries`/`RegistryKeys` → `net.minecraft.core.registries.BuiltInRegistries`
  (built-in codec registries like `BIOME_SOURCE`, `DENSITY_FUNCTION_TYPE`) and
  `net.minecraft.core.registries.Registries` (registry-key constants).
- `RegistryEntry` → `Holder`; `RegistryEntryLookup` → `HolderGetter`;
  `RegistryEntry.Reference` → `Holder.Reference`.
- `World` → `Level` (`World.OVERWORLD` → `Level.OVERWORLD`);
  `WorldView` → `LevelReader`; `ServerWorld` → `ServerLevel`;
  `world.getRegistryKey()` → `world.dimension()`.
- `WorldProperties`/`WorldProperties.SpawnPoint` → spawn set via
  `ServerLevelData.setSpawnPoint(GlobalPos, float)` (single yaw angle).
  `ServerWorldProperties` → `ServerLevelData`.
- `ChunkLoadProgress` → `ChunkProgressListener`.
- `MinecraftServer.setupSpawn` → `MinecraftServer.setInitialSpawn`.
- `Biome`/`BiomeKeys`/`BiomeSource`/`BiomeCoords`/`MultiNoiseUtil` →
  `net.minecraft.world.level.biome.Biome`/`Biomes`/`BiomeSource`/(quart helpers inlined)/`Climate`.
  `MultiNoiseUtil.MultiNoiseSampler` → `Climate.Sampler`.
- `DensityFunction` → `net.minecraft.world.level.levelgen.DensityFunction`.
  Nested `NoisePos` → `FunctionPos`; `sample` → `compute`;
  `DensityFunctionVisitor` → `DensityFunction.Visitor`.
- `DimensionOptions` → `LevelStem` (`OVERWORLD`, `chunkGenerator()` → `generator()`);
  `DimensionOptionsRegistryHolder` → `WorldDimensions`;
  `DimensionType` package → `net.minecraft.world.level.dimension.DimensionType`.
- `WorldPreset` → `net.minecraft.world.level.levelgen.presets.WorldPreset`.
- Client: `MinecraftClient` → `Minecraft`; `Screen` → `net.minecraft.client.gui.screens.Screen`;
  `CreateWorldScreen`/`WorldCreator` → `net.minecraft.client.gui.screens.worldselection.*`;
  `ButtonWidget` → `Button` (`builder().dimensions()` → `builder().bounds()`);
  `TextFieldWidget` → `EditBox` (`setText`/`getText` → `setValue`/`getValue`,
  `setChangedListener` → `setResponder`); `TextWidget` → `StringWidget`;
  `textRenderer` → `font`; `this.client` → `this.minecraft`;
  `addDrawableChild` → `addRenderableWidget`.
- `Text`/`MutableText`/`ClickEvent` → `Component`/`MutableComponent`/`ClickEvent`;
  `Formatting` → `ChatFormatting`.
- Commands: `ServerCommandSource` → `CommandSourceStack`; `CommandManager.literal`
  → `Commands.literal`; `sendFeedback` → `sendSuccess`; `sendError` → `sendFailure`.

### 1.4 The 26.2 breakage (on top of 26.1)
Source: Fabric blog "Fabric for Minecraft 26.2" (2026-06-15).

- **Loom 1.17**, Gradle 9.5.1, Fabric Loader 0.19.3, Fabric API `0.152.2+26.2`.
- **GUI reorganisation**: methods for getting/setting the current screen moved
  out of `Minecraft` into `Minecraft.gui`. `Minecraft.setScreen(s)` →
  `Minecraft.gui.setScreen(s)`. (Applied in `WorldScaleSettingsScreen.close()`
  and `CreateWorldScreenWorldTabMixin`.)
- Block ids / item ids are now stored separately (`BlockIds`, `BlockItemIds`,
  `ItemIds`); `valueLookupBuilder` removed. *(Not used by this mod.)*
- Experimental Vulkan backend; raw OpenGL calls must migrate to Blaze3D. *(This
  mod does no direct GL.)*
- New enum-extension, tag-removal, and fluid-interaction APIs. *(Not used.)*

---

## 2. Patch applied

### 2.1 Build files
- **`gradle.properties`**: `minecraft_version=26.2`, `loader_version=0.19.3`,
  `fabric_version=0.152.2+26.2`, `mod_version=2.3.0`. Removed `yarn_mappings`
  (no longer used).
- **`build.gradle`**: Loom plugin → `id 'net.fabricmc.fabric-loom' version '1.17'`.
  Removed the `mappings` dependency. `modImplementation` → `implementation` for
  loader + fabric-api. `remapJar`-aware `jar` task kept (Loom 1.17 outputs the
  plain `jar`). Java target → `25`.
- **`gradle/wrapper/gradle-wrapper.properties`**: Gradle `8.14.1` → `9.5.1`.
- **`fabric.mod.json`**: dependency `"fabric"` → `"fabric-api"`.
- **`terrain-diffusion-mc.mixins.json`** and **`terrain-diffusion-mc.client.mixins.json`**:
  `compatibilityLevel` `JAVA_21` → `JAVA_25`.

### 2.2 Source files remapped (Yarn → Mojang)
10 files changed; 24 pure-Java files (tensor math, ONNX pipeline, noise, biome
classifier, explorer HTTP server) needed no changes.

| File | Key changes |
|------|-------------|
| `TerrainDiffusionMc.java` | Registries (`BuiltInRegistries.BIOME_SOURCE`/`DENSITY_FUNCTION_TYPE`), `Identifier.fromNamespaceAndPath`, `Level.OVERWORLD`, `world.dimension()`, `Component`, `Commands.literal`, `sendSuccess`/`sendFailure`. |
| `world/TerrainDiffusionBiomeSource.java` | `BiomeSource`/`Biome`/`Biomes`/`Climate.Sampler`/`Holder`/`HolderGetter`/`ResourceKey`, `RegistryOps.retrieveGetter(Registries.BIOME)`, `codec()`, inlined `BiomeCoords.toBlock` (`<<2`), added `possibleBiomes()`. |
| `world/TerrainDiffusionDensityFunction.java` | `CodecHolder`/`getCodecHolder()` removed → `codec()` returns `MapCodec`. `NoisePos`→`FunctionPos`, `sample`→`compute`, `DensityFunctionVisitor`→`Visitor`. |
| `world/WorldScaleSettingsState.java` | `PersistentState`→`SavedData`, `PersistentStateType`→`SavedDataType` (now `Identifier`+`MapCodec`), `markDirty`→`setDirty`, `Codec`→`MapCodec` via `RecordCodecBuilder.mapCodec`. |
| `world/WorldScaleManager.java` | `ServerWorld`→`ServerLevel`, `getPersistentStateManager().getOrCreate`→`getDataStorage().computeIfAbsent`. |
| `mixin/BiomeMixin.java` | `BlockPos`/`Biome` packages, `getTemperature`→`getBaseTemperature`. |
| `mixin/MinecraftServerMixin.java` | `setupSpawn`→`setInitialSpawn`, `ServerWorld`→`ServerLevel`, `ServerWorldProperties`→`ServerLevelData`, `ChunkLoadProgress`→`ChunkProgressListener`, spawn set via `setSpawnPoint(GlobalPos.of(Level.OVERWORLD, pos), 0f)`. |
| `client/WorldScaleSettingsScreen.java` | `Screen`/`Button`/`EditBox`/`StringWidget`/`Component`/`ChatFormatting`, `font`, `minecraft.gui.setScreen`, `LevelStem`/`WorldDimensions`, `generator()`, `getHolder`. |
| `client/mixin/client/CreateWorldScreenWorldTabMixin.java` | `Minecraft`/`Button`/`WorldPreset`/`ResourceKey`; **intermediary targets replaced with Mojang-name candidate arrays + `require = 0`** (see §3). |
| `pipeline/SpawnSelector.java` | `BlockPos` package only. |

Files needing no changes (no Minecraft imports, or only stable Fabric Loader /
entrypoint APIs): all of `infinitetensor/*`, most of `pipeline/*`
(`BiomeClassifier`, `EDMScheduler`, `FastNoiseLite`, `GaussianNoisePatch`,
`LaplacianUtils`, `LocalTerrainProvider`, `OnnxModel`, `PipelineModels`,
`PipelineTest`, `PortableRng`, `SyntheticMapFactory`, `WorldPipeline`,
`WorldPipelineModelConfig`), `explorer/*`, `world/HeightConverter`,
world/WorldScaleSelectionState`, `config/TerrainDiffusionConfig`,
`pipeline/ModelAssetManager`, `client/TerrainDiffusionMcClient`,
`client/TerrainDiffusionMcDataGenerator`.

### 2.3 Data files
- `data/.../dimension_type/*.json` and `data/.../worldgen/*` JSONs: **unchanged**.
  They already use the modern `attributes`/`timelines` dimension-type schema and
  the stable `noise_router`/`surface_rule`/`spawn_target` noise-settings schema.
  Data-pack format 107.1 (26.2) is compatible with their structure.
- `world_preset/terrain_diffusion.json`: unchanged (uses `dimensions` map with
  overworld/nether/end — still valid).

---

## 3. Verification status & items to confirm against the 26.2 jar

**This patch could not be compile-verified in the development sandbox** (only
Java 21 is installed; Gradle 9.5.1 / Java 25 / the Minecraft 26.2 jar are not
available offline). The mapping decisions above are drawn from authoritative
sources (Fabric blog, Fabric porting docs, NeoForged primer). The following
items should be confirmed against the actual 26.2 jar (e.g. via the
`mcsrc.dev` decompiler announced in the Fabric 26.1 blog) before shipping:

1. **`Identifier` vs `ResourceLocation`.** The NeoForged primer predominantly
   uses `Identifier.fromNamespaceAndPath(...)` for registry identifiers and
   `ResourceLocation.fromNamespaceAndPath(...)` for asset/shader locations,
   stating these are Mojang's official deobfuscated names. This patch uses
   `net.minecraft.resources.Identifier` everywhere a registry id is needed. If
   the 26.2 jar exposes these as `ResourceLocation` instead, apply a global
   `Identifier` → `ResourceLocation` rename (the `fromNamespaceAndPath` factory
   is identical).
2. **`CreateWorldScreen$WorldTab` client mixin.** This previously targeted Yarn
   *intermediary* names (`field_42182`, `method_48676`, `method_48680`,
   `method_48681`) which do **not** exist in the deobfuscated 26.2 jar. The
   patch replaces them with Mojang-name **candidate arrays** + Mixin `aliases`
   + `require = 0` so a wrong guess degrades gracefully instead of crashing.
   Before shipping: look up the real private field/method names in
   `CreateWorldScreen$WorldTab` (26.2), collapse each array to the single
   correct name, and restore `require = 1`. The affected members:
   - the outer-`CreateWorldScreen` field (candidates: `parent`, `createWorldScreen`, `screen`);
   - the customize-button field (`customizeButton`);
   - the "update customize button" method (candidates: `updateCustomizeButton`, `refreshCustomizeButton`, `onWorldTypeChanged`, `updateTabState`);
   - the "customize available" / "customize visible" boolean methods;
   - `openCustomizeScreen` (candidates: `openCustomizeScreen`, `openCustomizationScreen`, `openConfigScreen`).
3. **`DensityFunction` nested types.** `NoisePos`→`FunctionPos` and
   `sample`→`compute` are the assumed Mojang names. If 26.2 still uses
   `NoisePos`/`sample`, restore them in `TerrainDiffusionDensityFunction`.
4. **`Biome.getPrecipitation(BlockPos, int)`** and **`getBaseTemperature()`**.
   Minecraft has been moving biome precipitation toward a flat biome property.
   If 26.2 no longer exposes the two-arg `getPrecipitation`, the `BiomeMixin`
   is obsolete (altitude snow is already gone) — remove the class and drop its
   entry from `terrain-diffusion-mc.mixins.json`.
5. **`WorldCreator.applyModifier`**, **`WorldDimensions.getOrEmpty`**,
   **`WorldDimensions.dimensions()`**, **`HolderLookup.Provider.lookup`** +
   **`HolderGetter.get(ResourceKey)`** (chosen for the dimension-type lookup in
   `WorldScaleSettingsScreen` because it works whether `applyModifier` passes a
   `RegistryAccess` or a plain `HolderLookup.Provider`), **`WorldCreator.WorldType.preset()`**
   return type — confirm exact signatures.
6. **Fabric API renames.** Core entrypoints used here (`ModInitializer`,
   `ClientModInitializer`, `CommandRegistrationCallback`,
   `ServerLifecycleEvents`, `ServerWorldEvents`, `DataGeneratorEntrypoint`,
   `FabricDataGenerator`) are believed to be retained, but verify against the
   Fabric API 26.1 porting guide if any fail to resolve.
7. **GUI 26.2 specifics.** `addRenderableWidget`, `Button.builder().bounds()`,
   `EditBox.setValue/getValue/setResponder`, `StringWidget`,
   `Minecraft.gui.setScreen` — verify against 26.2.

### 3.1 How to fully verify
1. Install JDK 25 (e.g. Microsoft OpenJDK 25) and set it as the Gradle JVM.
2. `./gradlew --refresh-dependencies` (Loom 1.17 will download Minecraft 26.2 +
   Fabric API 0.152.2+26.2 + deobfuscated sources).
3. `./gradlew compileJava compileClientJava` — fix any residual mapping errors
   surfaced by the compiler (the items in §3 are the likely ones).
4. `./gradlew build -PuseCpu=true` (or `-PuseDml`/`-PuseCuda`) to produce the
   mod jar.
5. Launch Minecraft 26.2 with Fabric Loader 0.19.3 + Fabric API and create a
   Terrain Diffusion world to exercise the world-gen + customize-screen paths.

---

## 4. What was NOT changed (and why)
- **The ONNX inference pipeline, tensor math, biome classifier, and explorer
  HTTP server** are pure Java with no Minecraft dependencies — unaffected by the
  deobfuscation / mapping migration.
- **Model asset manifest generation** (Hugging Face fetch in `build.gradle`) is
  a build-time step independent of Minecraft version.
- **World-gen data JSONs** already use the modern schemas; no migration needed.
