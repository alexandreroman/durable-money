package io.temporal.demos.durablemoney.transfer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class TransferDecisionRepository {
    private static final TypeReference<List<String>> PARTICIPANTS_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    TransferDecisionRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Records the coordinator's final decision in its own committed transaction.
     * Must be {@code REQUIRES_NEW} so it survives even if the surrounding caller's transaction
     * is later aborted — this row is the durability anchor of the 2PC protocol.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(UUID transferId, String decision, String participantsJson,
                UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        jdbcClient.sql(
                "INSERT INTO transfer_decisions (transfer_id, decision, participants, " +
                        "source_account_id, target_account_id, amount) " +
                        "VALUES (?, ?, ?::jsonb, ?, ?, ?)")
                .params(transferId, decision, participantsJson,
                        sourceAccountId, targetAccountId, amount)
                .update();
    }

    void markFinalized(UUID transferId) {
        jdbcClient.sql(
                "UPDATE transfer_decisions SET finalized_at = now() " +
                        "WHERE transfer_id = ? AND finalized_at IS NULL")
                .param(transferId)
                .update();
    }

    // decided_at < now() - INTERVAL '5 seconds' so we don't race a coordinator still in phase 2.
    List<PendingDecision> findUnfinalized(int limit) {
        return jdbcClient.sql(
                "SELECT transfer_id, decision, participants, " +
                        "source_account_id, target_account_id, amount, decided_at " +
                        "FROM transfer_decisions " +
                        "WHERE finalized_at IS NULL AND decided_at < now() - INTERVAL '5 seconds' " +
                        "ORDER BY decided_at ASC LIMIT ?")
                .param(limit)
                .query((rs, n) -> new PendingDecision(
                        rs.getObject("transfer_id", UUID.class),
                        rs.getString("decision"),
                        objectMapper.readValue(rs.getString("participants"), PARTICIPANTS_TYPE),
                        rs.getObject("source_account_id", UUID.class),
                        rs.getObject("target_account_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getObject("decided_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    record PendingDecision(UUID transferId, String decision, List<String> participants,
                           UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
                           Instant decidedAt) {
    }
}
