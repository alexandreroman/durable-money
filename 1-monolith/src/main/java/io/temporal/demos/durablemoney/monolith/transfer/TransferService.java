package io.temporal.demos.durablemoney.monolith.transfer;

import io.temporal.demos.durablemoney.monolith.account.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
class TransferService {
    private final AccountService accountService;
    private final TransferRepository transferRepository;

    TransferService(AccountService accountService, TransferRepository transferRepository) {
        this.accountService = accountService;
        this.transferRepository = transferRepository;
    }

    @Transactional
    Transfer executeTransfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        // ACID baseline: debit, credit, and Transfer insert all run in a single local transaction —
        // any failure (e.g. InsufficientFundsException) rolls back the whole thing, so no
        // compensation logic is needed. Modules 2-4 must simulate this guarantee across processes
        // (REST, messaging, Temporal Saga) because they no longer share one database transaction.
        accountService.debit(sourceAccountId, amount);
        accountService.credit(targetAccountId, amount);

        var transfer = new Transfer();
        transfer.setSourceAccountId(sourceAccountId);
        transfer.setTargetAccountId(targetAccountId);
        transfer.setAmount(amount);
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    Transfer getTransfer(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }
}
