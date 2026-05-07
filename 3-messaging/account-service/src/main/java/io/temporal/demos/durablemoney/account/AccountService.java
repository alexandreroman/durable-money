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

    @Transactional
    Account debit(UUID id, BigDecimal amount) {
        // Locked read + check + explicit UPDATE + commit form a critical section serialized by
        // PostgreSQL on the account row. Throwing from inside @Transactional triggers an automatic
        // rollback, so InsufficientFundsException needs no manual compensation.
        var account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (account.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + id);
        }
        var newBalance = account.balance().subtract(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }

    @Transactional
    Account credit(UUID id, BigDecimal amount) {
        // Pessimistic lock kept symmetric with debit() to serialize concurrent updates on the row.
        var account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        var newBalance = account.balance().add(amount);
        accountRepository.updateBalance(id, newBalance);
        return new Account(account.id(), account.owner(), newBalance, account.createdAt());
    }
}
