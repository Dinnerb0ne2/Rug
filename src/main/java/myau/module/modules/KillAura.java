package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorEntity;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    private float[] attackRotations = null;

    private float saTemperature = 0.0f;
    private boolean saActive = false;
    private float saTargetYaw = 0.0f;
    private float saTargetPitch = 0.0f;

    private boolean wasRotating = false;
    private boolean isReturning = false;
    private float returnYaw = 0.0f;
    private float returnPitch = 0.0f;

    private float perlinTimeAccumulator = 0.0F;
    private float noiseFatigue = 0.0f;
    private int lastTargetEntityId = -1;

    private float fatigueLevel = 0.0f;
    private float fatigueDriftTime = 0.0f;

    public final ModeProperty mode;
    public final ModeProperty sort;
    public final ModeProperty autoBlock;
    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;

    public final ModeProperty rotationMode;
    public final FloatProperty saInitTemp;
    public final FloatProperty saCoolingRate;
    public final FloatProperty saMinTemp;
    public final FloatProperty saResetThreshold;

    public final BooleanProperty smoothBackProp;
    public final FloatProperty smoothBackSpeed;

    public final BooleanProperty bestHitVec;
    public final FloatProperty predictionTicks;
    public final BooleanProperty smartHit;
    public final BooleanProperty smartAim;

    public final ModeProperty noiseMode;
    public final FloatProperty noiseBaseScale;
    public final FloatProperty noiseFatigueScale;
    public final FloatProperty noisePitchRatio;
    public final FloatProperty noiseFrequency;
    public final FloatProperty noiseFatigueRate;
    public final FloatProperty noiseRecoveryRate;
    public final FloatProperty noiseYawBias;
    public final FloatProperty noisePitchBias;
    public final BooleanProperty noiseDistanceScale;
    public final FloatProperty noiseMicroJitter;
    public final BooleanProperty noiseGcdQuantize;

    public final ModeProperty fatigueMode;
    public final FloatProperty fatigueAccumRate;
    public final FloatProperty fatigueDecayRate;
    public final FloatProperty fatigueSmoothingMul;
    public final FloatProperty fatigueAngleReduction;
    public final FloatProperty fatigueDriftScale;
    public final FloatProperty fatigueDriftFreq;
    public final FloatProperty fatigueOvershootScale;
    public final FloatProperty fatigueOvershootProb;
    public final FloatProperty fatigueCpsPenalty;
    public final FloatProperty fatigueSwitchPenalty;
    public final BooleanProperty fatigueGcdQuantize;

    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public final ModeProperty showTarget;
    public final BooleanProperty dot;
    public final ColorProperty dotColor;
    public final FloatProperty dotSize;

    private static class AttackData {
        private final EntityLivingBase entity;
        public AttackData(EntityLivingBase entity) { this.entity = entity; }
        public EntityLivingBase getEntity() { return this.entity; }
        public AxisAlignedBB getBox() {
            float borderSize = this.entity.getCollisionBorderSize();
            return this.entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        }
        public double getX() { return this.entity.posX; }
        public double getY() { return this.entity.posY; }
        public double getZ() { return this.entity.posZ; }
    }

    private long getAttackDelay() {
        int effectiveMinCPS = this.minCPS.getValue();
        int effectiveMaxCPS = this.maxCPS.getValue();
        if (this.fatigueMode.getValue() != 0 && this.fatigueCpsPenalty.getValue() > 0.0f) {
            int penalty = (int) (this.fatigueLevel * this.fatigueCpsPenalty.getValue());
            effectiveMinCPS = Math.max(1, effectiveMinCPS - penalty);
            effectiveMaxCPS = Math.max(effectiveMinCPS, effectiveMaxCPS - penalty);
        }
        return 1000L / RandomUtil.nextLong(effectiveMinCPS, effectiveMaxCPS);
    }

    private int getPlayerPing() {
        try {
            for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
                if (info.getGameProfile().getId().equals(mc.thePlayer.getGameProfile().getId())) {
                    return info.getResponseTime();
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private float[] getDynamicRecoveryNoiseOffset() {
        if (this.noiseMode.getValue() == 0) return new float[]{0.0f, 0.0f};
        float effectiveScale = this.noiseBaseScale.getValue() + this.noiseFatigueScale.getValue() * this.noiseFatigue;
        float pRatio = this.noisePitchRatio.getValue();
        float time = this.perlinTimeAccumulator;
        float freq = this.noiseFrequency.getValue();
        if (this.noiseDistanceScale.getValue() && this.target != null) {
            double dist = RotationUtil.distanceToBox(this.target.getBox());
            effectiveScale *= (float) Math.max(1.0, 0.6 + dist / 3.0);
        }
        float fatigueFactor = 0.3f + 0.7f * this.noiseFatigue;
        float yawOffset = 0.0f, pitchOffset = 0.0f;
        switch (this.noiseMode.getValue()) {
            case 1:
                yawOffset = RotationUtil.getGaussianNoise() * effectiveScale * fatigueFactor;
                pitchOffset = RotationUtil.getGaussianNoise() * effectiveScale * pRatio * fatigueFactor;
                break;
            case 2:
                yawOffset = RotationUtil.getPerlinNoise(time, 0.0f) * effectiveScale * fatigueFactor;
                pitchOffset = RotationUtil.getPerlinNoise(time, 100.0f) * effectiveScale * pRatio * fatigueFactor;
                break;
            case 3:
                yawOffset = RotationUtil.getWaveNoise(time, freq) * effectiveScale * fatigueFactor;
                pitchOffset = RotationUtil.getWaveNoise(time + 50.0f, freq) * effectiveScale * pRatio * fatigueFactor;
                break;
            case 4:
                float pY = RotationUtil.getPerlinNoise(time, 0.0f);
                float pP = RotationUtil.getPerlinNoise(time, 100.0f);
                float wY = RotationUtil.getWaveNoise(time, freq);
                float wP = RotationUtil.getWaveNoise(time + 50.0f, freq);
                float gY = RotationUtil.getGaussianNoise();
                float gP = RotationUtil.getGaussianNoise();
                yawOffset = (pY * 0.4f + wY * 0.25f + gY * 0.35f) * effectiveScale * fatigueFactor;
                pitchOffset = (pP * 0.4f + wP * 0.25f + gP * 0.35f) * effectiveScale * pRatio * fatigueFactor;
                break;
        }
        if (this.noiseMicroJitter.getValue() > 0.0f) {
            float jitterScale = this.noiseMicroJitter.getValue() * (0.5f + 0.5f * this.noiseFatigue);
            float jt = time * 4.0f;
            yawOffset += RotationUtil.getPerlinNoise(jt, 200.0f) * jitterScale;
            pitchOffset += RotationUtil.getPerlinNoise(jt, 300.0f) * jitterScale * pRatio;
        }
        yawOffset += this.noiseYawBias.getValue();
        pitchOffset += this.noisePitchBias.getValue();
        if (this.noiseGcdQuantize.getValue()) {
            float gcd = RotationUtil.getSensitivityGCD();
            if (gcd > 0.0f) {
                yawOffset = Math.round(yawOffset / gcd) * gcd;
                pitchOffset = Math.round(pitchOffset / gcd) * gcd;
            }
        }
        return new float[]{yawOffset, pitchOffset};
    }

    private void updateNoiseFatigue(boolean inCombat) {
        if (inCombat) {
            this.noiseFatigue = Math.min(1.0f, this.noiseFatigue + this.noiseFatigueRate.getValue());
        } else {
            this.noiseFatigue *= (1.0f - this.noiseRecoveryRate.getValue());
            if (this.noiseFatigue < 0.001f) this.noiseFatigue = 0.0f;
        }
    }

    private void updateFatigue(boolean inCombat) {
        if (this.fatigueMode.getValue() == 0) return;
        if (inCombat) {
            float rate = this.fatigueAccumRate.getValue();
            switch (this.fatigueMode.getValue()) {
                case 1:
                    this.fatigueLevel = Math.min(1.0f, this.fatigueLevel + rate);
                    break;
                case 2:
                    this.fatigueLevel = Math.min(1.0f, this.fatigueLevel + rate * (1.0f + this.fatigueLevel * 2.0f));
                    break;
                case 3:
                    this.fatigueLevel = Math.min(1.0f, this.fatigueLevel + rate / (1.0f + this.fatigueLevel * 5.0f));
                    break;
                case 4:
                    float effort = 1.0f;
                    if (this.target != null) {
                        float[] rawTarget;
                        if (this.smartAim.getValue()) {
                            rawTarget = RotationUtil.getRawTargetSmartVec(this.target.getBox(), mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                        } else if (this.bestHitVec.getValue()) {
                            rawTarget = RotationUtil.getRawTargetBox(this.target.getBox());
                        } else {
                            rawTarget = RotationUtil.getRawTargetEntity(this.target.getEntity(), this.predictionTicks.getValue());
                        }
                        float angleDiff = Math.abs(MathHelper.wrapAngleTo180_float(rawTarget[0] - mc.thePlayer.rotationYaw));
                        effort = 1.0f + angleDiff / 90.0f;
                        double dist = RotationUtil.distanceToBox(this.target.getBox());
                        effort *= (float) (1.0 + dist / 4.0);
                    }
                    this.fatigueLevel = Math.min(1.0f, this.fatigueLevel + rate * effort);
                    break;
            }
        } else {
            float decay = this.fatigueDecayRate.getValue();
            switch (this.fatigueMode.getValue()) {
                case 1:
                    this.fatigueLevel = Math.max(0.0f, this.fatigueLevel - decay);
                    break;
                case 2:
                    this.fatigueLevel = Math.max(0.0f, this.fatigueLevel - decay * (1.0f + this.fatigueLevel));
                    break;
                case 3:
                    this.fatigueLevel = Math.max(0.0f, this.fatigueLevel - decay / (1.0f + this.fatigueLevel * 3.0f));
                    break;
                case 4:
                    this.fatigueLevel = Math.max(0.0f, this.fatigueLevel - decay * (1.0f + this.fatigueLevel * 0.5f));
                    break;
            }
            if (this.fatigueLevel < 0.001f) this.fatigueLevel = 0.0f;
        }
    }

    private float[] getFatigueDriftOffset() {
        if (this.fatigueMode.getValue() == 0 || this.fatigueDriftScale.getValue() == 0.0f) return new float[]{0.0f, 0.0f};
        float effectiveDrift = this.fatigueDriftScale.getValue() * this.fatigueLevel;
        float freq = this.fatigueDriftFreq.getValue();
        float time = this.fatigueDriftTime;
        float yawDrift = RotationUtil.getPerlinNoise(time, 50.0f) * effectiveDrift * 0.6f
                + RotationUtil.getWaveNoise(time, freq) * effectiveDrift * 0.4f;
        float pitchDrift = RotationUtil.getPerlinNoise(time, 150.0f) * effectiveDrift * 0.4f
                + RotationUtil.getWaveNoise(time + 25.0f, freq) * effectiveDrift * 0.3f;
        if (this.fatigueGcdQuantize.getValue()) {
            float gcd = RotationUtil.getSensitivityGCD();
            if (gcd > 0.0f) {
                yawDrift = Math.round(yawDrift / gcd) * gcd;
                pitchDrift = Math.round(pitchDrift / gcd) * gcd;
            }
        }
        return new float[]{yawDrift, pitchDrift};
    }

    private float[] getFatigueOvershoot(float yawDeltaToTarget, float pitchDeltaToTarget) {
        if (this.fatigueMode.getValue() == 0 || this.fatigueOvershootProb.getValue() <= 0.0f || this.fatigueLevel < 0.05f) {
            return new float[]{0.0f, 0.0f};
        }
        float prob = this.fatigueOvershootProb.getValue() * this.fatigueLevel;
        if (Math.random() > prob) return new float[]{0.0f, 0.0f};
        float scale = this.fatigueOvershootScale.getValue() * this.fatigueLevel;
        float yawOvershoot = Math.signum(yawDeltaToTarget) * (0.3f + (float) Math.random() * 0.7f) * scale;
        float pitchOvershoot = Math.signum(pitchDeltaToTarget) * (0.2f + (float) Math.random() * 0.5f) * scale * 0.5f;
        return new float[]{yawOvershoot, pitchOvershoot};
    }

    private void handleSmoothBack(UpdateEvent event) {
        if (!this.smoothBackProp.getValue() || (this.rotations.getValue() != 2 && this.rotations.getValue() != 3)) {
            this.isReturning = false;
            this.wasRotating = false;
            return;
        }
        if (!this.wasRotating) {
            this.isReturning = false;
            return;
        }
        if (!this.isReturning) {
            this.isReturning = true;
            this.returnYaw = event.getYaw();
            this.returnPitch = event.getPitch();
        }
        float playerYaw = mc.thePlayer.rotationYaw;
        float playerPitch = mc.thePlayer.rotationPitch;
        float backSpeed = this.smoothBackSpeed.getValue();
        if (this.fatigueMode.getValue() != 0) {
            backSpeed *= (1.0f - this.fatigueLevel * 0.5f);
            backSpeed = Math.max(0.01f, backSpeed);
        }
        float[] back = RotationUtil.smoothBack(this.returnYaw, this.returnPitch, playerYaw, playerPitch, backSpeed);
        this.returnYaw = back[0];
        this.returnPitch = back[1];
        float yD = Math.abs(MathHelper.wrapAngleTo180_float(playerYaw - this.returnYaw));
        float pD = Math.abs(MathHelper.wrapAngleTo180_float(playerPitch - this.returnPitch));
        if (yD > 1.0f || pD > 1.0f) {
            event.setRotation(this.returnYaw, this.returnPitch, 1);
            if (this.rotations.getValue() == 3) Myau.rotationManager.setRotation(this.returnYaw, this.returnPitch, 1, true);
            if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) event.setPervRotation(this.returnYaw, 1);
        } else {
            this.isReturning = false;
            this.wasRotating = false;
        }
    }

    private float[] getTargetRotations(float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        float ticks = this.predictionTicks.getValue();
        AxisAlignedBB box = this.target.getBox();
        AxisAlignedBB predBox = box.offset(this.target.getEntity().motionX * ticks, this.target.getEntity().motionY * ticks, this.target.getEntity().motionZ * ticks);

        if (this.smartAim.getValue()) {
            return RotationUtil.getRotationsToSmartVec(predBox, currentYaw, currentPitch, maxAngle, smoothFactor);
        } else if (this.bestHitVec.getValue()) {
            return RotationUtil.getRotationsToBox(predBox, currentYaw, currentPitch, maxAngle, smoothFactor);
        } else {
            return RotationUtil.getRotationsToEntity(this.target.getEntity(), currentYaw, currentPitch, maxAngle, smoothFactor, ticks);
        }
    }

    private float[] getRawTarget() {
        float ticks = this.predictionTicks.getValue();
        AxisAlignedBB box = this.target.getBox();
        AxisAlignedBB predBox = box.offset(this.target.getEntity().motionX * ticks, this.target.getEntity().motionY * ticks, this.target.getEntity().motionZ * ticks);

        if (this.smartAim.getValue()) {
            return RotationUtil.getRawTargetSmartVec(predBox, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        } else if (this.bestHitVec.getValue()) {
            return RotationUtil.getRawTargetBox(predBox);
        } else {
            return RotationUtil.getRawTargetEntity(this.target.getEntity(), ticks);
        }
    }

    private boolean performAttack(float yaw, float pitch, boolean isSmartHitting) {
        if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) return false;
            else if (this.attackDelayMS > 0L) return false;
            else {
                if (isSmartHitting) {
                    this.attackDelayMS += 500L;
                    return false;
                }
                this.attackDelayMS += this.getAttackDelay();
                mc.thePlayer.swingItem();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox())) && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) return false;
                else {
                    AttackEvent ae = new AttackEvent(this.target.getEntity());
                    EventManager.call(ae);
                    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                    PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
                    if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) PlayerUtil.attackEntity(this.target.getEntity());
                    this.hitRegistered = true;
                    return true;
                }
            }
        } else return false;
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack is) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(is));
        mc.thePlayer.setItemInUse(is, is.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.target != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, 8.0);
            if (mop != null) {
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), new Vec3(mop.hitVec.xCoord - this.target.getX(), mop.hitVec.yCoord - this.target.getY(), mop.hitVec.zCoord - this.target.getZ())));
                PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }

    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;
        else if (!(Boolean) this.weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant() || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) return false;
            else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) return false;
            else {
                AutoHeal ah = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
                if (ah.isEnabled() && ah.isSwitching()) return false;
                else {
                    BedNuker bn = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn abi = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
                    if (bn.isEnabled() && bn.isReady()) return false;
                    else if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false;
                    else if (abi.isEnabled()) return false;
                    else if (this.requirePress.getValue()) return PlayerUtil.isAttacking();
                    else return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                }
            }
        } else return false;
    }

    private boolean canAutoBlock() { return ItemUtil.isHoldingSword(); }

    private boolean hasValidTarget() {
        return mc.theWorld.loadedEntityList.stream().anyMatch(e -> e instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase) e) && this.isInSwingRange((EntityLivingBase) e));
    }

    private boolean isValidTarget(EntityLivingBase e) {
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;
        if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) return false;
        if (e == mc.getRenderViewEntity() || e == mc.getRenderViewEntity().ridingEntity) return false;
        if (e.deathTime > 0) return false;
        if (RotationUtil.angleToEntity(e) > this.fov.getValue().floatValue()) return false;
        if (!this.throughWalls.getValue() && RotationUtil.rayTrace(e) != null) return false;
        if (e instanceof EntityOtherPlayerMP) {
            if (!this.players.getValue()) return false;
            if (TeamUtil.isFriend((EntityPlayer) e)) return false;
            return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) e)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) e));
        }
        if (e instanceof EntityDragon || e instanceof EntityWither) return this.bosses.getValue();
        if (!(e instanceof EntityMob) && !(e instanceof EntitySlime)) {
            if (e instanceof EntityAnimal || e instanceof EntityBat || e instanceof EntitySquid || e instanceof EntityVillager) return this.animals.getValue();
            if (!(e instanceof EntityIronGolem)) return false;
            return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(e));
        }
        if (!(e instanceof EntitySilverfish)) return this.mobs.getValue();
        return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(e));
    }

    private boolean isInRange(EntityLivingBase e) { return this.isInSwingRange(e) || this.isInAttackRange(e); }
    private boolean isInSwingRange(EntityLivingBase e) { return RotationUtil.distanceToEntity(e) <= (double) this.swingRange.getValue(); }
    private boolean isBoxInSwingRange(AxisAlignedBB bb) { return RotationUtil.distanceToBox(bb) <= (double) this.swingRange.getValue(); }
    private boolean isInAttackRange(EntityLivingBase e) { return RotationUtil.distanceToEntity(e) <= (double) this.attackRange.getValue(); }
    private boolean isBoxInAttackRange(AxisAlignedBB bb) { return RotationUtil.distanceToBox(bb) <= (double) this.attackRange.getValue(); }
    private boolean isPlayerTarget(EntityLivingBase e) { return e instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) e); }

    public KillAura() {
        super("KillAura", false);
        this.mode = new ModeProperty("mode", 0, new String[]{"SINGLE", "SWITCH"});
        this.sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
        this.autoBlock = new ModeProperty("auto-block", 0, new String[]{"NONE", "VANILLA", "FAKE"});
        this.swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("fov", 360, 30, 360);
        this.minCPS = new IntProperty("min-aps", 14, 1, 20);
        this.maxCPS = new IntProperty("max-aps", 14, 1, 20);
        this.switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
        this.rotations = new ModeProperty("rotations", 2, new String[]{"NONE", "LEGIT", "SILENT", "LOCK_VIEW"});
        this.moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
        this.smoothing = new PercentProperty("smoothing", 0);
        this.rotationMode = new ModeProperty("rotation-mode", 0, new String[]{"BASIC", "SA"});
        this.saInitTemp = new FloatProperty("sa-init-temp", 5.0F, 0.5F, 20.0F, () -> this.rotationMode.getValue() == 1);
        this.saCoolingRate = new FloatProperty("sa-cooling", 0.92F, 0.80F, 0.99F, () -> this.rotationMode.getValue() == 1);
        this.saMinTemp = new FloatProperty("sa-min-temp", 0.1F, 0.0F, 2.0F, () -> this.rotationMode.getValue() == 1);
        this.saResetThreshold = new FloatProperty("sa-reset", 50.0F, 10.0F, 180.0F, () -> this.rotationMode.getValue() == 1);
        this.smoothBackProp = new BooleanProperty("smooth-back", true);
        this.smoothBackSpeed = new FloatProperty("smooth-back-speed", 0.3F, 0.05F, 1.0F);
        this.bestHitVec = new BooleanProperty("best-hit-vec", true);
        this.predictionTicks = new FloatProperty("prediction", 1.5F, 0.0F, 5.0F);
        this.smartHit = new BooleanProperty("SmartHit", false);
        this.smartAim = new BooleanProperty("SmartAim", false);
        this.noiseMode = new ModeProperty("noise-mode", 0, new String[]{"NONE", "GAUSSIAN", "PERLIN", "WAVE", "HYBRID"});
        this.noiseBaseScale = new FloatProperty("noise-base-scale", 0.5F, 0.0F, 5.0F);
        this.noiseFatigueScale = new FloatProperty("noise-fatigue-scale", 3.0F, 0.0F, 15.0F);
        this.noisePitchRatio = new FloatProperty("noise-pitch-ratio", 0.7F, 0.1F, 2.0F);
        this.noiseFrequency = new FloatProperty("noise-freq", 1.0F, 0.01F, 5.0F);
        this.noiseFatigueRate = new FloatProperty("noise-fatigue-rate", 0.005F, 0.001F, 0.05F);
        this.noiseRecoveryRate = new FloatProperty("noise-recovery-rate", 0.003F, 0.001F, 0.02F);
        this.noiseYawBias = new FloatProperty("noise-yaw-bias", 0.0F, -5.0F, 5.0F);
        this.noisePitchBias = new FloatProperty("noise-pitch-bias", 0.0F, -5.0F, 5.0F);
        this.noiseDistanceScale = new BooleanProperty("noise-distance-scale", true);
        this.noiseMicroJitter = new FloatProperty("noise-micro-jitter", 0.3F, 0.0F, 3.0F);
        this.noiseGcdQuantize = new BooleanProperty("noise-gcd-quantize", true);
        this.fatigueMode = new ModeProperty("fatigue-mode", 0, new String[]{"NONE", "LINEAR", "EXPONENTIAL", "LOGARITHMIC", "ADAPTIVE"});
        this.fatigueAccumRate = new FloatProperty("fatigue-accum-rate", 0.008F, 0.001F, 0.05F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueDecayRate = new FloatProperty("fatigue-decay-rate", 0.004F, 0.001F, 0.03F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueSmoothingMul = new FloatProperty("fatigue-smoothing-mul", 2.0F, 0.5F, 5.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueAngleReduction = new FloatProperty("fatigue-angle-reduction", 0.3F, 0.0F, 0.8F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueDriftScale = new FloatProperty("fatigue-drift-scale", 1.5F, 0.0F, 5.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueDriftFreq = new FloatProperty("fatigue-drift-freq", 0.8F, 0.1F, 3.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueOvershootScale = new FloatProperty("fatigue-overshoot-scale", 3.0F, 0.0F, 10.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueOvershootProb = new FloatProperty("fatigue-overshoot-prob", 0.3F, 0.0F, 1.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueCpsPenalty = new FloatProperty("fatigue-cps-penalty", 3.0F, 0.0F, 10.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueSwitchPenalty = new FloatProperty("fatigue-switch-penalty", 0.15F, 0.0F, 0.5F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueGcdQuantize = new BooleanProperty("fatigue-gcd-quantize", true, () -> this.fatigueMode.getValue() != 0);
        this.throughWalls = new BooleanProperty("through-walls", true);
        this.requirePress = new BooleanProperty("require-press", false);
        this.allowMining = new BooleanProperty("allow-mining", true);
        this.weaponsOnly = new BooleanProperty("weapons-only", true);
        this.allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("inventory-check", true);
        this.botCheck = new BooleanProperty("bot-check", true);
        this.players = new BooleanProperty("players", true);
        this.bosses = new BooleanProperty("bosses", false);
        this.mobs = new BooleanProperty("mobs", false);
        this.animals = new BooleanProperty("animals", false);
        this.golems = new BooleanProperty("golems", false);
        this.silverfish = new BooleanProperty("silverfish", false);
        this.teams = new BooleanProperty("teams", true);
        this.showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "DEFAULT", "HUD"});
        this.dot = new BooleanProperty("dot", true);
        this.dotColor = new ColorProperty("dot-color", -1);
        this.dotSize = new FloatProperty("dot-size", 5.0F, 1.0F, 50.0F);
    }

    public EntityLivingBase getTarget() { return this.target != null ? this.target.getEntity() : null; }

    public boolean isAttackAllowed() {
        Scaffold sc = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (sc.isEnabled()) return false;
        else if (!this.weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant() || this.allowTools.getValue() && ItemUtil.isHoldingTool())
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        else return false;
    }

    public boolean shouldAutoBlock() { return this.isPlayerBlocking() && this.isBlocking && this.autoBlock.getValue() == 1; }
    public boolean isBlocking() { return this.fakeBlockState && ItemUtil.isHoldingSword(); }
    public boolean isPlayerBlocking() { return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword(); }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (this.noiseMode.getValue() != 0) {
            this.perlinTimeAccumulator += this.noiseFrequency.getValue() * 0.05F + 0.02F * this.noiseFatigue;
        }
        if (this.fatigueMode.getValue() != 0) {
            this.fatigueDriftTime += this.fatigueDriftFreq.getValue() * 0.03F + 0.01F * this.fatigueLevel;
        }
        if (this.attackDelayMS > 0L) this.attackDelayMS -= 50L;

        this.updateNoiseFatigue(this.target != null);
        this.updateFatigue(this.target != null);

        boolean attack = this.target != null && this.canAttack();
        boolean block = attack && this.canAutoBlock();
        if (!block) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            this.isBlocking = false;
            this.fakeBlockState = false;
        }

        if (attack) {
            boolean swap = false;
            if (block) {
                switch (this.autoBlock.getValue()) {
                    case 0:
                        if (PlayerUtil.isUsingItem()) { this.isBlocking = true; if (!this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) swap = true; }
                        else { this.isBlocking = false; if (this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) this.stopBlock(); }
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.fakeBlockState = false; break;
                    case 1:
                        if (this.hasValidTarget()) { if (!this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) swap = true; Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.isBlocking = true; this.fakeBlockState = false; }
                        else { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.isBlocking = false; this.fakeBlockState = false; } break;
                    case 2:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.isBlocking = false; this.fakeBlockState = this.hasValidTarget();
                        if (PlayerUtil.isUsingItem() && !this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) swap = true; break;
                }
            }

            boolean attacked = false;
            if (this.isBoxInSwingRange(this.target.getBox())) {
                int hurtTimeThreshold = 3 + this.getPlayerPing() / 50;
                boolean isSmartHitting = this.smartHit.getValue() && mc.thePlayer.hurtTime == 0 && this.target.getEntity().hurtTime > hurtTimeThreshold;

                float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
                float fatigueMaxAngle = 180.0f;

                if (this.fatigueMode.getValue() != 0) {
                    smoothFactor = Math.min(1.0f, smoothFactor * (1.0f + this.fatigueLevel * this.fatigueSmoothingMul.getValue()));
                    fatigueMaxAngle *= (1.0f - this.fatigueLevel * this.fatigueAngleReduction.getValue());
                }

                float[] targetRotations;

                if (this.rotationMode.getValue() == 0) {
                    targetRotations = this.getTargetRotations(event.getYaw(), event.getPitch(), fatigueMaxAngle, smoothFactor);
                } else {
                    float[] rawTarget = this.getRawTarget();
                    float deltaYaw = Math.abs(MathHelper.wrapAngleTo180_float(rawTarget[0] - this.saTargetYaw));
                    float deltaPitch = Math.abs(MathHelper.wrapAngleTo180_float(rawTarget[1] - this.saTargetPitch));
                    if (!this.saActive || deltaYaw > this.saResetThreshold.getValue() || deltaPitch > this.saResetThreshold.getValue()) {
                        this.saTemperature = this.saInitTemp.getValue();
                        this.saTargetYaw = event.getYaw();
                        this.saTargetPitch = event.getPitch();
                        this.saActive = true;
                    }

                    float[] saResult = RotationUtil.simulatedAnnealingStep(this.saTargetYaw, this.saTargetPitch, rawTarget[0], rawTarget[1], this.saTemperature);
                    this.saTargetYaw = saResult[0];
                    this.saTargetPitch = saResult[1];
                    this.saTemperature = Math.max(this.saMinTemp.getValue(), this.saTemperature * this.saCoolingRate.getValue());

                    float yawDelta = MathHelper.wrapAngleTo180_float(this.saTargetYaw - event.getYaw());
                    float pitchDelta = MathHelper.wrapAngleTo180_float(this.saTargetPitch - event.getPitch());
                    yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, fatigueMaxAngle), smoothFactor);
                    pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, fatigueMaxAngle), smoothFactor);

                    targetRotations = new float[]{event.getYaw() + yawDelta, event.getPitch() + pitchDelta};
                }

                if (this.noiseMode.getValue() != 0) {
                    float[] n = this.getDynamicRecoveryNoiseOffset();
                    targetRotations[0] += n[0];
                    targetRotations[1] += n[1];
                }

                if (this.fatigueMode.getValue() != 0) {
                    float[] drift = this.getFatigueDriftOffset();
                    targetRotations[0] += drift[0];
                    targetRotations[1] += drift[1];
                    float yawDeltaToTarget = MathHelper.wrapAngleTo180_float(targetRotations[0] - event.getYaw());
                    float pitchDeltaToTarget = MathHelper.wrapAngleTo180_float(targetRotations[1] - event.getPitch());
                    float[] overshoot = this.getFatigueOvershoot(yawDeltaToTarget, pitchDeltaToTarget);
                    targetRotations[0] += overshoot[0];
                    targetRotations[1] += overshoot[1];
                }

                float finalYawDelta = MathHelper.wrapAngleTo180_float(targetRotations[0] - event.getYaw());
                float finalPitchDelta = MathHelper.wrapAngleTo180_float(targetRotations[1] - event.getPitch());
                float finalGcd = RotationUtil.getSensitivityGCD();
                if (finalGcd > 0.0f) {
                    finalYawDelta = Math.round(finalYawDelta / finalGcd) * finalGcd;
                    finalPitchDelta = Math.round(finalPitchDelta / finalGcd) * finalGcd;
                }
                targetRotations[0] = event.getYaw() + finalYawDelta;
                targetRotations[1] = event.getPitch() + finalPitchDelta;

                attackRotations = targetRotations;
                if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                    event.setRotation(targetRotations[0], targetRotations[1], 1);
                    if (this.rotations.getValue() == 3) Myau.rotationManager.setRotation(targetRotations[0], targetRotations[1], 1, true);
                    if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) event.setPervRotation(targetRotations[0], 1);
                    this.wasRotating = true;
                }
                if (attack) attacked = this.performAttack(event.getNewYaw(), event.getNewPitch(), isSmartHitting);
            } else {
                attackRotations = null;
                this.saActive = false;
                this.handleSmoothBack(event);
            }

            if (swap) { if (attacked) this.interactAttack(event.getNewYaw(), event.getNewPitch()); else this.sendUseItem(); }
        } else {
            attackRotations = null;
            this.saActive = false;
            this.handleSmoothBack(event);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;
        switch (event.getType()) {
            case PRE:
                if (this.target == null || !this.isValidTarget(this.target.getEntity()) || !this.isBoxInAttackRange(this.target.getBox()) || !this.isBoxInSwingRange(this.target.getBox()) || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                    this.timer.reset();
                    ArrayList<EntityLivingBase> targets = new ArrayList<>();
                    for (Entity en : mc.theWorld.loadedEntityList) if (en instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase) en) && this.isInRange((EntityLivingBase) en)) targets.add((EntityLivingBase) en);
                    if (targets.isEmpty()) { this.target = null; }
                    else {
                        if (targets.stream().anyMatch(this::isInSwingRange)) targets.removeIf(e -> !this.isInSwingRange(e));
                        if (targets.stream().anyMatch(this::isInAttackRange)) targets.removeIf(e -> !this.isInAttackRange(e));
                        if (targets.stream().anyMatch(this::isPlayerTarget)) targets.removeIf(e -> !this.isPlayerTarget(e));
                        targets.sort((e1, e2) -> { int s = 0; switch (this.sort.getValue()) { case 1: s = Float.compare(TeamUtil.getHealthScore(e1), TeamUtil.getHealthScore(e2)); break; case 2: s = Integer.compare(e1.hurtResistantTime, e2.hurtResistantTime); break; case 3: s = Float.compare(RotationUtil.angleToEntity(e1), RotationUtil.angleToEntity(e2)); break; } return s != 0 ? s : Double.compare(RotationUtil.distanceToEntity(e1), RotationUtil.distanceToEntity(e2)); });
                        if (this.mode.getValue() == 1 && this.hitRegistered) { this.hitRegistered = false; this.switchTick++; }
                        if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) this.switchTick = 0;
                        this.target = new AttackData(targets.get(this.switchTick));
                    }
                }
                if (this.target != null) {
                    int currentTargetId = this.target.getEntity().getEntityId();
                    if (currentTargetId != this.lastTargetEntityId) {
                        this.noiseFatigue *= 0.6f;
                        if (this.fatigueMode.getValue() != 0) {
                            this.fatigueLevel = Math.min(1.0f, this.fatigueLevel + this.fatigueSwitchPenalty.getValue());
                        }
                        this.lastTargetEntityId = currentTargetId;
                    }
                    this.target = new AttackData(this.target.getEntity());
                } else {
                    this.lastTargetEntityId = -1;
                }
                break;
            case POST:
                if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                break;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || !event.isCancelled() && mc.thePlayer != null && mc.theWorld != null) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) { if (((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) this.blockingState = false; }
            if (event.getPacket() instanceof C09PacketHeldItemChange) { this.blockingState = false; if (this.isBlocking) mc.thePlayer.stopUsingItem(); }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1 && this.rotations.getValue() != 3 && RotationState.isActived() && RotationState.getPriority() == 1.0F && MoveUtil.isForwardPressed()) MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            if (this.shouldAutoBlock()) mc.thePlayer.movementInput.jump = false;
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || target == null) return;
        if (this.showTarget.getValue() != 0 && TeamUtil.isEntityLoaded(this.target.getEntity()) && this.isAttackAllowed()) {
            Color c = new Color(-1);
            switch (this.showTarget.getValue()) { case 1: c = this.target.getEntity().hurtTime > 0 ? new Color(16733525) : new Color(5635925); break; case 2: c = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()); break; }
            RenderUtil.enableRenderState(); RenderUtil.drawEntityBox(this.target.getEntity(), c.getRed(), c.getGreen(), c.getBlue()); RenderUtil.disableRenderState();
        }
        if (this.dot.getValue() && attackRotations != null && TeamUtil.isEntityLoaded(this.target.getEntity()) && this.isAttackAllowed()) {
            float pt = event.getPartialTicks(); Vec3 ep = mc.thePlayer.getPositionEyes(pt); Vec3 lv = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(attackRotations[1], attackRotations[0]);
            Vec3 end = ep.addVector(lv.xCoord * swingRange.getValue(), lv.yCoord * swingRange.getValue(), lv.zCoord * swingRange.getValue());
            MovingObjectPosition mop = this.target.getBox().calculateIntercept(ep, end); Vec3 rp = mop != null && mop.hitVec != null ? mop.hitVec : end;
            double dist = ep.distanceTo(rp); if (dist < 0.1) dist = 0.1; float as = dotSize.getValue() / 100.0f * (float) Math.sqrt(dist);
            Color dc = new Color(dotColor.getValue(), true); double xp = rp.xCoord - mc.getRenderManager().viewerPosX, yp = rp.yCoord - mc.getRenderManager().viewerPosY, zp = rp.zCoord - mc.getRenderManager().viewerPosZ;
            GL11.glPushMatrix(); GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false); GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(dc.getRed() / 255.0f, dc.getGreen() / 255.0f, dc.getBlue() / 255.0f, dc.getAlpha() / 255.0f);
            drawCube(xp, yp, zp, as); GL11.glPopAttrib(); GL11.glPopMatrix();
        }
    }

    private void drawCube(double x, double y, double z, float s) { float h = s / 2.0f; GL11.glBegin(GL11.GL_QUADS); GL11.glVertex3d(x-h,y+h,z-h); GL11.glVertex3d(x+h,y+h,z-h); GL11.glVertex3d(x+h,y+h,z+h); GL11.glVertex3d(x-h,y+h,z+h); GL11.glVertex3d(x-h,y-h,z+h); GL11.glVertex3d(x+h,y-h,z+h); GL11.glVertex3d(x+h,y-h,z-h); GL11.glVertex3d(x-h,y-h,z-h); GL11.glVertex3d(x-h,y-h,z-h); GL11.glVertex3d(x+h,y-h,z-h); GL11.glVertex3d(x+h,y+h,z-h); GL11.glVertex3d(x-h,y+h,z-h); GL11.glVertex3d(x+h,y-h,z+h); GL11.glVertex3d(x-h,y-h,z+h); GL11.glVertex3d(x-h,y+h,z+h); GL11.glVertex3d(x+h,y+h,z+h); GL11.glVertex3d(x-h,y-h,z+h); GL11.glVertex3d(x-h,y-h,z-h); GL11.glVertex3d(x-h,y+h,z-h); GL11.glVertex3d(x-h,y+h,z+h); GL11.glVertex3d(x+h,y-h,z-h); GL11.glVertex3d(x+h,y-h,z+h); GL11.glVertex3d(x+h,y+h,z+h); GL11.glVertex3d(x+h,y+h,z-h); GL11.glEnd(); }

    @EventTarget public void onLeftClick(LeftClickMouseEvent e) { if (this.isBlocking) e.setCancelled(true); else if (this.isEnabled() && this.target != null && this.canAttack()) e.setCancelled(true); }
    @EventTarget public void onRightClick(RightClickMouseEvent e) { if (this.isBlocking) e.setCancelled(true); else if (this.isEnabled() && this.target != null && this.canAttack()) e.setCancelled(true); }
    @EventTarget public void onHitBlock(HitBlockEvent e) { if (this.isBlocking) e.setCancelled(true); else if (this.isEnabled() && this.target != null && this.canAttack()) e.setCancelled(true); }
    @EventTarget public void onCancelUse(CancelUseEvent e) { if (this.isBlocking) e.setCancelled(true); }

    @Override
    public void onEnabled() { this.target = null; this.switchTick = 0; this.hitRegistered = false; this.attackDelayMS = 0L; attackRotations = null; this.perlinTimeAccumulator = 0.0F; this.noiseFatigue = 0.0f; this.lastTargetEntityId = -1; this.fatigueLevel = 0.0f; this.fatigueDriftTime = 0.0f; this.saTemperature = 0.0f; this.saActive = false; this.saTargetYaw = 0.0f; this.saTargetPitch = 0.0f; this.wasRotating = false; this.isReturning = false; }

    @Override
    public void onDisabled() { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.blockingState = false; this.isBlocking = false; this.fakeBlockState = false; attackRotations = null; this.perlinTimeAccumulator = 0.0F; this.noiseFatigue = 0.0f; this.lastTargetEntityId = -1; this.fatigueLevel = 0.0f; this.fatigueDriftTime = 0.0f; this.saTemperature = 0.0f; this.saActive = false; this.saTargetYaw = 0.0f; this.saTargetPitch = 0.0f; this.wasRotating = false; this.isReturning = false; }

    @Override
    public void verifyValue(String v) {
        if (this.swingRange.getName().equals(v)) { if (this.swingRange.getValue() < this.attackRange.getValue()) this.attackRange.setValue(this.swingRange.getValue()); }
        else if (this.attackRange.getName().equals(v)) { if (this.swingRange.getValue() < this.attackRange.getValue()) this.swingRange.setValue(this.attackRange.getValue()); }
        else if (this.minCPS.getName().equals(v)) { if (this.minCPS.getValue() > this.maxCPS.getValue()) this.maxCPS.setValue(this.minCPS.getValue()); }
    }
}