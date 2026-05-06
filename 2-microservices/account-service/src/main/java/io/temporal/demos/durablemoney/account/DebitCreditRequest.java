package io.temporal.demos.durablemoney.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

record DebitCreditRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotNull UUID transferId
) {}
