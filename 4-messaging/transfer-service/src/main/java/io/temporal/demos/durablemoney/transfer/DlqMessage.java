package io.temporal.demos.durablemoney.transfer;

import java.time.Instant;
import java.util.UUID;

record DlqMessage(
        UUID id,
        UUID transferId,
        String originalExchange,
        String originalRoutingKey,
        String failureReason,
        int failureCount,
        String payload,
        String contentType,
        Status status,
        Instant parkedAt,
        Instant updatedAt
) {
    enum Status { PARKED, REPLAYED, DISCARDED }
}
