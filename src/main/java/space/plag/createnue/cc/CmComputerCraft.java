package space.plag.createnue.cc;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import space.plag.createnue.CmGenerator;
import space.plag.createnue.CmMeter;

/**
 * Registers the {@link CmGeneratorPeripheral} for any block entity that is a {@link CmGenerator}
 * (our water wheels + windmills) via a PeripheralLookup fallback — no new block, no Create BE-type
 * references. Called from the mod init ONLY when ComputerCraft is present, so this class (and its CC
 * imports) never loads otherwise.
 */
public final class CmComputerCraft {

    private CmComputerCraft() {
    }

    public static void register() {
        PeripheralLookup.get().registerFallback((level, pos, state, be, side) -> {
            if (be instanceof CmGenerator gen) {
                return new CmGeneratorPeripheral(gen);
            }
            if (be instanceof CmMeter meter) {
                return new CmMeterPeripheral(meter);
            }
            return null;
        });
    }
}
