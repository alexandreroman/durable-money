package io.temporal.demos.durablemoney.monolith.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record Transfer(UUID id, UUID sourceAccountId, UUID targetAccountId,
                BigDecimal amount, Instant createdAt, Instant completedAt) {
}
