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

    Transfer insertDebiting(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        var now = Instant.now();
        var transfer = new Transfer(UUID.randomUUID(), sourceAccountId, targetAccountId,
                amount, TransferStatus.DEBITING, null, now, now);
        jdbcClient.sql("""
                        INSERT INTO transfers (id, source_account_id, target_account_id, amount,
                                               status, error_message, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(transfer.id(), transfer.sourceAccountId(), transfer.targetAccountId(),
                        transfer.amount(), transfer.status().name(), transfer.errorMessage(),
                        transfer.createdAt().atOffset(ZoneOffset.UTC),
                        transfer.updatedAt().atOffset(ZoneOffset.UTC))
                .update();
        return transfer;
    }

    Optional<Transfer> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, source_account_id, target_account_id, amount,
                               status, error_message, created_at, updated_at
                        FROM transfers WHERE id = ?
                        """)
                .param(id)
                .query(Transfer.class)
                .optional();
    }

    void markCrediting(UUID id) {
        updateStatus(id, TransferStatus.CREDITING, null);
    }

    void markCompleted(UUID id) {
        updateStatus(id, TransferStatus.COMPLETED, null);
    }

    void markFailed(UUID id, String errorMessage) {
        updateStatus(id, TransferStatus.FAILED, errorMessage);
    }

    private void updateStatus(UUID id, TransferStatus status, String errorMessage) {
        jdbcClient.sql("UPDATE transfers SET status = ?, error_message = ?, updated_at = ? WHERE id = ?")
                .params(status.name(), errorMessage,
                        Instant.now().atOffset(ZoneOffset.UTC), id)
                .update();
    }
}
