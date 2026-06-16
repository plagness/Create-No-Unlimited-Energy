package space.plag.createnue;

/**
 * Duck-type implemented by the gauge mixin (Stressometer + Speedometer) so they can be read from
 * ComputerCraft as intermediate measurement points: speed AT THIS POINT plus the whole network's
 * capacity / used stress / overstress state. No ComputerCraft types here (loads without CC).
 */
public interface CmMeter {

    /** "speed", "stress", or "kinetic" — which gauge block this is. */
    String cm$gaugeType();

    /** |rotation speed| at this point (RPM) — varies through gearboxes, so a useful per-point probe. */
    double cm$speed();

    /** Whole-network capacity (SU). */
    double cm$capacity();

    /** Whole-network used stress (SU). */
    double cm$stressUsed();

    /** Network is overstressed (demand > capacity → halted). */
    boolean cm$overstressed();

    /** Used / capacity in 0..1 (0 if no capacity). */
    default double cm$load() {
        double c = cm$capacity();
        return c > 0.0 ? cm$stressUsed() / c : 0.0;
    }
}
