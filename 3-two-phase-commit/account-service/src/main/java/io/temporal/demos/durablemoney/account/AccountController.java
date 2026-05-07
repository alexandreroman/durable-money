package io.temporal.demos.durablemoney.account;

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
import java.util.List;
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

    @GetMapping
    List<AccountView> getAll() {
        return accountService.getAll().stream().map(AccountView::from).toList();
    }

    @PostMapping("/{id}/debit/prepare")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void prepareDebit(@PathVariable UUID id, @RequestBody @Valid PrepareRequest request) {
        accountService.prepareDebit(id, request.amount(), request.xid());
    }

    @PostMapping("/{id}/credit/prepare")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void prepareCredit(@PathVariable UUID id, @RequestBody @Valid PrepareRequest request) {
        accountService.prepareCredit(id, request.amount(), request.xid());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleNotFound(AccountNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Insufficient funds");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadXid(IllegalArgumentException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    record NewAccount(
            @NotBlank String owner,
            @NotNull @DecimalMin("0") BigDecimal initialBalance
    ) {}

    record PrepareRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String xid
    ) {}

    record AccountView(UUID id, String owner, BigDecimal balance, Instant createdAt) {
        static AccountView from(Account a) {
            return new AccountView(a.id(), a.owner(), a.balance(), a.createdAt());
        }
    }
}
