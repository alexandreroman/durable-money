package io.temporal.demos.durablemoney.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AccountRepository {
    private final JdbcClient jdbcClient;

    AccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Account insert(String owner, BigDecimal balance) {
        var account = new Account(UUID.randomUUID(), owner, balance, Instant.now());
        jdbcClient.sql("INSERT INTO accounts (id, owner, balance, created_at) VALUES (?, ?, ?, ?)")
                .params(account.id(), account.owner(), account.balance(),
                        account.createdAt().atOffset(ZoneOffset.UTC))
                .update();
        return account;
    }

    Optional<Account> findById(UUID id) {
        return jdbcClient.sql("SELECT id, owner, balance, created_at FROM accounts WHERE id = ?")
                .param(id)
                .query(Account.class)
                .optional();
    }

    List<Account> findAllOrderByOwner() {
        return jdbcClient.sql("SELECT id, owner, balance, created_at FROM accounts ORDER BY owner")
                .query(Account.class)
                .list();
    }
}
