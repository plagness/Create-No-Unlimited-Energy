package space.plag.createnue.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import space.plag.createnue.CmMeter;

/**
 * ComputerCraft peripheral on a Create gauge (Stressometer / Speedometer): an intermediate network
 * measurement point. Read-only. Loaded only when ComputerCraft is present.
 */
public class CmMeterPeripheral implements IPeripheral {

    private final CmMeter meter;

    public CmMeterPeripheral(CmMeter meter) {
        this.meter = meter;
    }

    @Override
    public String getType() {
        return "cm_meter";
    }

    /** "speed", "stress", or "kinetic". */
    @LuaFunction
    public final String gaugeType() {
        return meter.cm$gaugeType();
    }

    /** |speed| at this point (RPM). */
    @LuaFunction
    public final double speed() {
        return meter.cm$speed();
    }

    /** Network capacity (SU). */
    @LuaFunction
    public final double capacity() {
        return meter.cm$capacity();
    }

    /** Network used stress (SU). */
    @LuaFunction
    public final double stressUsed() {
        return meter.cm$stressUsed();
    }

    /** Used / capacity in 0..1. */
    @LuaFunction
    public final double load() {
        return meter.cm$load();
    }

    /** Network overstressed (halted). */
    @LuaFunction
    public final boolean overstressed() {
        return meter.cm$overstressed();
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof CmMeterPeripheral p && p.meter == this.meter;
    }
}
