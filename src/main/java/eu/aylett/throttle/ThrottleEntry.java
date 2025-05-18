/*
 * Copyright 2025 Andrew Aylett
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.aylett.throttle;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.TemporalAmount;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Represents a single attempt (success or failure) tracked by the throttle.
 * Used internally for time-based expiration of attempts.
 */
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
  @Contract(value = "null -> false", pure = true)
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof ThrottleEntry that) {
      return success == that.success && Objects.equals(expiry, that.expiry);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, expiry);
  }
}
