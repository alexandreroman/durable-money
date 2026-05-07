package io.temporal.demos.durablemoney.transfer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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

    Optional<Transfer> findById(UUID id) {
        return jdbcClient.sql(
                "SELECT id, source_account_id, target_account_id, amount, status, created_at, completed_at " +
                        "FROM transfers WHERE id = ?")
                .param(id)
                .query(Transfer.class)
                .optional();
    }
}
