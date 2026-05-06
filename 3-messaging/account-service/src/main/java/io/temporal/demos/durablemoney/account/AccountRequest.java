package io.temporal.demos.durablemoney.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record AccountRequest(@NotBlank String owner, @NotNull @DecimalMin("0") BigDecimal initialBalance) {}
