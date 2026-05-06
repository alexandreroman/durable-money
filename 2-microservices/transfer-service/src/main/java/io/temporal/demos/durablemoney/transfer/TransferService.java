package io.temporal.demos.durablemoney.transfer;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
class TransferService {
    private final AccountClient accountClient;

    TransferService(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    TransferResult executeTransfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        var transferId = UUID.randomUUID();
        try {
            accountClient.debit(sourceAccountId, amount);

            // ⚠️ If this call fails after the debit above succeeded, the source account is debited
            // but the target account is NOT credited. Without a distributed transaction, there is
            // no automatic rollback. Money has disappeared from the system.
            accountClient.credit(targetAccountId, amount);

            return new TransferResult(transferId, "COMPLETED", "Transfer successful");
        } catch (RestClientException e) {
            return new TransferResult(transferId, "FAILED", e.getMessage());
        }
    }

    record TransferResult(UUID transferId, String status, String message) {}
}
