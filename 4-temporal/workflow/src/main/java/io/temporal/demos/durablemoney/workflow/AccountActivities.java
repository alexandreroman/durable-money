package io.temporal.demos.durablemoney.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.UUID;

@ActivityInterface
public interface AccountActivities {
    @ActivityMethod
    void debitAccount(UUID accountId, BigDecimal amount, UUID transferId);

    @ActivityMethod
    void creditAccount(UUID accountId, BigDecimal amount, UUID transferId);

    @ActivityMethod
    void reverseDebit(UUID accountId, BigDecimal amount, UUID transferId);
}
