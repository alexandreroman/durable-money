package io.temporal.demos.durablemoney.monolith.account;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
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

    @Transactional(readOnly = true)
    List<Account> getAll() {
        return accountRepository.findAll(Sort.by("owner"));
    }

    @Transactional
    public void debit(UUID id, BigDecimal amount) {
        // Locked read + check + write + commit form a critical section serialized by PostgreSQL on
        // the account row. Throwing from inside @Transactional triggers an automatic rollback, so
        // InsufficientFundsException needs no manual compensation.
        var account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + id);
        }
        account.setBalance(account.getBalance().subtract(amount));
    }

    @Transactional
    public void credit(UUID id, BigDecimal amount) {
        // Pessimistic lock kept symmetric with debit() to serialize concurrent updates on the row.
        var account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setBalance(account.getBalance().add(amount));
    }
}
