package io.temporal.demos.durablemoney.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/accounts/{id}")
class PrepareController {
    private final PreparedTransactionService preparedTxService;

    PrepareController(PreparedTransactionService preparedTxService) {
        this.preparedTxService = preparedTxService;
    }

    @PostMapping("/debit/prepare")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void prepareDebit(@PathVariable UUID id, @RequestBody @Valid PrepareRequest request) {
        preparedTxService.prepareDebit(id, request.amount(), request.xid());
    }

    @PostMapping("/credit/prepare")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void prepareCredit(@PathVariable UUID id, @RequestBody @Valid PrepareRequest request) {
        preparedTxService.prepareCredit(id, request.amount(), request.xid());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Insufficient funds");
        return problem;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleNotFound(AccountNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadXid(IllegalArgumentException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    record PrepareRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String xid
    ) {}
}
