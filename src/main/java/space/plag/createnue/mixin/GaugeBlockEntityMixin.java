package space.plag.createnue.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import space.plag.createnue.CmMeter;

/**
 * Makes Create's Stressometer + Speedometer readable from ComputerCraft (via the cm_meter peripheral)
 * as intermediate network probes — speed at this point + the network's capacity/stress/overstress.
 * One mixin into the abstract GaugeBlockEntity covers both gauge types.
 */
@Pseudo
@Mixin(GaugeBlockEntity.class)
public abstract class GaugeBlockEntityMixin extends KineticBlockEntity implements CmMeter {

    public GaugeBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public String cm$gaugeType() {
        if (((Object) this) instanceof SpeedGaugeBlockEntity) {
            return "speed";
        }
        if (((Object) this) instanceof StressGaugeBlockEntity) {
            return "stress";
        }
        return "kinetic";
    }

    @Override
    public double cm$speed() {
        return Math.abs(getSpeed());
    }

    @Override
    public double cm$capacity() {
        KineticNetwork net = getOrCreateNetwork();
        return net == null ? 0.0 : net.calculateCapacity();
    }

    @Override
    public double cm$stressUsed() {
        KineticNetwork net = getOrCreateNetwork();
        return net == null ? 0.0 : net.calculateStress();
    }

    @Override
    public boolean cm$overstressed() {
        return isOverStressed();
    }
}
