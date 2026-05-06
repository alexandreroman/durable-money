package io.temporal.demos.durablemoney.account;

import java.math.BigDecimal;
import java.util.UUID;

record AccountCommandMessage(UUID transferId, UUID accountId, BigDecimal amount, String type) {}
