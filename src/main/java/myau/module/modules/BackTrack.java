package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty minHitRange = new FloatProperty("MinHitRange", 2.0F, 0.0F, 8.0F);
    public final FloatProperty maxHitRange = new FloatProperty("MaxHitRange", 6.0F, 0.0F, 8.0F);
    public final BooleanProperty dynamic = new BooleanProperty("Dynamic", true);
    public final IntProperty minDelay = new IntProperty("MinDelay", 600, 0, 1000);
    public final IntProperty maxDelay = new IntProperty("MaxDelay", 800, 0, 1000);
    public final IntProperty coolDownTimer = new IntProperty("CoolDown", 600, 0, 1000);
    public final BooleanProperty onlyWhenNeed = new BooleanProperty("OnlyWhenNeed", true);
    public final BooleanProperty releaseOnS12 = new BooleanProperty("ReleaseOnS12", true);
    public final ModeProperty distanceMode = new ModeProperty("DistanceMode", 0, new String[]{"MotionPrediction", "ServerPrediction"});
    public final BooleanProperty cancelS32 = new BooleanProperty("CancelS32", true);
    public final BooleanProperty cancelS00 = new BooleanProperty("CancelS00", true);
    public final BooleanProperty handleS08 = new BooleanProperty("HandleS08", true);
    public final BooleanProperty handleS12 = new BooleanProperty("HandleS12", true);
    public final BooleanProperty handleS27 = new BooleanProperty("HandleS27", true);
    public final BooleanProperty onlyAura = new BooleanProperty("OnlyAura", true);
    public final BooleanProperty botCheck = new BooleanProperty("BotCheck", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);

    private EntityLivingBase target = null;
    private EntityLivingBase prevTarget = null;
    private final ArrayList<QueuedPacket> packetsQueued = new ArrayList<>();
    private double futureDistance = 0;
    private double currentDistance = 0;
    private boolean backtracking = false;
    private double realX = 0, realY = 0, realZ = 0;
    private double previousX = 0, previousY = 0, previousZ = 0;
    private double packetX = 0, packetY = 0, packetZ = 0;
    private boolean updatedPreviousPosition = true;
    private long range = 0;
    private boolean blockingPacket = false;
    private double smoothX1 = 0, smoothY1 = 0, smoothZ1 = 0;
    private double lastRenderX = 0, lastRenderY = 0, lastRenderZ = 0;
    private long lastReleaseTime = 0;

    private static class QueuedPacket {
        Packet packet;
        long time;
        QueuedPacket(Packet p, long t) { packet = p; time = t; }
    }

    public BackTrack() {
        super("BackTrack", false);
    }

    private boolean isValidTarget(EntityLivingBase e) {
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;
        if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) return false;
        if (e == mc.getRenderViewEntity() || e.ridingEntity == mc.getRenderViewEntity()) return false;
        if (e.deathTime > 0) return false;
        if (!(e instanceof EntityPlayer)) return false;
        EntityPlayer ep = (EntityPlayer) e;
        if (TeamUtil.isFriend(ep)) return false;
        return (!this.teams.getValue() || !TeamUtil.isSameTeam(ep)) && (!this.botCheck.getValue() || !TeamUtil.isBot(ep));
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

    private void updateRange() {
        double mean = (minDelay.getValue() + maxDelay.getValue()) / 2.0;
        double stddev = (maxDelay.getValue() - minDelay.getValue()) / 6.0;
        range = (long) Math.max(minDelay.getValue(), Math.min(maxDelay.getValue(), mean + new Random().nextGaussian() * stddev));
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null) {
            backtracking = false;
            packetsQueued.clear();
            return;
        }

        target = getClosestEntity(10.0);
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (target == null || !ka.isEnabled() && this.onlyAura.getValue()) {
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
            previousX = target.posX;
            previousY = target.posY;
            previousZ = target.posZ;
            updatedPreviousPosition = true;
        }

        double posX, posY, posZ;
        switch (distanceMode.getValue()) {
            case 0:
                posX = target.serverPosX / 32.0;
                posY = target.serverPosY / 32.0;
                posZ = target.serverPosZ / 32.0;
                break;
            default:
                posX = 2 * target.posX - previousX;
                posY = 2 * target.posY - previousY;
                posZ = 2 * target.posZ - previousZ;
                break;
        }

        Vec3 eyePosition = mc.thePlayer.getPositionEyes(1.0F);
        float size = target.getCollisionBorderSize();
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(size, size, size);
        AxisAlignedBB newBB = bb.offset(posX - target.posX, posY - target.posY, posZ - target.posZ);
        AxisAlignedBB realBB = bb.offset(realX - target.posX, realY - target.posY, realZ - target.posZ);
        currentDistance = RotationUtil.distanceToBox(bb);
        futureDistance = backtracking ? RotationUtil.distanceToBox(realBB) : RotationUtil.distanceToBox(newBB);

        previousX = target.posX;
        previousY = target.posY;
        previousZ = target.posZ;

        lastRenderX = smoothX1;
        lastRenderY = smoothY1;
        lastRenderZ = smoothZ1;

        if (currentDistance > minHitRange.getValue() && currentDistance < maxHitRange.getValue() && (!onlyWhenNeed.getValue() || target.hurtTime * 50 <= range)) {
            backtracking = true;
        }

        if (currentDistance >= futureDistance || futureDistance < minHitRange.getValue() || futureDistance > maxHitRange.getValue()) {
            backtracking = false;
        }

        if (System.currentTimeMillis() - lastReleaseTime < coolDownTimer.getValue()) {
            backtracking = false;
        }

        if (backtracking) {
            releasePacketToDistance();
        } else {
            releaseAllPackets();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (mc.theWorld == null || target == null || !ka.isEnabled() && this.onlyAura.getValue()) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof S14PacketEntity) {
            if (((S14PacketEntity) packet).getEntity(mc.theWorld) == target) {
                realX += ((S14PacketEntity) packet).func_149062_c() / 32.0;
                realY += ((S14PacketEntity) packet).func_149061_d() / 32.0;
                realZ += ((S14PacketEntity) packet).func_149064_e() / 32.0;
            }
        }
        if (packet instanceof S18PacketEntityTeleport) {
            if (((S18PacketEntityTeleport) packet).getEntityId() == target.getEntityId()) {
                realX = ((S18PacketEntityTeleport) packet).getX() / 32.0;
                realY = ((S18PacketEntityTeleport) packet).getY() / 32.0;
                realZ = ((S18PacketEntityTeleport) packet).getZ() / 32.0;
            }
        }

        if (packet instanceof S08PacketPlayerPosLook) {
            if (blockingPacket) {
                backtracking = false;
                releaseAllPackets();
            }
            return;
        }

        if (blockingPacket) {
            if (mc.thePlayer.ticksExisted < 20) return;
            if (packet instanceof S12PacketEntityVelocity && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId() && this.releaseOnS12.getValue()) {
                backtracking = false;
            }
            if (isPacketToBeBlocked(packet)) {
                packetsQueued.add(new QueuedPacket(packet, System.currentTimeMillis()));
                event.setCancelled(true);
            }
        }
    }

    private boolean isPacketToBeBlocked(Packet<?> packet) {
        return packet instanceof S00PacketKeepAlive && this.cancelS00.getValue()
                || packet instanceof S12PacketEntityVelocity
                || packet instanceof S27PacketExplosion
                || packet instanceof S32PacketConfirmTransaction && this.cancelS32.getValue()
                || packet instanceof S14PacketEntity
                || packet instanceof S18PacketEntityTeleport
                || packet instanceof S19PacketEntityHeadLook
                || packet instanceof S0FPacketSpawnMob;
    }

    private void releasePacketToDistance() {
        mc.addScheduledTask(() -> {
            try {
                if (packetsQueued.isEmpty()) {
                    blockingPacket = true;
                    return;
                }
                updateRange();
                packetX = target.posX;
                packetY = target.posY;
                packetZ = target.posZ;
                double dist = currentDistance;
                long deltaTime = System.currentTimeMillis() - packetsQueued.get(0).time;

                while ((dist < minHitRange.getValue() || dist > maxHitRange.getValue() || deltaTime > range) && !packetsQueued.isEmpty()) {
                    QueuedPacket qp = packetsQueued.remove(0);
                    qp.packet.processPacket(mc.thePlayer.sendQueue);

                    if (!packetsQueued.isEmpty()) {
                        QueuedPacket next = packetsQueued.get(0);
                        Packet p = next.packet;
                        if (p instanceof S14PacketEntity) {
                            if (((S14PacketEntity) p).getEntity(mc.theWorld) == target) {
                                packetX += ((S14PacketEntity) p).func_149062_c() / 32.0;
                                packetY += ((S14PacketEntity) p).func_149061_d() / 32.0;
                                packetZ += ((S14PacketEntity) p).func_149064_e() / 32.0;
                            }
                        }
                        if (p instanceof S18PacketEntityTeleport) {
                            if (((S18PacketEntityTeleport) p).getEntityId() == target.getEntityId()) {
                                packetX = ((S18PacketEntityTeleport) p).getX() / 32.0;
                                packetY = ((S18PacketEntityTeleport) p).getY() / 32.0;
                                packetZ = ((S18PacketEntityTeleport) p).getZ() / 32.0;
                            }
                        }
                        if (dynamic.getValue()) {
                            AxisAlignedBB bb2 = target.getEntityBoundingBox().expand(target.getCollisionBorderSize(), target.getCollisionBorderSize(), target.getCollisionBorderSize()).offset(packetX - target.posX, packetY - target.posY, packetZ - target.posZ);
                            dist = RotationUtil.distanceToBox(bb2);
                            deltaTime = System.currentTimeMillis() - next.time;
                        }
                    }
                }
                blockingPacket = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void releaseAllPackets() {
        if (packetsQueued.isEmpty()) return;
        lastReleaseTime = System.currentTimeMillis();
        mc.addScheduledTask(() -> {
            try {
                while (!packetsQueued.isEmpty()) {
                    packetsQueued.remove(0).packet.processPacket(mc.thePlayer.sendQueue);
                }
                blockingPacket = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || target == null) return;
        Color color = new Color(160, 255, 195, 255);
        if (!packetsQueued.isEmpty()) {
            double partialTicks = event.getPartialTicks();
            smoothX1 = lastRenderX + (realX - lastRenderX) * partialTicks;
            smoothY1 = lastRenderY + (realY - lastRenderY) * partialTicks;
            smoothZ1 = lastRenderZ + (realZ - lastRenderZ) * partialTicks;

            double renderX = smoothX1 - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
            double renderY = smoothY1 - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
            double renderZ = smoothZ1 - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
            float width = target.width / 2.0F;
            float size = target.getCollisionBorderSize();
            AxisAlignedBB aabb = new AxisAlignedBB(
                    renderX - width - size, renderY - size, renderZ - width - size,
                    renderX + width + size, renderY + target.height + size, renderZ + width + size
            );
            RenderUtil.enableRenderState();
            RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
            RenderUtil.disableRenderState();
        } else {
            smoothX1 = realX;
            smoothY1 = realY;
            smoothZ1 = realZ;
        }
    }

    @Override
    public void onDisabled() {
        releaseAllPackets();
        backtracking = false;
        blockingPacket = false;
        target = null;
        prevTarget = null;
    }

    @Override
    public String[] getSuffix() {
        long ping = packetsQueued.isEmpty() ? 0 : System.currentTimeMillis() - packetsQueued.get(0).time;
        return new String[]{ping + "ms"};
    }
}