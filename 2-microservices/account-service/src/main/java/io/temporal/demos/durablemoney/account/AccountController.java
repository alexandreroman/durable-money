package io.temporal.demos.durablemoney.account;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{id}")
    AccountView get(@PathVariable UUID id) {
        return AccountView.from(accountService.getAccount(id));
    }

    @PostMapping("/{id}/debit")
    AccountView debit(@PathVariable UUID id, @RequestBody @Valid DebitCredit request) {
        return AccountView.from(accountService.debit(id, request.amount()));
    }

    @PostMapping("/{id}/credit")
    AccountView credit(@PathVariable UUID id, @RequestBody @Valid DebitCredit request) {
        return AccountView.from(accountService.credit(id, request.amount()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setTitle("Insufficient funds");
        return problem;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail handleNotFound(EntityNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Account not found");
        return problem;
    }

    record NewAccount(
            @NotBlank String owner,
            @NotNull @DecimalMin("0") BigDecimal initialBalance
    ) {}

    record DebitCredit(@NotNull @DecimalMin("0.01") BigDecimal amount) {}

    record AccountView(UUID id, String owner, BigDecimal balance, Instant createdAt) {
        static AccountView from(Account a) {
            return new AccountView(a.getId(), a.getOwner(), a.getBalance(), a.getCreatedAt());
        }
    }
}
