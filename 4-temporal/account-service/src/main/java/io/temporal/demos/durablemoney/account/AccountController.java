package io.temporal.demos.durablemoney.account;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
    AccountResponse create(@RequestBody @Valid AccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return accountService.getAccount(id);
    }

    @PostMapping("/{id}/debit")
    AccountResponse debit(@PathVariable UUID id, @RequestBody @Valid DebitCreditRequest request) {
        return accountService.debit(id, request.amount());
    }

    @PostMapping("/{id}/credit")
    AccountResponse credit(@PathVariable UUID id, @RequestBody @Valid DebitCreditRequest request) {
        return accountService.credit(id, request.amount());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<Map<String, String>> handleInsufficient(InsufficientFundsException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
