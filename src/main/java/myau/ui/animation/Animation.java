package myau.ui.animation;

public class Animation {
    private final Easing easing;
    private final long duration;
    private long startTime;
    private double startValue;
    private double endValue;
    private double value;
    private boolean direction = true;

    public Animation(Easing easing, long duration) {
        this.easing = easing;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.startValue = 0.0;
        this.endValue = 1.0;
        this.value = 0.0;
    }

    public void start(boolean direction) {
        this.direction = direction;
        this.startValue = this.value;
        this.endValue = direction ? 1.0 : 0.0;
        this.startTime = System.currentTimeMillis();
    }

    public void start(double startValue, double endValue) {
        this.startValue = startValue;
        this.endValue = endValue;
        this.direction = endValue > startValue;
        this.value = startValue;
        this.startTime = System.currentTimeMillis();
    }

    public void update() {
        if (duration <= 0) {
            value = endValue;
            return;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        double progress = Math.min((double) elapsed / (double) duration, 1.0);
        double easedProgress = easing.getFunction().apply(progress);
        value = startValue + (endValue - startValue) * easedProgress;
    }

    public double getValue() {
        return value;
    }

    public boolean isForward() {
        return direction;
    }

    public boolean isFinished() {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed >= duration;
    }
}