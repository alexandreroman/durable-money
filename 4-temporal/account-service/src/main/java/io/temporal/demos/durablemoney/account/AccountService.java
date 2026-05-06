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
        var account = new Account();
        account.setOwner(owner);
        account.setBalance(initialBalance);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    Account debit(UUID id, BigDecimal amount) {
        // Locked read + check + write + commit form a critical section serialized by PostgreSQL on
        // the account row. JPA dirty checking flushes the balance update at commit, so no explicit
        // save() is needed. Throwing from inside @Transactional triggers an automatic rollback.
        var account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + id);
        }
        account.setBalance(account.getBalance().subtract(amount));
        return account;
    }

    @Transactional
    Account credit(UUID id, BigDecimal amount) {
        // Pessimistic lock kept symmetric with debit() to serialize concurrent updates on the row.
        var account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setBalance(account.getBalance().add(amount));
        return account;
    }
}
