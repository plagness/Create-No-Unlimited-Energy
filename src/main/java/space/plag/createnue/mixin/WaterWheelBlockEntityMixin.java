package space.plag.createnue.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.plag.createnue.CmGenerator;
import space.plag.createnue.CmWheelSign;
import space.plag.createnue.NueConfig;
import space.plag.createnue.CreateNue;
import traben.flowing_fluids.api.FlowingFluidsAPI;

import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hydro power + DIRECTION STABILITY for Create water wheels under Flowing Fluids. See {@link CreateNue}.
 *
 * Create normalizes the flow vector before its sign vote, so smoothing the per-side VECTOR cannot stop
 * direction flips (only the sign reaches the score). The real cure is hysteresis on the SIGNED score plus
 * shaft consensus, both here. Power scaling (flow x head x biome, total-SU cap) and the water-as-fuel tax
 * are unchanged.
 */
@Pseudo
@Mixin(WaterWheelBlockEntity.class)
public abstract class WaterWheelBlockEntityMixin extends GeneratingKineticBlockEntity implements CmWheelSign, CmGenerator {

    @Shadow public abstract void setFlowScoreAndUpdate(int score);
    @Shadow protected abstract Direction.Axis getAxis();

    @Unique private final Map<BlockPos, Vec3> nue$smoothFlow = new HashMap<>();
    @Unique private double nue$flowMag = 0.0;
    @Unique private int nue$headBlocks = 0;
    @Unique private double nue$bestFlow = 0.0;
    @Unique private BlockPos nue$feedPos = null;
    @Unique private float nue$biomeFactor = 1.0f;
    @Unique private int nue$taxCooldown = 0;
    // direction hysteresis state
    @Unique private int nue$committedSign = 0;
    @Unique private double nue$scoreEma = 0.0;
    @Unique private int nue$flipStreak = 0;
    // dam coherence (megastructure reward) — registry of GENERATING wheels per dimension
    @Unique private static final Map<ResourceKey<Level>, Set<BlockPos>> NUE$WHEELS = new ConcurrentHashMap<>();
    @Unique private int nue$damSize = 1;
    @Unique private boolean nue$wheelRegistered = false;

    public WaterWheelBlockEntityMixin(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int cm$committedSign() {
        return this.nue$committedSign;
    }

    // -- CmGenerator: expose live numbers to ComputerCraft / readers --------------------------------
    @Override
    public String cm$kind() {
        return "waterwheel";
    }

    @Override
    public boolean cm$active() {
        return this.nue$flowMag > NueConfig.get().flowMinThreshold;
    }

    @Override
    public double cm$capacity() {
        return calculateAddedStressCapacity();
    }

    @Override
    public double cm$rpm() {
        return Math.abs(getGeneratedSpeed());
    }

    @Override
    public Map<String, Object> cm$details() {
        Map<String, Object> m = new HashMap<>();
        m.put("flow", this.nue$flowMag);
        m.put("head", this.nue$headBlocks);
        m.put("biome", (double) this.nue$biomeFactor);
        m.put("dam", this.nue$damSize);
        return m;
    }

    // -- Real flow vector (bypass FF's broken getFlowVectorAtPosition) + EMA (coast on empty) + measure --
    @Redirect(
        method = "determineAndApplyFlowScore",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;getFlowVectorAtPosition(Lnet/minecraft/class_2338;)Lnet/minecraft/class_243;"
        ),
        remap = false
    )
    private Vec3 nue$realFlowVector(WaterWheelBlockEntity self, BlockPos pos) {
        Level level = self.getLevel();
        if (level == null) {
            return Vec3.ZERO;
        }
        FluidState fluid = level.getFluidState(pos);
        Vec3 raw = fluid.isEmpty() ? Vec3.ZERO : fluid.getFlow(level, pos);

        NueConfig cfg = NueConfig.get();
        Vec3 result;
        if (!cfg.flowStabilizationEnabled) {
            result = raw;
        } else {
            Vec3 prev = this.nue$smoothFlow.getOrDefault(pos, Vec3.ZERO);
            Vec3 next;
            if (raw.lengthSqr() > 1.0e-6) {
                double a = cfg.flowSmoothing;
                next = prev.scale(1.0 - a).add(raw.scale(a)); // live water: blend toward it
            } else {
                next = prev.scale(cfg.flowDecay);             // momentarily empty: COAST (FF cells empty most ticks)
            }
            if (next.length() < cfg.flowMinThreshold) {       // only now is the side truly dry
                next = Vec3.ZERO;
                this.nue$smoothFlow.remove(pos);
            } else {
                this.nue$smoothFlow.put(pos.immutable(), next);
            }
            result = next;
        }

        double len = result.length();
        this.nue$flowMag += len;
        if (len > this.nue$bestFlow) {
            this.nue$bestFlow = len;
            this.nue$feedPos = pos.immutable();
        }
        if (result.y < -0.1) {
            int h = nue$scanHead(level, pos, cfg.headMaxScan);
            if (h > this.nue$headBlocks) {
                this.nue$headBlocks = h;
            }
        }
        return result;
    }

