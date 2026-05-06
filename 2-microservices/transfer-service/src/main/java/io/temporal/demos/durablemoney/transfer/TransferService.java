package io.temporal.demos.durablemoney.transfer;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class TransferService {

    private final AccountClient accountClient;

    TransferService(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    TransferResponse executeTransfer(TransferRequest request) {
        var transferId = UUID.randomUUID();
        try {
            accountClient.debit(request.sourceAccountId(), request.amount(), transferId);

            // ⚠️ If this call fails after the debit above succeeded, the source account is debited
            // but the target account is NOT credited. Without a distributed transaction, there is
            // no automatic rollback. Money has disappeared from the system.
            accountClient.credit(request.targetAccountId(), request.amount(), transferId);

            return new TransferResponse(transferId, "COMPLETED", "Transfer successful");
        } catch (Exception e) {
            return new TransferResponse(transferId, "FAILED", e.getMessage());
        }
    }
}
