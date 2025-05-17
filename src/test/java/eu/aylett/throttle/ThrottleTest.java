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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opentest4j.AssertionFailedError;

import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnsecureRandomNumberGeneration")
class ThrottleTest {

  @Test
  void testSuccess() throws Exception {
    var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42)::nextDouble);
    var result = throttle.checkedAttempt(() -> "ok");
    Assertions.assertEquals("ok", result);
  }

  @Test
  void testFailure() {
    var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42)::nextDouble);
    Callable<String> callable = () -> {
      throw new IllegalArgumentException("fail");
    };
    assertThrows(IllegalArgumentException.class, () -> throttle.checkedAttempt(callable));
  }

  @Test
  void testThrottleException() {
    // Use a throttle with overhead=1.0 and a Random always returning 1.0 to force
    // throttling
    var throttle = new Throttle(1.0, Clock.systemUTC(), () -> 1.0);

    // First call fails, so failures=1
    try {
      throttle.checkedAttempt(() -> {
        throw new IllegalStateException("fail");
      });
      throttle.checkedAttempt(() -> {
        throw new IllegalStateException("fail");
      });
    } catch (Exception ignored) {
      // ignored
    }

    // Third call should be throttled
    assertThrows(ThrottleException.class, () -> throttle.checkedAttempt(() -> "should not run"));
  }

  static class DummyClock extends Clock {
    private Instant instant;

    DummyClock(Instant start) {
      this.instant = start;
    }

    void advanceSeconds() {
      instant = instant.plusSeconds(61);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.systemDefault();
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  @Test
  void testEntryExpiryWithDummyClock() throws Exception {
    var clock = new DummyClock(Instant.parse("2024-01-01T00:00:00Z"));
    var throttle = new Throttle(2.0, clock, new Random(42)::nextDouble);

    // Success and failure
    throttle.checkedAttempt(() -> "ok");
    try {
      throttle.checkedAttempt(() -> {
        throw new RuntimeException("fail");
      });
    } catch (RuntimeException ignored) {
      // ignored
    }

    // Advance clock far enough for entries to expire (ThrottleEntry uses 60s delay)
    clock.advanceSeconds();

    // Next attempt should clear expired entries, so counters reset
    throttle.checkedAttempt(() -> "ok"); // Should not throw

    // If we throw again, failures should be 1, and throttling logic should work as
    // normal
    try {
      throttle.checkedAttempt(() -> {
        throw new RuntimeException("fail2");
      });
    } catch (RuntimeException ignored) {
      // ignored
    }

    // Advance clock again to expire
    clock.advanceSeconds();
    throttle.checkedAttempt(() -> "ok"); // Should not throw
  }

  @Test
  void testAttemptRunnableSuccess() {
    var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42)::nextDouble);
    final var ran = new boolean[]{false};
    throttle.attempt(() -> ran[0] = true);
    Assertions.assertTrue(ran[0]);
  }

  @Test
  void testAttemptRunnableThrows() {
    var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42)::nextDouble);
    assertThrows(IllegalArgumentException.class, () -> throttle.attempt(() -> {
      throw new IllegalArgumentException("fail");
    }));
  }

  @Test
  void testAttemptSupplierSuccess() {
    var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42)::nextDouble);
    var result = throttle.attempt(() -> "supplied");
    Assertions.assertEquals("supplied", result);
  }

  @Test
  void testAttemptSupplierThrows() {
    var throttle = new Throttle(2.0, Clock.systemUTC(), new Random(42)::nextDouble);
    assertThrows(IllegalStateException.class, () -> throttle.attempt(() -> {
      throw new IllegalStateException("fail");
    }));
  }

  @Test
  void testAttemptRunnableThrottleException() {
    var throttle = new Throttle(1.0, Clock.systemUTC(), () -> 1.0);
    try {
      throttle.attempt(() -> {
        throw new RuntimeException("fail");
      });
      throttle.attempt(() -> {
        throw new RuntimeException("fail");
      });
    } catch (RuntimeException ignored) {
      // ignored
    }
    assertThrows(ThrottleException.class, () -> throttle.attempt(() -> {
    }));
  }

  @Test
  void testAttemptSupplierThrottleException() {
    var throttle = new Throttle(1.0, Clock.systemUTC(), () -> 1.0);
    try {
      throttle.attempt(() -> {
        throw new RuntimeException("fail");
      });
    } catch (RuntimeException ignored) {
      // ignored
    }
    assertThrows(ThrottleException.class, () -> throttle.attempt(() -> "should not run"));
  }

  @Test
  void testWrapRunnable() {
    var throttle = new Throttle();
    Runnable original = () -> {
    };
    var wrapped = throttle.wrap(original);
    Assertions.assertNotSame(original, wrapped);
    wrapped.run(); // Should not throw
  }

  @Test
  void testWrapSupplier() {
    var throttle = new Throttle();
    Supplier<String> original = () -> "wrapped";
    var wrapped = throttle.wrap(original);
    Assertions.assertNotSame(original, wrapped);
    Assertions.assertEquals("wrapped", wrapped.get());
  }

  @Test
  void testWrapMultipleFunctionsWithSingleThrottle() {
    var throttle = new Throttle();

    final var counter = new int[]{0};
    Runnable r1 = () -> counter[0]++;
    Runnable r2 = () -> counter[0] += 2;
    Supplier<Integer> s1 = () -> counter[0] * 10;

    var wrappedR1 = throttle.wrap(r1);
    var wrappedR2 = throttle.wrap(r2);
    var wrappedS1 = throttle.wrap(s1);

    wrappedR1.run();
    Assertions.assertEquals(1, counter[0]);
    wrappedR2.run();
    Assertions.assertEquals(3, counter[0]);
    int result = wrappedS1.get();
    Assertions.assertEquals(30, result);

    // All wrapped functions should still work after several invocations
    wrappedR1.run();
    wrappedR2.run();
    Assertions.assertEquals(6, counter[0]);
    Assertions.assertEquals(60, wrappedS1.get());
  }

  @Test
  void testWrapFunctionsThrottleTogether() {
    // Use a throttle that will throttle after a failure
    var throttle = new Throttle(1.0, Clock.systemUTC(), () -> 1.0);

    Runnable fail = () -> {
      throw new RuntimeException("fail");
    };
    Runnable ok = () -> {
    };
    var wrappedFail = throttle.wrap(fail);
    var wrappedOk = throttle.wrap(ok);

    // Cause a failure
    try {
      wrappedFail.run();
      wrappedFail.run();
    } catch (RuntimeException ignored) {
      // ignored
    }

    // Now both wrapped lambdas should be throttled
    assertThrows(ThrottleException.class, wrappedOk::run);
    assertThrows(ThrottleException.class, wrappedFail::run);

    // Same for Supplier
    Supplier<String> supplier = () -> "hi";
    var wrappedSupplier = throttle.wrap(supplier);
    assertThrows(ThrottleException.class, wrappedSupplier::get);
  }

  @Test
  void chainOfAttempts() {
    var clock = mock(InstantSource.class);

    var instantAnswer = new Answer<Instant>() {
      public void plusSeconds(int i) {
        now = now.plusSeconds(i);
      }

      public Instant now = Instant.parse("2024-01-01T00:00:00Z");

      @Override
      public Instant answer(InvocationOnMock invocation) {
        return now;
      }
    };

    enum Operation {
      SUCCESS, FAILURE, THROTTLE, WAIT,
    }

    when(clock.instant()).thenAnswer(instantAnswer);

    var throttle = new Throttle(2.0, clock, new Random(42)::nextDouble);

    var assertAttempted = throttle.wrap(() -> {
    });
    Runnable attemptAndFail = () -> assertThrowsExactly(RuntimeException.class, () -> throttle.attempt(() -> {
      throw new RuntimeException("fail");
    }));
    Runnable assertThrottled = () -> {
      var ignored = assertThrows(ThrottleException.class, assertAttempted::run);
    };

    var operations = List.of(Operation.SUCCESS, Operation.SUCCESS, Operation.WAIT, Operation.SUCCESS, Operation.SUCCESS,
        Operation.WAIT, Operation.SUCCESS, Operation.SUCCESS, Operation.WAIT, Operation.FAILURE, Operation.SUCCESS,
        Operation.SUCCESS, Operation.WAIT,
        // Starts failing after 68s
        Operation.FAILURE, Operation.FAILURE, Operation.FAILURE, Operation.FAILURE, Operation.WAIT, Operation.FAILURE,
        Operation.FAILURE, Operation.FAILURE, Operation.FAILURE, Operation.WAIT, Operation.FAILURE, Operation.FAILURE,
        Operation.FAILURE, Operation.THROTTLE, Operation.WAIT,
        // Starts succeeding again, but still throttled
        Operation.THROTTLE, Operation.THROTTLE, Operation.SUCCESS, Operation.THROTTLE, Operation.WAIT,
        Operation.THROTTLE, Operation.THROTTLE, Operation.THROTTLE, Operation.THROTTLE, Operation.WAIT,
        Operation.SUCCESS, Operation.SUCCESS, Operation.SUCCESS, Operation.SUCCESS);

    var i = 0;
    for (var op : operations) {
      try {
        switch (op) {
          case SUCCESS -> assertAttempted.run();
          case FAILURE -> attemptAndFail.run();
          case THROTTLE -> assertThrottled.run();
          case WAIT -> instantAnswer.plusSeconds(17);
          default -> throw new IllegalStateException("Unexpected value: " + op);
        }
      } catch (AssertionFailedError e) {
        throw new AssertionFailedError("Failed at operation " + i + " with " + op, e);
      }
      i++;
    }
  }
}
