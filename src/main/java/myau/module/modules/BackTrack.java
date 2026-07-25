package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil coolDownTimer = new TimerUtil();

    private EntityLivingBase target = null;
    private EntityLivingBase prevTarget = null;

    private final List<QueuedPacket> packetsQueued = new ArrayList<>();

    private double futureDistance = 0;
    private double currentDistance = 0;
    private long range = 0;
    private long ping = 0;

    private boolean backtracking = false;

    private double realX = 0, realY = 0, realZ = 0;
    private float previousX = 0, previousY = 0, previousZ = 0;
    private boolean updatedPreviousPosition = false;

    private double smoothX1 = 0, smoothY1 = 0, smoothZ1 = 0;
    private double lastRenderX = 0, lastRenderY = 0, lastRenderZ = 0;

    public final FloatProperty minHitRange;
    public final FloatProperty maxHitRange;
    public final BooleanProperty dynamic;
    public final IntProperty minDelay;
    public final IntProperty maxDelay;
    public final IntProperty coolDownTime;
    public final BooleanProperty onlyWhenNeed;
    public final BooleanProperty releaseOnS12;
    public final ModeProperty distanceMode;
    public final BooleanProperty cancelS32;
    public final BooleanProperty cancelS00;
    public final BooleanProperty handleS08;
    public final BooleanProperty handleS12;
    public final BooleanProperty handleS27;
    public final BooleanProperty onlyAura;

    private static class QueuedPacket {
        private final Packet<?> packet;
        private final long time;

        public QueuedPacket(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }

    public BackTrack() {
        super("BackTrack", false);
        this.minHitRange = new FloatProperty("MinHitRange", 2.0F, 0.0F, 8.0F);
        this.maxHitRange = new FloatProperty("MaxHitRange", 6.0F, 0.0F, 8.0F);
        this.dynamic = new BooleanProperty("Dynamic", true);
        this.minDelay = new IntProperty("MinDelay", 600, 0, 1000);
        this.maxDelay = new IntProperty("MaxDelay", 800, 0, 1000);
        this.coolDownTime = new IntProperty("CoolDownTimer", 600, 0, 1000);
        this.onlyWhenNeed = new BooleanProperty("OnlyWhenNeed", true);
        this.releaseOnS12 = new BooleanProperty("ReleaseOnS12", true);
        this.distanceMode = new ModeProperty("DistanceMode", 1, new String[]{"ServerPrediction", "MotionPrediction"});
        this.cancelS32 = new BooleanProperty("CancelS32", true);
        this.cancelS00 = new BooleanProperty("CancelS00", true);
        this.handleS08 = new BooleanProperty("HandleS08", true);
        this.handleS12 = new BooleanProperty("HandleS12", true);
        this.handleS27 = new BooleanProperty("HandleS27", true);
        this.onlyAura = new BooleanProperty("OnlyAura", true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEnabled() {
        backtracking = false;
        packetsQueued.clear();
        target = null;
        prevTarget = null;
        updatedPreviousPosition = false;
        coolDownTimer.reset();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onDisabled() {
        // 必须同步释放，否则模块关闭后仍会有包卡在队列中
        for (QueuedPacket qp : packetsQueued) {
            try {
                ((Packet<INetHandler>) qp.packet).processPacket(mc.thePlayer.sendQueue);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        packetsQueued.clear();
        backtracking = false;
        target = null;
        prevTarget = null;
        updatedPreviousPosition = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        
        if (mc.theWorld == null) {
            backtracking = false;
            releaseAllPackets();
            return;
        }
        
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
            target = ka.getTarget();
        } else {
            if (onlyAura.getValue()) {
                target = null;
            } else {
                target = getClosestEntity(10.0);
            }
        }

        if (!packetsQueued.isEmpty()) {
            long post = packetsQueued.get(0).time;
            long now = System.currentTimeMillis();
            ping = now - post;
        } else {
            ping = 0;
        }

        if (target == null) {
            updatedPreviousPosition = false;
            backtracking = false;
            releaseAllPackets();
            return;
        }

        if (prevTarget != target) {
            realX = target.posX;
            realY = target.posY;
            realZ = target.posZ;
            updatedPreviousPosition = false;
            prevTarget = target;
        }

        if (!updatedPreviousPosition) {
            updatePreviousPosition();
        }

        float[] position;
        if (distanceMode.getValue() == 0) {
            position = new float[]{ target.serverPosX / 32.0f, target.serverPosY / 32.0f, target.serverPosZ / 32.0f };
        } else {
            position = new float[]{ 2.0f * (float)target.posX - previousX, 2.0f * (float)target.posY - previousY, 2.0f * (float)target.posZ - previousZ };
        }

        float size = target.getCollisionBorderSize();
        AxisAlignedBB baseBB = target.getEntityBoundingBox().expand(size, size, size);
        AxisAlignedBB realBB = baseBB.offset(realX - target.posX, realY - target.posY, realZ - target.posZ);
        AxisAlignedBB newBB = baseBB.offset(position[0] - target.posX, position[1] - target.posY, position[2] - target.posZ);

        currentDistance = RotationUtil.distanceToBox(baseBB);
        futureDistance = backtracking ? RotationUtil.distanceToBox(realBB) : RotationUtil.distanceToBox(newBB);

        previousX = (float) target.posX;
        previousY = (float) target.posY;
        previousZ = (float) target.posZ;

        lastRenderX = smoothX1;
        lastRenderY = smoothY1;
        lastRenderZ = smoothZ1;

        prevTarget = target;

        if (currentDistance > minHitRange.getValue() && currentDistance < maxHitRange.getValue() && (!onlyWhenNeed.getValue() || target.hurtTime * 50 <= range)) {
            backtracking = true;
        }

        if (currentDistance >= futureDistance || futureDistance < minHitRange.getValue() || futureDistance > maxHitRange.getValue()) {
            backtracking = false;
        }

        if (!coolDownTimer.hasTimeElapsed(coolDownTime.getValue().longValue())) {
            backtracking = false;
        }

        if (backtracking) {
            releasePacketToDistance();
        } else {
            releaseAllPackets();
            coolDownTimer.reset();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        
        if (mc.theWorld == null || target == null) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            if (s14.getEntity(mc.theWorld) == target) {
                realX += s14.func_149062_c() / 32.0;
                realY += s14.func_149061_d() / 32.0;
                realZ += s14.func_149064_e() / 32.0;
            }
        }
        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            if (s18.getEntityId() == target.getEntityId()) {
                realX = s18.getX() / 32.0;
                realY = s18.getY() / 32.0;
                realZ = s18.getZ() / 32.0;
            }
        }

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
            if (s12.getEntityID() == mc.thePlayer.getEntityId() && releaseOnS12.getValue()) {
                backtracking = false;
            }
        }

        if (backtracking && isPacketToBeBlocked(packet)) {
            packetsQueued.add(new QueuedPacket(packet, System.currentTimeMillis()));
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled()) return;
        
        if (mc.theWorld == null || target == null) return;
        if (packetsQueued.isEmpty()) {
            smoothX1 = realX;
            smoothY1 = realY;
            smoothZ1 = realZ;
            return;
        }
        float partialTicks = event.getPartialTicks();
        smoothX1 = lastRenderX + (realX - lastRenderX) * partialTicks;
        smoothY1 = lastRenderY + (realY - lastRenderY) * partialTicks;
        smoothZ1 = lastRenderZ + (realZ - lastRenderZ) * partialTicks;

        double viewerPosX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double viewerPosY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double viewerPosZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        double fixX = smoothX1 - viewerPosX;
        double fixY = smoothY1 - viewerPosY;
        double fixZ = smoothZ1 - viewerPosZ;

        double width = target.width;
        double height = target.height;
        double halfWidth = width / 2.0;

        AxisAlignedBB bb = new AxisAlignedBB(
                fixX - halfWidth, fixY, fixZ - halfWidth,
                fixX + halfWidth, fixY + height, fixZ + halfWidth
        );

        Color color = new Color(160, 255, 195, 255);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);

        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glEnd();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    @SuppressWarnings("unchecked")
    private void releasePacketToDistance() {
        mc.addScheduledTask(() -> {
            try {
                if (packetsQueued.isEmpty()) {
                    return;
                }
                double pX = target.posX, pY = target.posY, pZ = target.posZ;
                double distance = currentDistance;
                updateRange();
                long deltaTime = System.currentTimeMillis() - packetsQueued.get(0).time;
                while (!packetsQueued.isEmpty() && ((distance < minHitRange.getValue() || distance > maxHitRange.getValue()) || deltaTime > range)) {
                    Packet<?> packet = packetsQueued.remove(0).packet;
                    ((Packet<INetHandler>) packet).processPacket(mc.thePlayer.sendQueue);

                    if (!packetsQueued.isEmpty()) {
                        Packet<?> nextPacket = packetsQueued.get(0).packet;
                        if (nextPacket instanceof S14PacketEntity) {
                            S14PacketEntity s14 = (S14PacketEntity) nextPacket;
                            if (s14.getEntity(mc.theWorld) == target) {
                                pX += s14.func_149062_c() / 32.0;
                                pY += s14.func_149061_d() / 32.0;
                                pZ += s14.func_149064_e() / 32.0;
                            }
                        } else if (nextPacket instanceof S18PacketEntityTeleport) {
                            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) nextPacket;
                            if (s18.getEntityId() == target.getEntityId()) {
                                pX = s18.getX() / 32.0;
                                pY = s18.getY() / 32.0;
                                pZ = s18.getZ() / 32.0;
                            }
                        }
                        if (dynamic.getValue()) {
                            float size = target.getCollisionBorderSize();
                            AxisAlignedBB bb2 = target.getEntityBoundingBox().expand(size, size, size).offset(pX - target.posX, pY - target.posY, pZ - target.posZ);
                            distance = RotationUtil.distanceToBox(bb2);
                            deltaTime = System.currentTimeMillis() - packetsQueued.get(0).time;
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("[BackTrack] Error: " + ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void releaseAllPackets() {
        mc.addScheduledTask(() -> {
            try {
                while (!packetsQueued.isEmpty()) {
                    Packet<?> packet = packetsQueued.remove(0).packet;
                    ((Packet<INetHandler>) packet).processPacket(mc.thePlayer.sendQueue);
                }
            } catch (Exception ex) {
                System.err.println("[BackTrack] Error: " + ex);
            }
        });
    }

    private void updateRange() {
        long min = minDelay.getValue().longValue();
        long max = maxDelay.getValue().longValue();
        if (max <= min) {
            range = min;
        } else {
            range = ThreadLocalRandom.current().nextLong(min, max + 1);
        }
    }

    private void updatePreviousPosition() {
        previousX = (float) target.posX;
        previousY = (float) target.posY;
        previousZ = (float) target.posZ;
        updatedPreviousPosition = true;
    }

    private boolean isPacketToBeBlocked(Packet<?> packet) {
        if (target == null) return false;

        if (packet instanceof S12PacketEntityVelocity) {
            return ((S12PacketEntityVelocity) packet).getEntityID() == target.getEntityId();
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            return s14.getEntity(mc.theWorld) == target;
        }
        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            return s18.getEntityId() == target.getEntityId();
        }
        if (packet instanceof S19PacketEntityHeadLook) {
            S19PacketEntityHeadLook s19 = (S19PacketEntityHeadLook) packet;
            return s19.getEntity(mc.theWorld) == target;
        }

        if (packet instanceof S00PacketKeepAlive && cancelS00.getValue()) return true;
        if (packet instanceof S32PacketConfirmTransaction && cancelS32.getValue()) return true;
        if (packet instanceof S08PacketPlayerPosLook) return true;

        return false;
    }

    private EntityLivingBase getClosestEntity(double range) {
        if (mc.theWorld == null) return null;
        EntityLivingBase closest = null;
        double closestDist = range;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
                EntityLivingBase e = (EntityLivingBase) entity;
                if (!e.isEntityAlive() || e.deathTime > 0) continue;
                if (e instanceof EntityPlayer && TeamUtil.isFriend((EntityPlayer) e)) continue;
                double dist = RotationUtil.distanceToEntity(e);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{ ping + "ms" };
    }
}