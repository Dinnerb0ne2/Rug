package myau.util;

import myau.mixin.IAccessorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final PerlinNoise perlinNoise = new PerlinNoise(new Random(System.nanoTime()));
    private static float brownianYawOffset = 0.0f;
    private static float brownianPitchOffset = 0.0f;

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) angle = maxAngle;
        else if (angle < -maxAngle) angle = -maxAngle;
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }

    public static float getSensitivityGCD() {
        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return f * f * f * 8.0F * 0.15F;
    }

    public static Vec3 getBestHitVec(AxisAlignedBB boundingBox) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double ex = MathHelper.clamp_double(eyePos.xCoord, boundingBox.minX, boundingBox.maxX);
        double ey = MathHelper.clamp_double(eyePos.yCoord, boundingBox.minY, boundingBox.maxY);
        double ez = MathHelper.clamp_double(eyePos.zCoord, boundingBox.minZ, boundingBox.maxZ);
        return new Vec3(ex, ey, ez);
    }

    public static Vec3 getStableHitVec(AxisAlignedBB boundingBox, double distance) {
        Vec3 bestHit = getBestHitVec(boundingBox);
        if (distance < 2.5) {
            Vec3 center = new Vec3(
                (boundingBox.minX + boundingBox.maxX) / 2.0,
                (boundingBox.minY + boundingBox.maxY) / 2.0,
                (boundingBox.minZ + boundingBox.maxZ) / 2.0
            );
            double blend = Math.max(0.0, (2.5 - distance) / 2.5) * 0.5;
            return new Vec3(
                bestHit.xCoord * (1.0 - blend) + center.xCoord * blend,
                bestHit.yCoord * (1.0 - blend) + center.yCoord * blend,
                bestHit.zCoord * (1.0 - blend) + center.zCoord * blend
            );
        }
        return bestHit;
    }

    public static Vec3 getSmartHitVec(AxisAlignedBB boundingBox, float currentYaw, float currentPitch) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(currentPitch, currentYaw);
        Vec3 endPos = eyePos.addVector(lookVec.xCoord * 6.0, lookVec.yCoord * 6.0, lookVec.zCoord * 6.0);
        MovingObjectPosition mop = boundingBox.calculateIntercept(eyePos, endPos);
        if (mop != null && mop.hitVec != null) {
            return mop.hitVec;
        }
        return getBestHitVec(boundingBox);
    }

    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 bestHitVec = getBestHitVec(boundingBox);
        double deltaX = bestHitVec.xCoord - eyePos.xCoord;
        double deltaY = bestHitVec.yCoord - eyePos.yCoord;
        double deltaZ = bestHitVec.zCoord - eyePos.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, currentYaw, currentPitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsToBoxStable(AxisAlignedBB boundingBox, float currentYaw, float currentPitch, float maxAngle, float smoothFactor, double distance) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 hitVec = getStableHitVec(boundingBox, distance);
        double deltaX = hitVec.xCoord - eyePos.xCoord;
        double deltaY = hitVec.yCoord - eyePos.yCoord;
        double deltaZ = hitVec.zCoord - eyePos.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, currentYaw, currentPitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsToSmartVec(AxisAlignedBB boundingBox, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 smartHitVec = getSmartHitVec(boundingBox, currentYaw, currentPitch);
        double deltaX = smartHitVec.xCoord - eyePos.xCoord;
        double deltaY = smartHitVec.yCoord - eyePos.yCoord;
        double deltaZ = smartHitVec.zCoord - eyePos.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, currentYaw, currentPitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsToEntity(EntityLivingBase entity, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaY = (entity.posY + entity.getEyeHeight()) - eyePos.yCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, currentYaw, currentPitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        float gcd = getSensitivityGCD();
        float newYaw = currentYaw + yawDelta;
        float newPitch = currentPitch + pitchDelta;
        if (gcd > 0.0f) {
            newYaw = Math.round(newYaw / gcd) * gcd;
            newPitch = Math.round(newPitch / gcd) * gcd;
        }
        return new float[]{newYaw, newPitch};
    }

    public static float[] getRawTargetBox(AxisAlignedBB box) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 bestHitVec = getBestHitVec(box);
        double deltaX = bestHitVec.xCoord - eyePos.xCoord;
        double deltaY = bestHitVec.yCoord - eyePos.yCoord;
        double deltaZ = bestHitVec.zCoord - eyePos.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return new float[]{targetYaw, targetPitch};
    }

    public static float[] getRawTargetSmartVec(AxisAlignedBB box, float currentYaw, float currentPitch) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 smartHitVec = getSmartHitVec(box, currentYaw, currentPitch);
        double deltaX = smartHitVec.xCoord - eyePos.xCoord;
        double deltaY = smartHitVec.yCoord - eyePos.yCoord;
        double deltaZ = smartHitVec.zCoord - eyePos.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return new float[]{targetYaw, targetPitch};
    }

    public static float[] getRawTargetEntity(EntityLivingBase entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaY = (entity.posY + entity.getEyeHeight()) - eyePos.yCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return new float[]{targetYaw, targetPitch};
    }

    public static float[] simulatedAnnealingBoxStep(
            AxisAlignedBB box,
            float playerYaw, float playerPitch,
            float currentPointX, float currentPointY, float currentPointZ,
            float temperature, int iterations,
            boolean checkWalls) {

        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);

        double cpX = Math.max(box.minX, Math.min(box.maxX, currentPointX));
        double cpY = Math.max(box.minY, Math.min(box.maxY, currentPointY));
        double cpZ = Math.max(box.minZ, Math.min(box.maxZ, currentPointZ));

        double currentEnergy = calculateBoxEnergy(cpX, cpY, cpZ, eyePos, playerYaw, playerPitch);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double boxW = box.maxX - box.minX;
        double boxH = box.maxY - box.minY;
        double boxD = box.maxZ - box.minZ;
        double maxBoxDim = Math.max(boxW, Math.max(boxH, boxD));

        double bestEnergy = currentEnergy;
        double bestX = cpX, bestY = cpY, bestZ = cpZ;

        for (int i = 0; i < iterations; i++) {
            double tempScale = temperature * (1.0 - (double) i / iterations);
            double perturbScale = Math.min(maxBoxDim * 0.4, tempScale * 0.15 + 0.05);

            double dx, dy, dz;
            if (rand.nextBoolean()) {
                dx = rand.nextGaussian() * perturbScale;
                dy = rand.nextGaussian() * perturbScale;
                dz = rand.nextGaussian() * perturbScale;
            } else {
                dx = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.5;
                dy = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.5;
                dz = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.5;
            }

            if (rand.nextDouble() < 0.05) {
                dx *= 3.0; dy *= 3.0; dz *= 3.0;
            }

            double newX = Math.max(box.minX, Math.min(box.maxX, cpX + dx));
            double newY = Math.max(box.minY, Math.min(box.maxY, cpY + dy));
            double newZ = Math.max(box.minZ, Math.min(box.maxZ, cpZ + dz));

            if (checkWalls) {
                Vec3 newVec = new Vec3(newX, newY, newZ);
                MovingObjectPosition wallHit = mc.theWorld.rayTraceBlocks(eyePos, newVec);
                if (wallHit != null) {
                    continue;
                }
            }

            double newEnergy = calculateBoxEnergy(newX, newY, newZ, eyePos, playerYaw, playerPitch);
            double deltaE = newEnergy - currentEnergy;

            if (deltaE <= 0 || rand.nextDouble() < Math.exp(-deltaE / Math.max(0.001, tempScale))) {
                cpX = newX;
                cpY = newY;
                cpZ = newZ;
                currentEnergy = newEnergy;

                if (currentEnergy < bestEnergy) {
                    bestEnergy = currentEnergy;
                    bestX = cpX; bestY = cpY; bestZ = cpZ;
                }
            }
        }

        double deltaX = bestX - eyePos.xCoord;
        double deltaY = bestY - eyePos.yCoord;
        double deltaZ = bestZ - eyePos.zCoord;
        double hDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-Math.atan2(deltaY, hDist) * 180.0 / Math.PI);

        return new float[]{targetYaw, targetPitch, (float) bestX, (float) bestY, (float) bestZ};
    }

    private static double calculateBoxEnergy(double px, double py, double pz, Vec3 eyePos, float playerYaw, float playerPitch) {
        double deltaX = px - eyePos.xCoord;
        double deltaY = py - eyePos.yCoord;
        double deltaZ = pz - eyePos.zCoord;
        double hDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double rotYaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
        double rotPitch = -Math.toDegrees(Math.atan2(deltaY, hDist));

        double yawDiff = MathHelper.wrapAngleTo180_float((float) (rotYaw - playerYaw));
        double pitchDiff = MathHelper.wrapAngleTo180_float((float) (rotPitch - playerPitch));
        double angleDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        double heightDiff = Math.abs(py - eyePos.yCoord);

        return 0.5 * angleDiff + 0.15 * distance + 0.35 * heightDiff;
    }

    public static float[] smoothBack(float currentYaw, float currentPitch,
                                     float targetYaw, float targetPitch, float speed) {
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);
        float factor = Math.max(0.01f, Math.min(1.0f, speed));
        yawDelta *= factor;
        pitchDelta *= factor;
        if (Math.abs(yawDelta) < 0.05f) yawDelta = 0.0f;
        if (Math.abs(pitchDelta) < 0.05f) pitchDelta = 0.0f;

        float newYaw = currentYaw + yawDelta;
        float newPitch = currentPitch + pitchDelta;
        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            newYaw = Math.round(newYaw / gcd) * gcd;
            newPitch = Math.round(newPitch / gcd) * gcd;
        }
        return new float[]{newYaw, newPitch};
    }

    public static float getGaussianNoise() {
        return (float) ThreadLocalRandom.current().nextGaussian();
    }

    public static float getPerlinNoise(float x, float y) {
        return (float) perlinNoise.octaveNoise(x, y, 0.0, 4, 0.5);
    }

    public static float getWaveNoise(float time, float frequency) {
        return (float) (Math.sin(time * frequency) * 0.6f
                + Math.sin(time * frequency * 2.17f + 1.3f) * 0.25f
                + Math.sin(time * frequency * 0.73f + 2.7f) * 0.15f);
    }

    public static float[] applyBrownianMotion(float yaw, float pitch, float intensity) {
        brownianYawOffset += (float) ThreadLocalRandom.current().nextGaussian() * intensity;
        brownianPitchOffset += (float) ThreadLocalRandom.current().nextGaussian() * intensity * 0.5f;

        if (brownianYawOffset > 15.0f) brownianYawOffset = 15.0f;
        if (brownianYawOffset < -15.0f) brownianYawOffset = -15.0f;
        if (brownianPitchOffset > 15.0f) brownianPitchOffset = 15.0f;
        if (brownianPitchOffset < -15.0f) brownianPitchOffset = -15.0f;

        float newYaw = yaw + brownianYawOffset;
        float newPitch = pitch + brownianPitchOffset;
        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            newYaw = Math.round(newYaw / gcd) * gcd;
            newPitch = Math.round(newPitch / gcd) * gcd;
        }
        return new float[]{newYaw, newPitch};
    }

    public static float[] applyBrownianMotion(float yaw, float pitch, float intensity, AxisAlignedBB boundingBox, double attackRange) {
        if (boundingBox == null) {
            return applyBrownianMotion(yaw, pitch, intensity);
        }
        brownianYawOffset += (float) ThreadLocalRandom.current().nextGaussian() * intensity;
        brownianPitchOffset += (float) ThreadLocalRandom.current().nextGaussian() * intensity * 0.5f;

        if (brownianYawOffset > 15.0f) brownianYawOffset = 15.0f;
        if (brownianYawOffset < -15.0f) brownianYawOffset = -15.0f;
        if (brownianPitchOffset > 15.0f) brownianPitchOffset = 15.0f;
        if (brownianPitchOffset < -15.0f) brownianPitchOffset = -15.0f;

        float testYaw = yaw + brownianYawOffset;
        float testPitch = pitch + brownianPitchOffset;

        if (rayTrace(boundingBox, testYaw, testPitch, attackRange) != null) {
            float gcd = getSensitivityGCD();
            if (gcd > 0.0f) {
                testYaw = Math.round(testYaw / gcd) * gcd;
                testPitch = Math.round(testPitch / gcd) * gcd;
            }
            return new float[]{testYaw, testPitch};
        }

        for (int i = 0; i < 5; i++) {
            brownianYawOffset *= 0.5f;
            brownianPitchOffset *= 0.5f;
            testYaw = yaw + brownianYawOffset;
            testPitch = pitch + brownianPitchOffset;
            if (rayTrace(boundingBox, testYaw, testPitch, attackRange) != null) {
                float gcd = getSensitivityGCD();
                if (gcd > 0.0f) {
                    testYaw = Math.round(testYaw / gcd) * gcd;
                    testPitch = Math.round(testPitch / gcd) * gcd;
                }
                return new float[]{testYaw, testPitch};
            }
        }

        brownianYawOffset = 0.0f;
        brownianPitchOffset = 0.0f;
        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            yaw = Math.round(yaw / gcd) * gcd;
            pitch = Math.round(pitch / gcd) * gcd;
        }
        return new float[]{yaw, pitch};
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
        double[] coords = new double[]{vector.xCoord, vector.yCoord, vector.zCoord};
        double[] minCoords = new double[]{boundingBox.minX, boundingBox.minY, boundingBox.minZ};
        double[] maxCoords = new double[]{boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ};
        for (int i = 0; i < 3; ++i) {
            if (coords[i] > maxCoords[i]) { coords[i] = maxCoords[i]; continue; }
            if (coords[i] < minCoords[i]) coords[i] = minCoords[i];
        }
        return new Vec3(coords[0], coords[1], coords[2]);
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        return distanceToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        return clampVecToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        return clampVecToBox(boundingBox, mc.thePlayer.getPositionEyes(1.0f));
    }

    public static double clampVecToBox(AxisAlignedBB boundingBox, Vec3 point) {
        if (boundingBox.isVecInside(point)) return 0.0;
        Vec3 clampedPoint = clampVecToBox(point, boundingBox);
        double deltaX = clampedPoint.xCoord - point.xCoord;
        double deltaY = clampedPoint.yCoord - point.yCoord;
        double deltaZ = clampedPoint.zCoord - point.zCoord;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) return 0.0f;
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - mc.thePlayer.rotationYaw)) * 2.0f;
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return MathHelper.wrapAngleTo180_float((float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0f - mc.thePlayer.rotationYaw);
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 lookVec = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(Entity entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        Vec3 targetPos = clampVecToBox(eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(AxisAlignedBB boundingBox, float yaw, float pitch, double distance) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return boundingBox.calculateIntercept(eyePos, targetPos);
    }

    public static class BezierRotator {
        private float startYaw, startPitch;
        private float targetYaw, targetPitch;
        private float ctrl1Yaw, ctrl1Pitch;
        private float ctrl2Yaw, ctrl2Pitch;
        private float progress = 1.0f;
        private float speed;
        private final ThreadLocalRandom rand = ThreadLocalRandom.current();

        public void setup(float curYaw, float curPitch, float tgtYaw, float tgtPitch, float speed) {
            this.startYaw = curYaw;
            this.startPitch = curPitch;
            this.targetYaw = tgtYaw;
            this.targetPitch = tgtPitch;
            this.speed = Math.max(0.05f, speed);

            float yawDelta = MathHelper.wrapAngleTo180_float(tgtYaw - curYaw);
            float pitchDelta = MathHelper.wrapAngleTo180_float(tgtPitch - curPitch);

            ctrl1Yaw = curYaw + yawDelta * (0.25f + rand.nextFloat() * 0.25f) + (float)rand.nextGaussian() * 15.0f;
            ctrl1Pitch = curPitch + pitchDelta * (0.25f + rand.nextFloat() * 0.25f) + (float)rand.nextGaussian() * 8.0f;

            ctrl2Yaw = curYaw + yawDelta * (0.5f + rand.nextFloat() * 0.25f) + (float)rand.nextGaussian() * 15.0f;
            ctrl2Pitch = curPitch + pitchDelta * (0.5f + rand.nextFloat() * 0.25f) + (float)rand.nextGaussian() * 8.0f;

            this.progress = 0.0f;
        }

        public float[] getNextRotation() {
            if (progress >= 1.0f) {
                return new float[]{targetYaw, targetPitch};
            }
            progress += speed;
            if (progress > 1.0f) progress = 1.0f;

            float t = progress;
            float u = 1.0f - t;
            float tt = t * t;
            float uu = u * u;
            float uuu = uu * u;
            float ttt = tt * t;

            float pY = uuu * startYaw + 3 * uu * t * ctrl1Yaw + 3 * u * tt * ctrl2Yaw + ttt * targetYaw;
            float pP = uuu * startPitch + 3 * uu * t * ctrl1Pitch + 3 * u * tt * ctrl2Pitch + ttt * targetPitch;

            pY = startYaw + MathHelper.wrapAngleTo180_float(pY - startYaw);
            pP = startPitch + MathHelper.wrapAngleTo180_float(pP - startPitch);

            float gcd = getSensitivityGCD();
            if (gcd > 0.0f) {
                pY = Math.round(pY / gcd) * gcd;
                pP = Math.round(pP / gcd) * gcd;
            }
            return new float[]{pY, pP};
        }

        public boolean isFinished() {
            return progress >= 1.0f;
        }

        public boolean needsUpdate(float tgtYaw, float tgtPitch, float threshold) {
            if (progress >= 1.0f) return true;
            float yDiff = Math.abs(MathHelper.wrapAngleTo180_float(tgtYaw - this.targetYaw));
            float pDiff = Math.abs(MathHelper.wrapAngleTo180_float(tgtPitch - this.targetPitch));
            return yDiff > threshold || pDiff > threshold;
        }
    }

    private static class PerlinNoise {
        private final int[] p;
        public PerlinNoise(Random random) {
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;
            for (int i = 255; i > 0; i--) { int j = random.nextInt(i + 1); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
            p = new int[512];
            for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
        }
        private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        private double lerp(double t, double a, double b) { return a + t * (b - a); }
        private double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
        public double noise(double x, double y, double z) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            int Z = (int) Math.floor(z) & 255;
            x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
            double u = fade(x), v = fade(y), w = fade(z);
            int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z;
            int B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
            return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                                lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
                        lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                                lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1))));
        }
        public double octaveNoise(double x, double y, double z, int octaves, double persistence) {
            double total = 0.0;
            double frequency = 1.0;
            double amplitude = 1.0;
            double maxValue = 0.0;
            for (int i = 0; i < octaves; i++) {
                total += noise(x * frequency, y * frequency, z * frequency) * amplitude;
                maxValue += amplitude;
                amplitude *= persistence;
                frequency *= 2.0;
            }
            return total / maxValue;
        }
    }
}