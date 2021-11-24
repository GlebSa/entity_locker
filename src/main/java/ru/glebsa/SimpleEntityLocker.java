package ru.glebsa;

import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple EntityLocker based on ConcurrentMap and ReentrantLock
 * for each Id, own ReentrantLock is created, which exists until lock is unlocked
 * or as long as there is a queue of threads for blocking by the current Id.
 * <p>
 * Can be used for reentrant.
 * <p>
 * ThreadLocal is used to protect against deadlocks, adds Id to the current thread,
 * in case of an attempt to capture one more id, which is already captured by another thread,
 * acquire of lock will be rejected
 * <p>
 * Implemented for demo purposes
 * there are some simplifications, in particular a small possibility of a memory leak,
 * and a small probability of failure when obtaining a lock due to protection against deadlocks
 *
 * @param <T> type of Id
 */
public final class SimpleEntityLocker<T> implements EntityLocker<T> {
    private final Logger log = Logger.getLogger(SimpleEntityLocker.class);

    private final ConcurrentMap<T, ReentrantLock> lockMap;

    //using for protection from deadlocks
    private final ThreadLocal<Set<T>> threadLocal;


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

        saveLockedId(id);

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
        if (lockMap.computeIfAbsent(id, o -> new ReentrantLock())
                .tryLock(timout, unit)) {

            saveLockedId(id);
            return true;
        }
        return false;
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
                removeLockedId(id);
            }
        }
    }

    public boolean hasActiveLocks() {
        return !lockMap.isEmpty();
    }

    private void saveLockedId(T id) {
        Set<T> ids = Optional.ofNullable(threadLocal.get())
                .map(HashSet::new)
                .orElse(new HashSet<>());
        ids.add(id);
        threadLocal.set(ids);
    }

    private void removeLockedId(T id) {
        Set<T> ids = Optional.ofNullable(threadLocal.get())
                .map(HashSet::new)
                .orElse(new HashSet<>());
        ids.remove(id);
        if (ids.isEmpty()) {
            threadLocal.remove();
        } else {
            threadLocal.set(ids);
        }
    }

    private boolean checkForDeadlockPossibility(T id) {
        Set<T> ids = threadLocal.get();
        if (ids != null && !ids.contains(id) && lockMap.containsKey(id)) {
            log.warn("Lock not acquired due to the possibility of deadlock!");
            return true;
        }
        return false;
    }
}
