package ru.glebsa;

import org.apache.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class SimpleLocker<T> implements Locker<T> {
    private final Logger log = Logger.getLogger(SimpleLocker.class);

    private final ConcurrentMap<T, ReentrantLock> lockMap;

    public SimpleLocker() {
        this.lockMap = new ConcurrentHashMap<>();
    }

    @Override
    public void lock(T id) throws InterruptedException{
        Objects.requireNonNull(id, "Id mast not be null!");

        log.debug(Thread.currentThread().getName() + " acquires lock");
        lockMap.computeIfAbsent(id, o -> new ReentrantLock())
                .lockInterruptibly();
    }

    @Override
    public boolean lock(T id, long timout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(id, "Id mast not be null!");
        Objects.requireNonNull(unit, "TimeUnit mast not be null!");

        log.debug(Thread.currentThread().getName() + " acquires lock");

        return lockMap.computeIfAbsent(id, o -> new ReentrantLock())
                .tryLock(timout, unit);
    }

    @Override
    public void unlock(T id) {
        Objects.requireNonNull(id, "Id mast not be null!");
        ReentrantLock lock = lockMap.get(id);

        if (lock != null && lock.isHeldByCurrentThread()) {
            log.debug(Thread.currentThread().getName() + " releases lock");

            if (!lock.hasQueuedThreads() && lock.getHoldCount() == 1) {
                log.debug(Thread.currentThread().getName() + " removes lock");

                /*FIXME hasQueuedThreads not guarantee that lock has queued threads
                *  so there may be memory leaks, one of the simple solution:
                *  demon thread end expire time for each lock */
                lockMap.remove(id);
            }
            lock.unlock();
        }
    }

    public boolean hasActiveLocks() {
        return !lockMap.isEmpty();
    }
}
