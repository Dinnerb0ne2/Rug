package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty slowdown = new PercentProperty("SlowDown", 0);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground-Only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach-Only", false);

    public KeepSprint() {
        super("KeepSprint", false);
    }

    public boolean shouldKeepSprint() {
        if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
            return false;
        } else {
            return !this.reachOnly.getValue() || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
    }
}
