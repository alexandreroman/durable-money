package io.temporal.demos.durablemoney.monolith.account;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(UUID id) {
        super("Account not found: " + id);
    }
}
