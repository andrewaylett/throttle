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

import com.google.common.testing.EqualsTester;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThrottleEntryTest {
  @Test
  void equalityAndHashcodeTest() {

    var changingClock = mock(InstantSource.class);

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

    when(changingClock.instant()).thenAnswer(instantAnswer);

    var tester = new EqualsTester();
    var clock1 = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var clock2 = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    // Same timestamp, different clocks
    tester.addEqualityGroup(new ThrottleEntry(true, clock1), new ThrottleEntry(true, clock2),
        new ThrottleEntry(true, changingClock));
    // Different status, same timestamp
    tester.addEqualityGroup(new ThrottleEntry(false, clock1), new ThrottleEntry(false, clock2),
        new ThrottleEntry(false, changingClock));

    // Change the clock, now equal to a new fixed time
    instantAnswer.plusSeconds(1);
    var clock3 = Clock.fixed(Instant.parse("2024-01-01T00:00:01Z"), ZoneId.of("UTC"));
    tester.addEqualityGroup(new ThrottleEntry(false, clock3), new ThrottleEntry(false, changingClock));

    tester.testEquals();
  }
}
