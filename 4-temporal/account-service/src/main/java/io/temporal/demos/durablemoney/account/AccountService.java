package io.temporal.demos.durablemoney.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Account balance operations with consumer-side idempotency.
 *
 * <p>Temporal activities have at-least-once execution semantics: an activity
 * whose HTTP response is lost in transit will be retried by the worker, even
 * though the side effect already happened. The {@code transfers} table holds
 * a unique {@code (transferId, operation)} slot; each balance update is
 * paired with a slot insert in the same {@code @Transactional} unit, so a
 * retry either inserts the slot AND updates the balance atomically (first
 * attempt) or finds the slot already taken and short-circuits (replay).
 *
 * <p>Compensations use a distinct {@code "reverse_debit"} operation key so
 * they cannot collide with the original {@code "debit"} slot — the same
 * transferId can therefore safely be debited and later compensated.
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
            // Slot already taken: this transferId has already debited this account on a
            // prior attempt whose response was lost. Return the current state without
            // re-applying — Temporal sees a successful retry and moves on.
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
            // Replay of an already-credited (or already-compensated) attempt — see debit().
            return account;
        }
        var newBalance = account.balance().add(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }
}
