package eu.aylett.throttle;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class Throttle {
    private final double overhead;
    private final Clock clock;
    private final Random random;
    private final DelayQueue<ThrottleEntry> queue = new DelayQueue<>();
    private final AtomicLong successes = new AtomicLong(0);
    private final AtomicLong failures = new AtomicLong(0);

    public Throttle(double overhead, Clock clock, Random random) {
        this.overhead = overhead;
        this.clock = clock;
        this.random = random;
    }

    public Throttle() {
        this(2.0, Clock.systemUTC(), new SecureRandom());
    }

    public <T> T checkedAttempt(Callable<T> callable) throws Exception {
        ThrottleEntry old;
        T result;
        while ((old = queue.poll()) != null) {
            if (old.success) {
                successes.decrementAndGet();
            } else {
                failures.decrementAndGet();
            }
        }

        try {
            if (failures.getAcquire() > 0) {
                var ratio = overhead * ((double) successes.getAcquire() / (successes.getAcquire() + failures.getAcquire()));
                if (ratio < 1.0) {
                    var sample = random.nextDouble();
                    if (sample > ratio) {
                        throw new ThrottleException("Throttle limit exceeded");
                    }
                }
            }
            result = callable.call();
        } catch (Throwable e) {
            failures.incrementAndGet();
            queue.offer(new ThrottleEntry(false, clock));
            throw e;
        }

        successes.incrementAndGet();
        queue.offer(new ThrottleEntry(true, clock));
        return result;
    }

    public void attempt(Runnable runnable) {
        try {
            checkedAttempt(() -> {
                runnable.run();
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedCheckedException(e);
        }
    }

    public <T> T attempt(Supplier<T> supplier) {
        try {
            return checkedAttempt(supplier::get);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedCheckedException(e);
        }
    }
}
