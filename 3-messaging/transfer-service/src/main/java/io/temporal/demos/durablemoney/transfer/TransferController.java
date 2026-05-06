package io.temporal.demos.durablemoney.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
@RequestMapping("/transfers")
class TransferController {
    private final TransferService transferService;

    TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    TransferView create(@RequestBody @Valid NewTransfer request) {
        return TransferView.from(
                transferService.initiateTransfer(request.sourceAccountId(), request.targetAccountId(), request.amount()));
    }

    @GetMapping("/{id}")
    TransferView get(@PathVariable UUID id) {
        return TransferView.from(transferService.getTransfer(id));
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ProblemDetail handleNotFound(TransferNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Transfer not found");
        return problem;
    }

    record NewTransfer(
            @NotNull UUID sourceAccountId,
            @NotNull UUID targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}

    record TransferView(UUID id, String status, String message, Instant createdAt, Instant updatedAt) {
        static TransferView from(Transfer t) {
            return new TransferView(
                    t.getId(), t.getStatus().name(), t.getErrorMessage(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
