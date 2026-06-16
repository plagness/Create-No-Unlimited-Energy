# Maintaining Create: No Unlimited Energy

Guidance for anyone — humans or AI agents — changing this mod. **Read this before editing.**

## 0. This is a PUBLIC mod — keep it that way

Create: NUE is a **standalone, public** Fabric mod (GitHub + Modrinth, MIT). It is **not** tied to any
private server or modpack, even if it is used in one. Every change must respect that:

- **English first.** README, CHANGELOG, this file, and the **default in-game text** are English.
- **Localize, never hardcode.** Any new player-facing string uses `Component.translatable("create_nue.<key>", …)`
  with the key added to **all** `src/main/resources/assets/create_nue/lang/*.json` (`en_us` is the
  fallback/default; `ru_ru` + `zh_cn` ship too). See §5.
- **No private references** in names, descriptions, code, assets, or screenshots (no server names, no
  internal project names). The mod stands on its own.
- **Autonomous dependencies.** Hard-depend on **Create (Fabric)** + **Flowing Fluids** only;
  **ComputerCraft is optional** (soft dep, guarded — see §4).
- **Ship every change to BOTH GitHub and Modrinth together**, with a `CHANGELOG.md` entry. See §6.
- **Server-authoritative + client-synced.** Anything the client displays must be synced from the
  server, or non-host players see wrong numbers (see §3).

## 1. Environment

Fabric **1.20.1** · Fabric Loom + **Mojang mappings** · compiles to **Java 17** bytecode, runs on **17 or 21**
(use a JDK 21 as `JAVA_HOME` to run Gradle). Targets **Create 6.0.8.x** and **Flowing Fluids 1.0.6**.

## 2. Build

`libs/` holds **compile-only** dependency jars — **not redistributed, gitignored**. Populate it with:
- Create (Fabric) jar, Flowing Fluids jar, CC: Tweaked jar,
- the `fabric-api-lookup-api-v1` module jar (extract from fabric-api's `META-INF/jars/` — needed for
  `BlockApiLookup`, used by the CC peripheral).

Then `./gradlew build` → `build/libs/create-nue-<version>.jar`. (`build.gradle` adds every `libs/*.jar`
as `modCompileOnly` via a fileTree, so just dropping jars in is enough.)

## 3. Architecture (`src/main/java/space/plag/createnue`)

- **`CreateNue`** — entrypoint: loads config, caches the Flowing Fluids API, and registers the CC
  peripheral **only if** ComputerCraft is present.
