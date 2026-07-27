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
import myau.ui.animation.Animation;
import myau.ui.animation.Easing;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
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
    private float saAcceptRate = 0.0f;

    private boolean wasRotating = false;
    private boolean isReturning = false;
    private float returnYaw = 0.0f;
    private float returnPitch = 0.0f;

    private int lastTargetEntityId = -1;

    private float lastSentYaw = 0.0f;
    private float lastSentPitch = 0.0f;
    private boolean lastSentInitialized = false;

    private long lastDebugTime = 0L;

    private final RotationUtil.BezierRotator bezierRotator = new RotationUtil.BezierRotator();
    private final RotationUtil.MLRotator mlRotator = new RotationUtil.MLRotator();
    private final RotationUtil.NoiseRecoverySystem recoverySystem = new RotationUtil.NoiseRecoverySystem();
    private final RotationUtil.BrownianState brownianState = new RotationUtil.BrownianState(8);

    private Animation dotScaleAnim = new Animation(Easing.EaseOutCubic, 300);
    private int currentDotAnimSpeed = 300;
    private int currentDotEasingIndex = 8;

    private float overshootYawOffset = 0.0f;
    private float overshootPitchOffset = 0.0f;
    private float prevRotationDeltaYaw = 180.0f;
    private float prevRotationDeltaPitch = 180.0f;

    private int smartCpsValue = 8;
    private boolean shouldAttack = true;
    private boolean shouldCrit = true;
    private int hitSelectTickCounter = 10;
    private boolean shouldFirstlyHit_1 = true;
    private boolean shouldFirstlyHit_2 = true;

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
    public final ModeProperty saPerturbationMode;
    public final FloatProperty saPerturbationScale;
    public final FloatProperty saJumpProb;
    public final FloatProperty saEnergyAngleW;
    public final FloatProperty saEnergyDistW;
    public final FloatProperty saEnergyHeightW;
    public final FloatProperty saEnergyWallW;
    public final FloatProperty saEnergyRandomW;
    public final BooleanProperty saAdaptiveStep;
    public final BooleanProperty saEdgeExploration;

    public final BooleanProperty smoothBackProp;
    public final FloatProperty smoothBackSpeed;

    public final BooleanProperty bestHitVec;
    public final ModeProperty hitSelect;
    public final BooleanProperty smartCps;
    public final BooleanProperty smartAim;

    public final FloatProperty bezierControl;
    public final FloatProperty bezierControl2;
    public final FloatProperty bezierControl3;
    public final FloatProperty yawMinStep;
    public final FloatProperty pitchMinStep;
    public final FloatProperty yawDynStepAS;
    public final FloatProperty pitchDynStepAS;

    public final FloatProperty mlSmoothFactor;
    public final FloatProperty mlOvershootProb;
    public final FloatProperty mlOvershootScale;
    public final FloatProperty mlNoiseScale;

    public final BooleanProperty brownianMotion;
    public final FloatProperty brownianIntensity;
    public final FloatProperty brownianYawScale;
    public final FloatProperty brownianPitchScale;
    public final FloatProperty brownianDamping;
    public final FloatProperty brownianDrift;
    public final IntProperty brownianOctaves;
    public final FloatProperty brownianPersistence;
    public final FloatProperty brownianImpulseProb;
    public final FloatProperty brownianImpulseScale;
    public final BooleanProperty brownianAdaptive;
    public final FloatProperty brownianMaxAngle;
    public final FloatProperty brownianCorrectionSpeed;

    public final BooleanProperty noiseRecoveryEnabled;
    public final FloatProperty nrStiffness;
    public final FloatProperty nrDamping;
    public final FloatProperty nrFatigueRate;
    public final FloatProperty nrRecoveryRate;
    public final FloatProperty nrScale;
    public final FloatProperty nrPitchRatio;
    public final FloatProperty nrImpulseProb;
    public final FloatProperty nrImpulseScale;
    public final FloatProperty nrMicroJitter;
    public final BooleanProperty nrDistanceScale;
    public final BooleanProperty nrGcdQuantize;
    public final BooleanProperty nrClampToBox;

    public final BooleanProperty overshootEnabled;
    public final FloatProperty overshootProbability;
    public final FloatProperty overshootScale;
    public final FloatProperty overshootPitchRatio;
    public final FloatProperty overshootDecay;
    public final FloatProperty overshootVelThreshold;
    public final FloatProperty overshootMaxAngle;

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
    public final BooleanProperty dot;
    public final ColorProperty dotColor;
    public final FloatProperty dotSize;
    public final ModeProperty dotEasing;
    public final IntProperty dotAnimSpeed;
    public final BooleanProperty debug;

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

    private void updateSmartCPS() {
        if (!this.smartCps.getValue()) return;

        int effectiveMinCPS = this.minCPS.getValue();
        int effectiveMaxCPS = this.maxCPS.getValue();
        int dynamicMin = Math.max(1, effectiveMinCPS - 5);
        int dynamicMax = Math.min(20, effectiveMaxCPS + 5);

        if (this.target == null) {
            this.smartCpsValue = (int) RandomUtil.nextLong(dynamicMin, dynamicMin + 3);
            return;
        }

        EntityLivingBase targetEntity = this.target.getEntity();
        double dist = RotationUtil.distanceToBox(this.target.getBox());

        boolean fastIncrease = (mc.thePlayer.hurtTime >= 3 && mc.thePlayer.hurtTime <= 10) ||
                               (targetEntity.hurtTime >= 0 && targetEntity.hurtTime <= 4) ||
                               (dist >= this.attackRange.getValue() - 0.1 && dist <= this.attackRange.getValue() + 0.9);

        int inc = fastIncrease ? (int) RandomUtil.nextLong(2, 5) : (int) RandomUtil.nextLong(-3, 0);
        this.smartCpsValue += inc;
        this.smartCpsValue = Math.max(dynamicMin, Math.min(dynamicMax, this.smartCpsValue));
    }

    private long getAttackDelay() {
        int effectiveMinCPS = this.minCPS.getValue();
        int effectiveMaxCPS = this.maxCPS.getValue();
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
                effectiveMinCPS = Math.min(20, (int) (effectiveMinCPS * (1.0f + upScale)));
                effectiveMaxCPS = Math.min(20, (int) (effectiveMaxCPS * (1.0f + upScale)));
            }
        }
        if (effectiveMaxCPS < effectiveMinCPS) effectiveMaxCPS = effectiveMinCPS;

        if (this.smartCps.getValue()) {
            return 1000L / this.smartCpsValue;
        } else {
            return 1000L / RandomUtil.nextLong(effectiveMinCPS, effectiveMaxCPS);
        }
    }

    private boolean checkHitSelectTiming() {
        if (this.target == null) return false;
        EntityLivingBase targetEntity = this.target.getEntity();
        boolean canCrit = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.thePlayer.ridingEntity == null;

        if (this.hitSelect.getValue() == 1) {
            if (targetEntity.hurtTime > 1) {
                this.shouldAttack = true;
            }
            if (targetEntity.hurtTime <= 1 && this.shouldAttack) {
                this.shouldAttack = false;
                return true;
            }
            if (mc.thePlayer.hurtTime >= 5) {
                return true;
            }
            if (canCrit && this.shouldCrit) {
                this.shouldCrit = false;
                return true;
            }
            if (targetEntity.hurtTime != 0) {
                this.hitSelectTickCounter = 0;
            } else {
                this.hitSelectTickCounter++;
                if (this.hitSelectTickCounter >= targetEntity.maxHurtTime) {
                    this.hitSelectTickCounter = 0;
                    return true;
                }
            }
            return false;
        } else if (this.hitSelect.getValue() == 2) {
            if (targetEntity.maxHurtTime <= 1 || targetEntity.hurtTime > 1) {
                this.shouldAttack = true;
            }
            if (targetEntity.hurtTime <= 1 && this.shouldAttack) {
                this.shouldAttack = false;
                return true;
            }
            if (canCrit && this.shouldCrit) {
                this.shouldCrit = false;
                return true;
            }
            if (targetEntity.hurtTime != 0) {
                this.hitSelectTickCounter = 0;
            } else {
                this.hitSelectTickCounter++;
                if (this.hitSelectTickCounter >= targetEntity.maxHurtTime) {
                    this.hitSelectTickCounter = 0;
                    return true;
                }
            }
            return false;
        }
        return true;
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
            float bezierSpeed = Math.max(0.02f, 1.0f - smoothFactor * 0.98f);
            if (bezierRotator.isFinished() || bezierRotator.needsUpdate(rawTarget[0], rawTarget[1], 15.0f)) {
                bezierRotator.setup(currentYaw, currentPitch, rawTarget[0], rawTarget[1], bezierSpeed,
                        this.bezierControl.getValue(), this.bezierControl2.getValue(), this.bezierControl3.getValue(),
                        this.yawMinStep.getValue(), this.pitchMinStep.getValue(),
                        this.yawDynStepAS.getValue(), this.pitchDynStepAS.getValue());
            }
            return bezierRotator.getNextRotation();
        } else if (this.rotationMode.getValue() == 3) {
            float[] rawTarget;
            if (this.smartAim.getValue()) {
                rawTarget = RotationUtil.getRotationsToSmartVec(box, currentYaw, currentPitch, 180.0f, 0.0f);
            } else if (this.bestHitVec.getValue()) {
                rawTarget = RotationUtil.getRotationsToBoxStable(box, currentYaw, currentPitch, 180.0f, 0.0f, distance);
            } else {
                rawTarget = RotationUtil.getRotationsToEntity(entity, currentYaw, currentPitch, 180.0f, 0.0f);
            }
            if (mlRotator.isFinished() || mlRotator.needsUpdate(rawTarget[0], rawTarget[1], 10.0f)) {
                mlRotator.setup(currentYaw, currentPitch, rawTarget[0], rawTarget[1],
                        this.mlSmoothFactor.getValue(), this.mlOvershootProb.getValue(),
                        this.mlOvershootScale.getValue(), this.mlNoiseScale.getValue());
            }
            return mlRotator.getNextRotation();
        }

        if (this.smartAim.getValue()) {
            return RotationUtil.getRotationsToSmartVec(box, currentYaw, currentPitch, maxAngle, smoothFactor);
        } else if (this.bestHitVec.getValue()) {
            return RotationUtil.getRotationsToBoxStable(box, currentYaw, currentPitch, maxAngle, smoothFactor, distance);
        } else {
            return RotationUtil.getRotationsToEntity(entity, currentYaw, currentPitch, maxAngle, smoothFactor);
        }
    }

    private boolean performAttack(float yaw, float pitch, boolean skipAttack) {
        if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) return false;
            else if (this.attackDelayMS > 0L) return false;
            else {
                if (skipAttack) {
                    this.attackDelayMS += 50L;
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
        this.rotationMode = new ModeProperty("Rotation-Mode", 0, new String[]{"Basic", "SA", "Bezier", "ML"});
        
        this.saInitTemp = new FloatProperty("SA-Init-Temp", 3.0F, 0.5F, 20.0F, () -> this.rotationMode.getValue() == 1);
        this.saCoolingRate = new FloatProperty("SA-Cooling", 0.95F, 0.80F, 0.99F, () -> this.rotationMode.getValue() == 1);
        this.saMinTemp = new FloatProperty("SA-Min-Temp", 0.8F, 0.0F, 5.0F, () -> this.rotationMode.getValue() == 1);
        this.saIterations = new IntProperty("Sa-Iterations", 20, 5, 50, () -> this.rotationMode.getValue() == 1);
        this.saPerturbationMode = new ModeProperty("SA-Perturb-Mode", 0, new String[]{"Hybrid", "Gaussian", "Perlin", "Adaptive"}, () -> this.rotationMode.getValue() == 1);
        this.saPerturbationScale = new FloatProperty("SA-Perturb-Scale", 1.0F, 0.1F, 3.0F, () -> this.rotationMode.getValue() == 1);
        this.saJumpProb = new FloatProperty("SA-Jump-Prob", 0.05F, 0.0F, 0.5F, () -> this.rotationMode.getValue() == 1);
        this.saEnergyAngleW = new FloatProperty("SA-Energy-Angle-W", 0.5F, 0.0F, 2.0F, () -> this.rotationMode.getValue() == 1);
        this.saEnergyDistW = new FloatProperty("SA-Energy-Dist-W", 0.15F, 0.0F, 1.0F, () -> this.rotationMode.getValue() == 1);
        this.saEnergyHeightW = new FloatProperty("SA-Energy-Height-W", 0.35F, 0.0F, 1.0F, () -> this.rotationMode.getValue() == 1);
        this.saEnergyWallW = new FloatProperty("SA-Energy-Wall-W", 1.0F, 0.0F, 5.0F, () -> this.rotationMode.getValue() == 1);
        this.saEnergyRandomW = new FloatProperty("SA-Energy-Random-W", 0.1F, 0.0F, 1.0F, () -> this.rotationMode.getValue() == 1);
        this.saAdaptiveStep = new BooleanProperty("SA-Adaptive-Step", true, () -> this.rotationMode.getValue() == 1);
        this.saEdgeExploration = new BooleanProperty("SA-Edge-Explore", true, () -> this.rotationMode.getValue() == 1);

        this.smoothBackProp = new BooleanProperty("Smooth-Back", true);
        this.smoothBackSpeed = new FloatProperty("Smooth-Back-Speed", 0.3F, 0.05F, 1.0F);
        this.bestHitVec = new BooleanProperty("Best-Hit-Vec", true);
        this.hitSelect = new ModeProperty("HitSelect", 0, new String[]{"None", "Smart", "Full"});
        this.smartCps = new BooleanProperty("SmartCPS", false);
        this.smartAim = new BooleanProperty("SmartAim", false);

        this.bezierControl = new FloatProperty("Bezier-Control", 0.25F, 0.0F, 1.0F, () -> this.rotationMode.getValue() == 2);
        this.bezierControl2 = new FloatProperty("Bezier-Control2", 0.75F, 0.0F, 1.0F, () -> this.rotationMode.getValue() == 2);
        this.bezierControl3 = new FloatProperty("Bezier-Control3", 15.0F, 0.0F, 50.0F, () -> this.rotationMode.getValue() == 2);
        this.yawMinStep = new FloatProperty("YawMinStep", 0.5F, 0.0F, 5.0F, () -> this.rotationMode.getValue() == 2);
        this.pitchMinStep = new FloatProperty("PitchMinStep", 0.5F, 0.0F, 5.0F, () -> this.rotationMode.getValue() == 2);
        this.yawDynStepAS = new FloatProperty("yawDynStepAS", 1.0F, 0.0F, 10.0F, () -> this.rotationMode.getValue() == 2);
        this.pitchDynStepAS = new FloatProperty("pitchDynStepAS", 1.0F, 0.0F, 10.0F, () -> this.rotationMode.getValue() == 2);

        this.mlSmoothFactor = new FloatProperty("ML-Smooth", 0.5F, 0.1F, 1.0F, () -> this.rotationMode.getValue() == 3);
        this.mlOvershootProb = new FloatProperty("ML-Overshoot-Prob", 0.3F, 0.0F, 1.0F, () -> this.rotationMode.getValue() == 3);
        this.mlOvershootScale = new FloatProperty("ML-Overshoot-Scale", 1.2F, 0.1F, 3.0F, () -> this.rotationMode.getValue() == 3);
        this.mlNoiseScale = new FloatProperty("ML-Noise-Scale", 0.2F, 0.0F, 2.0F, () -> this.rotationMode.getValue() == 3);

        this.brownianMotion = new BooleanProperty("Brownian-Motion", true);
        this.brownianIntensity = new FloatProperty("Brownian-Intensity", 0.5F, 0.0F, 5.0F, () -> this.brownianMotion.getValue());
        this.brownianYawScale = new FloatProperty("Brownian-Yaw-Scale", 1.0F, 0.1F, 5.0F, () -> this.brownianMotion.getValue());
        this.brownianPitchScale = new FloatProperty("Brownian-Pitch-Scale", 0.6F, 0.1F, 5.0F, () -> this.brownianMotion.getValue());
        this.brownianDamping = new FloatProperty("Brownian-Damping", 0.15F, 0.01F, 1.0F, () -> this.brownianMotion.getValue());
        this.brownianDrift = new FloatProperty("Brownian-Drift", 0.0F, -2.0F, 2.0F, () -> this.brownianMotion.getValue());
        this.brownianOctaves = new IntProperty("Brownian-Octaves", 4, 1, 8, () -> this.brownianMotion.getValue());
        this.brownianPersistence = new FloatProperty("Brownian-Persistence", 0.5F, 0.1F, 0.9F, () -> this.brownianMotion.getValue());
        this.brownianImpulseProb = new FloatProperty("Brownian-Impulse-Prob", 0.05F, 0.0F, 0.5F, () -> this.brownianMotion.getValue());
        this.brownianImpulseScale = new FloatProperty("Brownian-Impulse-Scale", 0.3F, 0.0F, 3.0F, () -> this.brownianMotion.getValue());
        this.brownianAdaptive = new BooleanProperty("Brownian-Adaptive", true, () -> this.brownianMotion.getValue());
        this.brownianMaxAngle = new FloatProperty("Brownian-Max-Angle", 5.0F, 0.5F, 30.0F, () -> this.brownianMotion.getValue());
        this.brownianCorrectionSpeed = new FloatProperty("Brownian-Correction-Speed", 0.85F, 0.5F, 0.99F, () -> this.brownianMotion.getValue());

        this.noiseRecoveryEnabled = new BooleanProperty("Noise-Recovery", true);
        this.nrStiffness = new FloatProperty("NR-Stiffness", 0.15F, 0.01F, 1.0F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrDamping = new FloatProperty("NR-Damping", 0.85F, 0.5F, 0.99F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrFatigueRate = new FloatProperty("NR-Fatigue-Rate", 0.005F, 0.001F, 0.05F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrRecoveryRate = new FloatProperty("NR-Recovery-Rate", 0.003F, 0.001F, 0.02F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrScale = new FloatProperty("NR-Scale", 1.0F, 0.1F, 5.0F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrPitchRatio = new FloatProperty("NR-Pitch-Ratio", 0.6F, 0.1F, 2.0F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrImpulseProb = new FloatProperty("NR-Impulse-Prob", 0.05F, 0.0F, 0.5F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrImpulseScale = new FloatProperty("NR-Impulse-Scale", 0.3F, 0.0F, 3.0F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrMicroJitter = new FloatProperty("NR-Micro-Jitter", 0.3F, 0.0F, 3.0F, () -> this.noiseRecoveryEnabled.getValue());
        this.nrDistanceScale = new BooleanProperty("NR-Distance-Scale", true, () -> this.noiseRecoveryEnabled.getValue());
        this.nrGcdQuantize = new BooleanProperty("NR-GCD-Quantize", true, () -> this.noiseRecoveryEnabled.getValue());
        this.nrClampToBox = new BooleanProperty("NR-Clamp-To-Box", true, () -> this.noiseRecoveryEnabled.getValue());

        this.overshootEnabled = new BooleanProperty("Overshoot", true);
        this.overshootProbability = new FloatProperty("Overshoot-Prob", 0.15F, 0.0F, 1.0F, () -> this.overshootEnabled.getValue());
        this.overshootScale = new FloatProperty("Overshoot-Scale", 1.5F, 0.1F, 10.0F, () -> this.overshootEnabled.getValue());
        this.overshootPitchRatio = new FloatProperty("Overshoot-Pitch-Ratio", 0.5F, 0.1F, 2.0F, () -> this.overshootEnabled.getValue());
        this.overshootDecay = new FloatProperty("Overshoot-Decay", 0.75F, 0.3F, 0.99F, () -> this.overshootEnabled.getValue());
        this.overshootVelThreshold = new FloatProperty("Overshoot-Vel-Threshold", 0.5F, 0.1F, 10.0F, () -> this.overshootEnabled.getValue());
        this.overshootMaxAngle = new FloatProperty("Overshoot-Max-Angle", 3.0F, 0.5F, 15.0F, () -> this.overshootEnabled.getValue());

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
        this.dot = new BooleanProperty("Dot", true);
        this.dotColor = new ColorProperty("Dot-Color", 0xFF0670BE);
        this.dotSize = new FloatProperty("Dot-Size", 5.0F, 1.0F, 50.0F);
        this.dotEasing = new ModeProperty("Dot-Easing", 8, new String[]{"Linear", "EaseInSine", "EaseOutSine", "EaseInOutSine", "EaseInQuad", "EaseOutQuad", "EaseInOutQuad", "EaseInCubic", "EaseOutCubic", "EaseInOutCubic", "EaseInQuart", "EaseOutQuart", "EaseInOutQuart", "EaseInQuint", "EaseOutQuint", "EaseInOutQuint", "EaseInExpo", "EaseOutExpo", "EaseInOutExpo", "EaseInCirc", "EaseOutCirc", "EaseInOutCirc", "EaseInBack", "EaseOutBack", "EaseInOutBack"});
        this.dotAnimSpeed = new IntProperty("Dot-Anim-Speed", 300, 50, 1000);
        this.debug = new BooleanProperty("Debug", false);
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

    private void sendDebug(float yaw, float pitch) {
        if (!this.debug.getValue() || this.target == null) return;
        EntityLivingBase entity = this.target.getEntity();
        if (entity.hurtTime != 0) return;

        long now = System.currentTimeMillis();
        if (now - this.lastDebugTime < 200L) return;
        this.lastDebugTime = now;

        AxisAlignedBB box = this.target.getBox();
        MovingObjectPosition mop = RotationUtil.rayTrace(box, yaw, pitch, this.attackRange.getValue());
        double distance;
        if (mop != null && mop.hitVec != null) {
            Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
            distance = eyePos.distanceTo(mop.hitVec);
        } else {
            distance = RotationUtil.distanceToBox(box);
        }

        String msg = String.format("[KA-Debug] Dist: %.10f | Yaw: %.2f | Pitch: %.2f", distance, yaw, pitch);
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    private float[] updateAndGetOvershoot(float rotationDeltaYaw, float rotationDeltaPitch) {
        float decay = this.overshootDecay.getValue();
        this.overshootYawOffset *= decay;
        this.overshootPitchOffset *= decay;
        if (Math.abs(this.overshootYawOffset) < 0.01f) this.overshootYawOffset = 0.0f;
        if (Math.abs(this.overshootPitchOffset) < 0.01f) this.overshootPitchOffset = 0.0f;

        if (this.overshootEnabled.getValue()) {
            boolean isClose = Math.abs(rotationDeltaYaw) < 10.0f && Math.abs(rotationDeltaPitch) < 10.0f;
            float yawApproach = Math.abs(this.prevRotationDeltaYaw) - Math.abs(rotationDeltaYaw);
            float pitchApproach = Math.abs(this.prevRotationDeltaPitch) - Math.abs(rotationDeltaPitch);
            boolean isApproaching = yawApproach > this.overshootVelThreshold.getValue() || pitchApproach > this.overshootVelThreshold.getValue();
            boolean isLocked = Math.abs(rotationDeltaYaw) < 1.0f && Math.abs(rotationDeltaPitch) < 1.0f;

            if (isClose && (isApproaching || isLocked) && Math.abs(this.overshootYawOffset) < 0.01f && Math.abs(this.overshootPitchOffset) < 0.01f) {
                float prob = isLocked ? this.overshootProbability.getValue() * 0.1f : this.overshootProbability.getValue();
                if (Math.random() < prob) {
                    float scale = this.overshootScale.getValue();
                    float yawDir = Math.signum(this.prevRotationDeltaYaw);
                    float pitchDir = Math.signum(this.prevRotationDeltaPitch);
                    if (yawDir == 0.0f) yawDir = (Math.random() < 0.5f ? 1.0f : -1.0f);
                    if (pitchDir == 0.0f) pitchDir = (Math.random() < 0.5f ? 1.0f : -1.0f);
                    
                    this.overshootYawOffset = yawDir * scale * (0.5f + 0.5f * (float) Math.random());
                    this.overshootPitchOffset = pitchDir * scale * this.overshootPitchRatio.getValue() * (0.5f + 0.5f * (float) Math.random());

                    float maxOvershoot = this.overshootMaxAngle.getValue();
                    this.overshootYawOffset = Math.max(-maxOvershoot, Math.min(maxOvershoot, this.overshootYawOffset));
                    this.overshootPitchOffset = Math.max(-maxOvershoot * 0.5f, Math.min(maxOvershoot * 0.5f, this.overshootPitchOffset));
                }
            }
        }

        this.prevRotationDeltaYaw = rotationDeltaYaw;
        this.prevRotationDeltaPitch = rotationDeltaPitch;

        return new float[]{this.overshootYawOffset, this.overshootPitchOffset};
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        if (this.attackDelayMS > 0L) this.attackDelayMS -= 50L;

        this.updateSmartCPS();

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
                boolean shouldAttackNow = true;
                if (this.hitSelect.getValue() != 0) {
                    shouldAttackNow = this.checkHitSelectTiming();
                }

                float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
                float maxAngle = 180.0f;

                boolean useSilentBase = (this.rotations.getValue() == 2 || this.rotations.getValue() == 3);
                float refYaw = useSilentBase ? (this.lastSentInitialized ? this.lastSentYaw : event.getYaw()) : event.getYaw();
                float refPitch = useSilentBase ? (this.lastSentInitialized ? this.lastSentPitch : event.getPitch()) : event.getPitch();

                float[] targetRotations;

                if (this.rotationMode.getValue() == 0 || this.rotationMode.getValue() == 2 || this.rotationMode.getValue() == 3) {
                    targetRotations = this.getTargetRotations(refYaw, refPitch, maxAngle, smoothFactor);
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
                        this.saAcceptRate = 0.0f;
                    }

                    boolean checkWalls = !this.throughWalls.getValue();

                    float[] saResult = RotationUtil.simulatedAnnealingBoxStepAdvanced(
                            box, refYaw, refPitch,
                            this.saPointX, this.saPointY, this.saPointZ,
                            this.saTemperature, this.saIterations.getValue(),
                            checkWalls, this.saPerturbationMode.getValue(),
                            this.saPerturbationScale.getValue(), this.saJumpProb.getValue(),
                            this.saEnergyAngleW.getValue(), this.saEnergyDistW.getValue(),
                            this.saEnergyHeightW.getValue(), this.saEnergyWallW.getValue(),
                            this.saEnergyRandomW.getValue(), this.saAdaptiveStep.getValue(),
                            this.saEdgeExploration.getValue());

                    this.saPointX = saResult[2];
                    this.saPointY = saResult[3];
                    this.saPointZ = saResult[4];
                    this.saTemperature = Math.max(this.saMinTemp.getValue(), this.saTemperature * this.saCoolingRate.getValue());

                    float saSpeedFactor = 1.0f - smoothFactor * 0.95f;
                    float yawDelta = MathHelper.wrapAngleTo180_float(saResult[0] - refYaw);
                    float pitchDelta = MathHelper.wrapAngleTo180_float(saResult[1] - refPitch);
                    yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.clampAngle(yawDelta, maxAngle) * saSpeedFactor;
                    pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.clampAngle(pitchDelta, maxAngle) * saSpeedFactor;

                    targetRotations = new float[]{refYaw + yawDelta, refPitch + pitchDelta};
                }

                if (this.noiseRecoveryEnabled.getValue()) {
                    double dist = RotationUtil.distanceToBox(this.target.getBox());
                    targetRotations = RotationUtil.applyNoiseRecovery(
                            targetRotations[0], targetRotations[1],
                            this.recoverySystem,
                            this.nrStiffness.getValue(), this.nrDamping.getValue(),
                            this.nrFatigueRate.getValue(), this.nrRecoveryRate.getValue(),
                            this.nrScale.getValue(), this.nrPitchRatio.getValue(),
                            this.nrImpulseProb.getValue(), this.nrImpulseScale.getValue(),
                            this.nrMicroJitter.getValue(), this.nrDistanceScale.getValue(),
                            this.nrGcdQuantize.getValue(), this.nrClampToBox.getValue(),
                            this.target.getBox(), this.attackRange.getValue(), dist);
                }

                if (this.brownianMotion.getValue()) {
                    targetRotations = RotationUtil.applyAdvancedBrownianMotion(
                            targetRotations[0], targetRotations[1],
                            this.brownianState,
                            this.brownianIntensity.getValue(),
                            this.brownianYawScale.getValue(), this.brownianPitchScale.getValue(),
                            this.brownianDamping.getValue(), this.brownianDrift.getValue(),
                            this.brownianOctaves.getValue(), this.brownianPersistence.getValue(),
                            this.brownianImpulseProb.getValue(), this.brownianImpulseScale.getValue(),
                            this.brownianAdaptive.getValue(), this.brownianMaxAngle.getValue(),
                            this.brownianCorrectionSpeed.getValue(),
                            this.target.getBox(), this.attackRange.getValue(),
                            RotationUtil.distanceToBox(this.target.getBox()));
                }

                float rotationDeltaYaw = MathHelper.wrapAngleTo180_float(targetRotations[0] - refYaw);
                float rotationDeltaPitch = MathHelper.wrapAngleTo180_float(targetRotations[1] - refPitch);
                float[] overshoot = this.updateAndGetOvershoot(rotationDeltaYaw, rotationDeltaPitch);
                targetRotations[0] += overshoot[0];
                targetRotations[1] += overshoot[1];

                float finalGcd = RotationUtil.getSensitivityGCD();
                if (finalGcd > 0.0f) {
                    float yDelta = MathHelper.wrapAngleTo180_float(targetRotations[0] - refYaw);
                    float pDelta = MathHelper.wrapAngleTo180_float(targetRotations[1] - refPitch);
                    
                    if (yDelta != 0.0f && Math.abs(yDelta) < finalGcd) {
                        yDelta = Math.signum(yDelta) * finalGcd;
                    } else {
                        yDelta = Math.round(yDelta / finalGcd) * finalGcd;
                    }
                    
                    if (pDelta != 0.0f && Math.abs(pDelta) < finalGcd) {
                        pDelta = Math.signum(pDelta) * finalGcd;
                    } else {
                        pDelta = Math.round(pDelta / finalGcd) * finalGcd;
                    }
                    
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
                if (attack) attacked = this.performAttack(event.getNewYaw(), event.getNewPitch(), !shouldAttackNow);

                this.sendDebug(attackRotations[0], attackRotations[1]);
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
                if (this.dotAnimSpeed.getValue() != this.currentDotAnimSpeed || this.dotEasing.getValue() != this.currentDotEasingIndex) {
                    Easing[] easings = Easing.values();
                    int idx = this.dotEasing.getValue();
                    Easing easing = idx >= 0 && idx < easings.length ? easings[idx] : Easing.Linear;
                    this.dotScaleAnim = new Animation(easing, this.dotAnimSpeed.getValue());
                    this.currentDotAnimSpeed = this.dotAnimSpeed.getValue();
                    this.currentDotEasingIndex = idx;
                }

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
                        this.recoverySystem.reset();
                        this.brownianState.reset();
                        this.overshootYawOffset = 0.0f;
                        this.overshootPitchOffset = 0.0f;
                        this.prevRotationDeltaYaw = 180.0f;
                        this.prevRotationDeltaPitch = 180.0f;
                        this.lastTargetEntityId = currentTargetId;
                        this.shouldAttack = true;
                        this.shouldCrit = true;
                        this.hitSelectTickCounter = 10;
                        this.shouldFirstlyHit_1 = true;
                        this.shouldFirstlyHit_2 = true;
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
        if (!this.isEnabled()) return;

        boolean showDot = this.target != null && this.dot.getValue() && attackRotations != null && TeamUtil.isEntityLoaded(this.target.getEntity()) && this.isAttackAllowed();
        if (showDot) {
            if (!dotScaleAnim.isForward()) dotScaleAnim.start(true);
        } else {
            if (dotScaleAnim.isForward()) dotScaleAnim.start(false);
        }
        dotScaleAnim.update();

        if (dotScaleAnim.getValue() > 0.001 && this.target != null && attackRotations != null) {
            float pt = event.getPartialTicks(); 
            Vec3 ep = mc.thePlayer.getPositionEyes(pt); 
            Vec3 lv = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(attackRotations[1], attackRotations[0]);
            Vec3 end = ep.addVector(lv.xCoord * swingRange.getValue(), lv.yCoord * swingRange.getValue(), lv.zCoord * swingRange.getValue());
            MovingObjectPosition mop = this.target.getBox().calculateIntercept(ep, end); 
            Vec3 rp = mop != null && mop.hitVec != null ? mop.hitVec : end;
            double dist = ep.distanceTo(rp); 
            if (dist < 0.1) dist = 0.1; 
            
            float animVal = (float) dotScaleAnim.getValue();
            float as = dotSize.getValue() / 100.0f * (float) Math.sqrt(dist) * animVal;
            Color dc = new Color(dotColor.getValue(), true); 
            double xp = rp.xCoord - mc.getRenderManager().viewerPosX, yp = rp.yCoord - mc.getRenderManager().viewerPosY, zp = rp.zCoord - mc.getRenderManager().viewerPosZ;
            GL11.glPushMatrix(); 
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); 
            GL11.glDisable(GL11.GL_DEPTH_TEST); 
            GL11.glDepthMask(false); 
            GL11.glDisable(GL11.GL_TEXTURE_2D); 
            GL11.glDisable(GL11.GL_LIGHTING); 
            GL11.glDisable(GL11.GL_CULL_FACE); 
            GL11.glEnable(GL11.GL_BLEND); 
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(dc.getRed() / 255.0f, dc.getGreen() / 255.0f, dc.getBlue() / 255.0f, dc.getAlpha() / 255.0f * animVal);
            drawCube(xp, yp, zp, as); 
            GL11.glPopAttrib(); 
            GL11.glPopMatrix();
        }
    }

    private void drawCube(double x, double y, double z, float s) { float h = s / 2.0f; GL11.glBegin(GL11.GL_QUADS); GL11.glVertex3d(x-h,y+h,z-h); GL11.glVertex3d(x+h,y+h,z-h); GL11.glVertex3d(x+h,y+h,z+h); GL11.glVertex3d(x-h,y+h,z+h); GL11.glVertex3d(x-h,y-h,z+h); GL11.glVertex3d(x+h,y-h,z+h); GL11.glVertex3d(x+h,y-h,z-h); GL11.glVertex3d(x-h,y-h,z-h); GL11.glVertex3d(x-h,y-h,z-h); GL11.glVertex3d(x+h,y-h,z-h); GL11.glVertex3d(x+h,y+h,z-h); GL11.glVertex3d(x-h,y+h,z-h); GL11.glVertex3d(x+h,y-h,z+h); GL11.glVertex3d(x-h,y-h,z+h); GL11.glVertex3d(x-h,y+h,z+h); GL11.glVertex3d(x+h,y+h,z+h); GL11.glVertex3d(x-h,y-h,z+h); GL11.glVertex3d(x-h,y-h,z-h); GL11.glVertex3d(x-h,y+h,z-h); GL11.glVertex3d(x-h,y+h,z+h); GL11.glVertex3d(x+h,y-h,z-h); GL11.glVertex3d(x+h,y-h,z+h); GL11.glVertex3d(x+h,y+h,z+h); GL11.glVertex3d(x+h,y+h,z-h); GL11.glEnd(); }

    @EventTarget public void onLeftClick(LeftClickMouseEvent e) { if (this.isBlocking) e.setCancelled(true); else if (this.isEnabled() && this.target != null && this.canAttack()) e.setCancelled(true); }
    @EventTarget public void onRightClick(RightClickMouseEvent e) { if (this.isBlocking) e.setCancelled(true); else if (this.isEnabled() && this.target != null && this.canAttack()) e.setCancelled(true); }
    @EventTarget public void onHitBlock(HitBlockEvent e) { if (this.isBlocking) e.setCancelled(true); else if (this.isEnabled() && this.target != null && this.canAttack()) e.setCancelled(true); }
    @EventTarget public void onCancelUse(CancelUseEvent e) { if (this.isBlocking) e.setCancelled(true); }

    @Override
    public void onEnabled() { 
        this.target = null; this.switchTick = 0; this.hitRegistered = false; this.attackDelayMS = 0L; attackRotations = null; 
        this.lastTargetEntityId = -1; this.recoverySystem.reset(); this.brownianState.reset(); this.overshootYawOffset = 0.0f; this.overshootPitchOffset = 0.0f; 
        this.prevRotationDeltaYaw = 180.0f; this.prevRotationDeltaPitch = 180.0f; this.saTemperature = 0.0f; this.saActive = false; this.saPointX = 0.0f; this.saPointY = 0.0f; 
        this.saPointZ = 0.0f; this.saLastTargetId = -1; this.saAcceptRate = 0.0f; this.wasRotating = false; this.isReturning = false; this.lastSentYaw = 0.0f; this.lastSentPitch = 0.0f; 
        this.lastSentInitialized = false; this.shouldAttack = true; this.shouldCrit = true; this.hitSelectTickCounter = 10; this.shouldFirstlyHit_1 = true; this.shouldFirstlyHit_2 = true; 
        this.smartCpsValue = 8; this.lastDebugTime = 0L; 
        Easing[] easings = Easing.values();
        int idx = this.dotEasing.getValue();
        Easing easing = idx >= 0 && idx < easings.length ? easings[idx] : Easing.Linear;
        this.dotScaleAnim = new Animation(easing, this.dotAnimSpeed.getValue());
        this.dotScaleAnim.start(false);
        this.currentDotAnimSpeed = this.dotAnimSpeed.getValue();
        this.currentDotEasingIndex = idx;
    }

    @Override
    public void onDisabled() { 
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.blockingState = false; this.isBlocking = false; this.fakeBlockState = false; attackRotations = null; 
        this.lastTargetEntityId = -1; this.recoverySystem.reset(); this.brownianState.reset(); this.overshootYawOffset = 0.0f; this.overshootPitchOffset = 0.0f; 
        this.prevRotationDeltaYaw = 180.0f; this.prevRotationDeltaPitch = 180.0f; this.saTemperature = 0.0f; this.saActive = false; this.saPointX = 0.0f; this.saPointY = 0.0f; 
        this.saPointZ = 0.0f; this.saLastTargetId = -1; this.saAcceptRate = 0.0f; this.wasRotating = false; this.isReturning = false; this.lastSentYaw = 0.0f; this.lastSentPitch = 0.0f; 
        this.lastSentInitialized = false; this.shouldAttack = true; this.shouldCrit = true; this.hitSelectTickCounter = 10; this.shouldFirstlyHit_1 = true; this.shouldFirstlyHit_2 = true; 
        this.smartCpsValue = 8; this.lastDebugTime = 0L; 
        this.dotScaleAnim.start(false);
    }

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