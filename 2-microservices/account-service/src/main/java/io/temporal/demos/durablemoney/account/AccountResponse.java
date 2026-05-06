package io.temporal.demos.durablemoney.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String owner, BigDecimal balance, Instant createdAt) {

    static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getOwner(), a.getBalance(), a.getCreatedAt());
    }
}
