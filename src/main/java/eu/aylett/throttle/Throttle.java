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

/**
 * A simple throttle that allows you to call a method, but only if the throttle
 * limit is not exceeded.
 */
public class Throttle {
  private final double overhead;
  private final InstantSource clock;
  private final DoubleSupplier randomSource;
  private final DelayQueue<ThrottleEntry> queue = new DelayQueue<>();
  private final AtomicLong successes = new AtomicLong(0);
  private final AtomicLong failures = new AtomicLong(0);

  /**
   * A fully configurable throttle.
   * <p>
   * You probably don't need to call this constructor directly.
   * </p>
   *
   * @param overhead
   *          the ratio of attempts to successes; higher values allow more
   *          attempts per success
   * @param clock
   *          the time source used for expiring old entries (mainly for testing)
   * @param randomSource
   *          the random number generator used to probabilistically throttle
   *          attempts (mainly for testing)
   */
  public Throttle(double overhead, InstantSource clock, DoubleSupplier randomSource) {
    this.overhead = overhead;
    this.clock = clock;
    this.randomSource = randomSource;
  }

  /**
   * Throttle with a default overhead of 2.0, using the system clock and a secure
   * random number generator.
   */
  public Throttle() {
    this(2.0, Clock.systemUTC(), new SecureRandom()::nextDouble);
  }

  /**
   * Either call the callable, or throw a ThrottleException if the throttle limit
   * is exceeded.
   *
   * @throws ThrottleException
   *           if the throttle limit is exceeded, or any exception thrown by the
   *           callable.
   */
  public <T> T checkedAttempt(Callable<T> callable) throws Exception {
    ThrottleEntry old;
    T result;
    var deltaSuccesses = 0L;
    var deltaFailures = 0L;
    while ((old = queue.poll()) != null) {
      if (old.success) {
        deltaSuccesses--;
      } else {
        deltaFailures--;
      }
    }

    // We always update the counts after polling the queue and before adding to it
    // so that the count will always be no less than the number of entries in the
    // queue
    var instantaneousFailures = failures.addAndGet(deltaFailures);
    var instantaneousSuccesses = successes.addAndGet(deltaSuccesses);

    // Should hold by construction.
    assert instantaneousFailures >= 0;
    assert instantaneousSuccesses >= 0;

    var success = false;
    try {
      if (instantaneousFailures > 0) {
        // We want a non-zero chance of running, even if we've not seen any successes
        // for a while
        var ratio = overhead * ((overhead + instantaneousSuccesses) / (instantaneousSuccesses + instantaneousFailures));
        if (ratio <= 1.0) {
          var sample = randomSource.getAsDouble();
          if (sample >= ratio) {
            // Counts as a failure, because we want to let through a proportion of total
            // attempts including throttles
            // to let through twice the successes seen.
            throw new ThrottleException("Throttle limit exceeded", instantaneousSuccesses, instantaneousFailures,
                ratio);
          }
        }
      }
      result = callable.call();

      success = true;
      successes.getAndIncrement();
      queue.offer(new ThrottleEntry(true, clock));

      return result;
    } finally {
      if (!success) {
        failures.getAndIncrement();
        queue.offer(new ThrottleEntry(false, clock));
      }
    }
  }

  /**
   * Call the runnable, or throw a ThrottleException if the throttle limit is
   * exceeded.
   *
   * @throws ThrottleException
   *           if the throttle limit is exceeded, or any exception thrown by the
   *           runnable.
   */
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

  /**
   * Call the supplier, or throw a ThrottleException if the throttle limit is
   * exceeded.
   *
   * @throws ThrottleException
   *           if the throttle limit is exceeded, or any exception thrown by the
   *           supplier.
   */
  public <T> T attempt(Supplier<T> supplier) {
    try {
      return checkedAttempt(supplier::get);
    } catch (Exception e) {
      throw sneakyThrow(e);
    }
  }

  /**
   * Call the callable, or throw a ThrottleException if the throttle limit is
   * exceeded.
   *
   * @throws ThrottleException
   *           if the throttle limit is exceeded, or any exception thrown by the
   *           callable.
   */
  public <T> Supplier<T> wrap(Supplier<T> supplier) {
    return () -> attempt(supplier);
  }

  /**
   * Wrap a Runnable so that when it's called, it may be throttled.
   */
  public Runnable wrap(Runnable runnable) {
    return () -> attempt(runnable);
  }

  /**
   * Wrap a Function so that when it's called, it may be throttled.
   */
  public <T, R> Function<T, R> wrap(Function<T, R> function) {
    return (T t) -> attempt(() -> function.apply(t));
  }

  /**
   * Wrap a BiFunction so that when it's called, it may be throttled.
   */
  public <T, U, R> BiFunction<T, U, R> wrap(BiFunction<T, U, R> function) {
    return (T t, U u) -> attempt(() -> function.apply(t, u));
  }
}
