package io.temporal.demos.durablemoney.account;

import jakarta.persistence.EntityNotFoundException;

import java.util.UUID;

class AccountNotFoundException extends EntityNotFoundException {
    AccountNotFoundException(UUID id) {
        super("Account not found: " + id);
    }
}
