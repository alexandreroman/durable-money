package io.temporal.demos.durablemoney.transfer;

import java.math.BigDecimal;
import java.util.UUID;

record DebitCreditRequest(BigDecimal amount, UUID transferId) {}
