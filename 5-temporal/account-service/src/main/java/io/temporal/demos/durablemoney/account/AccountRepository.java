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
                .params(account.id(), account.owner(), account.balance(), account.createdAt().atOffset(ZoneOffset.UTC))
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

    /**
     * Loads an account with a row-level write lock ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Required to prevent lost updates on concurrent debits/credits: under PostgreSQL's default
     * READ COMMITTED isolation, two transactions can both read {@code balance=100}, both pass the
     * "sufficient funds" check, and both write {@code balance=0} — silently losing money or
     * producing a negative balance. The pessimistic lock serializes access to the row.
     */
    Optional<Account> findByIdForUpdate(UUID id) {
        return jdbcClient.sql("SELECT id, owner, balance, created_at FROM accounts WHERE id = ? FOR UPDATE")
                .param(id)
                .query(Account.class)
                .optional();
    }

    // Row count is intentionally ignored: findByIdForUpdate (called earlier in the same transaction)
    // holds a row-level lock that prevents the row from being deleted before this UPDATE.
    void updateBalance(UUID id, BigDecimal balance) {
        jdbcClient.sql("UPDATE accounts SET balance = ? WHERE id = ?")
                .params(balance, id)
                .update();
    }

    /**
     * Records that a transfer leg ({@code debit} or {@code credit}) has been applied for a
     * given {@code transferId}. Returns {@code true} if a new row was inserted (first time
     * this leg runs), or {@code false} if a row already existed (RabbitMQ at-least-once
     * redelivery — caller must short-circuit and skip the balance update).
     */
    boolean hasTransfer(UUID transferId, String operation, UUID accountId) {
        return jdbcClient.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM transfers
                            WHERE transfer_id = ? AND operation = ? AND account_id = ?
                        )
                        """)
                .params(transferId, operation, accountId)
                .query(Boolean.class)
                .single();
    }

    boolean recordTransfer(UUID transferId, String operation, UUID accountId, BigDecimal amount) {
        var inserted = jdbcClient.sql("""
                        INSERT INTO transfers (transfer_id, operation, account_id, amount)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (transfer_id, operation) DO NOTHING
                        """)
                .params(transferId, operation, accountId, amount)
                .update();
        return inserted == 1;
    }
}
