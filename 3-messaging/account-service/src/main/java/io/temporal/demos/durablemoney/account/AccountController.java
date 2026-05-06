package io.temporal.demos.durablemoney.account;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
        return AccountResponse.from(accountService.createAccount(request.owner(), request.initialBalance()));
    }
}
