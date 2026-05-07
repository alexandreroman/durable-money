package io.temporal.demos.durablemoney.transfer;

import io.temporal.demos.durablemoney.transfer.DlqMessage.Status;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class DlqMessageRepository {
    // JdbcClient cannot map VARCHAR -> Status enum out of the box; use an explicit RowMapper.
    private static final RowMapper<DlqMessage> ROW_MAPPER = (rs, rowNum) -> new DlqMessage(
            rs.getObject("id", UUID.class),
            rs.getObject("transfer_id", UUID.class),
            rs.getString("original_exchange"),
            rs.getString("original_routing_key"),
            rs.getString("failure_reason"),
            rs.getInt("failure_count"),
            rs.getString("payload"),
            rs.getString("content_type"),
            Status.valueOf(rs.getString("status")),
            rs.getObject("parked_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()
    );

    private final JdbcClient jdbcClient;

    DlqMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void insert(DlqMessage msg) {
        jdbcClient.sql("""
                        INSERT INTO dlq_messages (id, transfer_id, original_exchange, original_routing_key,
                                                  failure_reason, failure_count, payload, content_type,
                                                  status, parked_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(msg.id(), msg.transferId(), msg.originalExchange(), msg.originalRoutingKey(),
                        msg.failureReason(), msg.failureCount(), msg.payload(), msg.contentType(),
                        msg.status().name(),
                        msg.parkedAt().atOffset(ZoneOffset.UTC),
                        msg.updatedAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    Optional<DlqMessage> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM dlq_messages WHERE id = ?")
                .param(id)
                .query(ROW_MAPPER)
                .optional();
    }

    List<DlqMessage> findAll() {
        return jdbcClient.sql("SELECT * FROM dlq_messages ORDER BY parked_at DESC")
                .query(ROW_MAPPER)
                .list();
    }

    List<DlqMessage> findByStatus(Status status) {
        return jdbcClient.sql("SELECT * FROM dlq_messages WHERE status = ? ORDER BY parked_at DESC")
                .param(status.name())
                .query(ROW_MAPPER)
                .list();
    }

    void updateStatus(UUID id, Status status, Instant updatedAt) {
        jdbcClient.sql("UPDATE dlq_messages SET status = ?, updated_at = ? WHERE id = ?")
                .params(status.name(), updatedAt.atOffset(ZoneOffset.UTC), id)
                .update();
    }
}
