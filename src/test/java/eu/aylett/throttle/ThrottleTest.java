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
        var result = throttle.attempt(callable);
        Assertions.assertEquals("ok", result);
    }

    @Test
    void testFailure() {
        var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42));
        Callable<String> callable = () -> { throw new IllegalArgumentException("fail"); };
        Assertions.assertThrows(IllegalArgumentException.class, () -> throttle.attempt(callable));
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
            throttle.attempt(() -> { throw new IllegalStateException("fail"); });
        } catch (Exception ignored) {}

        // Second call should be throttled
        Assertions.assertThrows(ThrottleException.class, () -> throttle.attempt(() -> "should not run"));
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
        throttle.attempt(() -> "ok");
        try {
            throttle.attempt(() -> { throw new RuntimeException("fail"); });
        } catch (RuntimeException ignored) {}

        // Advance clock far enough for entries to expire (ThrottleEntry uses 60s delay)
        clock.advanceSeconds(61);

        // Next attempt should clear expired entries, so counters reset
        throttle.attempt(() -> "ok"); // Should not throw

        // If we throw again, failures should be 1, and throttling logic should work as normal
        try {
            throttle.attempt(() -> { throw new RuntimeException("fail2"); });
        } catch (RuntimeException ignored) {}

        // Advance clock again to expire
        clock.advanceSeconds(61);
        throttle.attempt(() -> "ok"); // Should not throw
    }
}
