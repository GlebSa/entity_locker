#EntityLocker

The task is to create a reusable utility class that provides synchronization mechanism similar to row-level DB locking.

The class is supposed to be used by the components that are responsible for managing storage and caching of different type of entities in the application. EntityLocker itself does not deal with the entities, only with the IDs (primary keys) of the entities.

Requirements:

1. EntityLocker should support different types of entity IDs.

2. EntityLockerвЂ™s interface should allow the caller to specify which entity does it want to work with (using entity ID), and designate the boundaries of the code that should have exclusive access to the entity (called вЂњprotected codeвЂќ).

3. For any given entity, EntityLocker should guarantee that at most one thread executes protected code on that entity. If thereвЂ™s a concurrent request to lock the same entity, the other thread should wait until the entity becomes available.

4. EntityLocker should allow concurrent execution of protected code on different entities.


Bonus requirements (optional):

I. Allow reentrant locking.

II. Allow the caller to specify timeout for locking an entity.

III. Implement protection from deadlocks (but not taking into account possible locks outside EntityLocker).

IV. Implement global lock. Protected code that executes under a global lock must not execute concurrently with any other protected code.

V. Implement lock escalation. If a single thread has locked too many entities, escalate its lock to be a global lock.

#Current implemetation

SimpleEntityLocker class based on ConcurrentMap and ReentrantLock
for each Id, own ReentrantLock is created, which exists until lock is unlocked
or as long as there is a queue of threads for blocking by the current Id.

Can be used for reentrant.

ThreadLocal is used to protect against deadlocks, adds Id to the current thread if it acquired lock,
in case of an attempt to capture one more id, which is already captured by another thread,
acquire of lock for another id will be rejected

Implemented for demo purposes
there are some simplifications, in result is a small possibility of a memory leak due remove ReentrantLock,
and a small probability of failure when obtaining a lock due to protection against deadlocks

#Not implemented
Bonus requirements IV and V