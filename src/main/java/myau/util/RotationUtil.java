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

    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 bestHitVec = getBestHitVec(boundingBox);
        double deltaX = bestHitVec.xCoord - eyePos.xCoord;
        double deltaY = bestHitVec.yCoord - eyePos.yCoord;
        double deltaZ = bestHitVec.zCoord - eyePos.zCoord;
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
        if (gcd > 0.0f) {
            yawDelta = Math.round(yawDelta / gcd) * gcd;
            pitchDelta = Math.round(pitchDelta / gcd) * gcd;
        }
        return new float[]{currentYaw + yawDelta, currentPitch + pitchDelta};
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

    // ==================== Simulated Annealing ====================

    public static float[] simulatedAnnealingStep(
            float currentYaw, float currentPitch,
            float targetYaw, float targetPitch,
            float temperature, float smoothFactor) {

        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

        float convergenceFactor = Math.max(0.05f, (1.0f - smoothFactor) * 0.5f + 0.05f);
        float baseYawStep = yawDelta * convergenceFactor;
        float basePitchStep = pitchDelta * convergenceFactor;

        float yawPerturb = (float) ThreadLocalRandom.current().nextGaussian() * temperature;
        float pitchPerturb = (float) ThreadLocalRandom.current().nextGaussian() * temperature * 0.6f;

        float candidateYawStep = baseYawStep + yawPerturb;
        float candidatePitchStep = basePitchStep + pitchPerturb;

        float currentEnergy = yawDelta * yawDelta + pitchDelta * pitchDelta;
        float newYawRemaining = yawDelta - candidateYawStep;
        float newPitchRemaining = pitchDelta - candidatePitchStep;
        float newEnergy = newYawRemaining * newYawRemaining + newPitchRemaining * newPitchRemaining;
        float deltaE = newEnergy - currentEnergy;

        float yawStep, pitchStep;
        if (deltaE <= 0) {
            yawStep = candidateYawStep;
            pitchStep = candidatePitchStep;
        } else if (temperature > 0.001f) {
            float acceptance = (float) Math.exp(-deltaE / (temperature * 100.0f));
            if (ThreadLocalRandom.current().nextFloat() < acceptance) {
                yawStep = candidateYawStep;
                pitchStep = candidatePitchStep;
            } else {
                yawStep = baseYawStep;
                pitchStep = basePitchStep;
            }
        } else {
            yawStep = baseYawStep;
            pitchStep = basePitchStep;
        }

        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            yawStep = Math.round(yawStep / gcd) * gcd;
            pitchStep = Math.round(pitchStep / gcd) * gcd;
        }

        return new float[]{currentYaw + yawStep, currentPitch + pitchStep};
    }

    // ==================== Smooth Back ====================

    public static float[] smoothBack(float currentYaw, float currentPitch,
                                     float targetYaw, float targetPitch, float speed) {
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);
        float factor = Math.max(0.01f, Math.min(1.0f, speed));
        yawDelta *= factor;
        pitchDelta *= factor;
        if (Math.abs(yawDelta) < 0.05f) yawDelta = 0.0f;
        if (Math.abs(pitchDelta) < 0.05f) pitchDelta = 0.0f;
        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            yawDelta = Math.round(yawDelta / gcd) * gcd;
            pitchDelta = Math.round(pitchDelta / gcd) * gcd;
        }
        return new float[]{currentYaw + yawDelta, currentPitch + pitchDelta};
    }

    // ==================== Noise ====================

    public static float getGaussianNoise() {
        return (float) ThreadLocalRandom.current().nextGaussian();
    }

    public static float getPerlinNoise(float x, float y) {
        return (float) perlinNoise.noise(x, y);
    }

    public static float getWaveNoise(float time, float frequency) {
        return (float) (Math.sin(time * frequency) * 0.6f
                + Math.sin(time * frequency * 2.17f + 1.3f) * 0.25f
                + Math.sin(time * frequency * 0.73f + 2.7f) * 0.15f);
    }

    // ==================== Vector/Distance ====================

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
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return distanceToBox(boundingBox);
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

    // ==================== Ray Trace ====================

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

    // ==================== Perlin Noise ====================

    private static class PerlinNoise {
        private final int[] p;

        public PerlinNoise(Random random) {
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temp = perm[i]; perm[i] = perm[j]; perm[j] = temp;
            }
            p = new int[512];
            for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
        }

        private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        private double lerp(double t, double a, double b) { return a + t * (b - a); }
        private double grad(int hash, double x, double y) {
            int h = hash & 3;
            double u = h < 2 ? x : y;
            double v = h < 2 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        public double noise(double x, double y) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            x -= Math.floor(x); y -= Math.floor(y);
            double u = fade(x), v = fade(y);
            int A = p[X] + Y, B = p[X + 1] + Y;
            return lerp(v, lerp(u, grad(p[A], x, y), grad(p[B], x - 1, y)),
                    lerp(u, grad(p[A + 1], x, y - 1), grad(p[B + 1], x - 1, y - 1)));
        }
    }
}