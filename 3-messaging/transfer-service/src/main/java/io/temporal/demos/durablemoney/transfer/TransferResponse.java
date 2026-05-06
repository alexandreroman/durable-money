package io.temporal.demos.durablemoney.transfer;

import java.time.Instant;
import java.util.UUID;

record TransferResponse(UUID id, String status, String message, Instant createdAt) {
    static TransferResponse from(Transfer t) {
        return new TransferResponse(t.getId(), t.getStatus().name(), t.getErrorMessage(), t.getCreatedAt());
    }
}
