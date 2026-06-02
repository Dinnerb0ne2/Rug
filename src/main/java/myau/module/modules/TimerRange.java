package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

public class TimerRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Field timerField;

    static {
        try {
            timerField = Minecraft.class.getDeclaredField("timer");
            timerField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private net.minecraft.util.Timer getTimer() {
        try {
            return (net.minecraft.util.Timer) timerField.get(mc);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public final ModeProperty workMode = new ModeProperty("WorkMode", 0, new String[]{"PRE", "POST"});
    public final BooleanProperty outGoing = new BooleanProperty("OutGoing", true);
    public final IntProperty maxTick = new IntProperty("MaxTick", 10, 1, 30);
    public final FloatProperty maxDistance = new FloatProperty("MaxDistance", 3.5F, 0.0F, 8.0F);
    public final IntProperty maxTimer = new IntProperty("MaxTimer", 7, 1, 10);
    public final FloatProperty minTimer = new FloatProperty("MinTimer", 0.0F, 0.0F, 1.0F);
    public final FloatProperty slowTimerFactor = new FloatProperty("SlowFactor", 1.5F, 0.0F, 3.0F);
    public final FloatProperty fastTimerFactor = new FloatProperty("FastFactor", 0.0F, 0.0F, 3.0F);
    public final IntProperty delay = new IntProperty("Delay", 1600, 400, 5000);
    public final FloatProperty minBps = new FloatProperty("MinBPS", 0.08F, 0.01F, 0.16F);
    public final BooleanProperty renderPoint = new BooleanProperty("RenderPoint", true);
    public final BooleanProperty renderOnlyNeed = new BooleanProperty("RenderOnlyNeed", true);

    private double timerBalance = 0;
    private double smartMaxBalance = 0;
    private boolean getHurt = false;
    private long hurtTime = 0;
    private long delayTime = 0;
    private boolean work = false;
    private boolean stopWorking = false;
    private boolean timerReset = false;
    private boolean attack = false;
    private long attackTime = 0;
    private Vec3 predictedPosition = new Vec3(0, 0, 0);
    private final ArrayList<Packet<?>> blinkPackets = new ArrayList<>();
    private double lastRenderX = 0, lastRenderY = 0, lastRenderZ = 0;

    public TimerRange() {
        super("TimerRange", false);
    }

    @Override
    public void onEnabled() {
        delayTime = System.currentTimeMillis();
        hurtTime = System.currentTimeMillis();
        resetState();
    }

    @Override
    public void onDisabled() {
        releasePackets();
        resetState();
        net.minecraft.util.Timer timer = getTimer();
        if (timer != null) timer.timerSpeed = 1.0f;
    }

    private void resetState() {
        timerBalance = 0;
        smartMaxBalance = 0;
        work = false;
        getHurt = false;
        timerReset = false;
        attack = false;
        blinkPackets.clear();
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            resetState();
            net.minecraft.util.Timer timer = getTimer();
            if (timer != null) timer.timerSpeed = 1.0f;
            return;
        }

        predictedPosition = predictPosition(mc.thePlayer, maxTick.getValue());
        EntityLivingBase target = getClosestEntity(6.0);
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (target == null || !ka.isEnabled()) {
            stopWorking = true;
        } else {
            stopWorking = checkStopWorking(target);
        }

        if (getHurt && System.currentTimeMillis() - hurtTime > 400) {
            getHurt = false;
        }

        if (attack && System.currentTimeMillis() - attackTime > 400) {
            attack = false;
        }

        if (!stopWorking && target != null) {
            double playerBPS = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
            double distance = calculateDistance(
                    new Vec3(predictedPosition.xCoord, predictedPosition.yCoord + mc.thePlayer.getEyeHeight(), predictedPosition.zCoord),
                    new Vec3(target.serverPosX / 32.0, target.serverPosY / 32.0, target.serverPosZ / 32.0)
            );
            distance -= target.getCollisionBorderSize() * 3.5;
            distance += distanceAdjust(target);

            if (System.currentTimeMillis() - delayTime > delay.getValue() && isCrosshairOnEntity(target) != null) {
                setSmartBalance(target, distance, playerBPS);
                if (smartMaxBalance <= maxTick.getValue() && smartMaxBalance > 0 && timerBalance == 0) {
                    work = true;
                    delayTime = System.currentTimeMillis();
                }
            }
        }

        if (stopWorking && !work && timerBalance < 0 && workMode.getValue() == 0) {
            resetTimer();
        }

        net.minecraft.util.Timer timer = getTimer();
        if (timer == null) return;
        float currentSpeed = timer.timerSpeed;

        switch (workMode.getValue()) {
            case 0:
                if (work) {
                    if (timerBalance > smartMaxBalance && currentSpeed == minTimer.getValue()) {
                        timer.timerSpeed = maxTimer.getValue();
                        work = false;
                    } else {
                        timerReset = true;
                        timer.timerSpeed = minTimer.getValue();
                    }
                } else if (timerBalance < 0) {
                    resetTimer();
                }
                break;
            case 1:
                if (work) {
                    timer.timerSpeed = maxTimer.getValue();
                    if (Math.abs(timerBalance) > smartMaxBalance) {
                        timerReset = true;
                        work = false;
                    }
                } else {
                    if (timerBalance < 0) {
                        timer.timerSpeed = minTimer.getValue();
                    } else {
                        resetTimer();
                    }
                }
                break;
        }

        currentSpeed = timer.timerSpeed;
        if (currentSpeed == minTimer.getValue()) {
            if (workMode.getValue() == 0 && work || workMode.getValue() == 1 && !work) {
                timerBalance += slowTimerFactor.getValue() - minTimer.getValue();
            }
        } else if (currentSpeed == maxTimer.getValue()) {
            timerBalance -= maxTimer.getValue() + fastTimerFactor.getValue();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            getHurt = true;
            hurtTime = System.currentTimeMillis();
            work = false;
            timerReset = false;
            net.minecraft.util.Timer timer = getTimer();
            if (timer != null) timer.timerSpeed = 1.0f;
            timerBalance = 0;
            releasePackets();
        }

        if (packet instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
                getHurt = true;
                hurtTime = System.currentTimeMillis();
            }
        }

        if (packet instanceof C02PacketUseEntity) {
            attack = true;
            attackTime = System.currentTimeMillis();
        }

        if (outGoing.getValue()) {
            if (work && workMode.getValue() == 0 || workMode.getValue() == 1 && (work || timerBalance < 0 && !work)) {
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
        if (renderOnlyNeed.getValue() && !work) {
            lastRenderX = mc.thePlayer.posX;
            lastRenderY = mc.thePlayer.posY + 0.18;
            lastRenderZ = mc.thePlayer.posZ;
            return;
        }

        double smoothFactor = 0.05;
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
        
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, 255, 120, 20);
        RenderUtil.disableRenderState();
    }

    private void resetTimer() {
        timerBalance = 0;
        if (!timerReset) return;
        net.minecraft.util.Timer timer = getTimer();
        if (timer != null) timer.timerSpeed = 1.0f;
        timerReset = false;
    }

    private void releasePackets() {
        if (blinkPackets.isEmpty()) return;
        try {
            for (Packet<?> p : new ArrayList<>(blinkPackets)) {
                if (p != null) {
                    mc.thePlayer.sendQueue.addToSendQueue(p);
                }
            }
        } catch (ConcurrentModificationException ignored) {}
        blinkPackets.clear();
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

    private boolean checkStopWorking(EntityLivingBase target) {
        double playerBPS = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        return mc.thePlayer.isOnLadder() 
                || !mc.gameSettings.keyBindForward.isKeyDown() 
                || getHurt 
                || mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer)).getBlock() == Blocks.web
                || rayTraceCheck() 
                || !inRange(target) 
                || attack 
                || playerBPS < minBps.getValue();
    }

    private boolean inRange(EntityLivingBase target) {
        double distance = calculateDistance(
                new Vec3(predictedPosition.xCoord, predictedPosition.yCoord + mc.thePlayer.getEyeHeight(), predictedPosition.zCoord),
                new Vec3(target.serverPosX / 32.0, target.serverPosY / 32.0, target.serverPosZ / 32.0)
        );
        distance -= target.getCollisionBorderSize() * 3.5;
        distance += distanceAdjust(target);
        return distance <= maxDistance.getValue();
    }

    private boolean rayTraceCheck() {
        Vec3 playerPos = mc.thePlayer.getPositionEyes(1.0F);
        float yaw = mc.thePlayer.rotationYaw;
        Vec3 direction = getVectorForRotation(0, yaw);
        Vec3 end = playerPos.addVector(direction.xCoord * maxDistance.getValue(), direction.yCoord * maxDistance.getValue(), direction.zCoord * maxDistance.getValue());
        return mc.theWorld.rayTraceBlocks(playerPos, end) != null;
    }

    private void setSmartBalance(EntityLivingBase target, double distance, double playerBPS) {
        if (target == null) { smartMaxBalance = 0; return; }
        double entityMotionX = Math.abs(target.lastTickPosX - target.posX);
        double entityMotionZ = Math.abs(target.lastTickPosZ - target.posZ);
        double entityBPS = Math.sqrt(entityMotionX * entityMotionX + entityMotionZ * entityMotionZ);
        entityBPS = Math.max(0.12, entityBPS);
        playerBPS = Math.max(0.12, playerBPS);
        double dis2 = mc.thePlayer.getDistanceToEntity(target) - target.getCollisionBorderSize() * 3.5;
        double finalDistance = dis2 - distance + 0.45;
        double a = mc.thePlayer.onGround ? 1 : 0.6;
        smartMaxBalance = finalDistance / (playerBPS * a + (entityBPS / 3.0));
    }

    private double distanceAdjust(EntityLivingBase target) {
        double lastDist = mc.thePlayer.getDistance(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ);
        double currDist = mc.thePlayer.getDistance(target.posX, target.posY, target.posZ);
        if (lastDist < currDist - 0.05) return -0.5;
        if (lastDist > currDist + 0.1) return 0.3;
        return 0;
    }

    private Vec3 isCrosshairOnEntity(EntityLivingBase target) {
        float size = target.getCollisionBorderSize();
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(size, size, size);
        Vec3 playerEyesPos = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 playerLookVec = mc.thePlayer.getLook(1.0F);
        double reachDistance = Math.min(3.0F, mc.thePlayer.getDistanceToEntity(target));
        Vec3 rayEnd = playerEyesPos.addVector(playerLookVec.xCoord * reachDistance, playerLookVec.yCoord * reachDistance, playerLookVec.zCoord * reachDistance);
        MovingObjectPosition hitResult = bb.calculateIntercept(playerEyesPos, rayEnd);
        return hitResult != null ? hitResult.hitVec : null;
    }

    private double calculateDistance(Vec3 from, Vec3 to) {
        double dx = to.xCoord - from.xCoord;
        double dy = to.yCoord - from.yCoord;
        double dz = to.zCoord - from.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
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

    private EntityLivingBase getClosestEntity(double range) {
        EntityLivingBase closest = null;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && isValidTarget((EntityLivingBase) entity)) {
                double dist = RotationUtil.distanceToEntity(entity);
                if (dist < range) {
                    range = dist;
                    closest = (EntityLivingBase) entity;
                }
            }
        }
        return closest;
    }

    private boolean isValidTarget(EntityLivingBase e) {
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;
        if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) return false;
        if (e.deathTime > 0) return false;
        if (!(e instanceof EntityPlayer)) return false;
        EntityPlayer ep = (EntityPlayer) e;
        if (TeamUtil.isFriend(ep)) return false;
        return true;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{workMode.getValue() == 0 ? "PRE" : "POST"};
    }
}