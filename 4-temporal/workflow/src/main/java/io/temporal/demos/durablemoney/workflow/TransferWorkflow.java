package io.temporal.demos.durablemoney.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TransferWorkflow {
    String TASK_QUEUE = "MONEY_TRANSFER";

    @WorkflowMethod
    TransferResult execute(TransferInput input);
}
