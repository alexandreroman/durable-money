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

    @Transactional
    void debit(UUID id, BigDecimal amount) {
        var account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + id);
        }
        account.setBalance(account.getBalance().subtract(amount));
    }

    @Transactional
    void credit(UUID id, BigDecimal amount) {
        var account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setBalance(account.getBalance().add(amount));
    }
}
