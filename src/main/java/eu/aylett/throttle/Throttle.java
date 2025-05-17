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

import java.security.SecureRandom;
import java.time.Clock;
import java.time.InstantSource;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static eu.aylett.throttle.SneakyThrows.sneakyThrow;

public class Throttle {
  private final double overhead;
  private final InstantSource clock;
  private final DoubleSupplier randomSource;
  private final DelayQueue<ThrottleEntry> queue = new DelayQueue<>();
  private final AtomicLong successes = new AtomicLong(0);
  private final AtomicLong failures = new AtomicLong(0);

  public Throttle(double overhead, InstantSource clock, DoubleSupplier randomSource) {
    this.overhead = overhead;
    this.clock = clock;
    this.randomSource = randomSource;
  }

  public Throttle() {
    this(2.0, Clock.systemUTC(), new SecureRandom()::nextDouble);
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

    var success = false;
    try {
      var f = failures.getAcquire();
      if (f > 0) {
        var s = successes.getAcquire();
        // We want a non-zero chance of running, even if we've not seen any successes
        // for a while
        var ratio = overhead * ((overhead + s) / (s + f));
        if (ratio <= 1.0) {
          var sample = randomSource.getAsDouble();
          if (sample >= ratio) {
            // Don't record this as a failure, we didn't even try
            success = true;
            throw new ThrottleException("Throttle limit exceeded");
          }
        }
      }
      result = callable.call();

      success = true;
      successes.incrementAndGet();
      queue.offer(new ThrottleEntry(true, clock));

      return result;
    } finally {
      if (!success) {
        failures.incrementAndGet();
        queue.offer(new ThrottleEntry(false, clock));
      }
    }
  }

  public void attempt(Runnable runnable) {
    try {
      checkedAttempt(() -> {
        runnable.run();
        return null;
      });
    } catch (Exception e) {
      throw sneakyThrow(e);
    }
  }

  public <T> T attempt(Supplier<T> supplier) {
    try {
      return checkedAttempt(supplier::get);
    } catch (Exception e) {
      throw sneakyThrow(e);
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
