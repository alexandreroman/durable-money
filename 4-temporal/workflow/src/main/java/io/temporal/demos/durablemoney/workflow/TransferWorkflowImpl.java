package io.temporal.demos.durablemoney.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(workers = "transfer")
class TransferWorkflowImpl implements TransferWorkflow {

    @Override
    public TransferResult execute(TransferInput input) {
        var options = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build();
        var activities = Workflow.newActivityStub(AccountActivities.class, options);

        // Temporal automatically persists workflow state and retries failed activities.
        // If this process crashes mid-execution, Temporal replays the workflow from its
        // event history — no completed activity is re-executed.
        var saga = new Saga(new Saga.Options.Builder().build());
        try {
            // Register compensation BEFORE the activity it guards.
            // If creditAccount fails, saga.compensate() reverses the debit.
            saga.addCompensation(activities::reverseDebit,
                input.sourceAccountId(), input.amount(), input.transferId());
            activities.debitAccount(input.sourceAccountId(), input.amount(), input.transferId());

            activities.creditAccount(input.targetAccountId(), input.amount(), input.transferId());

            return new TransferResult(input.transferId(), "COMPLETED", null);
        } catch (ActivityFailure e) {
            // Temporal retried the activity before reaching here (maxAttempts exhausted).
            // saga.compensate() executes registered compensations in LIFO order.
            saga.compensate();
            return new TransferResult(input.transferId(), "FAILED", e.getMessage());
        }
    }
}
