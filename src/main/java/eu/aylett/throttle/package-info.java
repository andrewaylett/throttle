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

/**
 * As a service client, there's little point in sending requests to the service
 * that we can be reasonably sure will fail.
 * <p>
 * Once we start seeing remote failures, we'll only send 2x the number of
 * successes observed over the past minute.
 * </p>
 * <p>
 * One instance of Throttle should be used for each distinct fault zone
 * (normally each service) you call. You <i>should</i> use the same instance for
 * different methods called on the same service.
 * </p>
 * <p>
 * I recommend putting the Throttle around service-specific logic, rather than
 * at the point of actually making a network call. There's no point in setting
 * up the call only to decide to throttle it.
 * </p>
 */
@NullMarked
package eu.aylett.throttle;

import org.jspecify.annotations.NullMarked;
