package io.temporal.demos.durablemoney.transfer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
class TransferController {

    private final TransferService transferService;

    TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    TransferResponse create(@RequestBody @Valid TransferRequest request) {
        return transferService.executeTransfer(request);
    }
}
