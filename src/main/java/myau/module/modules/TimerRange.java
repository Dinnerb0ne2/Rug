package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RotationUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TimerRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private void setTimerSpeed(float speed) {
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = speed;
    }

    public final ModeProperty workMode = new ModeProperty("WorkMode", 0, new String[]{"POST", "PRE"});
    public final BooleanProperty outGoing = new BooleanProperty("OutGoing", true);
    public final IntProperty maxTick = new IntProperty("MaxTick", 10, 1, 30);
    public final FloatProperty maxDistance = new FloatProperty("MaxDistance", 3.5F, 0.0F, 8.0F);
    public final FloatProperty maxTimer = new FloatProperty("MaxTimer", 2.0F, 1.1F, 5.0F);
    public final FloatProperty minTimer = new FloatProperty("MinTimer", 0.5F, 0.1F, 0.9F);
    public final IntProperty delay = new IntProperty("Delay", 1000, 100, 5000);
    public final FloatProperty minBps = new FloatProperty("MinBPS", 0.08F, 0.01F, 0.5F);
    public final BooleanProperty renderPoint = new BooleanProperty("RenderPoint", true);

    private static final int IDLE = 0;
    private static final int RUSH = 1;
    private static final int REPAY = 2;

    private int state = IDLE;
    private double balance = 0;
    private long delayTime = 0;
    private Vec3 predictedPosition = new Vec3(0, 0, 0);
    private final ArrayList<Packet<?>> blinkPackets = new ArrayList<>();
    private double lastRenderX = 0, lastRenderY = 0, lastRenderZ = 0;

    public TimerRange() {
        super("TimerRange", false);
    }

    @Override
    public void onEnabled() {
        resetState();
    }

    @Override
    public void onDisabled() {
        releasePackets();
        resetState();
        setTimerSpeed(1.0f);
    }

    private void resetState() {
        state = IDLE;
        balance = 0;
        delayTime = System.currentTimeMillis();
        blinkPackets.clear();
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            resetState();
            setTimerSpeed(1.0f);
            return;
        }

        predictedPosition = predictPosition(mc.thePlayer, maxTick.getValue());
        boolean shouldWork = checkShouldWork();

        switch (state) {
            case IDLE:
                setTimerSpeed(1.0f);
                if (shouldWork && System.currentTimeMillis() - delayTime > delay.getValue()) {
                    if (workMode.getValue() == 0) {
                        state = RUSH;
                    } else {
                        state = REPAY;
                    }
                }
                break;

            case RUSH:
                setTimerSpeed(maxTimer.getValue());
                balance += (maxTimer.getValue() - 1.0f);
                if (balance >= maxTick.getValue() || !shouldWork) {
                    state = REPAY;
                }
                break;

            case REPAY:
                setTimerSpeed(minTimer.getValue());
                balance -= (1.0f - minTimer.getValue());
                if (balance <= 0) {
                    balance = 0;
                    state = IDLE;
                    delayTime = System.currentTimeMillis();
                }
                break;
        }
    }

    private boolean checkShouldWork() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (!ka.isEnabled()) return false;

        EntityLivingBase target = ka.getTarget();
        if (target == null) return false;

        double playerBPS = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        if (playerBPS < minBps.getValue()) return false;
        if (mc.thePlayer.isOnLadder() || mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer)).getBlock() == Blocks.web) return false;
        if (!mc.gameSettings.keyBindForward.isKeyDown()) return false;

        double dist = mc.thePlayer.getDistanceToEntity(target);
        return dist <= maxDistance.getValue();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            resetState();
            setTimerSpeed(1.0f);
            releasePackets();
            return;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
                resetState();
                setTimerSpeed(1.0f);
                releasePackets();
                return;
            }
        }

        if (outGoing.getValue()) {
            if (state == RUSH) {
                if (isOutgoingPacket(packet)) {
                    blinkPackets.add(packet);
                    event.setCancelled(true);
                }
            } else if (!blinkPackets.isEmpty()) {
                releasePackets();
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !renderPoint.getValue() || predictedPosition == null || mc.theWorld == null) return;

        double smoothFactor = 0.15;
        double smoothX = lastRenderX + (predictedPosition.xCoord - lastRenderX) * smoothFactor;
        double smoothY = lastRenderY + (predictedPosition.yCoord + 0.18 - lastRenderY) * smoothFactor;
        double smoothZ = lastRenderZ + (predictedPosition.zCoord - lastRenderZ) * smoothFactor;
        lastRenderX = smoothX;
        lastRenderY = smoothY;
        lastRenderZ = smoothZ;

        double renderX = smoothX - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderY = smoothY - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderZ = smoothZ - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        double size = 0.2;
        AxisAlignedBB bb = new AxisAlignedBB(renderX - size, renderY - size, renderZ - size, renderX + size, renderY + size, renderZ + size);
        
        int color = state == RUSH ? 0xFF00FF00 : (state == REPAY ? 0xFFFF0000 : 0xFFFF8000);
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, (int)(red * 255), (int)(green * 255), (int)(blue * 255));
        RenderUtil.disableRenderState();
    }

    private void releasePackets() {
        if (blinkPackets.isEmpty()) return;
        List<Packet<?>> toSend = new ArrayList<>(blinkPackets);
        blinkPackets.clear();
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return;
        try {
            for (Packet<?> p : toSend) {
                if (p != null) mc.thePlayer.sendQueue.addToSendQueue(p);
            }
        } catch (Exception ignored) {}
    }

    private boolean isOutgoingPacket(Packet<?> packet) {
        return packet instanceof C00PacketKeepAlive 
                || packet instanceof C01PacketChatMessage 
                || packet instanceof C02PacketUseEntity 
                || packet instanceof C03PacketPlayer 
                || packet instanceof C07PacketPlayerDigging 
                || packet instanceof C08PacketPlayerBlockPlacement 
                || packet instanceof C09PacketHeldItemChange 
                || packet instanceof C0APacketAnimation 
                || packet instanceof C0BPacketEntityAction 
                || packet instanceof C0CPacketInput 
                || packet instanceof C0DPacketCloseWindow 
                || packet instanceof C0EPacketClickWindow 
                || packet instanceof C0FPacketConfirmTransaction;
    }

    private Vec3 predictPosition(EntityPlayer player, int ticks) {
        double predX = player.posX;
        double predY = player.posY;
        double predZ = player.posZ;
        double motX = player.motionX;
        double motY = player.motionY;
        double motZ = player.motionZ;

        for (int i = 0; i < ticks; i++) {
            motY -= 0.08;
            motY *= 0.98;
            float friction = player.onGround ? player.worldObj.getBlockState(new BlockPos(MathHelper.floor_double(predX), MathHelper.floor_double(predY) - 1, MathHelper.floor_double(predZ))).getBlock().slipperiness * 0.91F : 0.91F;
            motX *= friction;
            motZ *= friction;
            predX += motX;
            predY += motY;
            predZ += motZ;
            if (predY < 0) { predY = 0; motY = 0; }
        }
        return new Vec3(predX, predY, predZ);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{state == RUSH ? "RUSH" : (state == REPAY ? "REPAY" : "IDLE")};
    }
}