package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty hitRange;
    public final IntProperty timerDelay;
    public final BooleanProperty esp;
    public final BooleanProperty onlyWhenNeed;
    public final BooleanProperty onlyKillAura;
    public final FloatProperty range;
    
    public final BooleanProperty players;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty villagers;

    public final BooleanProperty delayVelocity;
    public final BooleanProperty delayExplosion;
    public final BooleanProperty delayTimeUpdate;
    public final BooleanProperty delayKeepAlive;

    private EntityLivingBase target = null;
    private EntityLivingBase prevTarget = null;
    private boolean blockPackets = false;
    private final List<Packet<?>> packets = new ArrayList<>();
    private final TimerUtil timeHelper = new TimerUtil();
    private long blockStartTime = 0;
    private double trueX = 0, trueY = 0, trueZ = 0;

    public BackTrack() {
        super("BackTrack", false);
        this.hitRange = new FloatProperty("MaxHitRange", 6.0F, 3.0F, 6.0F);
        this.timerDelay = new IntProperty("Time", 4000, 0, 30000);
        this.esp = new BooleanProperty("Esp", true);
        this.onlyWhenNeed = new BooleanProperty("OnlyWhenNeed", true);
        this.onlyKillAura = new BooleanProperty("OnlyKillAura", true);
        this.range = new FloatProperty("PreAimRange", 4.0F, 0.0F, 15.0F);
        
        this.players = new BooleanProperty("Players", true);
        this.mobs = new BooleanProperty("Mobs", true);
        this.animals = new BooleanProperty("Animals", true);
        this.villagers = new BooleanProperty("Villagers", true);

        this.delayVelocity = new BooleanProperty("Delay-Velocity", true);
        this.delayExplosion = new BooleanProperty("Delay-Explosion", true);
        this.delayTimeUpdate = new BooleanProperty("Delay-TimeUpdate", true);
        this.delayKeepAlive = new BooleanProperty("Delay-KeepAlive", true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
            target = ka.getTarget();
        } else if (!this.onlyKillAura.getValue()) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
                target = (EntityLivingBase) mc.objectMouseOver.entityHit;
            } else {
                target = getClosestEntity();
            }
        } else {
            target = null;
        }

        if (target != null) {
            if (prevTarget != target) {
                trueX = target.posX;
                trueY = target.posY;
                trueZ = target.posZ;
                releasePackets();
                prevTarget = target;
            }

            double distCurrent = mc.thePlayer.getDistance(target.posX, target.posY, target.posZ);
            double distFuture = mc.thePlayer.getDistance(trueX, trueY, trueZ);

            boolean needBlock = false;
            if (!this.onlyWhenNeed.getValue()) {
                needBlock = true;
            } else if (mc.thePlayer.hurtTime > 3 && mc.thePlayer.hurtTime < 8) {
                needBlock = true;
            } else if (distFuture > distCurrent && distFuture <= this.hitRange.getValue()) {
                needBlock = true;
            }

            if (needBlock) {
                if (!blockPackets) {
                    blockStartTime = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - blockStartTime < this.timerDelay.getValue()) {
                    blockPackets = true;
                } else {
                    blockPackets = false;
                    releasePackets();
                }
            } else {
                blockPackets = false;
                releasePackets();
            }
        } else {
            blockPackets = false;
            releasePackets();
            prevTarget = null;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        Packet<?> packet = event.getPacket();
        if (event.isCancelled()) return;

        if (packet instanceof S08PacketPlayerPosLook) {
            releasePackets();
            return;
        }

        if (target == null) {
            releasePackets();
            return;
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            Entity ent = s14.getEntity(mc.theWorld);
            if (ent == target) {
                trueX += s14.func_149062_c() / 32.0;
                trueY += s14.func_149061_d() / 32.0;
                trueZ += s14.func_149064_e() / 32.0;
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            Entity ent = mc.theWorld.getEntityByID(s18.getEntityId());
            if (ent == target) {
                trueX = s18.getX() / 32.0;
                trueY = s18.getY() / 32.0;
                trueZ = s18.getZ() / 32.0;
            }
        }

        if (blockPackets) {
            if (shouldDelay(packet)) {
                synchronized (packets) {
                    packets.add(packet);
                }
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.esp.getValue() || target == null || !blockPackets) return;

        double x = trueX - mc.getRenderManager().viewerPosX;
        double y = trueY - mc.getRenderManager().viewerPosY;
        double z = trueZ - mc.getRenderManager().viewerPosZ;

        float halfWidth = target.width / 2.0f;
        AxisAlignedBB bb = new AxisAlignedBB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + target.height, z + halfWidth);
        
        Color color = new Color(0, 255, 0, 100);
        drawBox(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color);
    }

    private boolean shouldDelay(Packet<?> packet) {
        if (mc.currentScreen != null) return false;
        if (packet instanceof S03PacketTimeUpdate) return this.delayTimeUpdate.getValue();
        if (packet instanceof S00PacketKeepAlive) return this.delayKeepAlive.getValue();
        if (packet instanceof S12PacketEntityVelocity) return this.delayVelocity.getValue();
        if (packet instanceof S27PacketExplosion) return this.delayExplosion.getValue();
        if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus s19 = (S19PacketEntityStatus) packet;
            return s19.getOpCode() != 2 || !(s19.getEntity(mc.theWorld) instanceof EntityLivingBase);
        }
        return !(packet instanceof S06PacketUpdateHealth) 
            && !(packet instanceof S29PacketSoundEffect) 
            && !(packet instanceof S3EPacketTeams) 
            && !(packet instanceof S0CPacketSpawnPlayer);
    }

    @SuppressWarnings("unchecked")
    private void releasePackets() {
        List<Packet<?>> toProcess = new ArrayList<>();
        synchronized (packets) {
            toProcess.addAll(packets);
            packets.clear();
        }
        for (Packet<?> p : toProcess) {
            try {
                ((Packet<INetHandler>) p).processPacket(mc.thePlayer.sendQueue);
            } catch (Exception ignored) {}
        }
    }

    private void drawBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private EntityLivingBase getClosestEntity() {
        EntityLivingBase closest = null;
        double closestDist = this.range.getValue() * this.range.getValue();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
                EntityLivingBase elb = (EntityLivingBase) entity;
                if (!isValidTarget(elb)) continue;
                double dist = elb.getDistanceSqToEntity(mc.thePlayer);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = elb;
                }
            }
        }
        return closest;
    }

    private boolean isValidTarget(EntityLivingBase e) {
        if (e.isInvisible() || e.deathTime > 1 || e.isDead || e.ticksExisted < 50) return false;
        if (e instanceof EntityPlayer && !this.players.getValue()) return false;
        if (e instanceof EntityMob && !this.mobs.getValue()) return false;
        if (e instanceof EntityAnimal && !this.animals.getValue()) return false;
        if (e instanceof EntityVillager && !this.villagers.getValue()) return false;
        return true;
    }

    @Override
    public void onEnabled() {
        blockPackets = false;
        packets.clear();
        prevTarget = null;
        timeHelper.reset();
    }

    @Override
    public void onDisabled() {
        releasePackets();
        prevTarget = null;
    }
}