package io.temporal.demos.durablemoney.monolith.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record Account(UUID id, String owner, BigDecimal balance, Instant createdAt) {
}
