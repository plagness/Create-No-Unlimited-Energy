package space.plag.createnue.mixin;

import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.plag.createnue.CmGenerator;
import space.plag.createnue.NueConfig;
import space.plag.createnue.CreateNue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wind power for Create windmills, modelled as TWO independent levers (Create: SU = capacity x RPM):
 *
 *   SPEED (RPM, the visible rotation) = how windy it is right now: gust x weather x day/night,
 *       smoothed (EMA) and de-flickered (hysteresis) so the rotor turns at a legible, steady-but-
 *       drifting rate (calm = slower, storm/day = faster). Shared in a cluster, capped by a soft-knee.
 *   FORCE (capacity per RPM, the SU) = the windmill's placement: exposure x height x region x a
 *       balance nerf, so wind delivers less power per rotation than a water wheel.
 *
 * SU = FORCE x SPEED. Speed reads the wind to the player; force balances and rewards good siting.
 * Both are synced to the client so the goggle/stress tooltip matches the server. See {@link CreateNue}.
 */
@Pseudo
@Mixin(WindmillBearingBlockEntity.class)
public abstract class WindmillBearingBlockEntityMixin extends MechanicalBearingBlockEntity implements CmGenerator {

    // movedContraption + running are inherited protected fields of MechanicalBearingBlockEntity.
    @Unique private static final Map<ResourceKey<Level>, Set<BlockPos>> NUE$WINDMILLS = new ConcurrentHashMap<>();

    // SPEED (visible rotation): dynamic, wind-driven
    @Unique private float nue$speedFactor = -1.0f; // smoothed factor on sail RPM; -1 = uninitialised
    @Unique private double nue$lastBaseRpm = 1.0;   // sail RPM magnitude from last compute
    @Unique private int nue$appliedMag = -1;        // rendered + synced RPM magnitude; -1 = uninitialised
    // FORCE (capacity per RPM): placement-driven
    @Unique private float nue$forceFactor = 1.0f;   // capacity multiplier (synced)

    @Unique private int nue$recheckIn = 0;
    @Unique private boolean nue$registered = false;

    public WindmillBearingBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // -- SPEED: scale the generated RPM to the rendered, smoothed magnitude --------------------------
    @Inject(method = "getGeneratedSpeed", at = @At("RETURN"), cancellable = true, remap = false)
    private void nue$scaleWindSpeed(CallbackInfoReturnable<Float> cir) {
        if (!NueConfig.get().windmillEnabled) {
            return;
        }
        if (this.movedContraption == null) { // cached path -> don't double-apply
            return;
        }
        float base = cir.getReturnValue();
        if (base == 0.0f) {
            return;
        }
        int mag = this.nue$appliedMag;
        if (mag < 0) {
            return; // not computed/synced yet: leave the unscaled value for one tick
        }
        if (mag == 0) {
            cir.setReturnValue(0.0f); // boxed / dead
            return;
        }
        cir.setReturnValue(base > 0.0f ? (float) mag : (float) -mag);
    }

    // -- FORCE: scale the capacity (SU per RPM) by the windmill's placement --------------------------
    @Override
    public float calculateAddedStressCapacity() {
        float base = super.calculateAddedStressCapacity();
        if (!NueConfig.get().windmillEnabled) {
            return base;
        }
        return Math.round(base * this.nue$forceFactor);
    }

    // -- CmGenerator: expose live numbers to ComputerCraft / readers --------------------------------
    @Override
    public String cm$kind() {
        return "windmill";
    }

    @Override
    public boolean cm$active() {
        return this.running && this.movedContraption != null && this.nue$appliedMag > 0;
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
        m.put("speedFactor", (double) this.nue$speedFactor);
        m.put("forceFactor", (double) this.nue$forceFactor);
        m.put("rpm", this.nue$appliedMag);
        return m;
    }

    // HEAD, not TAIL: WindmillBearingBlockEntity.tick() returns early at `if (!queuedReassembly) return`
    // almost every tick, so a TAIL inject practically never runs.
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void nue$windTick(CallbackInfo ci) {
        if (this.level == null || this.level.isClientSide || !NueConfig.get().windmillEnabled) {
            return;
        }
        boolean active = this.running && this.movedContraption != null;
        if (active && !this.nue$registered) {
            nue$register();
        } else if (!active && this.nue$registered) {
            nue$unregister();
        }
        if (!active) {
            return;
        }
        if (--this.nue$recheckIn > 0) {
            return;
        }
        this.nue$recheckIn = Math.max(20, NueConfig.get().windmillRecheckTicks);
        nue$recompute();
    }

