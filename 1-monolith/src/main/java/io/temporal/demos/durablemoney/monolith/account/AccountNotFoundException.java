package io.temporal.demos.durablemoney.monolith.account;

import jakarta.persistence.EntityNotFoundException;

import java.util.UUID;

public class AccountNotFoundException extends EntityNotFoundException {
    public AccountNotFoundException(UUID id) {
        super("Account not found: " + id);
    }
}
