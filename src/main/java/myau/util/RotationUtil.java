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
            if (yawDelta != 0.0f && Math.abs(yawDelta) < gcd) {
                yawDelta = Math.signum(yawDelta) * gcd;
            } else {
                yawDelta = Math.round(yawDelta / gcd) * gcd;
            }
            if (pitchDelta != 0.0f && Math.abs(pitchDelta) < gcd) {
                pitchDelta = Math.signum(pitchDelta) * gcd;
            } else {
                pitchDelta = Math.round(pitchDelta / gcd) * gcd;
            }
            newYaw = currentYaw + yawDelta;
            newPitch = currentPitch + pitchDelta;
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

    public static float[] simulatedAnnealingBoxStepAdvanced(
            AxisAlignedBB box,
            float playerYaw, float playerPitch,
            float currentPointX, float currentPointY, float currentPointZ,
            float temperature, int iterations,
            boolean checkWalls, int perturbationMode,
            float perturbationScale, float jumpProb,
            float energyAngleW, float energyDistW,
            float energyHeightW, float energyWallW,
            float energyRandomW, boolean adaptiveStep,
            boolean edgeExploration) {

        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);

        double cpX = Math.max(box.minX, Math.min(box.maxX, currentPointX));
        double cpY = Math.max(box.minY, Math.min(box.maxY, currentPointY));
        double cpZ = Math.max(box.minZ, Math.min(box.maxZ, currentPointZ));

        double currentEnergy = calculateAdvancedBoxEnergy(cpX, cpY, cpZ, eyePos, playerYaw, playerPitch, checkWalls, energyAngleW, energyDistW, energyHeightW, energyWallW, energyRandomW);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double boxW = box.maxX - box.minX;
        double boxH = box.maxY - box.minY;
        double boxD = box.maxZ - box.minZ;
        double maxBoxDim = Math.max(boxW, Math.max(boxH, boxD));

        double bestEnergy = currentEnergy;
        double bestX = cpX, bestY = cpY, bestZ = cpZ;
        int acceptedCount = 0;

        double currentTemp = temperature;

        for (int i = 0; i < iterations; i++) {
            double tempScale = currentTemp * (1.0 - (double) i / iterations);
            double baseStep = Math.min(maxBoxDim * 0.4, tempScale * 0.15 + 0.05);
            double perturbScale = baseStep * perturbationScale;

            if (adaptiveStep) {
                double acceptRate = (i == 0 ? 0.5 : (double) acceptedCount / i);
                if (acceptRate > 0.3) perturbScale *= 1.2;
                else perturbScale *= 0.8;
                perturbScale = Math.min(maxBoxDim * 0.8, perturbScale);
            }

            double dx, dy, dz;
            if (rand.nextDouble() < jumpProb) {
                dx = (rand.nextDouble() * 2.0 - 1.0) * maxBoxDim * 0.5;
                dy = (rand.nextDouble() * 2.0 - 1.0) * maxBoxDim * 0.5;
                dz = (rand.nextDouble() * 2.0 - 1.0) * maxBoxDim * 0.5;
            } else {
                switch (perturbationMode) {
                    case 1:
                        dx = rand.nextGaussian() * perturbScale;
                        dy = rand.nextGaussian() * perturbScale;
                        dz = rand.nextGaussian() * perturbScale;
                        break;
                    case 2:
                        float t = (float) i / iterations * 10.0f;
                        dx = perlinNoise.noise(t, 0.0, 0.0) * perturbScale;
                        dy = perlinNoise.noise(0.0, t, 0.0) * perturbScale;
                        dz = perlinNoise.noise(0.0, 0.0, t) * perturbScale;
                        break;
                    case 3:
                        if (rand.nextBoolean()) {
                            dx = rand.nextGaussian() * perturbScale;
                            dy = rand.nextGaussian() * perturbScale;
                            dz = rand.nextGaussian() * perturbScale;
                        } else {
                            dx = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.5;
                            dy = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.5;
                            dz = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.5;
                        }
                        break;
                    default:
                        if (rand.nextBoolean()) {
                            dx = rand.nextGaussian() * perturbScale * 0.5 + perlinNoise.noise(i * 0.1, 0.0, 0.0) * perturbScale * 0.5;
                            dy = rand.nextGaussian() * perturbScale * 0.5 + perlinNoise.noise(0.0, i * 0.1, 0.0) * perturbScale * 0.5;
                            dz = rand.nextGaussian() * perturbScale * 0.5 + perlinNoise.noise(0.0, 0.0, i * 0.1) * perturbScale * 0.5;
                        } else {
                            dx = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.2;
                            dy = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.2;
                            dz = (rand.nextDouble() * 2.0 - 1.0) * perturbScale * 1.2;
                        }
                        break;
                }
            }

            if (edgeExploration && rand.nextDouble() < 0.1) {
                int face = rand.nextInt(6);
                switch (face) {
                    case 0: dx = box.maxX - cpX; break;
                    case 1: dx = box.minX - cpX; break;
                    case 2: dy = box.maxY - cpY; break;
                    case 3: dy = box.minY - cpY; break;
                    case 4: dz = box.maxZ - cpZ; break;
                    case 5: dz = box.minZ - cpZ; break;
                }
            }

            double newX = Math.max(box.minX, Math.min(box.maxX, cpX + dx));
            double newY = Math.max(box.minY, Math.min(box.maxY, cpY + dy));
            double newZ = Math.max(box.minZ, Math.min(box.maxZ, cpZ + dz));

            double newEnergy = calculateAdvancedBoxEnergy(newX, newY, newZ, eyePos, playerYaw, playerPitch, checkWalls, energyAngleW, energyDistW, energyHeightW, energyWallW, energyRandomW);
            double deltaE = newEnergy - currentEnergy;

            if (deltaE <= 0 || rand.nextDouble() < Math.exp(-deltaE / Math.max(0.001, tempScale))) {
                cpX = newX;
                cpY = newY;
                cpZ = newZ;
                currentEnergy = newEnergy;
                acceptedCount++;

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

    private static double calculateAdvancedBoxEnergy(double px, double py, double pz, Vec3 eyePos, float playerYaw, float playerPitch, boolean checkWalls, float angleW, float distW, float heightW, float wallW, float randomW) {
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

        double wallPenalty = 0.0;
        if (checkWalls) {
            Vec3 newVec = new Vec3(px, py, pz);
            MovingObjectPosition wallHit = mc.theWorld.rayTraceBlocks(eyePos, newVec);
            if (wallHit != null) {
                wallPenalty = 10.0;
            }
        }

        double randomNoise = ThreadLocalRandom.current().nextDouble(-0.5, 0.5) * randomW;

        return angleW * angleDiff + distW * distance + heightW * heightDiff + wallW * wallPenalty + randomNoise;
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
            if (yawDelta != 0.0f && Math.abs(yawDelta) < gcd) {
                yawDelta = Math.signum(yawDelta) * gcd;
            } else {
                yawDelta = Math.round(yawDelta / gcd) * gcd;
            }
            if (pitchDelta != 0.0f && Math.abs(pitchDelta) < gcd) {
                pitchDelta = Math.signum(pitchDelta) * gcd;
            } else {
                pitchDelta = Math.round(pitchDelta / gcd) * gcd;
            }
            newYaw = currentYaw + yawDelta;
            newPitch = currentPitch + pitchDelta;
        }
        return new float[]{newYaw, newPitch};
    }

    public static class NoiseRecoverySystem {
        public float yawOffset = 0.0f;
        public float pitchOffset = 0.0f;
        public float yawVelocity = 0.0f;
        public float pitchVelocity = 0.0f;
        public float fatigue = 0.0f;
        public float timeAccumulator = 0.0f;
        public long lastUpdateMillis;

        public NoiseRecoverySystem() {
            reset();
        }

        public void reset() {
            yawOffset = 0.0f;
            pitchOffset = 0.0f;
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
            fatigue = 0.0f;
            timeAccumulator = 0.0f;
            lastUpdateMillis = System.currentTimeMillis();
        }
    }

    public static float[] applyNoiseRecovery(
            float yaw, float pitch,
            NoiseRecoverySystem state,
            float stiffness, float damping,
            float fatigueRate, float recoveryRate,
            float scale, float pitchRatio,
            float impulseProb, float impulseScale,
            float microJitter, boolean distanceScale,
            boolean gcdQuantize, boolean clampToBox,
            AxisAlignedBB box, double attackRange,
            double distance) {

        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, Math.min(0.1f, (now - state.lastUpdateMillis) / 1000.0f));
        state.lastUpdateMillis = now;
        state.timeAccumulator += dt;

        state.fatigue = Math.min(1.0f, state.fatigue + fatigueRate);
        float fatigueFactor = 0.3f + 0.7f * state.fatigue;

        float effScale = scale;
        if (distanceScale && distance > 0.0) {
            effScale *= (float)(0.5 + Math.min(2.5, distance / 3.0) * 0.5);
        }

        float perlinYaw = (float) perlinNoise.noise(state.timeAccumulator * 0.5, 0.0, 0.0);
        float perlinPitch = (float) perlinNoise.noise(0.0, state.timeAccumulator * 0.5, 0.0);
        float brownianYaw = (float) (Math.sin(state.timeAccumulator * 1.3) * 0.6 + Math.sin(state.timeAccumulator * 2.7 + 1.5) * 0.3);
        float brownianPitch = (float) (Math.sin(state.timeAccumulator * 1.7 + 0.8) * 0.6 + Math.sin(state.timeAccumulator * 3.1 + 2.2) * 0.3);

        float targetYawMean = (perlinYaw * 0.6f + brownianYaw * 0.4f) * effScale * fatigueFactor;
        float targetPitchMean = (perlinPitch * 0.6f + brownianPitch * 0.4f) * effScale * pitchRatio * fatigueFactor;

        targetYawMean += microJitter * (float) ThreadLocalRandom.current().nextGaussian() * 0.1f;
        targetPitchMean += microJitter * pitchRatio * (float) ThreadLocalRandom.current().nextGaussian() * 0.1f;

        float yawForce = (targetYawMean - state.yawOffset) * stiffness;
        float pitchForce = (targetPitchMean - state.pitchOffset) * stiffness;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble() < impulseProb) {
            float imp = (float) rand.nextGaussian() * impulseScale * effScale * fatigueFactor;
            yawForce += imp;
            pitchForce += imp * 0.5f * pitchRatio;
        }

        float dtScale = dt * 60.0f;
        state.yawVelocity = state.yawVelocity * damping + yawForce * dtScale;
        state.pitchVelocity = state.pitchVelocity * damping + pitchForce * dtScale;

        state.yawOffset += state.yawVelocity * dtScale;
        state.pitchOffset += state.pitchVelocity * dtScale;

        float maxYawOff = effScale * 5.0f;
        float maxPitchOff = effScale * 5.0f * pitchRatio;
        if (state.yawOffset > maxYawOff) { state.yawOffset = maxYawOff; state.yawVelocity *= -0.3f; }
        if (state.yawOffset < -maxYawOff) { state.yawOffset = -maxYawOff; state.yawVelocity *= -0.3f; }
        if (state.pitchOffset > maxPitchOff) { state.pitchOffset = maxPitchOff; state.pitchVelocity *= -0.3f; }
        if (state.pitchOffset < -maxPitchOff) { state.pitchOffset = -maxPitchOff; state.pitchVelocity *= -0.3f; }

        float testYaw = yaw + state.yawOffset;
        float testPitch = pitch + state.pitchOffset;

        if (gcdQuantize) {
            float gcd = getSensitivityGCD();
            if (gcd > 0.0f) {
                float yDelta = testYaw - yaw;
                float pDelta = testPitch - pitch;
                if (yDelta != 0.0f && Math.abs(yDelta) < gcd) yDelta = Math.signum(yDelta) * gcd;
                else yDelta = Math.round(yDelta / gcd) * gcd;
                if (pDelta != 0.0f && Math.abs(pDelta) < gcd) pDelta = Math.signum(pDelta) * gcd;
                else pDelta = Math.round(pDelta / gcd) * gcd;
                testYaw = yaw + yDelta;
                testPitch = pitch + pDelta;
            }
        }

        if (clampToBox && box != null) {
            if (rayTrace(box, testYaw, testPitch, attackRange) == null) {
                boolean foundValid = false;
                for (int i = 0; i < 6; i++) {
                    float s = 1.0f - (i + 1) * 0.15f;
                    float sYaw = yaw + state.yawOffset * s;
                    float sPitch = pitch + state.pitchOffset * s;
                    if (rayTrace(box, sYaw, sPitch, attackRange) != null) {
                        state.yawOffset *= s;
                        state.pitchOffset *= s;
                        state.yawVelocity *= s;
                        state.pitchVelocity *= s;
                        testYaw = sYaw;
                        testPitch = sPitch;
                        foundValid = true;
                        break;
                    }
                }
                if (!foundValid) {
                    state.yawOffset *= 0.5f;
                    state.pitchOffset *= 0.5f;
                    state.yawVelocity *= 0.5f;
                    state.pitchVelocity *= 0.5f;
                    testYaw = yaw + state.yawOffset;
                    testPitch = pitch + state.pitchOffset;
                }
            }
        }

        return new float[]{testYaw, testPitch};
    }

    public static class BrownianState {
        public float yawOffset = 0.0f;
        public float pitchOffset = 0.0f;
        public float yawVelocity = 0.0f;
        public float pitchVelocity = 0.0f;
        public final float[] octavePhases;
        public long lastUpdateMillis;

        public BrownianState(int maxOctaves) {
            octavePhases = new float[maxOctaves];
            reset();
        }

        public void reset() {
            yawOffset = 0.0f;
            pitchOffset = 0.0f;
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
            for (int i = 0; i < octavePhases.length; i++) {
                octavePhases[i] = (float)(Math.random() * 1000.0);
            }
            lastUpdateMillis = System.currentTimeMillis();
        }
    }

    public static float[] applyAdvancedBrownianMotion(
            float yaw, float pitch,
            BrownianState state,
            float intensity,
            float yawScale, float pitchScale,
            float damping, float drift,
            int octaves, float persistence,
            float impulseProb, float impulseScale,
            boolean adaptive, float maxAngle,
            float correctionSpeed,
            AxisAlignedBB box, double attackRange,
            double distance) {

        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, Math.min(0.1f, (now - state.lastUpdateMillis) / 1000.0f));
        state.lastUpdateMillis = now;

        float effIntensity = intensity;
        if (adaptive && distance > 0.0) {
            effIntensity *= (float)(0.5 + Math.min(2.5, distance / 3.0) * 0.5);
        }

        float octaveYaw = 0.0f, octavePitch = 0.0f;
        float maxAmp = 0.0f, amp = 1.0f, freq = 1.0f;
        for (int i = 0; i < octaves && i < state.octavePhases.length; i++) {
            state.octavePhases[i] += dt * freq * 3.0f;
            float phase = state.octavePhases[i];
            float nY = (float)(Math.sin(phase) * 0.6 + Math.sin(phase * 1.73 + 0.5) * 0.3 + Math.sin(phase * 0.47 + 1.2) * 0.1);
            float nP = (float)(Math.sin(phase + 1.7) * 0.6 + Math.sin(phase * 1.73 + 2.1) * 0.3 + Math.sin(phase * 0.47 + 2.8) * 0.1);
            octaveYaw += nY * amp;
            octavePitch += nP * amp;
            maxAmp += amp;
            amp *= persistence;
            freq *= 2.0;
        }
        if (maxAmp > 0.0f) {
            octaveYaw /= maxAmp;
            octavePitch /= maxAmp;
        }

        float targetYawMean = drift + octaveYaw * effIntensity * yawScale;
        float targetPitchMean = drift * 0.4f + octavePitch * effIntensity * pitchScale;

        float yawForce = (targetYawMean - state.yawOffset) * damping;
        float pitchForce = (targetPitchMean - state.pitchOffset) * damping;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble() < impulseProb) {
            float imp = (float)rand.nextGaussian() * impulseScale * effIntensity;
            yawForce += imp;
            pitchForce += imp * 0.5f;
        }

        float dtScale = dt * 60.0f;
        state.yawVelocity = state.yawVelocity * correctionSpeed + yawForce * dtScale;
        state.pitchVelocity = state.pitchVelocity * correctionSpeed + pitchForce * dtScale;

        state.yawOffset += state.yawVelocity * dtScale;
        state.pitchOffset += state.pitchVelocity * dtScale;

        float maxYawOff = maxAngle * yawScale;
        float maxPitchOff = maxAngle * pitchScale;
        if (state.yawOffset > maxYawOff) { state.yawOffset = maxYawOff; state.yawVelocity *= -0.3f; }
        if (state.yawOffset < -maxYawOff) { state.yawOffset = -maxYawOff; state.yawVelocity *= -0.3f; }
        if (state.pitchOffset > maxPitchOff) { state.pitchOffset = maxPitchOff; state.pitchVelocity *= -0.3f; }
        if (state.pitchOffset < -maxPitchOff) { state.pitchOffset = -maxPitchOff; state.pitchVelocity *= -0.3f; }

        float testYaw = yaw + state.yawOffset;
        float testPitch = pitch + state.pitchOffset;

        if (box != null) {
            if (rayTrace(box, testYaw, testPitch, attackRange) != null) {
                float gcd = getSensitivityGCD();
                if (gcd > 0.0f) {
                    float yDelta = testYaw - yaw;
                    float pDelta = testPitch - pitch;
                    if (yDelta != 0.0f && Math.abs(yDelta) < gcd) yDelta = Math.signum(yDelta) * gcd;
                    else yDelta = Math.round(yDelta / gcd) * gcd;
                    if (pDelta != 0.0f && Math.abs(pDelta) < gcd) pDelta = Math.signum(pDelta) * gcd;
                    else pDelta = Math.round(pDelta / gcd) * gcd;
                    testYaw = yaw + yDelta;
                    testPitch = pitch + pDelta;
                }
                return new float[]{testYaw, testPitch};
            }
            for (int i = 0; i < 6; i++) {
                float scale = 1.0f - (i + 1) * 0.15f;
                float sYaw = yaw + state.yawOffset * scale;
                float sPitch = pitch + state.pitchOffset * scale;
                if (rayTrace(box, sYaw, sPitch, attackRange) != null) {
                    state.yawOffset *= scale;
                    state.pitchOffset *= scale;
                    state.yawVelocity *= scale;
                    state.pitchVelocity *= scale;
                    float gcd = getSensitivityGCD();
                    if (gcd > 0.0f) {
                        float yDelta = sYaw - yaw;
                        float pDelta = sPitch - pitch;
                        if (yDelta != 0.0f && Math.abs(yDelta) < gcd) yDelta = Math.signum(yDelta) * gcd;
                        else yDelta = Math.round(yDelta / gcd) * gcd;
                        if (pDelta != 0.0f && Math.abs(pDelta) < gcd) pDelta = Math.signum(pDelta) * gcd;
                        else pDelta = Math.round(pDelta / gcd) * gcd;
                        sYaw = yaw + yDelta;
                        sPitch = pitch + pDelta;
                    }
                    return new float[]{sYaw, sPitch};
                }
            }
            state.yawOffset *= 0.5f;
            state.pitchOffset *= 0.5f;
            state.yawVelocity *= 0.5f;
            state.pitchVelocity *= 0.5f;
            float gcd = getSensitivityGCD();
            if (gcd > 0.0f) {
                yaw = Math.round(yaw / gcd) * gcd;
                pitch = Math.round(pitch / gcd) * gcd;
            }
            float microYaw = yaw + state.yawOffset;
            float microPitch = pitch + state.pitchOffset;
            if (gcd > 0.0f) {
                float yDelta = microYaw - yaw;
                float pDelta = microPitch - pitch;
                if (yDelta != 0.0f && Math.abs(yDelta) < gcd) yDelta = Math.signum(yDelta) * gcd;
                else yDelta = Math.round(yDelta / gcd) * gcd;
                if (pDelta != 0.0f && Math.abs(pDelta) < gcd) pDelta = Math.signum(pDelta) * gcd;
                else pDelta = Math.round(pDelta / gcd) * gcd;
                microYaw = yaw + yDelta;
                microPitch = pitch + pDelta;
            }
            return new float[]{microYaw, microPitch};
        }

        float gcd = getSensitivityGCD();
        if (gcd > 0.0f) {
            float yDelta = testYaw - yaw;
            float pDelta = testPitch - pitch;
            if (yDelta != 0.0f && Math.abs(yDelta) < gcd) yDelta = Math.signum(yDelta) * gcd;
            else yDelta = Math.round(yDelta / gcd) * gcd;
            if (pDelta != 0.0f && Math.abs(pDelta) < gcd) pDelta = Math.signum(pDelta) * gcd;
            else pDelta = Math.round(pDelta / gcd) * gcd;
            testYaw = yaw + yDelta;
            testPitch = pitch + pDelta;
        }
        return new float[]{testYaw, testPitch};
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
        private float currentBezierYaw;
        private float currentBezierPitch;

        private float ctrl1Pos;
        private float ctrl2Pos;
        private float ctrl3Rand;
        private float yMinStep;
        private float pMinStep;
        private float yDynStep;
        private float pDynStep;

        private final ThreadLocalRandom rand = ThreadLocalRandom.current();

        public void setup(float curYaw, float curPitch, float tgtYaw, float tgtPitch, float speed,
                          float ctrl1Pos, float ctrl2Pos, float ctrl3Rand,
                          float yMinStep, float pMinStep,
                          float yDynStep, float pDynStep) {
            this.startYaw = curYaw;
            this.startPitch = curPitch;
            this.targetYaw = tgtYaw;
            this.targetPitch = tgtPitch;
            this.speed = Math.max(0.02f, speed);
            this.ctrl1Pos = ctrl1Pos;
            this.ctrl2Pos = ctrl2Pos;
            this.ctrl3Rand = ctrl3Rand;
            this.yMinStep = yMinStep;
            this.pMinStep = pMinStep;
            this.yDynStep = yDynStep;
            this.pDynStep = pDynStep;

            float yawDelta = MathHelper.wrapAngleTo180_float(tgtYaw - curYaw);
            float pitchDelta = MathHelper.wrapAngleTo180_float(tgtPitch - curPitch);

            ctrl1Yaw = curYaw + yawDelta * ctrl1Pos + (float)rand.nextGaussian() * ctrl3Rand;
            ctrl1Pitch = curPitch + pitchDelta * ctrl1Pos + (float)rand.nextGaussian() * ctrl3Rand * 0.5f;

            ctrl2Yaw = curYaw + yawDelta * ctrl2Pos + (float)rand.nextGaussian() * ctrl3Rand;
            ctrl2Pitch = curPitch + pitchDelta * ctrl2Pos + (float)rand.nextGaussian() * ctrl3Rand * 0.5f;

            this.progress = 0.0f;
            this.currentBezierYaw = curYaw;
            this.currentBezierPitch = curPitch;
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

            float yawDiff = MathHelper.wrapAngleTo180_float(pY - currentBezierYaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(pP - currentBezierPitch);

            if (yDynStep > 0) {
                yawDiff *= (1.0f + Math.abs(yawDiff) / 180.0f * yDynStep);
            }
            if (pDynStep > 0) {
                pitchDiff *= (1.0f + Math.abs(pitchDiff) / 180.0f * pDynStep);
            }

            if (Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - currentBezierYaw)) > 0.01f && Math.abs(yawDiff) < yMinStep) {
                yawDiff = Math.signum(MathHelper.wrapAngleTo180_float(targetYaw - currentBezierYaw)) * yMinStep;
            }
            if (Math.abs(MathHelper.wrapAngleTo180_float(targetPitch - currentBezierPitch)) > 0.01f && Math.abs(pitchDiff) < pMinStep) {
                pitchDiff = Math.signum(MathHelper.wrapAngleTo180_float(targetPitch - currentBezierPitch)) * pMinStep;
            }

            currentBezierYaw += yawDiff;
            currentBezierPitch += pitchDiff;

            float gcd = getSensitivityGCD();
            if (gcd > 0.0f) {
                currentBezierYaw = Math.round(currentBezierYaw / gcd) * gcd;
                currentBezierPitch = Math.round(currentBezierPitch / gcd) * gcd;
            }
            return new float[]{currentBezierYaw, currentBezierPitch};
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

    public static class MLRotator {
        private float startYaw, startPitch;
        private float targetYaw, targetPitch;
        private float[] yawDeltas;
        private float[] pitchDeltas;
        private int currentTick = 0;
        private int totalTicks = 0;
        private float smoothFactor;
        private float overshootProb;
        private float overshootScale;
        private float noiseScale;

        public void setup(float curYaw, float curPitch, float tgtYaw, float tgtPitch,
                          float smoothFactor, float overshootProb, float overshootScale, float noiseScale) {
            this.startYaw = curYaw;
            this.startPitch = curPitch;
            this.targetYaw = tgtYaw;
            this.targetPitch = tgtPitch;
            this.smoothFactor = smoothFactor;
            this.overshootProb = overshootProb;
            this.overshootScale = overshootScale;
            this.noiseScale = noiseScale;

            float dYaw = MathHelper.wrapAngleTo180_float(tgtYaw - curYaw);
            float dPitch = MathHelper.wrapAngleTo180_float(tgtPitch - curPitch);

            totalTicks = Math.max(2, (int) Math.ceil(Math.sqrt(dYaw * dYaw + dPitch * dPitch) / (5.0f * smoothFactor)));
            if (totalTicks > 20) totalTicks = 20;

            yawDeltas = new float[totalTicks];
            pitchDeltas = new float[totalTicks];

            ThreadLocalRandom rand = ThreadLocalRandom.current();
            float sumY = 0, sumP = 0;
            float[] rawY = new float[totalTicks];
            float[] rawP = new float[totalTicks];

            for (int i = 0; i < totalTicks; i++) {
                float progress = (float) i / (float) (totalTicks - 1);
                float speed = (float) (Math.exp(-Math.exp(-10.0 * (progress - 0.5))) * 0.5);
                rawY[i] = speed + (float) rand.nextGaussian() * 0.05f;
                rawP[i] = speed + (float) rand.nextGaussian() * 0.05f;
                sumY += rawY[i];
                sumP += rawP[i];
            }

            if (sumY == 0) sumY = 1;
            if (sumP == 0) sumP = 1;

            for (int i = 0; i < totalTicks; i++) {
                yawDeltas[i] = (rawY[i] / sumY) * dYaw;
                pitchDeltas[i] = (rawP[i] / sumP) * dPitch;
            }

            if (totalTicks > 3 && rand.nextDouble() < overshootProb) {
                int overTick = totalTicks - 2;
                float overScale = 1.0f + (float) (rand.nextDouble() * 0.1 + 0.02) * overshootScale;
                yawDeltas[overTick] *= overScale;
                pitchDeltas[overTick] *= overScale;
                yawDeltas[overTick + 1] -= (yawDeltas[overTick] * (overScale - 1.0f));
                pitchDeltas[overTick + 1] -= (pitchDeltas[overTick] * (overScale - 1.0f));
            }

            currentTick = 0;
        }

        public float[] getNextRotation() {
            if (currentTick >= totalTicks) {
                return new float[]{targetYaw, targetPitch};
            }
            float y = startYaw;
            float p = startPitch;
            for (int i = 0; i <= currentTick; i++) {
                y += yawDeltas[i];
                p += pitchDeltas[i];
            }
            currentTick++;

            float gcd = getSensitivityGCD();
            if (gcd > 0.0f) {
                y = Math.round(y / gcd) * gcd;
                p = Math.round(p / gcd) * gcd;
            }

            return new float[]{y, p};
        }

        public boolean isFinished() {
            return currentTick >= totalTicks;
        }

        public boolean needsUpdate(float tgtYaw, float tgtPitch, float threshold) {
            if (currentTick >= totalTicks) return true;
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
    }
}