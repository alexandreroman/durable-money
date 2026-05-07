package io.temporal.demos.durablemoney.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
class AccountController {
    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountView create(@RequestBody @Valid NewAccount request) {
        return AccountView.from(accountService.createAccount(request.owner(), request.initialBalance()));
    }

    record NewAccount(
            @NotBlank String owner,
            @NotNull @DecimalMin("0") BigDecimal initialBalance
    ) {}

    record AccountView(UUID id, String owner, BigDecimal balance, Instant createdAt) {
        static AccountView from(Account a) {
            return new AccountView(a.id(), a.owner(), a.balance(), a.createdAt());
        }
    }
}
