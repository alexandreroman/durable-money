package io.temporal.demos.durablemoney.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Saga;

import static io.temporal.workflow.Workflow.newActivityStub;
import static java.time.Duration.ofSeconds;

@WorkflowImpl(workers = "transfer")
class TransferWorkflowImpl implements TransferWorkflow {
    private final AccountActivities activities = newActivityStub(AccountActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    @Override
    public TransferWorkflow.Result execute(TransferWorkflow.Input input) {
        // Temporal automatically persists workflow state and retries failed activities.
        // If this process crashes mid-execution, Temporal replays the workflow from its
        // event history — no completed activity is re-executed.
        var saga = new Saga(new Saga.Options.Builder().build());
        try {
            var debitInput = new AccountActivities.DebitInput(
                    input.sourceAccountId(), input.amount(), input.transferId());
            var creditInput = new AccountActivities.CreditInput(
                    input.targetAccountId(), input.amount(), input.transferId());
            var reverseDebitInput = new AccountActivities.ReverseDebitInput(
                    input.sourceAccountId(), input.amount(), input.transferId());

            // Register compensation BEFORE the activity it guards.
            // If creditAccount fails, saga.compensate() reverses the debit.
            saga.addCompensation(activities::reverseDebit, reverseDebitInput);
            activities.debitAccount(debitInput);

            activities.creditAccount(creditInput);

            return new TransferWorkflow.Result(input.transferId(), "COMPLETED", null);
        } catch (ActivityFailure e) {
            // Temporal retried the activity before reaching here (maxAttempts exhausted).
            // saga.compensate() executes registered compensations in LIFO order.
            saga.compensate();
            return new TransferWorkflow.Result(input.transferId(), "FAILED", e.getMessage());
        }
    }
}
