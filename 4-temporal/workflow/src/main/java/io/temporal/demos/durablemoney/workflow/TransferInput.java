package io.temporal.demos.durablemoney.workflow;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferInput(
    UUID transferId,
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount
) {}
