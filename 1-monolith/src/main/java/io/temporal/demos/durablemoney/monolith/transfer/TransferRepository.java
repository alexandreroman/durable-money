package io.temporal.demos.durablemoney.monolith.transfer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
class TransferRepository {
    private final JdbcClient jdbcClient;

    TransferRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Transfer insert(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        var now = Instant.now();
        var transfer = new Transfer(UUID.randomUUID(), sourceAccountId, targetAccountId, amount, now, now);
        jdbcClient.sql("""
                        INSERT INTO transfers (id, source_account_id, target_account_id, amount, created_at, completed_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(transfer.id(), transfer.sourceAccountId(), transfer.targetAccountId(),
                        transfer.amount(), transfer.createdAt().atOffset(ZoneOffset.UTC),
                        transfer.completedAt().atOffset(ZoneOffset.UTC))
                .update();
        return transfer;
    }

    Optional<Transfer> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, source_account_id, target_account_id, amount, created_at, completed_at
                        FROM transfers WHERE id = ?
                        """)
                .param(id)
                .query(Transfer.class)
                .optional();
    }
}
