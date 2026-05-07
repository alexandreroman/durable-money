package io.temporal.demos.durablemoney.account;

import java.util.UUID;

class DlqMessageNotFoundException extends RuntimeException {
    DlqMessageNotFoundException(UUID id) {
        super("DLQ message not found: " + id);
    }
}
