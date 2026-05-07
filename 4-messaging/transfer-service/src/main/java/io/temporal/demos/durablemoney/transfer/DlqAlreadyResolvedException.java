package io.temporal.demos.durablemoney.transfer;

import io.temporal.demos.durablemoney.transfer.DlqMessage.Status;

import java.util.UUID;

class DlqAlreadyResolvedException extends RuntimeException {
    DlqAlreadyResolvedException(UUID id, Status status) {
        super("DLQ message " + id + " already resolved: " + status);
    }
}
