package space.plag.createnue;

import java.util.Map;

/**
 * Duck-type interface implemented by the water-wheel and windmill mixins so a ComputerCraft peripheral
 * (and anything else) can read a generator's live, mod-computed numbers without touching @Unique fields
 * directly. No ComputerCraft types here, so this interface loads even when CC is absent.
 */
public interface CmGenerator {

    /** "waterwheel" or "windmill". */
    String cm$kind();

    /** Currently producing power (real flow / live wind). */
    boolean cm$active();

    /** Current capacity per RPM (SU/RPM), after the mod's flow/head/biome/dam or wind force scaling. */
    double cm$capacity();

    /** Current generated speed magnitude (|RPM|). */
    double cm$rpm();

    /** Total stress contributed right now (SU) = capacity x |rpm|. */
    default double cm$stress() {
        return cm$capacity() * Math.abs(cm$rpm());
    }

    /** Type-specific extras (water: flow/head/biome/dam; wind: speedFactor/force), as a Lua-friendly table. */
    Map<String, Object> cm$details();
}
