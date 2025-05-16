package eu.aylett.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

final class ThrottleEntry implements Delayed {
    private static final TemporalAmount ONE_MINUTE = Duration.ofMinutes(1);
    public final boolean success;
    private final Clock clock;
    private final Instant expiry;

    ThrottleEntry(boolean success, Clock clock) {
        this.success = success;
        this.clock = clock;
        this.expiry = Instant.now(clock).plus(ONE_MINUTE);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return clock.instant().until(expiry).get(unit.toChronoUnit());
    }

    @Override
    public int compareTo(Delayed o) {
        if (o instanceof ThrottleEntry other) {
            return expiry.compareTo(other.expiry);
        }
        return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }
}
