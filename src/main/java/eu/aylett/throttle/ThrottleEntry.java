package eu.aylett.throttle;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.TemporalAmount;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

final class ThrottleEntry implements Delayed {
  private static final TemporalAmount ONE_MINUTE = Duration.ofMinutes(1);
  public final boolean success;
  private final InstantSource clock;
  private final Instant expiry;

  ThrottleEntry(boolean success, InstantSource clock) {
    this.success = success;
    this.clock = clock;
    this.expiry = clock.instant().plus(ONE_MINUTE);
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return clock.instant().until(expiry, unit.toChronoUnit());
  }

  @Override
  public int compareTo(Delayed o) {
    if (o instanceof ThrottleEntry other) {
      return expiry.compareTo(other.expiry);
    }
    return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof ThrottleEntry that) {
      return success == that.success && Objects.equals(clock, that.clock) && Objects.equals(expiry, that.expiry);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, clock, expiry);
  }
}
