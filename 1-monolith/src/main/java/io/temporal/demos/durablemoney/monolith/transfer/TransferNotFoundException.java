package io.temporal.demos.durablemoney.monolith.transfer;

import jakarta.persistence.EntityNotFoundException;

import java.util.UUID;

public class TransferNotFoundException extends EntityNotFoundException {
    public TransferNotFoundException(UUID id) {
        super("Transfer not found: " + id);
    }
}
