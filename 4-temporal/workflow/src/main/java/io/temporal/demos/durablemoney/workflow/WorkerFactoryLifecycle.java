package io.temporal.demos.durablemoney.workflow;

import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
class WorkerFactoryLifecycle {

    private final WorkerFactory workerFactory;

    WorkerFactoryLifecycle(WorkerFactory workerFactory) {
        this.workerFactory = workerFactory;
    }

    @PostConstruct
    void start() {
        workerFactory.start();
    }

    @PreDestroy
    void shutdown() {
        workerFactory.shutdown();
    }
}
