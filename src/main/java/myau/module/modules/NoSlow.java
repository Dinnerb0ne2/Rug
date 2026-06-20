package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public ModeProperty mode;
    private final String[] modeNames = new String[]{"Vanilla", "Hypixel", "Intave", "IntaveSpoof", "AAC5", "AAC4", "Switch"};

    public NoSlow() {
        super("NoSlow", false);
        this.mode = new ModeProperty("Mode", 0, modeNames);
    }

    private boolean isMoving() {
        return mc.thePlayer != null && (mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F);
    }

    public boolean isAnyActive() {
        return this.isEnabled() && mc.thePlayer.isUsingItem();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{modeNames[mode.getValue()]};
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        String currentMode = modeNames[mode.getValue()].toLowerCase();
        switch (currentMode) {
            case "switch":
                if (isMoving() && (mc.thePlayer.isUsingItem() || mc.thePlayer.isEating() || mc.thePlayer.isBlocking())) {
                    PacketUtil.sendPacket(new C09PacketHeldItemChange((mc.thePlayer.inventory.currentItem + 1) % 9));
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                }
                break;
            case "intave":
                PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, new BlockPos(mc.thePlayer.posX, mc.thePlayer.getPositionEyes(1.0f).yCoord, mc.thePlayer.posZ), EnumFacing.DOWN));
                break;
            case "intavespoof":
                if (isMoving() && (mc.thePlayer.isUsingItem() || mc.thePlayer.isEating() || mc.thePlayer.isBlocking())) {
                    PacketUtil.sendPacket(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                    PacketUtil.sendPacket(new C0DPacketCloseWindow(mc.thePlayer.openContainer.windowId));
                }
                break;
            case "aac4":
                if (isMoving() && (mc.thePlayer.isUsingItem() || mc.thePlayer.isEating() || mc.thePlayer.isBlocking())) {
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                }
                break;
            case "aac5":
                if (isMoving() && (mc.thePlayer.isUsingItem() || mc.thePlayer.isEating() || mc.thePlayer.isBlocking())) {
                    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, mc.thePlayer.inventory.getCurrentItem(), 0.0f, 0.0f, 0.0f));
                }
                break;
            case "hypixel":
                if (mc.thePlayer.onGround && (mc.thePlayer.isUsingItem() || mc.thePlayer.isEating() || mc.thePlayer.isBlocking())) {
                    mc.thePlayer.motionY += 1.0E-7;
                }
                break;
        }
    }
}