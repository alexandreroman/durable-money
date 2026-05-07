package io.temporal.demos.durablemoney.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
class TransferController {
    private final TransferCoordinator coordinator;
    private final TransferRepository transferRepository;

    TransferController(TransferCoordinator coordinator, TransferRepository transferRepository) {
        this.coordinator = coordinator;
        this.transferRepository = transferRepository;
    }

    @PostMapping
    ResponseEntity<?> create(@RequestBody @Valid NewTransfer request) {
        var result = coordinator.execute(
                request.sourceAccountId(), request.targetAccountId(), request.amount());
        return switch (result) {
            case TransferCoordinator.Success s -> ResponseEntity.ok(TransferView.from(s));
            case TransferCoordinator.Failure f -> {
                var problem = ProblemDetail.forStatusAndDetail(
                        HttpStatus.valueOf(f.cause().status()), f.cause().detail());
                problem.setTitle("Transfer aborted");
                problem.setProperty("transferId", f.transferId());
                yield ResponseEntity.status(f.cause().status()).body(problem);
            }
        };
    }

    @GetMapping("/{id}")
    TransferView get(@PathVariable UUID id) {
        var t = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        return TransferView.from(t);
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

    record TransferView(UUID id, UUID sourceAccountId, UUID targetAccountId,
                        BigDecimal amount, String status, Instant createdAt, Instant completedAt) {
        static TransferView from(TransferCoordinator.Success s) {
            return new TransferView(s.transferId(), s.sourceAccountId(), s.targetAccountId(),
                    s.amount(), "COMMITTED", s.createdAt(), Instant.now());
        }

        static TransferView from(Transfer t) {
            return new TransferView(t.id(), t.sourceAccountId(), t.targetAccountId(),
                    t.amount(), t.status(), t.createdAt(), t.completedAt());
        }
    }

    static class TransferNotFoundException extends RuntimeException {
        TransferNotFoundException(UUID id) {
            super("Transfer not found: " + id);
        }
    }
}