    @Unique
    private int nue$scanHead(Level level, BlockPos from, int maxScan) {
        BlockPos.MutableBlockPos p = from.mutable();
        int h = 0;
        for (int i = 0; i < maxScan; i++) {
            p.move(Direction.UP);
            if (level.getFluidState(p).isEmpty()) {
                break;
            }
            h++;
        }
        return h;
    }

    // -- THE CURE: hysteresis on the signed flow score (Create normalizes the vector, so we latch here) --
    @Redirect(
        method = "determineAndApplyFlowScore",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;setFlowScoreAndUpdate(I)V"
        ),
        remap = false
    )
    private void nue$hysteresisScore(WaterWheelBlockEntity self, int rawScore) {
        NueConfig cfg = NueConfig.get();
        if (!cfg.directionHysteresisEnabled) {
            this.setFlowScoreAndUpdate(rawScore);
            return;
        }
        double b = cfg.scoreSmoothing;
        this.nue$scoreEma = this.nue$scoreEma * (1.0 - b) + rawScore * b;
        int desired = (int) Math.signum(rawScore);
        int committed = this.nue$committedSign;

        if (desired == 0) {
            this.nue$flipStreak = 0;
            if (Math.abs(this.nue$scoreEma) < cfg.scoreDeadband) {
                committed = 0; // genuinely stalled -> stop
            }
        } else if (committed == 0) {
            committed = nue$consensusSign(desired); // spin-up: agree with the shaft if it already turns
            this.nue$flipStreak = 0;
        } else if (desired == committed) {
            this.nue$flipStreak = 0; // agreeing: stay
        } else { // opposite requested: require margin + persistence
            if (Math.abs(this.nue$scoreEma) >= cfg.scoreFlipThreshold
                    && (int) Math.signum(this.nue$scoreEma) == desired) {
                if (++this.nue$flipStreak >= cfg.flipHoldSamples) {
                    committed = desired;
                    this.nue$flipStreak = 0;
                }
            } else {
                this.nue$flipStreak = 0; // noise spike: ignore, KEEP current sign
            }
        }
        this.nue$committedSign = committed;
        this.setFlowScoreAndUpdate(committed == 0 ? 0 : committed * Math.max(1, Math.abs(rawScore)));
    }

    @Unique
    private int nue$consensusSign(int desired) {
        NueConfig cfg = NueConfig.get();
        if (!cfg.shaftConsensusEnabled || this.level == null) {
            return desired;
        }
        Direction.Axis axis = getAxis();
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        for (int s = -cfg.shaftConsensusReach; s <= cfg.shaftConsensusReach; s++) {
            if (s == 0) {
                continue;
            }
            BlockPos np = this.worldPosition.relative(along, s);
            if (this.level.getBlockEntity(np) instanceof CmWheelSign nb) {
                int ns = nb.cm$committedSign();
                if (ns != 0) {
                    return ns; // adopt an established neighbour direction
                }
            }
        }
        return desired;
    }

    // -- Per-pass reset + biome multiplier ----------------------------------------------------------
    @Inject(method = "determineAndApplyFlowScore", at = @At("HEAD"), remap = false)
    private void nue$beginScore(CallbackInfo ci) {
        this.nue$flowMag = 0.0;
        this.nue$headBlocks = 0;
        this.nue$bestFlow = 0.0;
        this.nue$feedPos = null;

        NueConfig cfg = NueConfig.get();
        if (!cfg.biomeEnabled || this.level == null) {
            this.nue$biomeFactor = 1.0f;
            return;
        }
        String id = this.level.getBiome(this.worldPosition).unwrapKey().map(k -> k.location().toString()).orElse("");
        if (cfg.biomeBoostIds.stream().anyMatch(id::contains)) {
            this.nue$biomeFactor = (float) cfg.biomeBoost;
        } else if (cfg.biomePenaltyIds.stream().anyMatch(id::contains)) {
            this.nue$biomeFactor = (float) cfg.biomePenalty;
        } else {
            this.nue$biomeFactor = 1.0f;
        }
    }

    // -- Power: scale added stress capacity (flow x head x biome), capped in total-SU space ---------
    @Override
    public float calculateAddedStressCapacity() {
        float base = super.calculateAddedStressCapacity();
        NueConfig cfg = NueConfig.get();
        double factor = 1.0;
        if (cfg.flowStrengthEnabled && cfg.flowStrengthReference > 0.0) {
            double t = Math.min(1.0, this.nue$flowMag / cfg.flowStrengthReference);
            factor *= 1.0 + (cfg.flowStrengthMax - 1.0) * t;
        }
        if (cfg.headBonusEnabled && cfg.headReference > 0) {
            double t = Math.min(1.0, (double) this.nue$headBlocks / cfg.headReference);
            factor *= 1.0 + cfg.headBonusMax * t;
        }
        if (cfg.biomeEnabled) {
            factor *= this.nue$biomeFactor;
        }
        // megastructure dam coherence: a cluster of GENERATING wheels (a real ГЭС) rewards super-linearly
        double coherenceMul = 1.0;
        if (cfg.damCoherenceEnabled && this.nue$damSize > 1) {
            coherenceMul = Math.min(cfg.damCoherenceMax,
                    1.0 + cfg.damCoherencePerWheel * (this.nue$damSize - 1));
            factor *= coherenceMul;
        }
        double perRpm = base * factor;
        if (cfg.maxStressCapEnabled && cfg.maxStressCapSU > 0.0) {
            double speed = Math.abs(getGeneratedSpeed());
            if (speed > 1.0e-3) {
                perRpm = Math.min(perRpm, (cfg.maxStressCapSU * coherenceMul) / speed); // cap grows with the dam
            }
        }
        // Round so the network total stays a clean integer (vanilla capacities are integers; our
        // float scaling otherwise prints as "3020.85083007812500" in the goggle/stressometer).
        return Math.round((float) perRpm);
    }

    // -- Conservation: water-as-fuel tax ------------------------------------------------------------
    @Inject(method = "lazyTick", at = @At("TAIL"), remap = false)
    private void nue$waterTax(CallbackInfo ci) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        NueConfig cfg = NueConfig.get();

        // -- dam coherence: register while generating, count nearby generating wheels (the dam size) --
        if (cfg.damCoherenceEnabled) {
            boolean generating = this.nue$flowMag > cfg.flowMinThreshold;
            if (generating && !this.nue$wheelRegistered) {
                nue$registerWheel();
            } else if (!generating && this.nue$wheelRegistered) {
                nue$unregisterWheel();
            }
            int newDam = generating ? nue$countNearbyWheels(cfg.damCoherenceRadius) : 1;
            if (newDam != this.nue$damSize) {
                this.nue$damSize = newDam;
                sendData(); // sync dam size to client (goggle) + refresh capacity display
            }
        } else if (this.nue$damSize != 1) {
            this.nue$damSize = 1;
            sendData();
        }

        // -- water-as-fuel tax --
        if (!cfg.waterTaxEnabled || cfg.waterTaxLevels <= 0) {
            return;
        }
        if (this.nue$feedPos == null || this.nue$flowMag <= 0.0) {
            return;
        }
        if (--this.nue$taxCooldown > 0) {
            return;
        }
        this.nue$taxCooldown = Math.max(1, Math.round(cfg.waterTaxIntervalTicks / 60.0f));

        FlowingFluidsAPI api = CreateNue.FF_API;
        if (api == null || api.isModCurrentlyMovingFluids()) {
            return;
        }
        try {
            if (cfg.waterTaxSkipInfiniteBiome
                    && api.doesBiomeInfiniteWaterRefill(this.level.getBiome(this.worldPosition))) {
                return;
            }
            api.removeFluidAmountFromPos(this.level, this.nue$feedPos, Fluids.WATER, 1, cfg.waterTaxLevels);
        } catch (Throwable t) {
            // never crash the BE tick
        }
    }

    // -- sync dam size to client (for the goggle readout) -------------------------------------------
    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void nue$writeDam(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        tag.putInt("NueDamSize", this.nue$damSize);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void nue$readDam(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (tag.contains("NueDamSize")) {
            this.nue$damSize = tag.getInt("NueDamSize");
        }
    }

    // -- goggle readout: show the ГЭС (dam) size + its coherence bonus when wearing goggles ---------
    // @Override (not @Inject): addToGoggleTooltip is declared on the superclass KineticBlockEntity, not
    // on WaterWheelBlockEntity, so an @Inject finds no target and fails the whole mixin. Overriding +
    // super-call merges cleanly (same pattern as calculateAddedStressCapacity).
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        NueConfig cfg = NueConfig.get();
        if (!cfg.damCoherenceEnabled || this.nue$damSize <= 1) {
            return added;
        }
        double mul = Math.min(cfg.damCoherenceMax, 1.0 + cfg.damCoherencePerWheel * (this.nue$damSize - 1));
        tooltip.add(Component.literal("    ").append(
                Component.translatable("create_nue.goggle.dam", this.nue$damSize,
                                String.format(java.util.Locale.ROOT, "%.2f", mul))
                        .withStyle(ChatFormatting.AQUA)));
        return true;
    }

    // -- dam coherence registry (reward clustered generating wheels = a real ГЭС) -------------------
    @Unique
    private void nue$registerWheel() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        NUE$WHEELS.computeIfAbsent(this.level.dimension(), k -> ConcurrentHashMap.newKeySet())
                .add(this.worldPosition.immutable());
        this.nue$wheelRegistered = true;
    }

    @Unique
    private void nue$unregisterWheel() {
        if (this.level == null) {
            return;
        }
        Set<BlockPos> s = NUE$WHEELS.get(this.level.dimension());
        if (s != null) {
            s.remove(this.worldPosition);
        }
        this.nue$wheelRegistered = false;
    }

    @Unique
    private int nue$countNearbyWheels(int r) {
        Set<BlockPos> s = NUE$WHEELS.get(this.level.dimension());
        if (s == null) {
            return 1;
        }
        long r2 = (long) r * r;
        int n = 1; // include self
        Iterator<BlockPos> it = s.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (p.equals(this.worldPosition)) {
                continue;
            }
            long dx = p.getX() - this.worldPosition.getX();
            long dy = p.getY() - this.worldPosition.getY();
            long dz = p.getZ() - this.worldPosition.getZ();
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            if (!this.level.isLoaded(p)) {
                continue; // unloaded: assume still valid
            }
            if (!(this.level.getBlockEntity(p) instanceof CmWheelSign)) {
                it.remove(); // self-heal a stale entry
                continue;
            }
            n++;
        }
        return n;
    }
}
