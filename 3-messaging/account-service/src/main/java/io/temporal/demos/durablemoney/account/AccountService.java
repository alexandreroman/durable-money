package io.temporal.demos.durablemoney.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Account balance operations with consumer-side idempotency.
 *
 * <p>RabbitMQ delivers messages at-least-once: an {@code account.commands}
 * message can be redelivered after a consumer crash, a missed broker ack, or a
 * lost result publish. The {@code transfers} table holds a unique
 * {@code (transferId, operation)} slot; each balance update is paired with a
 * slot insert in the same {@code @Transactional} unit, so a redelivery either
 * inserts the slot AND updates the balance atomically (first delivery), or
 * finds the slot already taken and short-circuits (replay).
 *
 * <p>This guarantees "exactly-once business effect" on top of "at-least-once
 * delivery" — the same property Temporal expects from idempotent activities
 * in module 4.
 */
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
            // Slot already taken: this transferId has already debited this account on a
            // prior delivery. Return the current state without touching the balance — the
            // listener will republish a success result, idempotent for the transfer-service too.
            return account;
        }
        var newBalance = account.balance().subtract(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }

    @Transactional
    Account credit(UUID transferId, UUID id, BigDecimal amount) {
        // Pessimistic lock kept symmetric with debit() to serialize concurrent updates on the row.
        var account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (!accountRepository.recordTransfer(transferId, "credit", id, amount)) {
            // Replay of an already-credited message — see debit() for the full reasoning.
            return account;
        }
        var newBalance = account.balance().add(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }
}
