package ru.glebsa;

import java.util.concurrent.TimeUnit;

public interface EntityLocker<ID> {

    boolean lock(ID id) throws InterruptedException;

    boolean lock(ID id, long timout, TimeUnit unit) throws InterruptedException;

    void unlock(ID id);

}
