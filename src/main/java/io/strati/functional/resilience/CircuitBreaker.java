/*
 * Copyright 2016 WalmartLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.strati.functional.resilience;

import io.strati.functional.LazyTry;
import io.strati.functional.Optional;
import io.strati.functional.Try;
import io.strati.functional.exception.CircuitBreakerOpenException;
import io.strati.functional.function.TryRunnable;
import io.strati.functional.function.TrySupplier;

/**
 * @author WalmartLabs
 * @author Georgi Khomeriki [gkhomeriki@walmartlabs.com]
 *
 * Implementation of the Circuit Breaker pattern, proposed by Michael T. Nygard in his book "Release It!".
 * Ported and extended version of the C# implementation proposed by Patrick Desjardins
 * (https://github.com/MrDesjardins/DotNetCircuitBreaker).
 */
public class CircuitBreaker {

  private String name;

  private final Object monitor = new Object();
  private CircuitBreakerState state;

  private int failures = 0;
  private final int threshold;
  private final long timeout;
  private Throwable lastObservedFailure;

  private Runnable toClosedStateListener = () -> {};
  private Runnable toHalfOpenStateListener = () -> {};
  private Runnable toOpenStateListener = () -> {};

  protected CircuitBreaker(final String name, final int threshold, final long timeout) {
    if (threshold < 1) {
      throw new IllegalArgumentException("Failure threshold should be greater than 0");
    }
    if (timeout < 1) {
      throw new IllegalArgumentException("Timeout should be greater than 0");
    }
    this.name = name;
    this.threshold = threshold;
    this.timeout = timeout;
    moveToClosedState();
  }

  public Try<Void> attempt(final TryRunnable protectedCode) {
    return attempt(LazyTry.ofFailable(protectedCode));
  }

  public <T> Try<T> attempt(final TrySupplier<T> protectedCode) {
    return attempt(LazyTry.ofFailable(protectedCode));
  }

  public <T> Try<T> attempt(final LazyTry<T> protectedCode) {
    synchronized (monitor) {
      state.protectedCodeAboutToBeCalled();
      if (CircuitBreakerOpenState.class.isInstance(state)) {
        return Try.failure(new CircuitBreakerOpenException("Circuit breaker open, attempt aborted", lastObservedFailure));
      }
    }

    this.lastObservedFailure = null;

    T result;

    try {
      result = protectedCode.run().get();
    } catch (Throwable t) {
      lastObservedFailure = t;
      synchronized (monitor) {
        state.actUponException(t);
      }
      return Try.failure(t);
    }

    synchronized (monitor) {
      state.protectedCodeHasBeenCalled();
    }

    return Try.success(result);
  }

  public void close() {
    synchronized (monitor) {
      moveToClosedState();
    }
  }

  public void open() {
    synchronized (monitor) {
      moveToOpenState();
    }
  }

  CircuitBreakerState moveToClosedState() {
    toClosedStateListener.run();
    state = new CircuitBreakerClosedState(this);
    return state;
  }

  CircuitBreakerState moveToHalfOpenState() {
    toHalfOpenStateListener.run();
    state = new CircuitBreakerHalfOpenState(this);
    return state;
  }

  CircuitBreakerState moveToOpenState() {
    toOpenStateListener.run();
    state = new CircuitBreakerOpenState(this);
    return state;
  }

  void increaseFailureCount() {
    failures++;
  }

  void resetFailureCount() {
    failures = 0;
  }

  public boolean isClosed() {
    return CircuitBreakerClosedState.class.isInstance(state.update());
  }

  public boolean isHalfOpen() {
    return CircuitBreakerHalfOpenState.class.isInstance(state.update());
  }

  public boolean isOpen() {
    return CircuitBreakerOpenState.class.isInstance(state.update());
  }

  public boolean isThresholdReached() {
    return failures >= threshold;
  }

  public Optional<Throwable> getFailureFromLastAttempt() {
    return Optional.ofNullable(lastObservedFailure);
  }

  public String getName() {
    return name;
  }

  public long getTimeout() {
    return timeout;
  }

  public int getThreshold() {
    return threshold;
  }

  public void setToClosedStateListener(final Runnable toClosedStateListener) {
    this.toClosedStateListener = toClosedStateListener;
  }

  public void setToHalfOpenStateListener(final Runnable toHalfOpenStateListener) {
    this.toHalfOpenStateListener = toHalfOpenStateListener;
  }

  public void setToOpenStateListener(final Runnable toOpenStateListener) {
    this.toOpenStateListener = toOpenStateListener;
  }

}
