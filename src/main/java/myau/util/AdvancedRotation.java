package myau.util;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.entity.EntityLivingBase;
import myau.util.RotationUtil;

/**
 * AdvancedRotation: implements higher-level rotation algorithms outlined in design notes.
 * - Minimum-jerk (5th order) trajectory with Fitts' Law duration
 * - Sigmoid / ease functions
 * - Spring-damper controller
 * - Pink noise generator (Voss-McCartney) for muscle-like jitter
 * - Prediction helpers (linear, quadratic)
 *
 * Exposed as static utilities so modules can compose pipelines.
 */
public final class AdvancedRotation {
    private AdvancedRotation() {}

    // Minimum-jerk (quintic) interpolation between angles
    // s in [0,1]
    public static float minJerkBlend(float t) {
        float s = Math.max(0.0f, Math.min(1.0f, t));
        return 10.0f * s * s * s - 15.0f * s * s * s * s + 6.0f * s * s * s * s * s;
    }

    // Fitts' Law duration estimator for angle movement
    public static float fittsDuration(float angleDistanceDegrees, float targetWidthDegrees, float a, float b) {
        float D = Math.abs(angleDistanceDegrees);
        float W = Math.max(0.01f, Math.abs(targetWidthDegrees));
        return a + b * (float) (Math.log((D / W) + 1.0) / Math.log(2.0));
    }

    // Sigmoid ease (logistic variant)
    public static float sigmoidEase(float t, float k) {
        float x = Math.max(0.0f, Math.min(1.0f, t));
        double ex = Math.exp(-k * (x - 0.5));
        double val = x / (1.0 + ex);
        return (float) Math.max(0.0, Math.min(1.0, val));
    }

    // Spring-damper update: returns new angle given stateful velocity array
    // velocity[0] = yawVel, velocity[1] = pitchVel (modified in-place)
    public static float[] springDamperStep(float cur, float target, float[] velocity, float stiffness, float damping, float dt) {
        float yawDelta = MathHelper.wrapAngleTo180_float(target - cur);
        float acc = -stiffness * yawDelta - damping * velocity[0];
        velocity[0] += acc * dt;
        float next = cur + velocity[0] * dt;
        return new float[]{next, velocity[0]};
    }

    // Quadratic prediction: uses prevVel and currVel to compute accel
    public static Vec3 quadraticPredict(EntityLivingBase e, float ticks) {
        double vx = e.posX - e.prevPosX;
        double vy = e.posY - e.prevPosY;
        double vz = e.posZ - e.prevPosZ;
        // Estimate previous vel as best-effort using motionX stored in NBT? fallback to zero
        // For simplicity use same vel (no accel) here; modules can cache velocities for better results
        double px = e.posX + vx * ticks + 0.5 * 0.0 * ticks * ticks;
        double py = e.posY + vy * ticks + 0.5 * 0.0 * ticks * ticks;
        double pz = e.posZ + vz * ticks + 0.5 * 0.0 * ticks * ticks;
        return new Vec3(px, py, pz);
    }

    // Pink noise generator (Voss-McCartney simplified)
    public static final class PinkNoise {
        private final double[] vals;
        private int counter = 0;

        public PinkNoise(int octaves) {
            this.vals = new double[Math.max(2, octaves)];
            for (int i = 0; i < this.vals.length; i++) this.vals[i] = Math.random() - 0.5;
        }

        public double next() {
            counter++;
            int idx = counter;
            double sum = 0.0;
            int i = 0;
            while (idx > 0 && i < this.vals.length) {
                if ((idx & 1) != 0) this.vals[i] = Math.random() - 0.5;
                sum += this.vals[i];
                i++; idx >>= 1;
            }
            return sum / this.vals.length;
        }
    }

    // Convert two angles to a normalized difference in degrees clamped
    public static float clampAngleDelta(float delta, float max) {
        float d = MathHelper.wrapAngleTo180_float(delta);
        if (Math.abs(d) > max) d = Math.copySign(max, d);
        return d;
    }

    // Compose minimum-jerk trajectory between (yaw0,pitch0) and (yaw1,pitch1)
    // t ∈ [0,1] gives interpolated yaw/pitch
    public static float[] minJerkTrajectory(float yaw0, float pitch0, float yaw1, float pitch1, float t) {
        float s = minJerkBlend(t);
        float yaw = MathHelper.wrapAngleTo180_float(yaw0 + MathHelper.wrapAngleTo180_float(yaw1 - yaw0) * s);
        float pitch = pitch0 + (pitch1 - pitch0) * s;
        return new float[]{yaw, pitch};
    }

    // Sample points on box faces (simple uniform sampling per face)
    public static Vec3[] sampleBoxFaces(AxisAlignedBB box, int perFace) {
        Vec3[] pts = new Vec3[6 * perFace * perFace];
        int idx = 0;
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < perFace; i++) {
                for (int j = 0; j < perFace; j++) {
                    double u = (i + 0.5) / perFace;
                    double v = (j + 0.5) / perFace;
                    double x = 0, y = 0, z = 0;
                    switch (f) {
                        case 0: x = minX; y = minY + u * (maxY - minY); z = minZ + v * (maxZ - minZ); break; // -X
                        case 1: x = maxX; y = minY + u * (maxY - minY); z = minZ + v * (maxZ - minZ); break; // +X
                        case 2: y = minY; x = minX + u * (maxX - minX); z = minZ + v * (maxZ - minZ); break; // -Y
                        case 3: y = maxY; x = minX + u * (maxX - minX); z = minZ + v * (maxZ - minZ); break; // +Y
                        case 4: z = minZ; x = minX + u * (maxX - minX); y = minY + v * (maxY - minY); break; // -Z
                        default: z = maxZ; x = minX + u * (maxX - minX); y = minY + v * (maxY - minY); break; // +Z
                    }
                    pts[idx++] = new Vec3(x, y, z);
                }
            }
        }
        return pts;
    }
}
