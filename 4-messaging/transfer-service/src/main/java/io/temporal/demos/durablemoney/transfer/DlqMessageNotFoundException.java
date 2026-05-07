package io.temporal.demos.durablemoney.transfer;

import java.util.UUID;

class DlqMessageNotFoundException extends RuntimeException {
    DlqMessageNotFoundException(UUID id) {
        super("DLQ message not found: " + id);
    }
}
