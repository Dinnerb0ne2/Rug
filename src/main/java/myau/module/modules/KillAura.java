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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private final TimerUtil timer = new TimerUtil();
    private AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    private int lastTickProcessed;
    private float[] attackRotations = null;

    private float saTemperature = 0.0f;
    private boolean saActive = false;

    private boolean wasRotating = false;
    private boolean isReturning = false;
    private float returnYaw = 0.0f;
    private float returnPitch = 0.0f;

    private float perlinTimeAccumulator = 0.0F;
    private float noiseFatigue = 0.0f;
    private int lastTargetEntityId = -1;

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
    public final ModeProperty debugLog;
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
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
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
        float[] back = RotationUtil.smoothBack(this.returnYaw, this.returnPitch, playerYaw, playerPitch, this.smoothBackSpeed.getValue());
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
        if (this.bestHitVec.getValue()) {
            AxisAlignedBB box = this.target.getBox();
            AxisAlignedBB predBox = box.offset(this.target.getEntity().motionX * ticks, this.target.getEntity().motionY * ticks, this.target.getEntity().motionZ * ticks);
            return RotationUtil.getRotationsToBox(predBox, currentYaw, currentPitch, maxAngle, smoothFactor);
        } else {
            return RotationUtil.getRotationsToEntity(this.target.getEntity(), currentYaw, currentPitch, maxAngle, smoothFactor, ticks);
        }
    }

    private float[] getRawTarget() {
        float ticks = this.predictionTicks.getValue();
        if (this.bestHitVec.getValue()) {
            AxisAlignedBB box = this.target.getBox();
            AxisAlignedBB predBox = box.offset(this.target.getEntity().motionX * ticks, this.target.getEntity().motionY * ticks, this.target.getEntity().motionZ * ticks);
            return RotationUtil.getRawTargetBox(predBox);
        } else {
            return RotationUtil.getRawTargetEntity(this.target.getEntity(), ticks);
        }
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) return false;
            else if (this.attackDelayMS > 0L) return false;
            else {
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
        this.lastTickProcessed = 0;
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
        this.debugLog = new ModeProperty("debug-log", 0, new String[]{"NONE", "HEALTH"});
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
        if (this.attackDelayMS > 0L) this.attackDelayMS -= 50L;

        this.updateNoiseFatigue(this.target != null);

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
                float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
                float[] targetRotations;

                if (this.rotationMode.getValue() == 0) {
                    targetRotations = this.getTargetRotations(event.getYaw(), event.getPitch(), 180.0f, smoothFactor);
                } else {
                    float[] rawTarget = this.getRawTarget();
                    float deltaYaw = Math.abs(MathHelper.wrapAngleTo180_float(rawTarget[0] - event.getYaw()));
                    float deltaPitch = Math.abs(MathHelper.wrapAngleTo180_float(rawTarget[1] - event.getPitch()));
                    if (!this.saActive || deltaYaw > this.saResetThreshold.getValue() || deltaPitch > this.saResetThreshold.getValue()) {
                        this.saTemperature = this.saInitTemp.getValue();
                        this.saActive = true;
                    }
                    targetRotations = RotationUtil.simulatedAnnealingStep(event.getYaw(), event.getPitch(), rawTarget[0], rawTarget[1], this.saTemperature, smoothFactor);
                    this.saTemperature = Math.max(this.saMinTemp.getValue(), this.saTemperature * this.saCoolingRate.getValue());
                }

                if (this.noiseMode.getValue() != 0) {
                    float[] n = this.getDynamicRecoveryNoiseOffset();
                    targetRotations[0] += n[0];
                    targetRotations[1] += n[1];
                }

                attackRotations = targetRotations;
                if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                    event.setRotation(targetRotations[0], targetRotations[1], 1);
                    if (this.rotations.getValue() == 3) Myau.rotationManager.setRotation(targetRotations[0], targetRotations[1], 1, true);
                    if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) event.setPervRotation(targetRotations[0], 1);
                    this.wasRotating = true;
                }
                if (attack) attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
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
            if (this.debugLog.getValue() == 1 && this.isAttackAllowed()) {
                if (event.getPacket() instanceof S06PacketUpdateHealth) { float p = ((S06PacketUpdateHealth) event.getPacket()).getHealth() - mc.thePlayer.getHealth(); if (p != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) { this.lastTickProcessed = mc.thePlayer.ticksExisted; ChatUtil.sendFormatted(String.format("%sHealth: %s&l%s&r (&otick: %d&r)&r", Myau.clientName, p > 0.0F ? "&a" : "&c", df.format(p), mc.thePlayer.ticksExisted)); } }
                if (event.getPacket() instanceof S1CPacketEntityMetadata) { S1CPacketEntityMetadata pk = (S1CPacketEntityMetadata) event.getPacket(); if (pk.getEntityId() == mc.thePlayer.getEntityId()) { for (WatchableObject wo : pk.func_149376_c()) { if (wo.getDataValueId() == 6) { float d = (Float) wo.getObject() - mc.thePlayer.getHealth(); if (d != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) { this.lastTickProcessed = mc.thePlayer.ticksExisted; ChatUtil.sendFormatted(String.format("%sHealth: %s&l%s&r (&otick: %d&r)&r", Myau.clientName, d > 0.0F ? "&a" : "&c", df.format(d), mc.thePlayer.ticksExisted)); } } } } }
            }
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
    public void onEnabled() { this.target = null; this.switchTick = 0; this.hitRegistered = false; this.attackDelayMS = 0L; attackRotations = null; this.perlinTimeAccumulator = 0.0F; this.noiseFatigue = 0.0f; this.lastTargetEntityId = -1; this.saTemperature = 0.0f; this.saActive = false; this.wasRotating = false; this.isReturning = false; }

    @Override
    public void onDisabled() { Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK); this.blockingState = false; this.isBlocking = false; this.fakeBlockState = false; attackRotations = null; this.perlinTimeAccumulator = 0.0F; this.noiseFatigue = 0.0f; this.lastTargetEntityId = -1; this.saTemperature = 0.0f; this.saActive = false; this.wasRotating = false; this.isReturning = false; }

    @Override
    public void verifyValue(String v) {
        if (this.swingRange.getName().equals(v)) { if (this.swingRange.getValue() < this.attackRange.getValue()) this.attackRange.setValue(this.swingRange.getValue()); }
        else if (this.attackRange.getName().equals(v)) { if (this.swingRange.getValue() < this.attackRange.getValue()) this.swingRange.setValue(this.attackRange.getValue()); }
        else if (this.minCPS.getName().equals(v)) { if (this.minCPS.getValue() > this.maxCPS.getValue()) this.maxCPS.setValue(this.minCPS.getValue()); }
    }
}