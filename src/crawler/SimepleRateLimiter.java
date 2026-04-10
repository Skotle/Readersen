package crawler;
import java.util.concurrent.TimeUnit;

class SimpleRateLimiter {
    private final long intervalNanos;
    private long nextAllowedTime;

    public SimpleRateLimiter(double permitsPerSecond) {
        this.intervalNanos = (long)(1_000_000_000L / permitsPerSecond);
        this.nextAllowedTime = System.nanoTime();
    }

    public synchronized void acquire() {
        long now = System.nanoTime();

        if (now < nextAllowedTime) {
            long sleepNanos = nextAllowedTime - now;
            try {
                TimeUnit.NANOSECONDS.sleep(sleepNanos);
            } catch (InterruptedException ignored) {}
        }

        nextAllowedTime = Math.max(now, nextAllowedTime) + intervalNanos;
    }
}