package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.RotationEvent;
import keystrokesmod.module.impl.combat.autoclicker.IAutoClicker;
import keystrokesmod.module.impl.combat.autoclicker.LowCPSAutoClicker;
import keystrokesmod.module.impl.combat.autoclicker.NormalAutoClicker;
import keystrokesmod.module.impl.combat.autoclicker.RecordAutoClicker;
import keystrokesmod.module.impl.other.RotationHandler;
import keystrokesmod.module.impl.other.SlotHandler;
import keystrokesmod.module.impl.other.anticheats.utils.world.PlayerRotation;
import keystrokesmod.module.impl.player.Blink;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ModeSetting;
import keystrokesmod.module.setting.impl.ModeValue;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.classes.Vec3;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.aim.AimSimulator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AutoRod extends IAutoClicker {
    private final ModeValue clickMode;
    private final ButtonSetting onlyWhileKillAura;
    private final SliderSetting range;
    private final SliderSetting minRange;
    private final SliderSetting aimSpeed;
    private final ModeSetting moveFix;
    private final ButtonSetting prediction;
    private final ButtonSetting smartPrediction;
    private final SliderSetting predictionTicks;
    private final SliderSetting maxAngleChange;
    private final ButtonSetting drawPos;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting silentSwitch;
    private final ButtonSetting prioritizeHurt;
    private final ButtonSetting checkLineOfSight;
    private final SliderSetting throwDelay;
    private final SliderSetting maxThrowInterval;
    private final SliderSetting minThrowInterval;
    private final SliderSetting motionThreshold;
    private final SliderSetting targetSwitchCooldown;
    private final ButtonSetting advancedRotation;
    private final ButtonSetting dynamicPrediction;
    private final SliderSetting predictionSmoothing;

    private int fromSlot = -1;
    private EntityLivingBase target = null;
    private EntityLivingBase lastTarget = null;
    private int predTicks = 0;
    private net.minecraft.util.Vec3 pos = null;
    private Float lastYaw = null, lastPitch = null;
    private long lastThrowTime = 0;
    private long lastTargetSwitchTime = 0;
    private boolean hasThrown = false;
    private Vec3[] targetMotionHistory = new Vec3[5];
    private int motionHistoryIndex = 0;
    private Vec3 predictedMotion = Vec3.ZERO;
    private double lastDistance = 0;
    private double lastTargetSpeed = 0;

    public AutoRod() {
        super("AutoRod", category.combat, "");

        this.registerSetting(onlyWhileKillAura = new ButtonSetting("Only while killAura", false));
        this.registerSetting(range = new SliderSetting("Max range", 10, 3, 15, 0.1, () -> !onlyWhileKillAura.isToggled()));
        this.registerSetting(minRange = new SliderSetting("Min range", 3, 0, 10, 0.1, () -> !onlyWhileKillAura.isToggled()));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(prioritizeHurt = new ButtonSetting("Prioritize hurt", true));
        this.registerSetting(checkLineOfSight = new ButtonSetting("Check line of sight", true));
        this.registerSetting(targetSwitchCooldown = new SliderSetting("Target switch cooldown", 500, 0, 2000, 50, "ms"));

        this.registerSetting(clickMode = new ModeValue("Click mode", this)
                .add(new LowCPSAutoClicker("Normal", this, false, true))
                .add(new NormalAutoClicker("NormalFast", this, false, true))
                .add(new RecordAutoClicker("Record", this, false, true))
        );

        this.registerSetting(throwDelay = new SliderSetting("Throw delay", 100, 0, 500, 10, "ms"));
        this.registerSetting(maxThrowInterval = new SliderSetting("Max throw interval", 1500, 500, 3000, 50, "ms"));
        this.registerSetting(minThrowInterval = new SliderSetting("Min throw interval", 500, 100, 1500, 50, "ms"));
        this.registerSetting(motionThreshold = new SliderSetting("Motion threshold", 0.15, 0, 1, 0.01));

        this.registerSetting(aimSpeed = new SliderSetting("Aim speed", 10, 1, 20, 0.1));
        this.registerSetting(moveFix = new ModeSetting("MoveFix", RotationHandler.MoveFix.MODES, 0));
        this.registerSetting(maxAngleChange = new SliderSetting("Max angle change", 180, 5, 180, 1, "°"));
        this.registerSetting(advancedRotation = new ButtonSetting("Advanced rotation", true));

        this.registerSetting(prediction = new ButtonSetting("Prediction", true));
        this.registerSetting(smartPrediction = new ButtonSetting("Smart prediction", true, prediction::isToggled));
        this.registerSetting(dynamicPrediction = new ButtonSetting("Dynamic prediction", true, prediction::isToggled));
        this.registerSetting(predictionTicks = new SliderSetting("Prediction ticks", 2, 0, 10, 1, "ticks", () -> prediction.isToggled() && !smartPrediction.isToggled()));
        this.registerSetting(predictionSmoothing = new SliderSetting("Prediction smoothing", 0.5, 0, 1, 0.1, () -> prediction.isToggled() && dynamicPrediction.isToggled()));

        this.registerSetting(drawPos = new ButtonSetting("Draw predicted pos", false, prediction::isToggled));
        this.registerSetting(silentSwitch = new ButtonSetting("Silent switch", true));
    }

    // ---- Vec3 数学助手（script.classes.Vec3 缺失这些方法，本地实现）----
    private static double vlen(Vec3 v) { return MathHelper.sqrt_double(v.x * v.x + v.y * v.y + v.z * v.z); }
    private static Vec3 vscale(Vec3 v, double s) { return new Vec3(v.x * s, v.y * s, v.z * s); }
    private static Vec3 vnorm(Vec3 v) {
        double l = vlen(v);
        return l < 1E-6 ? new Vec3(0, 0, 0) : new Vec3(v.x / l, v.y / l, v.z / l);
    }
    private static Vec3 vcross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }
    private static double vdot(Vec3 a, Vec3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }

    @Override
    public void onEnable() {
        clickMode.enable();
        fromSlot = -1;
        pos = null;
        lastYaw = lastPitch = null;
        lastThrowTime = 0;
        hasThrown = false;
        lastTargetSwitchTime = 0;
        targetMotionHistory = new Vec3[5];
        motionHistoryIndex = 0;
        predictedMotion = Vec3.ZERO;
    }

    @Override
    public void onDisable() {
        clickMode.disable();
        if (fromSlot != -1 && mc.thePlayer != null) {
            restoreOriginalSlot();
            fromSlot = -1;
        }
    }

    private void restoreOriginalSlot() {
        SlotHandler.setCurrentSlot(fromSlot);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        updateTarget();
        updatePredictionTicks();

        if (target != null) {
            int slot = getRod();
            if (slot == -1) {
                if (fromSlot != -1) { restoreOriginalSlot(); fromSlot = -1; }
                return;
            }
            if (fromSlot == -1) fromSlot = SlotHandler.getCurrentSlot();
            switchToRod(slot);
            if (shouldThrowRod()) {
                hasThrown = true;
                lastThrowTime = System.currentTimeMillis();
            }
        } else if (fromSlot != -1) {
            restoreOriginalSlot();
            fromSlot = -1;
            hasThrown = false;
        }
    }

    private void updateTarget() {
        target = null;
        if (KillAura.target != null && KillAura.target.isEntityAlive()) {
            double distance = new Vec3(KillAura.target).distanceTo(mc.thePlayer);
            if (distance > minRange.getInput() && distance <= range.getInput() && canTarget(KillAura.target)) {
                setNewTarget(KillAura.target);
                return;
            }
        }
        if (!onlyWhileKillAura.isToggled()) {
            List<EntityLivingBase> potentialTargets = mc.theWorld.playerEntities.stream()
                    .filter(p -> p != mc.thePlayer && p.isEntityAlive())
                    .filter(p -> !AntiBot.isBot(p))
                    .filter(p -> !ignoreTeammates.isToggled() || !Utils.isTeamMate(p))
                    .filter(p -> !Utils.isFriended(p))
                    .filter(this::isInRange)
                    .filter(p -> !checkLineOfSight.isToggled() || hasLineOfSight(p))
                    .collect(Collectors.toList());

            if (!potentialTargets.isEmpty()) {
                potentialTargets.sort(Comparator.<EntityLivingBase>comparingInt(p -> prioritizeHurt.isToggled() && p.hurtTime > 0 ? 0 : 1)
                        .thenComparingDouble(p -> mc.thePlayer.getDistanceToEntity(p)));
                setNewTarget(potentialTargets.get(0));
            }
        }
    }

    private void setNewTarget(EntityLivingBase newTarget) {
        if (newTarget != target) {
            if (System.currentTimeMillis() - lastTargetSwitchTime < targetSwitchCooldown.getInput()) return;
            lastTarget = target;
            target = newTarget;
            lastTargetSwitchTime = System.currentTimeMillis();
            targetMotionHistory = new Vec3[5];
            motionHistoryIndex = 0;
            predictedMotion = Vec3.ZERO;
            if (target != null) {
                lastDistance = mc.thePlayer.getDistanceToEntity(target);
                lastTargetSpeed = 0;
            }
        }
    }

    private boolean canTarget(EntityLivingBase entity) {
        if (checkLineOfSight.isToggled() && !hasLineOfSight(entity)) return false;
        if (ignoreTeammates.isToggled() && Utils.isTeamMate(entity)) return false;
        return !Utils.isFriended((EntityPlayer) entity);
    }

    private boolean isInRange(EntityLivingBase entity) {
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        return distance > minRange.getInput() && distance <= range.getInput();
    }

    private boolean hasLineOfSight(EntityLivingBase entity) {
        Vec3 targetPos = Utils.getEyePos(entity);
        Vec3 fromPos = Utils.getEyePos();
        return RotationUtils.rayCast(targetPos.distanceTo(fromPos), PlayerRotation.getYaw(targetPos), PlayerRotation.getPitch(targetPos)) == null;
    }

    private void switchToRod(int slot) {
        SlotHandler.setCurrentSlot(slot);
    }

    private boolean shouldThrowRod() {
        long timeSinceLastThrow = System.currentTimeMillis() - lastThrowTime;
        if (timeSinceLastThrow < throwDelay.getInput()) return false;
        if (!hasThrown) return true;

        Vec3 currentMotion = new Vec3(
                target.posX - target.lastTickPosX,
                target.posY - target.lastTickPosY,
                target.posZ - target.lastTickPosZ
        );
        double currentSpeed = vlen(currentMotion);
        double acceleration = Math.abs(currentSpeed - lastTargetSpeed);
        lastTargetSpeed = currentSpeed;

        targetMotionHistory[motionHistoryIndex % targetMotionHistory.length] = currentMotion;
        motionHistoryIndex++;

        Vec3 avgMotion = Vec3.ZERO;
        int count = 0;
        for (Vec3 motion : targetMotionHistory) {
            if (motion != null) { avgMotion = avgMotion.add(motion); count++; }
        }
        if (count > 0) avgMotion = vscale(avgMotion, 1.0 / count);

        if (dynamicPrediction.isToggled()) {
            double smoothing = predictionSmoothing.getInput();
            predictedMotion = vscale(predictedMotion, smoothing).add(vscale(avgMotion, 1.0 - smoothing));
        } else {
            predictedMotion = avgMotion;
        }

        double currentDistance = mc.thePlayer.getDistanceToEntity(target);
        double distanceChange = Math.abs(currentDistance - lastDistance);
        lastDistance = currentDistance;

        if (target.hurtTime > 0) return true;
        if (currentSpeed > motionThreshold.getInput()) return true;
        if (distanceChange > 0.1) return true;
        if (acceleration > 0.05) return true;

        double distanceFactor = MathHelper.clamp_double((currentDistance - minRange.getInput()) / (range.getInput() - minRange.getInput()), 0, 1);
        long dynamicInterval = (long) (minThrowInterval.getInput() + distanceFactor * (maxThrowInterval.getInput() - minThrowInterval.getInput()));
        return timeSinceLastThrow > dynamicInterval;
    }

    private int getRod() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.fishing_rod) return i;
        }
        return -1;
    }

    @SubscribeEvent
    public void onRotation(RotationEvent event) {
        if (target == null) {
            lastYaw = lastPitch = null;
            pos = null;
            return;
        }
        if (lastYaw == null || lastPitch == null) {
            lastYaw = event.getYaw();
            lastPitch = event.getPitch();
        }

        Vec3 hitPos = getAdvancedHitPos(target);
        float[] rotations = advancedRotation.isToggled()
                ? calculateAdvancedRotation(hitPos, lastYaw, lastPitch)
                : calculateBasicRotation(hitPos);

        float newYaw = rotations[0];
        float newPitch = rotations[1];

        float yawDiff = MathHelper.wrapAngleTo180_float(newYaw - lastYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(newPitch - lastPitch);
        float maxChange = (float) maxAngleChange.getInput();
        if (Math.abs(yawDiff) > maxChange) newYaw = lastYaw + (yawDiff > 0 ? maxChange : -maxChange);
        if (Math.abs(pitchDiff) > maxChange) newPitch = lastPitch + (pitchDiff > 0 ? maxChange : -maxChange);

        float aimSpeedValue = (float) aimSpeed.getInput();
        newYaw = AimSimulator.rotMove(newYaw, lastYaw, aimSpeedValue);
        newPitch = AimSimulator.rotMove(newPitch, lastPitch, aimSpeedValue);

        event.setYaw(lastYaw = newYaw);
        event.setPitch(lastPitch = newPitch);
        event.setMoveFix(RotationHandler.MoveFix.values()[(int) moveFix.getInput()]);

        pos = new net.minecraft.util.Vec3(hitPos.x, hitPos.y - target.getEyeHeight(), hitPos.z);
    }

    private Vec3 getAdvancedHitPos(EntityLivingBase entity) {
        Vec3 currentPos = Utils.getEyePos(entity);
        Vec3 motionToUse = predictedMotion;
        if (motionToUse == null || vlen(motionToUse) < 0.001) {
            motionToUse = new Vec3(
                    entity.posX - entity.lastTickPosX,
                    entity.posY - entity.lastTickPosY,
                    entity.posZ - entity.lastTickPosZ
            );
        }
        double distance = currentPos.distanceTo(Utils.getEyePos());
        double speed = vlen(motionToUse);
        int basePredTicks = predTicks;
        if (dynamicPrediction.isToggled()) {
            double distanceFactor = MathHelper.clamp_double((distance - 3) / 10.0, 0, 1);
            double speedFactor = MathHelper.clamp_double(speed / 0.5, 0, 2);
            basePredTicks = (int) Math.round(basePredTicks * (1 + distanceFactor * speedFactor));
        }
        if (motionToUse.y < 0) {
            motionToUse = new Vec3(motionToUse.x, motionToUse.y - 0.08, motionToUse.z);
        }
        // 线性预测（内联，避免 MoveUtil.predictedPos 依赖不确定）
        return currentPos.add(motionToUse.x * basePredTicks, motionToUse.y * basePredTicks, motionToUse.z * basePredTicks);
    }

    private float[] calculateBasicRotation(Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(Utils.getEyePos());
        double dist = MathHelper.sqrt_double(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) (Math.atan2(diff.z, diff.x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(diff.y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    private float[] calculateAdvancedRotation(Vec3 targetPos, float currentYaw, float currentPitch) {
        Vec3 currentDir = getForwardVector(currentYaw, currentPitch);
        Vec3 targetDir = vnorm(targetPos.subtract(Utils.getEyePos()));

        Vec3 rotationAxis = vcross(currentDir, targetDir);
        if (vlen(rotationAxis) < 1E-6) return new float[]{currentYaw, currentPitch};
        rotationAxis = vnorm(rotationAxis);
        double cosAngle = MathHelper.clamp_double(vdot(currentDir, targetDir), -1.0, 1.0);
        double angle = Math.acos(cosAngle);

        double distance = targetPos.distanceTo(Utils.getEyePos());
        double precisionFactor = MathHelper.clamp_double(1.0 - (distance - 3) / 12.0, 0.5, 1.0);
        angle *= precisionFactor;

        double halfAngle = angle * 0.5;
        double sinHalf = Math.sin(halfAngle);
        double qx = rotationAxis.x * sinHalf;
        double qy = rotationAxis.y * sinHalf;
        double qz = rotationAxis.z * sinHalf;
        double qw = Math.cos(halfAngle);

        double sinP = 2.0 * (qw * qy - qz * qx);
        sinP = MathHelper.clamp_double(sinP, -1.0, 1.0);
        float pitch = (float) Math.toDegrees(Math.asin(sinP));

        double sinY = 2.0 * (qw * qx + qy * qz);
        double cosY = 1.0 - 2.0 * (qx * qx + qy * qy);
        float yaw = (float) Math.toDegrees(Math.atan2(sinY, cosY));

        if (prediction.isToggled() && predictedMotion != null) {
            double speed = vlen(predictedMotion);
            if (speed > 0.1) {
                double leadFactor = MathHelper.clamp_double(speed / 0.5, 0, 1);
                Vec3 leadOffset = vscale(vnorm(predictedMotion), 0.2 * leadFactor);
                Vec3 leadPos = targetPos.add(leadOffset);
                float[] leadRot = calculateBasicRotation(leadPos);
                yaw = yaw + (leadRot[0] - yaw) * 0.3f;
                pitch = pitch + (leadRot[1] - pitch) * 0.3f;
            }
        }
        return new float[]{yaw, pitch};
    }

    private Vec3 getForwardVector(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float x = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float y = -MathHelper.sin(pitchRad);
        float z = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        return vnorm(new Vec3(x, y, z));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (drawPos.isToggled() && prediction.isToggled() && pos != null) {
            Blink.drawBox(pos);
        }
    }

    private void updatePredictionTicks() {
        if (prediction.isToggled()) {
            if (smartPrediction.isToggled()) {
                predTicks = (int) Math.floor(mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime() / 50.0);
                predTicks = MathHelper.clamp_int(predTicks, 0, 10);
            } else {
                predTicks = (int) predictionTicks.getInput();
            }
        } else {
            predTicks = 0;
        }
    }

    @Override
    public boolean click() {
        ItemStack item = SlotHandler.getHeldItem();
        if (item != null && item.getItem() instanceof ItemFishingRod && target != null && hasThrown) {
            Utils.sendClick(1, true);
            Utils.sendClick(1, false);
            hasThrown = false;
            if (fromSlot != -1) SlotHandler.setCurrentSlot(fromSlot);
            return true;
        }
        return false;
    }

    @Override
    public String getInfo() {
        return target != null ? target.getName() : null;
    }
}
