package io.temporal.demos.durablemoney.account;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        var account = new Account();
        account.setOwner(request.owner());
        account.setBalance(request.initialBalance());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        return accountRepository.findById(id)
            .map(AccountResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Account not found: " + id));
    }

    @Transactional
    public AccountResponse debit(UUID id, BigDecimal amount) {
        var account = accountRepository.findByIdWithLock(id)
            .orElseThrow(() -> new EntityNotFoundException("Account not found: " + id));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + id);
        }
        account.setBalance(account.getBalance().subtract(amount));
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse credit(UUID id, BigDecimal amount) {
        var account = accountRepository.findByIdWithLock(id)
            .orElseThrow(() -> new EntityNotFoundException("Account not found: " + id));
        account.setBalance(account.getBalance().add(amount));
        return AccountResponse.from(accountRepository.save(account));
    }
}
