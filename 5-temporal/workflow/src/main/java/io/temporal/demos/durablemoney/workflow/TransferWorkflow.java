package io.temporal.demos.durablemoney.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;
import java.util.UUID;

@WorkflowInterface
public interface TransferWorkflow {
    String TASK_QUEUE = "MONEY_TRANSFER";

    @WorkflowMethod
    Result execute(Input input);

    record Input(
            UUID transferId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount
    ) {
    }

    record Result(UUID transferId, String status, String errorMessage) {
    }
}
