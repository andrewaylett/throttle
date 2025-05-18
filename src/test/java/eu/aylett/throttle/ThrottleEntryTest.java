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
import java.util.concurrent.Delayed;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThrottleEntryTest {
  @Test
  void equalityAndHashcodeTest() {

    var changingClock = mock(InstantSource.class);
    var instantAnswer = new InstantAnswer();
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

  @Test
  void compareToSelf() {
    var clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var entry = new ThrottleEntry(true, clock);
    assertThat(entry, comparesEqualTo(entry));
  }

  @Test
  void compareToSameExpiry() {
    var clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var entry1 = new ThrottleEntry(true, clock);
    var entry2 = new ThrottleEntry(false, clock);
    assertThat(entry1, comparesEqualTo(entry2));
  }

  @Test
  void compareToDifferentExpiry() {
    var changingClock = mock(InstantSource.class);
    var instantAnswer = new InstantAnswer();
    when(changingClock.instant()).thenAnswer(instantAnswer);

    var entry1 = new ThrottleEntry(true, changingClock);
    instantAnswer.plusSeconds(1);
    var entry2 = new ThrottleEntry(false, changingClock);
    assertThat(entry1, lessThan(entry2));
  }

  @Test
  void compareToOtherDelayed() {
    var clock1 = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var entry1 = new ThrottleEntry(true, clock1);
    var entry2 = mock(Delayed.class);
    when(entry2.getDelay(any())).thenReturn(80000L);
    assertThat(entry1, lessThan(entry2));
  }

  @Test
  void hashCodeUsesSuccess() {
    var clock1 = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var entry1 = new ThrottleEntry(true, clock1);
    var entry2 = new ThrottleEntry(false, clock1);
    assertThat(entry1.hashCode(), not(equalTo(entry2.hashCode())));
  }

  @Test
  void hashCodeUsesExpiry() {
    var changingClock = mock(InstantSource.class);
    var instantAnswer = new InstantAnswer();
    when(changingClock.instant()).thenAnswer(instantAnswer);

    var entry1 = new ThrottleEntry(true, changingClock);
    instantAnswer.plusSeconds(1);
    var entry2 = new ThrottleEntry(true, changingClock);
    assertThat(entry1.hashCode(), not(equalTo(entry2.hashCode())));
  }

  @Test
  void hashCodeDoesNotUseClock() {
    var clock1 = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var clock2 = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    var entry1 = new ThrottleEntry(true, clock1);
    var entry2 = new ThrottleEntry(true, clock2);
    assertThat(entry1.hashCode(), equalTo(entry2.hashCode()));
  }

  private static class InstantAnswer implements Answer<Instant> {
    public void plusSeconds(int i) {
      now = now.plusSeconds(i);
    }

    public Instant now = Instant.parse("2024-01-01T00:00:00Z");

    @Override
    public Instant answer(InvocationOnMock invocation) {
      return now;
    }
  }
}
