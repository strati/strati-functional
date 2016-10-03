package io.strati.functional.resilience;

import io.strati.functional.Try;
import io.strati.functional.exception.CircuitBreakerOpenException;
import io.strati.functional.exception.WrappedCheckedException;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author WalmartLabs
 * @author Georgi Khomeriki [gkhomeriki@walmartlabs.com]
 */

public class CircuitBreakerTest {

  @Test
  public void testCBWithRunnable_OpenCircuitThenSucceed() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(2000)
        .build();

    assertTrue(cb.isClosed());

    Try<Void> result1 = cb.attempt(() -> System.out.println(3 / 0));
    assertFailure(ArithmeticException.class, result1);
    assertTrue(cb.isClosed());

    Try<Void> result2 = cb.attempt(() -> System.out.println(3 / 0));
    assertFailure(ArithmeticException.class, result2);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen());

    Try<Void> result3 = cb.attempt(() -> System.out.println("foo"));
    assertTrue("call should have succeeded", result3.isSuccess());
    assertTrue(cb.isClosed());
  }

  @Test
  public void testCBWithRunnable_OpenCircuitThenFail() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(Duration.ofMillis(2000))
        .build();

    assertTrue(cb.isClosed());

    Try<Void> result1 = cb.attempt(() -> System.out.println(3 / 0));
    assertFailure(ArithmeticException.class, result1);
    assertTrue(cb.isClosed());

    Try<Void> result2 = cb.attempt(() -> System.out.println(3 / 0));
    assertFailure(ArithmeticException.class, result2);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen());

    Try<Void> result3 = cb.attempt(() -> System.out.println(3 / 0));
    assertFailure(ArithmeticException.class, result3);
    assertTrue(cb.isOpen());
  }

  @Test
  public void testCBWithRunnable_closedCiruit() {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(Duration.ofMillis(2000))
        .build();

    assertTrue(cb.isClosed());
    IntStream.range(0, 100)
        .forEach(i -> {
          cb.attempt(() -> {});
          assertTrue(cb.isClosed());
        });
  }

  @Test
  public void testCBWithRunnable_openCiruit() {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(1)
        .timeout(Duration.ofMillis(1337))
        .build();

    assertTrue(cb.isClosed());
    IntStream.range(0, 100)
        .forEach(i -> {
          Try<Void> result = cb.attempt(() -> System.out.println(3 / 0));
          assertTrue("call should have failed", result.isFailure());
          assertTrue("circuit breaker should be open", cb.isOpen());
        });
  }

  @Test
  public void testCBWithSupplier_OpenCircuitThenSucceed() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(2000)
        .build();

    assertTrue(cb.isClosed());

    Try<Integer> result1 = cb.attempt(() -> 3/0);
    assertFailure(ArithmeticException.class, result1);
    assertTrue(cb.isClosed());

    Try<Integer> result2 = cb.attempt(() -> 3 / 0);
    assertFailure(ArithmeticException.class, result2);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen());

    Try<Integer> result3 = cb.attempt(() -> 3/1);
    assertTrue("call should have succeeded", result3.isSuccess());
    assertTrue("result should be 3", 3 == result3.get());
    assertTrue(cb.isClosed());
  }

  @Test
  public void testCBWithSupplier_OpenCircuitThenFail() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(Duration.ofMillis(2000))
        .build();

    assertTrue(cb.isClosed());

    Try<Integer> result1 = cb.attempt(() -> 3 / 0);
    assertFailure(ArithmeticException.class, result1);
    assertTrue(cb.isClosed());

    Try<Integer> result2 = cb.attempt(() -> 3 / 0);
    assertFailure(ArithmeticException.class, result2);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isOpen());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen());

    Try<Integer> result3 = cb.attempt(() -> 3 / 0);
    assertFailure(ArithmeticException.class, result3);
    assertTrue(cb.isOpen());
  }

  @Test
  public void testCB_noStatusChecks() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(1000)
        .build();

    cb.attempt(() -> 3 / 0);
    cb.attempt(() -> 3 / 0);
    assertFailure(CircuitBreakerOpenException.class, cb.attempt(() -> 3 / 0));
    Thread.sleep(1000);
    assertEquals("Circuit breaker should be closed and action should succeed", "foo", cb.attempt(() -> "foo").get());
  }

  @Test
  public void testCB_OpenStateFallback() {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(1000)
        .build();
    AtomicBoolean flag = new AtomicBoolean(false);
    cb.attempt(() -> 3 / 0);
    cb.attempt(() -> 3 / 0);
    assertTrue(cb.isOpen());
    assertFalse("flag should've been set to false", flag.get());

    cb.attempt(() -> 3 / 0).ifFailure(e -> flag.set(true));

    assertTrue(cb.isOpen());
    assertTrue("flag should've been set to true", flag.get());
  }

  @Test
  public void testCB_HalfOpenStateFallback() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(2)
        .timeout(1000)
        .build();
    AtomicBoolean flag = new AtomicBoolean(false);
    cb.attempt(() -> 3 / 0);
    cb.attempt(() -> 3 / 0);
    assertTrue(cb.isOpen());
    Thread.sleep(2000);
    assertTrue(cb.isHalfOpen());
    assertFalse("flag should've been set to false", flag.get());

    cb.attempt(() -> 3 / 0).ifFailure(e -> flag.set(true));
    assertTrue(cb.isOpen());
    assertTrue("flag should've been set to true", flag.get());
  }

  @Test
  public void testCB_getters() {
    String name = "foobar";
    int threshold = 13;
    long timeout = 1337;

    CircuitBreaker cb1 = CircuitBreakerBuilder.create(name)
        .threshold(threshold)
        .timeout(timeout)
        .build();

    cb1.getName();
    assertEquals("name should be equal", name, cb1.getName());
    assertEquals("threshold should be equal", threshold, cb1.getThreshold());
    assertEquals("timeout should be equal", timeout, cb1.getTimeout());

    CircuitBreaker cb2 = CircuitBreakerBuilder.create(name)
        .threshold(threshold)
        .timeout(Duration.ofMillis(timeout))
        .build();

    cb2.getName();
    assertEquals("name should be equal", name, cb2.getName());
    assertEquals("threshold should be equal", threshold, cb2.getThreshold());
    assertEquals("timeout should be equal", timeout, cb2.getTimeout());
  }

  @Test
  public void testCB_CircuitBreakerOpenException() throws InterruptedException {
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(1)
        .timeout(2000)
        .build();

    assertTrue(cb.isClosed());

    Try<Integer> result1 = cb.attempt(() -> 3 / 0);

    assertFailure(ArithmeticException.class, result1);
    assertTrue(cb.isOpen());

    Try<Integer> result2 = cb.attempt(() -> 3 / 0);
    assertFailure(CircuitBreakerOpenException.class, result2);
    assertEquals("original exception should be stored",
        ArithmeticException.class, cb.getFailureFromLastAttempt().get().getClass());

    Thread.sleep(2000);

    Try<Integer> result3 = cb.attempt(() -> 3);
    assertTrue("result should be 3", 3 == result3.get());
    assertTrue("failure from last attempt should be empty", cb.getFailureFromLastAttempt().isEmpty());
  }

  @Test
  public void testCB_StateChangeListener() throws InterruptedException {
    AtomicInteger count = new AtomicInteger(0);
    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(1)
        .timeout(1000)
        .stateChangeListener(count::incrementAndGet)
        .build();

    cb.attempt(() -> 3 / 0);
    assertTrue(cb.isOpen());

    assertEquals("count should be 1", 1, count.get());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen()); // this check triggers state change

    assertEquals("count should be 2", 2, count.get());

    cb.attempt(() -> 3);
    assertTrue(cb.isClosed());

    assertEquals("count should be 3", 3, count.get());
  }

  @Test
  public void testCB_StateChangeListeners() throws InterruptedException {
    AtomicInteger countToClosed = new AtomicInteger(0);
    AtomicInteger countToHalfOpen = new AtomicInteger(0);
    AtomicInteger countToOpen = new AtomicInteger(0);

    CircuitBreaker cb = CircuitBreakerBuilder.create()
        .threshold(1)
        .timeout(1000)
        .toClosedStateListener(countToClosed::incrementAndGet)
        .toHalfOpenStateListener(countToHalfOpen::incrementAndGet)
        .toOpenStateListener(countToOpen::incrementAndGet)
        .build();

    cb.attempt(() -> 3 / 0);
    assertTrue(cb.isOpen());

    assertEquals("count should be 1", 1, countToOpen.get());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen()); // this check triggers state change

    assertEquals("count should be 1", 1, countToHalfOpen.get());

    cb.attempt(() -> 3);
    assertTrue(cb.isClosed());

    assertEquals("count should be 1", 1, countToClosed.get());

    cb.attempt(() -> 3 / 0);
    assertTrue(cb.isOpen());

    assertEquals("count should be 2", 2, countToOpen.get());

    Thread.sleep(1000);
    assertTrue(cb.isHalfOpen()); // this check triggers state change

    assertEquals("count should be 2", 2, countToHalfOpen.get());

    cb.attempt(() -> 3);
    assertTrue(cb.isClosed());

    assertEquals("count should be 2", 2, countToClosed.get());
  }

  private static <T> void assertFailure(Class<? extends Throwable> ex, Try<T> value) {
    assertTrue("Expected failure didn't occur: " + ex, value.isFailure());
    try {
      value.get();
    } catch (Throwable t) {
      Class<? extends Throwable> exceptionClass =
          WrappedCheckedException.class.equals(t.getClass()) ? t.getCause().getClass() : t.getClass();
      assertEquals("Unexpected exception occurred", ex, exceptionClass);
    }
  }

}
