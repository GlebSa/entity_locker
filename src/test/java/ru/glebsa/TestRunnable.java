package ru.glebsa;

import java.util.function.Consumer;

public class TestRunnable implements Runnable{
    private final Long n;
    private final Consumer<Long> consumer;

    public TestRunnable(Long n, Consumer<Long> consumer) {
        this.n = n;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        consumer.accept(n);
    }
}
