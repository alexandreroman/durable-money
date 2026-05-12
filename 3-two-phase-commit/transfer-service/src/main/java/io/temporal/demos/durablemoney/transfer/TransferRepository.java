package io.temporal.demos.durablemoney.transfer;

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

    void markCompleted(UUID id, String status, Instant completedAt) {
        jdbcClient.sql("UPDATE transfers SET status = ?, completed_at = ? WHERE id = ?")
                .params(status, completedAt.atOffset(ZoneOffset.UTC), id)
                .update();
    }

    // Recovery-side ABORT: the PREPARE'd journal row was rolled back, so insert an ABORTED row
    // so GET /transfers/{id} matches the synchronous ABORT path. Idempotent via ON CONFLICT.
    void markAborted(UUID id, UUID source, UUID target, BigDecimal amount, Instant decidedAt) {
        jdbcClient.sql(
                "INSERT INTO transfers (id, source_account_id, target_account_id, amount, status, " +
                        "created_at, completed_at) VALUES (?, ?, ?, ?, 'ABORTED', ?, ?) " +
                        "ON CONFLICT (id) DO NOTHING")
                .params(id, source, target, amount,
                        decidedAt.atOffset(ZoneOffset.UTC),
                        Instant.now().atOffset(ZoneOffset.UTC))
                .update();
    }

    Optional<Transfer> findById(UUID id) {
        return jdbcClient.sql(
                "SELECT id, source_account_id, target_account_id, amount, status, created_at, completed_at " +
                        "FROM transfers WHERE id = ?")
                .param(id)
                .query(Transfer.class)
                .optional();
    }
}
