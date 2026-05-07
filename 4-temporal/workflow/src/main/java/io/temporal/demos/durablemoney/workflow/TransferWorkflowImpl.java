package io.temporal.demos.durablemoney.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.demos.durablemoney.workflow.AccountActivities.CreditInput;
import io.temporal.demos.durablemoney.workflow.AccountActivities.DebitInput;
import io.temporal.demos.durablemoney.workflow.AccountActivities.ReverseDebitInput;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

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

    // Compensation activities retry indefinitely: a failed rollback would strand the source
    // account in a debited-but-not-credited state, which is precisely what the Saga must avoid.
    // setMaximumAttempts(0) = unlimited; only StartToClose bounds each individual attempt.
    private final AccountActivities compensationActivities = newActivityStub(AccountActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(0)
                            .build())
                    .build());

    @Override
    public Result execute(Input input) {
        // Temporal automatically persists workflow state and retries failed activities.
        // If this process crashes mid-execution, Temporal replays the workflow from its
        // event history — no completed activity is re-executed.
        var saga = new Saga(new Saga.Options.Builder().build());
        try {
            var debitInput = new DebitInput(
                    input.sourceAccountId(), input.amount(), input.transferId());
            var creditInput = new CreditInput(
                    input.targetAccountId(), input.amount(), input.transferId());
            var reverseDebitInput = new ReverseDebitInput(
                    input.sourceAccountId(), input.amount(), input.transferId());

            // Debit first; only register a compensation for it once it has actually completed.
            // If debit fails, there is nothing to compensate. Temporal's workflow event history
            // makes this safe across crashes: on replay the workflow deterministically re-runs
            // the same activities and registrations from its history.
            activities.debitAccount(debitInput);
            saga.addCompensation(compensationActivities::reverseDebit, reverseDebitInput);

            // If creditAccount fails, saga.compensate() will run reverseDebit to undo the debit.
            activities.creditAccount(creditInput);

            return new Result(input.transferId(), "COMPLETED", null);
        } catch (ActivityFailure e) {
            // Detached scope ensures compensations run even if the workflow itself was cancelled.
            Workflow.newDetachedCancellationScope(saga::compensate).run();
            return new Result(input.transferId(), "FAILED", e.getMessage());
        }
    }
}
