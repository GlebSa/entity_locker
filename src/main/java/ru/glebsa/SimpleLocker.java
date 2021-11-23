package ru.glebsa;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class SimpleLocker<T> implements Locker<T> {
    private final ConcurrentMap<T, ReentrantLock> lockMap;

    public SimpleLocker() {
        this.lockMap = new ConcurrentHashMap<>();
    }

    @Override
    public void lock(T id) throws InterruptedException{
        Objects.requireNonNull(id, "Id mast not be null!");
        lockMap.computeIfAbsent(id, o -> new ReentrantLock())
                .lockInterruptibly();
    }

    @Override
    public boolean lock(T id, long timout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(id, "Id mast not be null!");
        Objects.requireNonNull(unit, "TimeUnit mast not be null!");
        return lockMap.computeIfAbsent(id, o -> new ReentrantLock())
                .tryLock(timout, unit);
    }

    @Override
    public void unlock(T id) {
        Objects.requireNonNull(id, "Id mast not be null!");
        ReentrantLock lock = lockMap.get(id);
        if (lock != null) {
            if (!lock.isHeldByCurrentThread()) {
                throw new IllegalCallerException(
                        String.format("Unlock fo this thread: %s not allowed!", Thread.currentThread().getName()));
            }
            if (!lock.hasQueuedThreads()) {
                lockMap.remove(id);
            }
            lock.unlock();
        }
    }
}
