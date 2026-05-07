package io.temporal.demos.durablemoney.account;

import java.util.UUID;

class AccountNotFoundException extends RuntimeException {
    AccountNotFoundException(UUID id) {
        super("Account not found: " + id);
    }
}
