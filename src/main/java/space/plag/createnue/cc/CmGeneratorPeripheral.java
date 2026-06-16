package space.plag.createnue.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import space.plag.createnue.CmGenerator;

import java.util.Map;

/**
 * ComputerCraft peripheral attached (via PeripheralLookup, no new block) to our water wheels and
 * windmills. Read-only telemetry of the mod-computed numbers, so players can build "связки" — read
 * generation/flow/wind/dam-size from Lua and drive their own control logic. Loaded only when CC present.
 */
public class CmGeneratorPeripheral implements IPeripheral {

    private final CmGenerator gen;

    public CmGeneratorPeripheral(CmGenerator gen) {
        this.gen = gen;
    }

    @Override
    public String getType() {
        return "cm_generator";
    }

    /** "waterwheel" or "windmill". */
    @LuaFunction
    public final String kind() {
        return gen.cm$kind();
    }

    /** Currently producing power (real flow / live wind). */
    @LuaFunction
    public final boolean active() {
        return gen.cm$active();
    }

    /** Capacity per RPM (SU/RPM) after the mod's flow/head/biome/dam or wind-force scaling. */
    @LuaFunction
    public final double capacity() {
        return gen.cm$capacity();
    }

    /** Generated speed magnitude (|RPM|). */
    @LuaFunction
    public final double rpm() {
        return gen.cm$rpm();
    }

    /** Total stress contributed now (SU) = capacity x |rpm|. */
    @LuaFunction
    public final double stress() {
        return gen.cm$stress();
    }

    /** Type-specific extras: water {flow, head, biome, dam}; wind {speedFactor, forceFactor, rpm}. */
    @LuaFunction
    public final Map<String, Object> info() {
        return gen.cm$details();
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof CmGeneratorPeripheral p && p.gen == this.gen;
    }
}
