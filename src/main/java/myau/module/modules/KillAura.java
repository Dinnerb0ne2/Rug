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
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
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
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.potion.Potion;
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
    private float saPointX = 0.0f;
    private float saPointY = 0.0f;
    private float saPointZ = 0.0f;
    private int saLastTargetId = -1;

    private boolean wasRotating = false;
    private boolean isReturning = false;
    private float returnYaw = 0.0f;
    private float returnPitch = 0.0f;

    private float perlinTimeAccumulator = 0.0F;
    private float noiseFatigue = 0.0f;
    private int lastTargetEntityId = -1;

    private float fatigueLevel = 0.0f;

    private int lastTargetHurtTime = 0;

    private float lastSentYaw = 0.0f;
    private float lastSentPitch = 0.0f;
    private boolean lastSentInitialized = false;

    private float brownianYawState = 0.0f;
    private float brownianPitchState = 0.0f;

    private final RotationUtil.BezierRotator bezierRotator = new RotationUtil.BezierRotator();

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
    public final IntProperty saIterations;

    public final BooleanProperty smoothBackProp;
    public final FloatProperty smoothBackSpeed;

    public final BooleanProperty bestHitVec;
    public final BooleanProperty perfectHit;
    public final BooleanProperty perfectHitGomme;
    public final BooleanProperty smartAim;

    public final BooleanProperty brownianMotion;
    public final FloatProperty brownianIntensity;

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
    public final BooleanProperty noiseClampToBox;

    public final ModeProperty fatigueMode;
    public final FloatProperty fatigueAccumRate;
    public final FloatProperty fatigueDecayRate;
    public final FloatProperty fatigueSmoothingMul;
    public final FloatProperty fatigueAngleReduction;
    public final FloatProperty fatigueOvershootScale;
    public final FloatProperty fatigueOvershootProb;
    public final FloatProperty fatigueCpsPenalty;
    public final FloatProperty fatigueSwitchPenalty;

    public final FloatProperty cpsHurtDownScale;
    public final FloatProperty cpsHurtUpScale;

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
        if (this.target != null) {
            EntityLivingBase targetEntity = this.target.getEntity();
            int hurtResistantTime = targetEntity.hurtResistantTime;
            int maxHurtResistantTime = 20;
            if (hurtResistantTime > 0) {
                float hurtRatio = (float) hurtResistantTime / (float) maxHurtResistantTime;
                float downScale = this.cpsHurtDownScale.getValue();
                int reduction = (int) (hurtRatio * downScale * effectiveMaxCPS);
                effectiveMinCPS = Math.max(1, effectiveMinCPS - reduction);
                effectiveMaxCPS = Math.max(effectiveMinCPS, effectiveMaxCPS - reduction);
            } else {
                float upScale = this.cpsHurtUpScale.getValue();
                float fatigueMul = 1.0f - this.fatigueLevel * 0.5f;
                effectiveMinCPS = Math.min(20, (int) (effectiveMinCPS * (1.0f + upScale * fatigueMul)));
                effectiveMaxCPS = Math.min(20, (int) (effectiveMaxCPS * (1.0f + upScale * fatigueMul)));
            }
        }
        if (effectiveMaxCPS < effectiveMinCPS) effectiveMaxCPS = effectiveMinCPS;
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
                float nY = RotationUtil.getPerlinNoise(time * 0.3f, 500.0f) * 0.5f + RotationUtil.getPerlinNoise(time * 0.1f, 600.0f) * 0.5f;
                float nP = RotationUtil.getPerlinNoise(time * 0.3f, 700.0f) * 0.5f + RotationUtil.getPerlinNoise(time * 0.1f, 800.0f) * 0.5f;
                float sY = (float) Math.cos(time * freq * 2.0f) * 0.5f;
                float sP = (float) Math.sin(time * freq * 2.0f) * 0.5f;
                float bY = this.brownianYawState;
                float bP = this.brownianPitchState;
                yawOffset = (pY * 0.18f + wY * 0.12f + gY * 0.15f + nY * 0.2f + sY * 0.12f + bY * 0.23f) * effectiveScale * fatigueFactor;
                pitchOffset = (pP * 0.18f + wP * 0.12f + gP * 0.15f + nP * 0.2f + sP * 0.12f + bP * 0.23f) * effectiveScale * pRatio * fatigueFactor;
                break;
            case 5:
                float pinkY = RotationUtil.getPerlinNoise(time, 0.0f) * 0.5f + RotationUtil.getPerlinNoise(time * 0.5f, 50.0f) * 0.3f + RotationUtil.getPerlinNoise(time * 0.2f, 100.0f) * 0.2f;
                float pinkP = RotationUtil.getPerlinNoise(time, 200.0f) * 0.5f + RotationUtil.getPerlinNoise(time * 0.5f, 250.0f) * 0.3f + RotationUtil.getPerlinNoise(time * 0.2f, 300.0f) * 0.2f;
                float spikeY = Math.random() < 0.05f ? (float) (Math.random() - 0.5) * 4.0f : 0.0f;
                float spikeP = Math.random() < 0.05f ? (float) (Math.random() - 0.5) * 4.0f : 0.0f;
                yawOffset = (pinkY + spikeY) * effectiveScale * fatigueFactor;
                pitchOffset = (pinkP + spikeP) * effectiveScale * pRatio * fatigueFactor;
                break;
            case 6:
                this.brownianYawState += (Math.random() - 0.5) * effectiveScale * 0.3f * fatigueFactor;
                this.brownianPitchState += (Math.random() - 0.5) * effectiveScale * 0.3f * pRatio * fatigueFactor;
                this.brownianYawState *= 0.92f;
                this.brownianPitchState *= 0.92f;
                float maxB = effectiveScale * 2.5f;
                this.brownianYawState = Math.max(-maxB, Math.min(maxB, this.brownianYawState));
                this.brownianPitchState = Math.max(-maxB, Math.min(maxB, this.brownianPitchState));
                yawOffset = this.brownianYawState;
                pitchOffset = this.brownianPitchState;
                break;
            case 7:
                float spiralRadius = effectiveScale * fatigueFactor * (0.5f + 0.5f * RotationUtil.getPerlinNoise(time * 0.3f, 400.0f));
                float spiralAngle = time * freq * 2.0f;
                float spiralPitchRad = time * freq * 1.5f;
                yawOffset = (float) Math.cos(spiralAngle) * spiralRadius;
                pitchOffset = (float) Math.sin(spiralPitchRad) * spiralRadius * pRatio;
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
                            rawTarget = RotationUtil.getRawTargetEntity(this.target.getEntity());
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
            this.returnYaw = this.lastSentInitialized ? this.lastSentYaw : event.getYaw();
            this.returnPitch = this.lastSentInitialized ? this.lastSentPitch : event.getPitch();
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
        
        float gcd = RotationUtil.getSensitivityGCD();
        if (gcd > 0.0f) {
            this.returnYaw = Math.round(this.returnYaw / gcd) * gcd;
            this.returnPitch = Math.round(this.returnPitch / gcd) * gcd;
        }

        float yD = Math.abs(MathHelper.wrapAngleTo180_float(playerYaw - this.returnYaw));
        float pD = Math.abs(MathHelper.wrapAngleTo180_float(playerPitch - this.returnPitch));
        if (yD > 0.5f || pD > 0.5f) {
            event.setRotation(this.returnYaw, this.returnPitch, 1);
            if (this.rotations.getValue() == 3) Myau.rotationManager.setRotation(this.returnYaw, this.returnPitch, 1, true);
            if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) event.setPervRotation(this.returnYaw, 1);
            this.lastSentYaw = this.returnYaw;
            this.lastSentPitch = this.returnPitch;
            this.lastSentInitialized = true;
        } else {
            float snapDeltaY = MathHelper.wrapAngleTo180_float(playerYaw - this.returnYaw);
            float snapDeltaP = MathHelper.wrapAngleTo180_float(playerPitch - this.returnPitch);
            float finalSnapYaw = this.returnYaw + snapDeltaY;
            float finalSnapPitch = this.returnPitch + snapDeltaP;
            if (gcd > 0.0f) {
                finalSnapYaw = Math.round(finalSnapYaw / gcd) * gcd;
                finalSnapPitch = Math.round(finalSnapPitch / gcd) * gcd;
            }
            event.setRotation(finalSnapYaw, finalSnapPitch, 1);
            if (this.rotations.getValue() == 3) Myau.rotationManager.setRotation(finalSnapYaw, finalSnapPitch, 1, true);
            if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) event.setPervRotation(finalSnapYaw, 1);
            this.lastSentYaw = finalSnapYaw;
            this.lastSentPitch = finalSnapPitch;
            this.lastSentInitialized = true;
            this.isReturning = false;
            this.wasRotating = false;
        }
    }

    private float[] getTargetRotations(float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        AxisAlignedBB box = this.target.getBox();
        EntityLivingBase entity = this.target.getEntity();
        double distance = RotationUtil.distanceToBox(box);

        if (this.rotationMode.getValue() == 2) {
            float[] rawTarget;
            if (this.smartAim.getValue()) {
                rawTarget = RotationUtil.getRotationsToSmartVec(box, currentYaw, currentPitch, 180.0f, 0.0f);
            } else if (this.bestHitVec.getValue()) {
                rawTarget = RotationUtil.getRotationsToBoxStable(box, currentYaw, currentPitch, 180.0f, 0.0f, distance);
            } else {
                rawTarget = RotationUtil.getRotationsToEntity(entity, currentYaw, currentPitch, 180.0f, 0.0f);
            }
            float bezierSpeed = 0.05f + (1.0f - (float) this.smoothing.getValue() / 100.0F) * 0.3f;
            if (bezierRotator.isFinished() || bezierRotator.needsUpdate(rawTarget[0], rawTarget[1], 15.0f)) {
                bezierRotator.setup(currentYaw, currentPitch, rawTarget[0], rawTarget[1], bezierSpeed);
            }
            return bezierRotator.getNextRotation();
        }

        if (this.smartAim.getValue()) {
            return RotationUtil.getRotationsToSmartVec(box, currentYaw, currentPitch, maxAngle, smoothFactor);
        } else if (this.bestHitVec.getValue()) {
            return RotationUtil.getRotationsToBoxStable(box, currentYaw, currentPitch, maxAngle, smoothFactor, distance);
        } else {
            return RotationUtil.getRotationsToEntity(entity, currentYaw, currentPitch, maxAngle, smoothFactor);
        }
    }

    private boolean performAttack(float yaw, float pitch, boolean isPerfectHitting) {
        if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) return false;
            else if (this.attackDelayMS > 0L) return false;
            else {
                if (isPerfectHitting) {
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
                    
                    if (Particles.shouldOverrideParticles()) {
                        boolean isCrit = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.thePlayer.ridingEntity == null;
                        boolean isSharp = false;
                        if (mc.thePlayer.getHeldItem() != null) {
                            int sharp = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, mc.thePlayer.getHeldItem());
                            int smite = EnchantmentHelper.getEnchantmentLevel(Enchantment.smite.effectId, mc.thePlayer.getHeldItem());
                            int bane = EnchantmentHelper.getEnchantmentLevel(Enchantment.baneOfArthropods.effectId, mc.thePlayer.getHeldItem());
                            isSharp = sharp > 0 || smite > 0 || bane > 0;
                        }
                        if (Particles.alwaysCriticals()) isCrit = true;
                        if (Particles.alwaysSharpness()) isSharp = true;
                        int critMult = Particles.getCriticalsMultiplier(isCrit);
                        int sharpMult = Particles.getSharpnessMultiplier(isSharp);
                        for (int i = 0; i < critMult; i++) {
                            mc.thePlayer.onCriticalHit(this.target.getEntity());
                        }
                        for (int i = 0; i < sharpMult; i++) {
                            mc.thePlayer.onEnchantmentCritical(this.target.getEntity());
                        }
                    }

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
        this.mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
        this.sort = new ModeProperty("sort", 0, new String[]{"Distance", "Health", "Hurt_Time", "Fov"});
        this.autoBlock = new ModeProperty("Auto-Block", 0, new String[]{"None", "Vanilla", "Fake"});
        this.swingRange = new FloatProperty("Swing-Range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("Attack-Range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("Fov", 360, 30, 360);
        this.minCPS = new IntProperty("Min-aps", 14, 1, 20);
        this.maxCPS = new IntProperty("Max-aps", 14, 1, 20);
        this.cpsHurtDownScale = new FloatProperty("Cps-Hurt-Down", 0.5F, 0.0F, 1.0F);
        this.cpsHurtUpScale = new FloatProperty("Cps-Hurt-Up", 0.3F, 0.0F, 1.0F);
        this.switchDelay = new IntProperty("Switch-Delay", 150, 0, 1000);
        this.rotations = new ModeProperty("Rotations", 2, new String[]{"None", "Legit", "Slient", "Lock_View"});
        this.moveFix = new ModeProperty("Move-Fix", 1, new String[]{"None", "Slient", "Strict"});
        this.smoothing = new PercentProperty("Smoothing", 0);
        this.rotationMode = new ModeProperty("Rotation-Mode", 0, new String[]{"Basic", "SA", "Bezier"});
        this.saInitTemp = new FloatProperty("SA-Init-Temp", 3.0F, 0.5F, 20.0F, () -> this.rotationMode.getValue() == 1);
        this.saCoolingRate = new FloatProperty("SA-Cooling", 0.95F, 0.80F, 0.99F, () -> this.rotationMode.getValue() == 1);
        this.saMinTemp = new FloatProperty("SA-Min-Temp", 0.8F, 0.0F, 5.0F, () -> this.rotationMode.getValue() == 1);
        this.saIterations = new IntProperty("Sa-Iterations", 20, 5, 50, () -> this.rotationMode.getValue() == 1);
        this.smoothBackProp = new BooleanProperty("Smooth-Back", true);
        this.smoothBackSpeed = new FloatProperty("Smooth-Back-Speed", 0.3F, 0.05F, 1.0F);
        this.bestHitVec = new BooleanProperty("Best-Hit-Vec", true);
        this.perfectHit = new BooleanProperty("PerfectHit", false);
        this.perfectHitGomme = new BooleanProperty("PerfectHit-Gomme", false);
        this.smartAim = new BooleanProperty("SmartAim", false);
        this.brownianMotion = new BooleanProperty("Brownian-Motion", true);
        this.brownianIntensity = new FloatProperty("Brownian-Intensity", 0.5F, 0.0F, 5.0F);
        this.noiseMode = new ModeProperty("noise-mode", 0, new String[]{"None", "Gaussian", "Perlin", "Wave", "Hybrid", "Neural", "Brownlan", "Spiral"});
        this.noiseBaseScale = new FloatProperty("Noise-Base-Scale", 0.5F, 0.0F, 5.0F, () -> this.noiseMode.getValue() != 0);
        this.noiseFatigueScale = new FloatProperty("Noise-Fatigue-Scale", 3.0F, 0.0F, 15.0F, () -> this.noiseMode.getValue() != 0);
        this.noisePitchRatio = new FloatProperty("Noise-Pitch-Ratio", 0.7F, 0.1F, 2.0F, () -> this.noiseMode.getValue() != 0);
        this.noiseFrequency = new FloatProperty("Noise-Freq", 1.0F, 0.01F, 5.0F, () -> this.noiseMode.getValue() != 0);
        this.noiseFatigueRate = new FloatProperty("Noise-Fatigue-Rate", 0.005F, 0.001F, 0.05F, () -> this.noiseMode.getValue() != 0);
        this.noiseRecoveryRate = new FloatProperty("Noise-Recovery-Rate", 0.003F, 0.001F, 0.02F, () -> this.noiseMode.getValue() != 0);
        this.noiseYawBias = new FloatProperty("Noise-Yaw-Bias", 0.0F, -5.0F, 5.0F, () -> this.noiseMode.getValue() != 0);
        this.noisePitchBias = new FloatProperty("Noise-Pitch-Bias", 0.0F, -5.0F, 5.0F, () -> this.noiseMode.getValue() != 0);
        this.noiseDistanceScale = new BooleanProperty("Noise-Distance-Scale", true, () -> this.noiseMode.getValue() != 0);
        this.noiseMicroJitter = new FloatProperty("Noise-Micro-Jitter", 0.3F, 0.0F, 3.0F, () -> this.noiseMode.getValue() != 0);
        this.noiseGcdQuantize = new BooleanProperty("Noise-GCD-Quantize", true, () -> this.noiseMode.getValue() != 0);
        this.noiseClampToBox = new BooleanProperty("Noise-Clamp-To-Box", true, () -> this.noiseMode.getValue() != 0);
        this.fatigueMode = new ModeProperty("Fatigue-Mode", 0, new String[]{"None", "Linear", "Exponential", "Logarithmic", "Adaptive"});
        this.fatigueAccumRate = new FloatProperty("Fatigue-Accum-Rate", 0.008F, 0.001F, 0.05F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueDecayRate = new FloatProperty("Fatigue-Decay-Rate", 0.004F, 0.001F, 0.03F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueSmoothingMul = new FloatProperty("Fatigue-Smoothing-Mul", 2.0F, 0.5F, 5.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueAngleReduction = new FloatProperty("Fatigue-Angle-Reduction", 0.3F, 0.0F, 0.8F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueOvershootScale = new FloatProperty("Fatigue-Overshoot-Scale", 3.0F, 0.0F, 10.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueOvershootProb = new FloatProperty("Fatigue-Overshoot-Prob", 0.3F, 0.0F, 1.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueCpsPenalty = new FloatProperty("Fatigue-Cps-Penalty", 3.0F, 0.0F, 10.0F, () -> this.fatigueMode.getValue() != 0);
        this.fatigueSwitchPenalty = new FloatProperty("Fatigue-Switch-Penalty", 0.15F, 0.0F, 0.5F, () -> this.fatigueMode.getValue() != 0);
        this.throughWalls = new BooleanProperty("Through-Walls", true);
        this.requirePress = new BooleanProperty("Require-Press", false);
        this.allowMining = new BooleanProperty("Allow-Mining", true);
        this.weaponsOnly = new BooleanProperty("Weapons-only", true);
        this.allowTools = new BooleanProperty("Allow-Tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("Inventory-Check", true);
        this.botCheck = new BooleanProperty("Bot-Check", true);
        this.players = new BooleanProperty("Players", true);
        this.bosses = new BooleanProperty("Bosses", false);
        this.mobs = new BooleanProperty("Mobs", false);
        this.animals = new BooleanProperty("Animals", false);
        this.golems = new BooleanProperty("Golems", false);
        this.silverfish = new BooleanProperty("Silverfish", false);
        this.teams = new BooleanProperty("Teams", true);
        this.showTarget = new ModeProperty("Show-Target", 0, new String[]{"None", "Default", "Hud"});
        this.dot = new BooleanProperty("Dot", true);
        this.dotColor = new ColorProperty("Dot-Color", 0xFF0670BE);
        this.dotSize = new FloatProperty("Dot-Size", 5.0F, 1.0F, 50.0F);
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
                boolean isPerfectHitting = false;
                if (this.perfectHit.getValue()) {
                    EntityLivingBase targetEntity = this.target.getEntity();
                    isPerfectHitting = targetEntity.hurtTime > 1;
                    if (this.perfectHitGomme.getValue() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
                        EntityLivingBase entity = (EntityLivingBase) mc.objectMouseOver.entityHit;
                        if (entity.hurtTime == 0 || entity.hurtTime == 1) {
                            isPerfectHitting = false;
                        }
                    }
                }

                float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
                float fatigueMaxAngle = 180.0f;

                if (this.fatigueMode.getValue() != 0) {
                    smoothFactor = Math.min(1.0f, smoothFactor * (1.0f + this.fatigueLevel * this.fatigueSmoothingMul.getValue()));
                    fatigueMaxAngle *= (1.0f - this.fatigueLevel * this.fatigueAngleReduction.getValue());
                }

                boolean useSilentBase = (this.rotations.getValue() == 2 || this.rotations.getValue() == 3);
                float refYaw = useSilentBase ? (this.lastSentInitialized ? this.lastSentYaw : event.getYaw()) : event.getYaw();
                float refPitch = useSilentBase ? (this.lastSentInitialized ? this.lastSentPitch : event.getPitch()) : event.getPitch();

                float[] targetRotations;

                if (this.rotationMode.getValue() == 0 || this.rotationMode.getValue() == 2) {
                    targetRotations = this.getTargetRotations(refYaw, refPitch, fatigueMaxAngle, smoothFactor);
                } else {
                    AxisAlignedBB box = this.target.getBox();
                    int currentTargetId = this.target.getEntity().getEntityId();
                    boolean targetChanged = currentTargetId != this.saLastTargetId;

                    if (!this.saActive || targetChanged) {
                        this.saTemperature = this.saInitTemp.getValue();
                        this.saPointX = (float) ((box.minX + box.maxX) / 2.0);
                        this.saPointY = (float) ((box.minY + box.maxY) / 2.0);
                        this.saPointZ = (float) ((box.minZ + box.maxZ) / 2.0);
                        this.saActive = true;
                        this.saLastTargetId = currentTargetId;
                    }

                    boolean checkWalls = !this.throughWalls.getValue();

                    float[] saResult = RotationUtil.simulatedAnnealingBoxStep(
                            box, refYaw, refPitch,
                            this.saPointX, this.saPointY, this.saPointZ,
                            this.saTemperature, this.saIterations.getValue(),
                            checkWalls);

                    this.saPointX = saResult[2];
                    this.saPointY = saResult[3];
                    this.saPointZ = saResult[4];
                    this.saTemperature = Math.max(this.saMinTemp.getValue(), this.saTemperature * this.saCoolingRate.getValue());

                    float yawDelta = MathHelper.wrapAngleTo180_float(saResult[0] - refYaw);
                    float pitchDelta = MathHelper.wrapAngleTo180_float(saResult[1] - refPitch);
                    yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, fatigueMaxAngle), smoothFactor);
                    pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, fatigueMaxAngle), smoothFactor);

                    targetRotations = new float[]{refYaw + yawDelta, refPitch + pitchDelta};
                }

                if (this.noiseMode.getValue() != 0) {
                    float[] n = this.getDynamicRecoveryNoiseOffset();
                    float noisedYaw = targetRotations[0] + n[0];
                    float noisedPitch = targetRotations[1] + n[1];
                    if (this.noiseClampToBox.getValue()) {
                        MovingObjectPosition noisedRay = RotationUtil.rayTrace(this.target.getBox(), noisedYaw, noisedPitch, this.attackRange.getValue());
                        if (noisedRay != null) {
                            targetRotations[0] = noisedYaw;
                            targetRotations[1] = noisedPitch;
                        } else {
                            boolean foundValid = false;
                            for (float scale = 0.75f; scale > 0.05f; scale -= 0.25f) {
                                float scaledYaw = targetRotations[0] + n[0] * scale;
                                float scaledPitch = targetRotations[1] + n[1] * scale;
                                MovingObjectPosition scaledRay = RotationUtil.rayTrace(this.target.getBox(), scaledYaw, scaledPitch, this.attackRange.getValue());
                                if (scaledRay != null) {
                                    targetRotations[0] = scaledYaw;
                                    targetRotations[1] = scaledPitch;
                                    foundValid = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        targetRotations[0] = noisedYaw;
                        targetRotations[1] = noisedPitch;
                    }
                }

                if (this.fatigueMode.getValue() != 0) {
                    float yawDeltaToTarget = MathHelper.wrapAngleTo180_float(targetRotations[0] - refYaw);
                    float pitchDeltaToTarget = MathHelper.wrapAngleTo180_float(targetRotations[1] - refPitch);
                    float[] overshoot = this.getFatigueOvershoot(yawDeltaToTarget, pitchDeltaToTarget);
                    targetRotations[0] += overshoot[0];
                    targetRotations[1] += overshoot[1];
                }

                if (this.brownianMotion.getValue()) {
                    targetRotations = RotationUtil.applyBrownianMotion(targetRotations[0], targetRotations[1], this.brownianIntensity.getValue(), this.target.getBox(), this.attackRange.getValue());
                }

                float finalGcd = RotationUtil.getSensitivityGCD();
                if (finalGcd > 0.0f) {
                    float yDelta = MathHelper.wrapAngleTo180_float(targetRotations[0] - refYaw);
                    float pDelta = MathHelper.wrapAngleTo180_float(targetRotations[1] - refPitch);
                    yDelta = Math.round(yDelta / finalGcd) * finalGcd;
                    pDelta = Math.round(pDelta / finalGcd) * finalGcd;
                    targetRotations[0] = refYaw + yDelta;
                    targetRotations[1] = refPitch + pDelta;
                }

                attackRotations = targetRotations;
                if (this.rotations.getValue() == 1 || this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                    event.setRotation(targetRotations[0], targetRotations[1], 1);
                    if (this.rotations.getValue() == 3) Myau.rotationManager.setRotation(targetRotations[0], targetRotations[1], 1, true);
                    if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) event.setPervRotation(targetRotations[0], 1);
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) this.wasRotating = true;
                    this.lastSentYaw = targetRotations[0];
                    this.lastSentPitch = targetRotations[1];
                } else {
                    this.lastSentYaw = event.getYaw();
                    this.lastSentPitch = event.getPitch();
                }
                this.lastSentInitialized = true;
                if (attack) attacked = this.performAttack(event.getNewYaw(), event.getNewPitch(), isPerfectHitting);
            } else {
                attackRotations = null;
                this.saActive = false;
                if (!this.isReturning) {
                    this.lastSentYaw = event.getYaw();
                    this.lastSentPitch = event.getPitch();
                    this.lastSentInitialized = true;
                }
                this.handleSmoothBack(event);
            }

            if (swap) { if (attacked) this.interactAttack(event.getNewYaw(), event.getNewPitch()); else this.sendUseItem(); }
        } else {
            attackRotations = null;
            this.saActive = false;
            if (!this.isReturning) {
                this.lastSentYaw = event.getYaw();
                this.lastSentPitch = event.getPitch();
                this.lastSentInitialized = true;
            }
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
                        this.brownianYawState = 0.0f;
                        this.brownianPitchState = 0.0f;
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
    public void onEnabled() { this.target = null; this.switchTick = 0; this.hitRegistered = false; this.attackDelayMS = 0L; attackRotations = null; this.perlinTimeAccumulator = 0.0F; this.noiseFatigue = 0.0f; this.lastTargetEntityId = -1; this.fatigueLevel = 0.0f; this.saTemperature = 0.0f; this.saActive = false; this.saPointX = 0.0f; this.saPointY = 0.0f; this.saPointZ = 0.0f; this.saLastTargetId = -1; this.wasRotating = false; this.isReturning = false; this.lastTargetHurtTime = 0; this.lastSentYaw = 0.0f; this.lastSentPitch = 0.0f; this.lastSentInitialized = false; this.brownianYawState = 0.0f; this.brownianPitchState = 0.0f; }

    @Override
    public void onDisabled() { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.blockingState = false; this.isBlocking = false; this.fakeBlockState = false; attackRotations = null; this.perlinTimeAccumulator = 0.0F; this.noiseFatigue = 0.0f; this.lastTargetEntityId = -1; this.fatigueLevel = 0.0f; this.saTemperature = 0.0f; this.saActive = false; this.saPointX = 0.0f; this.saPointY = 0.0f; this.saPointZ = 0.0f; this.saLastTargetId = -1; this.wasRotating = false; this.isReturning = false; this.lastTargetHurtTime = 0; this.lastSentYaw = 0.0f; this.lastSentPitch = 0.0f; this.lastSentInitialized = false; this.brownianYawState = 0.0f; this.brownianPitchState = 0.0f; }

    @Override
    public void verifyValue(String v) {
        if (this.swingRange.getName().equals(v)) { if (this.swingRange.getValue() < this.attackRange.getValue()) this.swingRange.setValue(this.attackRange.getValue()); }
    }

    private int getSwingRangeTargetCount() {
        int count = 0;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (e instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase) e) && this.isInSwingRange((EntityLivingBase) e)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.getSwingRangeTargetCount())};
    }
}