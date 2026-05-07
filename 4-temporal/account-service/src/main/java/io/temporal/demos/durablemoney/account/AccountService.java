package io.temporal.demos.durablemoney.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
class AccountService {
    private final AccountRepository accountRepository;

    AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    Account createAccount(String owner, BigDecimal initialBalance) {
        return accountRepository.insert(owner, initialBalance);
    }

    @Transactional(readOnly = true)
    Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    Account debit(UUID transferId, UUID id, BigDecimal amount) {
        // Locked read + check + explicit UPDATE + commit form a critical section serialized by
        // PostgreSQL on the account row. Throwing from inside @Transactional triggers an automatic
        // rollback, so InsufficientFundsException needs no manual compensation.
        var account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        // Funds check runs BEFORE recording the idempotency slot: a failed debit must remain
        // retryable (the slot is consumed only when the balance actually changes).
        if (account.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + id);
        }
        if (!accountRepository.recordTransfer(transferId, "debit", id, amount)) {
            return account;
        }
        var newBalance = account.balance().subtract(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }

    @Transactional
    Account credit(UUID transferId, UUID id, BigDecimal amount) {
        return applyCredit(transferId, "credit", id, amount);
    }

    @Transactional
    Account reverseDebit(UUID transferId, UUID id, BigDecimal amount) {
        // Compensation for a prior debit. Recorded under a distinct operation key so it does not
        // collide with the original debit's idempotency slot.
        return applyCredit(transferId, "reverse_debit", id, amount);
    }

    private Account applyCredit(UUID transferId, String operation, UUID id, BigDecimal amount) {
        // Pessimistic lock kept symmetric with debit() to serialize concurrent updates on the row.
        var account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (!accountRepository.recordTransfer(transferId, operation, id, amount)) {
            return account;
        }
        var newBalance = account.balance().add(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }
}
