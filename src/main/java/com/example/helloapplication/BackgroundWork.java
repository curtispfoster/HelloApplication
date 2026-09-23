package com.example.helloapplication;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

final class BackgroundWork {

    private final String threadName;
    private Task<?> current;

    BackgroundWork(String threadName) {
        this.threadName = threadName;
    }

    <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            if (task == current) {
                onSuccess.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            if (task == current) {
                onFailure.accept(task.getException());
            }
        });

        cancel();
        current = task;
        Thread worker = new Thread(task, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    void cancel() {
        if (current != null) {
            current.cancel();
            current = null;
        }
    }
}
