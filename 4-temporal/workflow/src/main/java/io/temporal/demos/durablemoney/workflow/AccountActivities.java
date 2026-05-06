package io.temporal.demos.durablemoney.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.UUID;

@ActivityInterface
public interface AccountActivities {
    @ActivityMethod
    DebitOutput debitAccount(DebitInput input);

    @ActivityMethod
    CreditOutput creditAccount(CreditInput input);

    @ActivityMethod
    ReverseDebitOutput reverseDebit(ReverseDebitInput input);

    record DebitInput(UUID accountId, BigDecimal amount, UUID transferId) {
    }

    record DebitOutput(BigDecimal newBalance) {
    }

    record CreditInput(UUID accountId, BigDecimal amount, UUID transferId) {
    }

    record CreditOutput(BigDecimal newBalance) {
    }

    record ReverseDebitInput(UUID accountId, BigDecimal amount, UUID transferId) {
    }

    record ReverseDebitOutput(BigDecimal newBalance) {
    }
}
