package io.temporal.demos.durablemoney.workflow;

class WorkflowNotFoundException extends RuntimeException {
    WorkflowNotFoundException(String workflowId) {
        super("Workflow not found: " + workflowId);
    }
}
