package space.plag.createnue;

/**
 * Duck-type interface implemented by the water-wheel mixin so neighbouring wheels can read each
 * other's committed rotation sign (for shaft consensus). Cross-instance access to a mixin's @Unique
 * member must go through an interface the mixin implements — {@code instanceof TheMixinClass} does
 * not work because the mixin is merged into the target, not a runtime type of its own.
 */
public interface CmWheelSign {
    /** -1 / 0 / +1 — the rotation direction this wheel is currently committed to. */
    int cm$committedSign();
}
