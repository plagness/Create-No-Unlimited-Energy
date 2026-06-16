package space.plag.createnue;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.flowing_fluids.api.FlowingFluidsAPI;

/**
 * CodeMine Waterwheel — physically-grounded hydro & wind power for Create + Flowing Fluids.
 *
 * Base fix: Create's getFlowVectorAtPosition is redirected to vanilla FluidState#getFlow (FF 1.0.6's
 * REQUIRE_FLOW path is doubly broken), so wheels only turn on a genuine current. On top of that:
 *  - power (added stress capacity) scales with current strength, the height water FALLS (head), and
 *    biome — rewarding big dams, elevated reservoirs and good siting (deeper energy extraction);
 *  - a small "water-as-fuel" tax destroys a little water at the intake while generating, so a sealed
 *    pump+wheel recirculation loop loses water and runs dry (no free/infinite energy) while a real
 *    rain/river-fed source is replenished faster and keeps working;
 *  - an absolute per-wheel stress cap backstops the maximum;
 *  - windmills scale with open-sky exposure, height and weather (a separate mixin).
 *
 * Requires Flowing Fluids create_waterWheelMode=REQUIRE_FLOW. Server-authoritative.
 */
public class CreateNue implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("create_nue");

    /** Cached FF API handle (getInstance() logs + allocates per call, so we cache it once). Null if absent. */
    public static FlowingFluidsAPI FF_API;

    @Override
    public void onInitialize() {
        NueConfig.load();
        try {
            FF_API = FlowingFluidsAPI.getInstance("create_nue");
        } catch (Throwable t) {
            FF_API = null;
            LOGGER.warn("[Create: NUE] Flowing Fluids API unavailable; water-tax disabled.", t);
        }
        NueConfig cfg = NueConfig.get();
        LOGGER.info("[Create: NUE] active: flowStrength={}, head={}, biome={}, cap={}, waterTax={}, windmills={} (FF API {}).",
                cfg.flowStrengthEnabled, cfg.headBonusEnabled, cfg.biomeEnabled, cfg.maxStressCapEnabled,
                cfg.waterTaxEnabled, cfg.windmillEnabled, FF_API != null ? "ok" : "absent");

        // Optional ComputerCraft peripheral on our wheels/windmills (read-only telemetry). Guarded so
        // the CC-referencing class only loads when ComputerCraft is present; no hard dependency.
        if (FabricLoader.getInstance().isModLoaded("computercraft")) {
            try {
                space.plag.createnue.cc.CmComputerCraft.register();
                LOGGER.info("[Create: NUE] ComputerCraft peripheral registered (cm_generator).");
            } catch (Throwable t) {
                LOGGER.warn("[Create: NUE] ComputerCraft integration failed.", t);
            }
        }
    }
}