    @Unique
    private void nue$recompute() {
        Level level = this.level;
        NueConfig cfg = NueConfig.get();
        if (level == null || this.movedContraption == null) {
            return;
        }
        BlockPos base = this.worldPosition;

        // base sail RPM (Create: clamp(sailBlocks / windmillSailsPerRPM[=8], 1, 16))
        double baseRpm;
        try {
            int sails = ((BearingContraption) this.movedContraption.getContraption()).getSailBlocks();
            baseRpm = Mth.clamp(sails / 8, 1, 16);
        } catch (Throwable t) {
            baseRpm = 1.0;
        }
        this.nue$lastBaseRpm = baseRpm;

        // open-sky exposure (gate)
        int n = Math.max(1, cfg.windmillSkySamples);
        int radius = 5, open = 0;
        for (int i = 0; i < n; i++) {
            double ang = (2.0 * Math.PI * i) / n;
            int x = base.getX() + (int) Math.round(radius * Math.cos(ang));
            int z = base.getZ() + (int) Math.round(radius * Math.sin(ang));
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) <= base.getY()) {
                open++;
            }
        }
        double expFrac = (double) open / n;
        double expNorm = expFrac < cfg.windmillExposureMin
                ? 0.0
                : (expFrac - cfg.windmillExposureMin) / Math.max(1.0e-3, 1.0 - cfg.windmillExposureMin);
        expNorm = Math.max(0.0, Math.min(1.0, expNorm));
        if (expNorm <= 0.0) { // boxed / underground -> dead (no speed, no force)
            nue$applyResult(0, 0.0f);
            return;
        }

        // ----- FORCE: height x region (x exposure x nerf) -> capacity per RPM -----
        long sum = 0;
        int cnt = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                sum += level.getHeight(Heightmap.Types.WORLD_SURFACE, base.getX() + dx, base.getZ() + dz);
                cnt++;
            }
        }
        double avgSurf = (double) sum / cnt;
        double aboveNorm = Math.max(0.0, Math.min(1.0, (base.getY() - avgSurf) / Math.max(1, cfg.windmillHeightAboveFull)));
        double heightMul = cfg.windmillHeightMin + (cfg.windmillHeightMax - cfg.windmillHeightMin) * aboveNorm;
        double regionMul = 1.0;
        if (cfg.windRegionEnabled) {
            String id = level.getBiome(base).unwrapKey().map(k -> k.location().toString()).orElse("");
            if (cfg.windyBiomeIds.stream().anyMatch(id::contains)) {
                regionMul = cfg.windRegionBoost;
            } else if (cfg.shelteredBiomeIds.stream().anyMatch(id::contains)) {
                regionMul = cfg.windRegionPenalty;
            }
        }
        float forceFactor = (float) (expNorm * heightMul * regionMul * cfg.windForceNerf);

        // ----- SPEED: current wind intensity -> visible RPM -----
        double weatherCeiling = level.isThundering() ? cfg.windStormCeiling
                : level.isRaining() ? cfg.windRainCeiling
                : cfg.windClearCeiling;
        double diurnalMul = 1.0;
        if (cfg.windDiurnalEnabled) {
            long dayTime = level.getDayTime() % 24000L; // 0=dawn 6000=noon 12000=dusk 18000=midnight
            double s = Math.sin(2.0 * Math.PI * ((dayTime - 3000.0) / 24000.0)); // +1 afternoon, -1 pre-dawn
            double mid = (cfg.windDayMul + cfg.windNightMul) / 2.0;
            double amp = (cfg.windDayMul - cfg.windNightMul) / 2.0;
            diurnalMul = mid + amp * s;
        }
        double ceiling = weatherCeiling * diurnalMul;
        double gust01 = 1.0;
        if (cfg.windGustEnabled) {
            long time = level.getGameTime();
            long ph = (long) base.getX() * 7349L + (long) base.getZ() * 9151L;
            double w1 = Math.sin((time + ph) * 0.0009);
            double w2 = Math.sin(time * 0.0031 + ph * 0.013);
            gust01 = Math.max(0.0, Math.min(1.0, 0.5 + 0.25 * w1 + 0.25 * w2));
        }
        double intensity = Math.max(0.0, Math.min(1.0, cfg.windGustFloor + (ceiling - cfg.windGustFloor) * gust01));
        double speedFactor = cfg.windSpeedMin + (cfg.windSpeedMax - cfg.windSpeedMin) * intensity;

        double rpm = baseRpm * speedFactor;
        if (cfg.windmillSoftKneeEnabled && rpm > cfg.windmillSoftKneeRpm) {
            rpm = cfg.windmillSoftKneeRpm + (rpm - cfg.windmillSoftKneeRpm) * cfg.windmillSoftK;
        }
        int nother = 0;
        if (cfg.windmillShareEnabled) {
            nother = nue$countNeighbours(cfg.windmillShareRadius);
            rpm *= 1.0 / Math.sqrt(1.0 + cfg.windmillShareK * nother);
        }
        double effSpeedFactor = rpm / baseRpm;

        // EMA smoothing of the speed factor (slow, smooth drift -> no jerk)
        if (this.nue$speedFactor < 0.0f) {
            this.nue$speedFactor = (float) effSpeedFactor;
        } else {
            this.nue$speedFactor += ((float) effSpeedFactor - this.nue$speedFactor) * (float) cfg.windEmaAlpha;
        }

        // hysteresis -> stable integer RPM (no 1<->2 flicker)
        double target = baseRpm * this.nue$speedFactor;
        int cur = this.nue$appliedMag;
        int next;
        if (cur < 0) {
            next = Math.max(1, (int) Math.round(target));
        } else {
            double margin = 0.5 + cfg.windSpeedHysteresis;
            next = (target >= cur + margin || target <= cur - margin) ? (int) Math.round(target) : cur;
            if (next < 1) {
                next = 1;
            }
        }
        nue$applyResult(next, forceFactor);

        if (cfg.windDebugLog) {
            CreateNue.LOGGER.info(String.format(
                "[WIND] %s | SPEED gust=%.2f ceil=%.2f int=%.2f sf=%.2f rpm=%d | FORCE exp=%.2f h=%.2f reg=%.2f f=%.2f cap=%d | SU=%d",
                base.toShortString(), gust01, ceiling, intensity, this.nue$speedFactor, this.nue$appliedMag,
                expNorm, heightMul, regionMul, this.nue$forceFactor, Math.round(512 * this.nue$forceFactor),
                Math.round(512 * this.nue$forceFactor) * Math.max(0, this.nue$appliedMag)));
        }
    }

    @Unique
    private void nue$applyResult(int newMag, float newForce) {
        boolean changed = newMag != this.nue$appliedMag
                || Math.abs(newForce - this.nue$forceFactor) > 0.01f;
        this.nue$appliedMag = newMag;
        this.nue$forceFactor = newForce;
        if (changed) {
            updateGeneratedRotation(); // re-reads getGeneratedSpeed + capacity, propagates + sendData()
            setChanged();
        }
    }

    // -- sync SPEED (rendered RPM) + FORCE (capacity factor) to the client so the goggle matches ----
    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void nue$writeWind(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        tag.putInt("NueWindMag", this.nue$appliedMag);
        tag.putFloat("NueWindForce", this.nue$forceFactor);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void nue$readWind(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (tag.contains("NueWindMag")) {
            this.nue$appliedMag = tag.getInt("NueWindMag");
        }
        if (tag.contains("NueWindForce")) {
            this.nue$forceFactor = tag.getFloat("NueWindForce");
        }
    }

    // -- anti-stacking registry (per-area shared wind budget) ----------------------------------------
    @Unique
    private void nue$register() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        NUE$WINDMILLS.computeIfAbsent(this.level.dimension(), k -> ConcurrentHashMap.newKeySet())
                .add(this.worldPosition.immutable());
        this.nue$registered = true;
    }

    @Unique
    private void nue$unregister() {
        if (this.level == null) {
            return;
        }
        Set<BlockPos> s = NUE$WINDMILLS.get(this.level.dimension());
        if (s != null) {
            s.remove(this.worldPosition);
        }
        this.nue$registered = false;
    }

    @Unique
    private int nue$countNeighbours(int r) {
        Set<BlockPos> s = NUE$WINDMILLS.get(this.level.dimension());
        if (s == null) {
            return 0;
        }
        long r2 = (long) r * r;
        int n = 0;
        Iterator<BlockPos> it = s.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (p.equals(this.worldPosition)) {
                continue;
            }
            long dx = p.getX() - this.worldPosition.getX();
            long dz = p.getZ() - this.worldPosition.getZ();
            if (dx * dx + dz * dz > r2) {
                continue;
            }
            if (!this.level.isLoaded(p)) {
                continue; // unloaded: assume still valid
            }
            if (!(this.level.getBlockEntity(p) instanceof WindmillBearingBlockEntity)) {
                it.remove(); // self-heal a stale entry
                continue;
            }
            n++;
        }
        return n;
    }
}
