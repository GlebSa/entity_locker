package ru.glebsa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class SimpleLockerTest {

    private ExecutorService executor;

    @BeforeEach
    void init() {
        executor = Executors.newFixedThreadPool(10);
    }

    @Test
    void testContentionForLock() throws Exception {
        //given
        final SimpleLocker<Long> locker = new SimpleLocker<>(false);
        final int count = 100;
        final CountDownLatch latch = new CountDownLatch(count);
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
    void testContentionForLockWithTimout() throws Exception {
        //given
        final SimpleLocker<Long> locker = new SimpleLocker<>(false);
        final int count = 10;
        final CountDownLatch latch = new CountDownLatch(count);
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

}