package eu.aylett.throttle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.Callable;

@SuppressWarnings("UnsecureRandomNumberGeneration")
class ThrottleTest {

    @Test
    void testSuccess() throws Exception {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        Callable<String> callable = () -> "ok";
        var result = throttle.checkedAttempt(callable);
        Assertions.assertEquals("ok", result);
    }

    @Test
    void testFailure() {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        Callable<String> callable = () -> { throw new IllegalArgumentException("fail"); };
        Assertions.assertThrows(IllegalArgumentException.class, () -> throttle.checkedAttempt(callable));
    }

    @Test
    void testThrottleException() {
        // Use a throttle with overhead=1.0 and a Random always returning 1.0 to force throttling
        var throttle = new Throttle(1.0, Clock.systemUTC(), new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        });

        // First call fails, so failures=1
        try {
            throttle.checkedAttempt(() -> { throw new IllegalStateException("fail"); });
        } catch (Exception ignored) {}

        // Second call should be throttled
        Assertions.assertThrows(ThrottleException.class, () -> throttle.checkedAttempt(() -> "should not run"));
    }

    static class DummyClock extends Clock {
        private Instant instant;
        DummyClock(Instant start) { this.instant = start; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @Test
    void testEntryExpiryWithDummyClock() throws Exception {
        DummyClock clock = new DummyClock(Instant.parse("2024-01-01T00:00:00Z"));
        var throttle = new Throttle(2.0, clock, new Random(42));

        // Success and failure
        throttle.checkedAttempt(() -> "ok");
        try {
            throttle.checkedAttempt(() -> { throw new RuntimeException("fail"); });
        } catch (RuntimeException ignored) {}

        // Advance clock far enough for entries to expire (ThrottleEntry uses 60s delay)
        clock.advanceSeconds(61);

        // Next attempt should clear expired entries, so counters reset
        throttle.checkedAttempt(() -> "ok"); // Should not throw

        // If we throw again, failures should be 1, and throttling logic should work as normal
        try {
            throttle.checkedAttempt(() -> { throw new RuntimeException("fail2"); });
        } catch (RuntimeException ignored) {}

        // Advance clock again to expire
        clock.advanceSeconds(61);
        throttle.checkedAttempt(() -> "ok"); // Should not throw
    }

    @Test
    void testAttemptRunnableSuccess() {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        final boolean[] ran = {false};
        throttle.attempt(() -> ran[0] = true);
        Assertions.assertTrue(ran[0]);
    }

    @Test
    void testAttemptRunnableThrows() {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
            throttle.attempt(() -> { throw new IllegalArgumentException("fail"); })
        );
    }

    @Test
    void testAttemptSupplierSuccess() {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        String result = throttle.attempt(() -> "supplied");
        Assertions.assertEquals("supplied", result);
    }

    @Test
    void testAttemptSupplierThrows() {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        Assertions.assertThrows(IllegalStateException.class, () ->
            throttle.attempt(() -> { throw new IllegalStateException("fail"); })
        );
    }

    @Test
    void testAttemptRunnableThrottleException() {
        var throttle = new Throttle(1.0, Clock.systemUTC(), new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        });
        try {
            throttle.attempt(() -> { throw new RuntimeException("fail"); });
        } catch (RuntimeException ignored) {}
        Assertions.assertThrows(ThrottleException.class, () ->
            throttle.attempt(() -> {})
        );
    }

    @Test
    void testAttemptSupplierThrottleException() {
        var throttle = new Throttle(1.0, Clock.systemUTC(), new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        });
        try {
            throttle.attempt(() -> { throw new RuntimeException("fail"); });
        } catch (RuntimeException ignored) {}
        Assertions.assertThrows(ThrottleException.class, () ->
            throttle.attempt(() -> "should not run")
        );
    }
}
