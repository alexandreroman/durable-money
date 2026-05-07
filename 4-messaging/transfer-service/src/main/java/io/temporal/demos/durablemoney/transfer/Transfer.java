package io.temporal.demos.durablemoney.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record Transfer(UUID id, UUID sourceAccountId, UUID targetAccountId,
                BigDecimal amount, TransferStatus status, String errorMessage,
                Instant createdAt, Instant updatedAt) {
}
