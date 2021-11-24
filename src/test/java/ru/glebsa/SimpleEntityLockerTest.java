package ru.glebsa;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class SimpleEntityLockerTest {
    private final Logger log = Logger.getLogger(SimpleEntityLockerTest.class);

    private ExecutorService executor;
    private SimpleEntityLocker<Long> locker;
    private CountDownLatch latch;
    private final AtomicBoolean exceptionOccurred = new AtomicBoolean();

    @BeforeEach
    void init() {
        executor = Executors.newFixedThreadPool(10);
        locker = new SimpleEntityLocker<>();
        exceptionOccurred.set(false);
    }

    @Test
    void testContentionForLock() throws Exception {
        //given
        final int count = 100;
        latch = new CountDownLatch(count);

        final Long id = 1L;
        final Set<Long> longs = new HashSet<>();

        //when
        LongStream.range(0, count)
                .mapToObj(i -> new TestRunnable(i, n -> {
                    try {
                        lockWithExceptionOnFalse(id);
                        longs.add(n);
                        Thread.sleep(10);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        exceptionOccurred.set(true);
                    } finally {
                        locker.unlock(id);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
        assertFalse(exceptionOccurred.get());
        assertFalse(locker.hasActiveLocks());

        Set<Long> expected = LongStream.range(0, count)
                .boxed()
                .collect(Collectors.toSet());
        assertEquals(expected, longs);
    }

    @Test
    void testDifferentIds() throws Exception {
        //given
        final int count = 100;
        latch = new CountDownLatch(count);

        //when
        LongStream.range(0, count)
                .mapToObj(i -> new TestRunnable(i, n -> {
                    try {
                        lockWithExceptionOnFalse(n);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        exceptionOccurred.set(true);
                    } finally {
                        locker.unlock(n);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
        assertFalse(exceptionOccurred.get());
        assertFalse(locker.hasActiveLocks());
    }

    @Test
    void testContentionForLockWithTimout() throws Exception {
        //given
        final int count = 10;
        latch = new CountDownLatch(count);

        final Long id = 1L;
        final Set<Long> longs = new HashSet<>();

        //when
        LongStream.range(0, count)
                .mapToObj(i -> new TestRunnable(i, n -> {
                    try {
                        if (locker.lock(id, 1000, TimeUnit.MILLISECONDS)) {
                            longs.add(n);
                            Thread.sleep(10);
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        exceptionOccurred.set(true);
                    } finally {
                        locker.unlock(id);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
        assertFalse(exceptionOccurred.get());
        assertFalse(locker.hasActiveLocks());

        Set<Long> expected = LongStream.range(0, count)
                .boxed()
                .collect(Collectors.toSet());
        assertEquals(expected, longs);
    }

    @Test
    void testContentionForLockWithTimoutExceeds() throws Exception {
        //given
        final int count = 10;
        latch = new CountDownLatch(count);

        final Long id = 1L;
        final Set<Long> longs = new HashSet<>();

        //when
        LongStream.range(0, count)
                .mapToObj(i -> new TestRunnable(i, n -> {
                    try {
                        if (locker.lock(id, 100, TimeUnit.MILLISECONDS)) {
                            longs.add(n);
                            Thread.sleep(10);
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        exceptionOccurred.set(true);
                    } finally {
                        locker.unlock(id);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
        assertFalse(exceptionOccurred.get());
        assertFalse(locker.hasActiveLocks());

        Set<Long> expected = LongStream.range(0, count)
                .boxed()
                .collect(Collectors.toSet());
        assertFalse(longs.isEmpty());
        assertNotEquals(expected, longs);
    }

    @Test
    void testReentrant() throws Exception {
        //given
        latch = new CountDownLatch(1);
        final Long id = 1L;
        final AtomicBoolean flag = new AtomicBoolean(false);

        //when
        executor.submit(() -> {
            try {
                lockWithExceptionOnFalse(id);
                lockWithExceptionOnFalse(id);
                lockWithExceptionOnFalse(id);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                exceptionOccurred.set(true);
            } finally {
                locker.unlock(id);

                flag.set(locker.hasActiveLocks());

                locker.unlock(id);
                locker.unlock(id);

                latch.countDown();
            }
        });

        latch.await();

        //then
        assertFalse(exceptionOccurred.get());
        assertTrue(flag.get());
        assertFalse(locker.hasActiveLocks());
    }

    @Test
    void testDeadlockAvoid() throws Exception {
        //given
        final Long id1 = 1L;
        final Long id2 = 2L;
        latch = new CountDownLatch(2);
        final AtomicBoolean firstLockAcquired = new AtomicBoolean();
        final AtomicBoolean secondLockAcquired = new AtomicBoolean();

        //when
        executor.submit(() -> {
            try {
                if (locker.lock(id1)) {
                    Thread.sleep(100);
                    firstLockAcquired.set(locker.lock(id2));
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                exceptionOccurred.set(true);
            } finally {
                locker.unlock(id1);
                locker.unlock(id2);

                latch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                if (locker.lock(id2)) {
                    secondLockAcquired.set(locker.lock(id1));
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                exceptionOccurred.set(true);
            } finally {
                locker.unlock(id2);
                locker.unlock(id1);

                latch.countDown();
            }
        });

        latch.await();

        //then
        assertFalse(exceptionOccurred.get());
        assertTrue(firstLockAcquired.get());
        assertFalse(secondLockAcquired.get());
    }

    private void lockWithExceptionOnFalse(Long n) throws InterruptedException, IllegalMonitorStateException {
        if (!locker.lock(n)) {
            throw new IllegalMonitorStateException("Lock not acquired!");
        }
    }

}