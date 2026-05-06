package io.temporal.demos.durablemoney.workflow;

class WorkflowNotFoundException extends RuntimeException {
    WorkflowNotFoundException(String message) {
        super(message);
    }
}
