package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class FlagDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static short counter = 0;

    public FlagDetector() {
        super("FlagDetector", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        
        if (event.getPacket() instanceof S08PacketPlayerPosLook && mc.thePlayer.ticksExisted > 40) {
            counter++;
            String prefix = Myau.clientName.replace("&", "\u00a7");
            mc.thePlayer.addChatMessage(new ChatComponentText(prefix + EnumChatFormatting.RED + "Flag Detected: " + EnumChatFormatting.GRAY + counter));
        }
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (!this.isEnabled()) return;
        
        if (mc.thePlayer == null || mc.theWorld == null) {
            counter = 0;
        }
    }

    @Override
    public void onEnabled() {
        counter = 0;
    }

    @Override
    public void onDisabled() {
        counter = 0;
    }
}