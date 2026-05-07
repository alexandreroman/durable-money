package io.temporal.demos.durablemoney.account;

import io.temporal.demos.durablemoney.account.DlqMessage.Status;

import java.util.UUID;

class DlqAlreadyResolvedException extends RuntimeException {
    DlqAlreadyResolvedException(UUID id, Status status) {
        super("DLQ message " + id + " already resolved: " + status);
    }
}
