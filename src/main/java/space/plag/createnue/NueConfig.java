package space.plag.createnue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Self-contained JSON config (no extra mod dependency) at config/create_nue.json.
 * Loaded once on init; defaults are written if missing (and back-filled on upgrade).
 */
public class NueConfig {

    // --- Flow stabilization (anti-oscillation) --------------------------------------------
    public boolean flowStabilizationEnabled = true;
    public double flowSmoothing = 0.25;
    public double flowDecay = 0.5;
    public double flowMinThreshold = 0.05;

    // --- Direction stability (signed-score hysteresis + shaft consensus) -------------------
    public boolean directionHysteresisEnabled = true;
    public double scoreSmoothing = 0.35;
    public double scoreFlipThreshold = 0.6;
    public double scoreDeadband = 0.25;
    public int flipHoldSamples = 3;
    public boolean shaftConsensusEnabled = true;
    public int shaftConsensusReach = 4;

    // --- WATER power: flow strength (credits any genuine current, incl. horizontal rivers) -
    public boolean flowStrengthEnabled = true;
    public double flowStrengthMax = 2.5;
    public double flowStrengthReference = 3.0;

    // --- WATER power: head / drop height (MEGASTRUCTURE incentive — tall dams pay off) -----
    public boolean headBonusEnabled = true;
    public double headBonusMax = 3.0;
    public int headReference = 10;
    public int headMaxScan = 32;

    // --- WATER power: biome ----------------------------------------------------------------
    public boolean biomeEnabled = true;
    public double biomeBoost = 1.5;
    public double biomePenalty = 0.5;
    public List<String> biomeBoostIds = List.of("river", "swamp", "mangrove", "ocean", "beach", "jungle", "stony_shore");
    public List<String> biomePenaltyIds = List.of("desert", "badlands", "savanna", "nether", "the_end", "wasteland");

    // --- WATER: MEGASTRUCTURE dam coherence (the "building a ГЭС is fun + profitable" reward) -----
    // Many GENERATING wheels clustered together (a real dam) reward super-linearly: each nearby
    // generating wheel raises this wheel's capacity AND its cap, so a big ГЭС beats scattered wheels.
    // Only generating wheels count (no water -> no flow -> no bonus), so a fake dry cluster gains nothing.
    public boolean damCoherenceEnabled = true;
    public int damCoherenceRadius = 24;        // blocks (horizontal+vertical) defining "one dam"
    public double damCoherencePerWheel = 0.05; // each extra nearby generating wheel: +5% capacity & cap
    public double damCoherenceMax = 1.8;       // cap the dam multiplier (reached at ~17 wheels)

    // --- WATER: absolute power cap (backstop, TOTAL SU per wheel) --------------------------
    public boolean maxStressCapEnabled = true;
    public double maxStressCapSU = 4096.0; // premium wheel ~3840; a multi-wheel plant clearly beats wind

    // --- WATER: conservation water-as-fuel tax (anti perpetual-motion) --------------------
    public boolean waterTaxEnabled = true;
    public int waterTaxIntervalTicks = 100;
    public int waterTaxLevels = 1;
    public boolean waterTaxSkipInfiniteBiome = true;

    // ======================================================================================
    // WIND — two independent levers (Create: SU = capacity-per-RPM x RPM):
    //   SPEED (RPM, the VISIBLE rotation) = current wind: gust x weather x day/night (+share, +soft-knee).
    //   FORCE (capacity per RPM, the SU)  = windmill placement: exposure x height x region x balance nerf.
    //   SU = FORCE x SPEED. Speed shows the wind (readable); force balances it weaker-than-water.
    // ======================================================================================
    public boolean windmillEnabled = true;

    // -- Wind SPEED range (factor on the sail RPM; keeps the rotor visibly turning, never crushed) --
    public double windSpeedMin = 0.40; // calm rotor (x sail RPM)
    public double windSpeedMax = 1.10; // full-wind rotor
    // -- Wind FORCE: balance nerf so wind delivers less SU per rotation than a water wheel --
    public double windForceNerf = 0.60;
    /** Ring sky-exposure samples; below windmillExposureMin open-fraction => zero power (boxed = dead). */
    public int windmillSkySamples = 8;
    public double windmillExposureMin = 0.6;
    /** Height: lerp(min,max) from local terrain up to windmillHeightAboveFull blocks above. */
    public int windmillHeightAboveFull = 12;
    public double windmillHeightMin = 0.45; // low/ground builds punished
    public double windmillHeightMax = 1.0;  // height = "don't lose power", not a doubler

    // -- Wind gusts (the variability) --
    public boolean windGustEnabled = true;
    /** Smooth gust oscillates between this floor and the weather ceiling over minutes, per windmill. */
    public double windGustFloor = 0.20;
    /** EMA smoothing of windFactor across rechecks (lower = slower, smoother drift). Anti-jerk. */
    public double windEmaAlpha = 0.30;
    /** Deadband (RPM) on the rendered integer speed so it doesn't flicker between e.g. 1 and 2. Anti-jerk. */
    public double windSpeedHysteresis = 0.35;
    public double windClearCeiling = 0.55; // clear day caps low -> can't power a whole factory
    public double windRainCeiling = 0.80;
    public double windStormCeiling = 1.0;  // storms can spike to full (and dips then overstress the factory)

    // -- Wind diurnal cycle (day vs night) — modulates the gust ceiling --
    public boolean windDiurnalEnabled = true;
    public double windDayMul = 1.0;    // afternoon convective peak
    public double windNightMul = 0.65; // calmer pre-dawn trough

    // -- debug: log each windmill recompute to the server log (off in production) --
    public boolean windDebugLog = false;

    // -- Wind region (windier biomes give more, sheltered less) --
    public boolean windRegionEnabled = true;
    public double windRegionBoost = 1.3;
    public double windRegionPenalty = 0.6;
    public List<String> windyBiomeIds = List.of("windswept", "peak", "hill", "ocean", "plains", "snowy", "meadow", "savanna", "tundra");
    public List<String> shelteredBiomeIds = List.of("jungle", "forest", "swamp", "lush", "dripstone", "deep_dark", "cave");

    // -- Wind soft-knee (caps a lone monster rotor without flattening nuance) --
    public boolean windmillSoftKneeEnabled = true;
    public double windmillSoftKneeRpm = 6.0;
    public double windmillSoftK = 0.5;

    // -- Wind anti-stacking (a cluster shares the area's wind budget; 2 rows != 2x) --
    public boolean windmillShareEnabled = true;
    public int windmillShareRadius = 24;
    public double windmillShareK = 1.0; // shareMul = 1/sqrt(1 + K*Nother)

    /** Recompute the wind factor every N ticks per running windmill (also the gust step). */
    public int windmillRecheckTicks = 100;

    // ======================================================================================
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("create_nue.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NueConfig INSTANCE = new NueConfig();

    public static NueConfig get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                NueConfig loaded = GSON.fromJson(Files.readString(PATH), NueConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            }
            save();
        } catch (IOException | RuntimeException e) {
            CreateNue.LOGGER.warn("[Create: NUE] failed to load config, using defaults", e);
            INSTANCE = new NueConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            CreateNue.LOGGER.warn("[Create: NUE] failed to save config", e);
        }
    }
}
