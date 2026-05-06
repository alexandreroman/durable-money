package io.temporal.demos.durablemoney.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
class TransferController {
    private final TransferService transferService;

    TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    TransferView create(@RequestBody @Valid NewTransfer request) {
        return TransferView.from(transferService.executeTransfer(
                request.sourceAccountId(), request.targetAccountId(), request.amount()));
    }

    record NewTransfer(
            @NotNull UUID sourceAccountId,
            @NotNull UUID targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}

    record TransferView(UUID transferId, String status, String message) {
        static TransferView from(TransferService.TransferResult r) {
            return new TransferView(r.transferId(), r.status(), r.message());
        }
    }
}
