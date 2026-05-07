package io.temporal.demos.durablemoney.transfer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
class TransferDecisionRepository {
    private final JdbcClient jdbcClient;

    TransferDecisionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Records the coordinator's final decision in its own committed transaction.
     * Must be {@code REQUIRES_NEW} so it survives even if the surrounding caller's transaction
     * is later aborted — this row is the durability anchor of the 2PC protocol.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(UUID transferId, String decision, String participantsJson) {
        jdbcClient.sql(
                "INSERT INTO transfer_decisions (transfer_id, decision, participants) " +
                        "VALUES (?, ?, ?::jsonb)")
                .params(transferId, decision, participantsJson)
                .update();
    }
}
