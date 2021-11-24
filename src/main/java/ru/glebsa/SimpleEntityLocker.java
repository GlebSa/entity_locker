package ru.glebsa;

import org.apache.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class SimpleEntityLocker<T> implements EntityLocker<T> {
    private final Logger log = Logger.getLogger(SimpleEntityLocker.class);

    private final ConcurrentMap<T, ReentrantLock> lockMap;

    //using for protection from deadlocks
    private final ThreadLocal<T> threadLocal;


    public SimpleEntityLocker() {
        this.lockMap = new ConcurrentHashMap<>();
        this.threadLocal = new ThreadLocal<>();
    }

    @Override
    public boolean lock(T id) throws InterruptedException {
        Objects.requireNonNull(id, "Id mast not be null!");

        if (checkForDeadlockPossibility(id)) {
            return false;
        }

        log.debug(Thread.currentThread().getName() + " acquires lock");
        lockMap.computeIfAbsent(id, o -> new ReentrantLock())
                .lockInterruptibly();

        threadLocal.set(id);

        return true;
    }

    @Override
    public boolean lock(T id, long timout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(id, "Id mast not be null!");
        Objects.requireNonNull(unit, "TimeUnit mast not be null!");

        if (checkForDeadlockPossibility(id)) {
            return false;
        }

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

                /*FIXME hasQueuedThreads() not guarantee 100% that lock has queued threads
                 *  so there may be a small chance of memory leaks, one of the simple solution:
                 *  demon thread end expire time for each lock */
                lockMap.remove(id);
            }
            boolean flag = lock.getHoldCount() == 1;

            lock.unlock();

            if (flag) {
                /*FIXME there is a small chance of checkForDeadlockPossibility() == true while it is already false,
                *  solution: implement your own QueuedSynchronizer instead of using ReentrantLock*/
                threadLocal.remove();
            }
        }
    }

    public boolean hasActiveLocks() {
        return !lockMap.isEmpty();
    }

    private boolean checkForDeadlockPossibility(T id) {
        T threadId = threadLocal.get();
        if (threadId != null && !threadId.equals(id) && lockMap.containsKey(id)) {
            log.warn("Lock not acquired due to the possibility of deadlock!");
            return true;
        }
        return false;
    }
}