- **`NueConfig`** — self-contained JSON config at `config/create_nue.json`; every balance value is here.
- **`mixin/WaterWheelBlockEntityMixin`** — the hydro core. `@Redirect`s Create's `getFlowVectorAtPosition`
  to vanilla `FluidState#getFlow` (fixes FF's broken `REQUIRE_FLOW`); signed-score **direction hysteresis**
  + shaft consensus (via `CmWheelSign`); power = `flow × head × biome` by **overriding**
  `calculateAddedStressCapacity`; **dam coherence** (a per-dimension registry of *generating* wheels →
  super-linear reward); **water-as-fuel tax** (in `lazyTick`, via the FF API); **goggle readout**
  (translatable). Implements `CmWheelSign` + `CmGenerator`.
- **`mixin/WindmillBearingBlockEntityMixin`** — wind as two levers (Create: `SU = capacity/RPM × RPM`):
  **SPEED** (gusts × weather × day/night, EMA-smoothed + integer hysteresis, area share, soft-knee) scales
  `getGeneratedSpeed`; **FORCE** (sky exposure × height × region biome × nerf) overrides the capacity.
  Implements `CmGenerator`.
- **`mixin/GaugeBlockEntityMixin`** — exposes the Stressometer + Speedometer as `CmMeter` (network
  capacity/stress/speed via `KineticNetwork`). One mixin on the abstract `GaugeBlockEntity` covers both.
- **`cc/`** — `CmGeneratorPeripheral` (`cm_generator`), `CmMeterPeripheral` (`cm_meter`),
  `CmComputerCraft` (registers a `PeripheralLookup` fallback — no new block).
- **`CmGenerator` / `CmMeter` / `CmWheelSign`** — duck-type interfaces for cross-instance `@Unique`
  access and the CC layer (no CC imports, so they load without CC).

## 4. Mixin conventions & hard-won gotchas (IMPORTANT — these were real bugs)

- **Inherited methods → `@Override`, never `@Inject`.** `addToGoggleTooltip` and
  `calculateAddedStressCapacity` live on the superclass `KineticBlockEntity`, not on the mixin target.
  An `@Inject(method="addToGoggleTooltip")` finds **no target** → a *critical* failure that disables the
  **entire** mixin (silently reverting wheels to vanilla). The mixin `extends` the superclass and
  `@Override`s + calls `super`.
- **`@At("HEAD")`, not `TAIL`, on the bearing tick.** `WindmillBearingBlockEntity.tick()` early-returns at
  `if (!queuedReassembly) return` almost every tick, so a TAIL inject basically never runs.
- **Client display must be synced.** `getGeneratedSpeed` / `calculateAddedStressCapacity` are evaluated
  **client-side** for the goggle/stress tooltip; the client recomputes with *default* `@Unique` state and
  shows wrong numbers unless you sync the result. Sync via `@Inject` on `write`/`read` (`clientPacket`):
  the windmill syncs its rendered RPM + force factor, the wheel syncs dam size.
- **`@Unique` prefix is `nue$`; NBT keys are `Nue…`.** Cross-instance access to a mixin's `@Unique` member
  must go through a duck-type interface the mixin implements — `instanceof TheMixin` does **not** work
  (the mixin is merged into the target, not a runtime type).
- **CC is guarded.** `CmComputerCraft.register()` is only called when `FabricLoader.isModLoaded("computercraft")`,
  so the CC-referencing classes never load otherwise — no hard dependency.
- **Reading Create internals:** decompile with `javap -p -c` against the remapped jar in
  `.gradle/loom-cache/remapped_mods/…/create-fabric-*.jar`.

## 5. Localization

Player-facing text → `Component.translatable("create_nue.<key>", args…)` and add the key to **every**
`assets/create_nue/lang/*.json`. `en_us` is the fallback. Adding a language = one new `<locale>.json`.
Today only `create_nue.goggle.dam` is localized (the mod is otherwise mechanical — no items/blocks/GUI).

## 6. Releasing (GitHub + Modrinth, together)

See `RELEASING.md`. In short, every release:
1. Add a **`CHANGELOG.md`** entry (newest first) + bump `mod_version` in `gradle.properties`.
2. `./gradlew build`.
3. **GitHub** — commit, push, and `gh release create vX.Y.Z build/libs/create-nue-X.Y.Z.jar` (the jar is
   attached to the Release).
4. **Modrinth** — upload the **same** jar as a new version: **Game versions 1.20.1 only**, loader Fabric,
   dependencies **Create Fabric** + **Flowing Fluids** *required*, **CC: Tweaked** *optional*.
5. Keep the in-repo `README.md` and the Modrinth description identical.

Versioning is honest: a version's jar must actually contain what its changelog claims (e.g. 1.0.0 has no
localization; 1.0.1 adds it).

## 7. Testing

- Build must apply cleanly: server log shows `[Create: NUE] active …` and **zero** `InvalidInjection`.
- In-game: a wheel in still water makes 0 SU; a real channel spins it; a dam cluster raises the goggle
  multiplier; a sealed pump→wheel loop runs dry; a windmill's rotor drifts with gusts/weather/time; the
  Stressometer/Speedometer + wheels/windmills appear as `cm_meter` / `cm_generator` peripherals.

## 8. Roadmap / design intent

NUE's thesis: *Create's free, easy energy is the bug — deepen it.* Generation should take engineering;
RF/electricity + storage stay a normal progression tier; a **huge hydro dam** should be the most rewarding
way to make electricity, with wheels + windmills as the mechanical base the whole energy economy builds on.
Planned: a profitable-generation economy hook, pumped-hydro storage, seasonal water/wind, richer CC telemetry.
NUE unites Create + Flowing Fluids + (optionally) ComputerCraft into building **complex energy systems**.
