package eu.aylett.throttle;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
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
            var f = failures.getAcquire();
            if (f > 0) {
                var s = successes.getAcquire();
                // We want a non-zero chance of running, even if we've not seen any successes for a while
                var ratio = overhead * ((overhead + s) / (s + f));
                if (ratio <= 1.0) {
                    var sample = random.nextDouble();
                    if (sample >= ratio) {
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

    public <T> Supplier<T> wrap(Supplier<T> supplier) {
        return () -> attempt(supplier);
    }

    public Runnable wrap(Runnable runnable) {
        return () -> attempt(runnable);
    }

    public <T, R> Function<T, R> wrap(Function<T, R> function) {
        return (T t) -> attempt(() -> function.apply(t));
    }

    public <T, U, R> BiFunction<T, U, R> wrap(BiFunction<T, U, R> function) {
        return (T t, U u) -> attempt(() -> function.apply(t, u));
    }
}
