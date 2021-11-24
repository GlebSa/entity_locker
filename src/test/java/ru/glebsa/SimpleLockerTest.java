package ru.glebsa;

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

class SimpleLockerTest {

    private ExecutorService executor;
    private SimpleLocker<Long> locker;
    private CountDownLatch latch;

    @BeforeEach
    void init() {
        executor = Executors.newFixedThreadPool(10);
        locker = new SimpleLocker<>();
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
                        locker.lock(id);
                        longs.add(n);
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        locker.unlock(id);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
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
                        locker.lock(n);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        locker.unlock(n);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
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
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        locker.unlock(id);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
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
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        locker.unlock(id);
                        latch.countDown();
                    }
                }))
                .forEach(executor::submit);

        latch.await();
        executor.shutdown();

        //then
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
                locker.lock(id);
                locker.lock(id);
                locker.lock(id);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
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
        assertTrue(flag.get());
        assertFalse(locker.hasActiveLocks());
    }

}