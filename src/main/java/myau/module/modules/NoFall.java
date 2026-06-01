package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil packetDelayTimer = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling = false;
    private boolean lastOnGround = false;
    private int legitState = 0;
    private int savedSlot = -1;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"PACKET", "BLINK", "NO_GROUND", "SPOOF", "LEGIT"});
    public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 10000);

    private boolean canTrigger() {
        return this.scoreboardResetTimer.hasTimeElapsed(3000) && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
    }

    public NoFall() {
        super("NoFall", false);
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            this.onDisabled();
        } else if (this.isEnabled() && event.getType() == EventType.SEND && !event.isCancelled()) {
            if (event.getPacket() instanceof C03PacketPlayer) {
                C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
                switch (this.mode.getValue()) {
                    case 0:
                        if (this.slowFalling) {
                            this.slowFalling = false;
                            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                        } else if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                this.slowFalling = true;
                                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.5F;
                            }
                        }
                        break;
                    case 1:
                        boolean allowed = !mc.thePlayer.isOnLadder() && !mc.thePlayer.capabilities.allowFlying && mc.thePlayer.hurtTime == 0;
                        if (Myau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
                            if (this.lastOnGround
                                    && !packet.isOnGround()
                                    && allowed
                                    && PlayerUtil.canFly(this.distance.getValue().intValue())
                                    && mc.thePlayer.motionY < 0.0) {
                                Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
                                Myau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
                            }
                        } else if (!allowed) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Myau.clientName, this.getName()));
                        } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Myau.clientName, this.getName()));
                        } else if (packet.isOnGround()) {
                            for (Packet<?> blinkedPacket : Myau.blinkManager.blinkedPackets) {
                                if (blinkedPacket instanceof C03PacketPlayer) {
                                    ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
                                }
                            }
                            Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            this.packetDelayTimer.reset();
                        }
                        this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
                        break;
                    case 2:
                        ((IAccessorC03PacketPlayer) packet).setOnGround(false);
                        break;
                    case 3:
                        if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                                mc.thePlayer.fallDistance = 0.0F;
                            }
                        }
                        break;
                    case 4:
                        break;
                }
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (ServerUtil.hasPlayerCountInfo()) {
                this.scoreboardResetTimer.reset();
            }
            if (this.mode.getValue() == 0 && this.slowFalling) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }

            if (this.mode.getValue() == 4) {
                if (legitState == 0) {
                    if (mc.thePlayer.fallDistance >= this.distance.getValue() && mc.thePlayer.motionY < 0 && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater()) {
                        int waterSlot = -1;
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                            if (stack != null && stack.getItem() == Items.water_bucket) {
                                waterSlot = i;
                                break;
                            }
                        }
                        if (waterSlot != -1) {
                            savedSlot = mc.thePlayer.inventory.currentItem;
                            mc.thePlayer.inventory.currentItem = waterSlot;
                            legitState = 1;
                        }
                    }
                } else if (legitState == 1) {
                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.inventory.currentItem = savedSlot;
                        legitState = 0;
                    } else {
                        mc.thePlayer.rotationPitch = 90.0f;
                        mc.entityRenderer.getMouseOver(1.0f);
                        ((IAccessorMinecraft) mc).callRightClickMouse();
                        legitState = 2;
                    }
                } else if (legitState == 2) {
                    mc.thePlayer.rotationPitch = 90.0f;
                    if (mc.thePlayer.onGround || mc.thePlayer.isInWater()) {
                        legitState = 3;
                    }
                } else if (legitState == 3) {
                    mc.thePlayer.rotationPitch = 90.0f;
                    mc.entityRenderer.getMouseOver(1.0f);
                    ((IAccessorMinecraft) mc).callRightClickMouse();
                    mc.thePlayer.inventory.currentItem = savedSlot;
                    legitState = 0;
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        this.lastOnGround = false;
        this.legitState = 0;
        this.savedSlot = -1;
        Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        if (this.slowFalling) {
            this.slowFalling = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        }
    }

    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}