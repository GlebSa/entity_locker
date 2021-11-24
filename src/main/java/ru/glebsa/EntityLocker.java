package ru.glebsa;

import java.util.concurrent.TimeUnit;

/**
 * The interface for managing locks by entity Id, similar to locking rows in a database
 *
 * @param <ID> type of Id
 */
public interface EntityLocker<ID> {

    /**
     * Lock by entity Id
     * can be used reentrantly
     *
     * @param id entity Id
     * @return true if thread successfully acquired the lock, false if not (deadlock possibility)
     * @throws InterruptedException if lock acquire was interrupted
     * @throws NullPointerException if id == null
     */
    boolean lock(ID id) throws InterruptedException;

    /**
     * Lock by entity Id with acquire lock with timeout
     * can be used reentrantly
     *
     * @param id     entity Id
     * @param timout the time to wait for the lock
     * @param unit   the time unit of the timeout argument
     * @return true if thread successfully acquired the lock, false if not (deadlock possibility) or timeout exceeds
     * @throws InterruptedException if lock acquire was interrupted
     * @throws NullPointerException if id == null or unit == null
     */
    boolean lock(ID id, long timout, TimeUnit unit) throws InterruptedException;

    /**
     * Unlock by entity id of locked thread, do nothing otherwise
     * if used reentrantly, must be unlocked as much as locked by same thread
     *
     * @param id entity Id
     * @throws NullPointerException if id == null
     */
    void unlock(ID id);

}
