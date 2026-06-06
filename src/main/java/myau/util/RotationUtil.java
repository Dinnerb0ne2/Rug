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

    public static float[] getRotationsToSmartVec(AxisAlignedBB boundingBox, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 smartHitVec = getSmartHitVec(boundingBox, currentYaw, currentPitch);
        double deltaX = smartHitVec.xCoord - eyePos.xCoord;
        double deltaY = smartHitVec.yCoord - eyePos.yCoord;
        double deltaZ = smartHitVec.zCoord - eyePos.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, currentYaw, currentPitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsToEntity(EntityLivingBase entity, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        return getRotationsToEntity(entity, currentYaw, currentPitch, maxAngle, smoothFactor, 0.0f);
    }

    public static float[] getRotationsToEntity(EntityLivingBase entity, float currentYaw, float currentPitch, float maxAngle, float smoothFactor, float predictionTicks) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double deltaX = (entity.posX + entity.motionX * predictionTicks) - eyePos.xCoord;
        double deltaY = (entity.posY + entity.getEyeHeight() + entity.motionY * predictionTicks) - eyePos.yCoord;
        double deltaZ = (entity.posZ + entity.motionZ * predictionTicks) - eyePos.zCoord;
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
        return getRawTargetEntity(entity, 0.0f);
    }

    public static float[] getRawTargetEntity(EntityLivingBase entity, float predictionTicks) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double deltaX = (entity.posX + entity.motionX * predictionTicks) - eyePos.xCoord;
        double deltaY = (entity.posY + entity.getEyeHeight() + entity.motionY * predictionTicks) - eyePos.yCoord;
        double deltaZ = (entity.posZ + entity.motionZ * predictionTicks) - eyePos.zCoord;
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

        for (int i = 0; i < iterations; i++) {
            double perturbScale = temperature * 0.35 + 0.08;
            double newX = Math.max(box.minX, Math.min(box.maxX, cpX + rand.nextGaussian() * perturbScale));
            double newY = Math.max(box.minY, Math.min(box.maxY, cpY + rand.nextGaussian() * perturbScale));
            double newZ = Math.max(box.minZ, Math.min(box.maxZ, cpZ + rand.nextGaussian() * perturbScale));

            if (checkWalls) {
                Vec3 newVec = new Vec3(newX, newY, newZ);
                MovingObjectPosition wallHit = mc.theWorld.rayTraceBlocks(eyePos, newVec);
                if (wallHit != null) {
                    continue;
                }
            }

            double newEnergy = calculateBoxEnergy(newX, newY, newZ, eyePos, playerYaw, playerPitch);
            double deltaE = newEnergy - currentEnergy;

            if (deltaE <= 0 || rand.nextDouble() < Math.exp(-deltaE / Math.max(0.001, temperature))) {
                cpX = newX;
                cpY = newY;
                cpZ = newZ;
                currentEnergy = newEnergy;
            }
        }

        double deltaX = cpX - eyePos.xCoord;
        double deltaY = cpY - eyePos.yCoord;
        double deltaZ = cpZ - eyePos.zCoord;
        double hDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-Math.atan2(deltaY, hDist) * 180.0 / Math.PI);

        return new float[]{targetYaw, targetPitch, (float) cpX, (float) cpY, (float) cpZ};
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

        return 0.3 * angleDiff + 0.2 * distance + 0.5 * heightDiff;
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
        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            yawDelta = Math.round(yawDelta / gcd) * gcd;
            pitchDelta = Math.round(pitchDelta / gcd) * gcd;
        }
        return new float[]{currentYaw + yawDelta, currentPitch + pitchDelta};
    }

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
        private double grad(int hash, double x, double y) {
            int h = hash & 3; double u = h < 2 ? x : y, v = h < 2 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
        public double noise(double x, double y) {
            int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255;
            x -= Math.floor(x); y -= Math.floor(y);
            double u = fade(x), v = fade(y);
            int A = p[X] + Y, B = p[X + 1] + Y;
            return lerp(v, lerp(u, grad(p[A], x, y), grad(p[B], x - 1, y)), lerp(u, grad(p[A + 1], x, y - 1), grad(p[B + 1], x - 1, y - 1)));
        }
    }
}