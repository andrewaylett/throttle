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

/**
 * Exception thrown when a throttle limit is exceeded.
 * <p>
 * Provides details about the number of successes, failures, and the allowed
 * ratio at the time of exception.
 * </p>
 */
public class ThrottleException extends RuntimeException {
  /**
   * The number of successful attempts in the current window.
   */
  public final long successes;
  /**
   * The number of failed attempts in the current window.
   */
  public final long failures;
  /**
   * The allowed ratio of attempts to successes at the time of exception.
   */
  public final double ratio;

  /**
   * Constructs a new ThrottleException with details about the throttle state.
   *
   * @param message
   *          the detail message
   * @param successes
   *          the number of successes in the current window
   * @param failures
   *          the number of failures in the current window
   * @param ratio
   *          the allowed ratio at the time of exception
   */
  public ThrottleException(String message, long successes, long failures, double ratio) {
    super(message + " (last 60s: " + successes + " successes, " + failures + " failures, allowed ratio " + ratio + ")");
    this.successes = successes;
    this.failures = failures;
    this.ratio = ratio;
  }
}
