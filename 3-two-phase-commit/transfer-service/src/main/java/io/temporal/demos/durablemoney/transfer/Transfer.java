package io.temporal.demos.durablemoney.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record Transfer(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String status,
        Instant createdAt,
        Instant completedAt
) {
}
