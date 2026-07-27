package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class RotationDataCollector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final List<String> currentTrack = new ArrayList<>();
    private float prevYaw = 0;
    private float prevPitch = 0;
    private boolean tracking = false;
    private int trackTicks = 0;
    
    public final BooleanProperty attackOnly;

    public RotationDataCollector() {
        super("DataCollector", false);
        attackOnly = new BooleanProperty("Attack-Only", true);
    }

    @Override
    public void onEnabled() {
        currentTrack.clear();
        tracking = false;
        trackTicks = 0;
        ChatUtil.sendFormatted("Started collecting. Move your mouse to attack targets.");
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (attackOnly.getValue() && mc.thePlayer != null) {
            tracking = true;
            currentTrack.clear();
            trackTicks = 0;
            prevYaw = mc.thePlayer.rotationYaw;
            prevPitch = mc.thePlayer.rotationPitch;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        
        if (!attackOnly.getValue() && !tracking) {
            tracking = true;
            currentTrack.clear();
            trackTicks = 0;
            prevYaw = mc.thePlayer.rotationYaw;
            prevPitch = mc.thePlayer.rotationPitch;
        }

        if (tracking) {
            float dYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - prevYaw);
            float dPitch = mc.thePlayer.rotationPitch - prevPitch;
            currentTrack.add(dYaw + "," + dPitch);
            prevYaw = mc.thePlayer.rotationYaw;
            prevPitch = mc.thePlayer.rotationPitch;
            trackTicks++;
            
            if (trackTicks >= 20) {
                saveTrack();
                tracking = false;
            }
        }
    }

    private void saveTrack() {
        try {
            File dir = new File(mc.mcDataDir, "myau_ml_data");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "rotations.csv");
            boolean isNew = !file.exists();
            
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                if (isNew) {
                    out.println("tick,dYaw,dPitch");
                }
                for (int i = 0; i < currentTrack.size(); i++) {
                    out.println(i + "," + currentTrack.get(i));
                }
                out.println("---");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}