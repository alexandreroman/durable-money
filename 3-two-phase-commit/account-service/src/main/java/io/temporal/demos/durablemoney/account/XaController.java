package io.temporal.demos.durablemoney.account;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/xa/{xid}")
class XaController {
    private final AccountService accountService;

    XaController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/commit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void commit(@PathVariable String xid) {
        accountService.commit(xid);
    }

    @PostMapping("/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rollback(@PathVariable String xid) {
        accountService.rollback(xid);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadXid(IllegalArgumentException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid xid");
        return problem;
    }
}
