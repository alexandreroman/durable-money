package io.temporal.demos.durablemoney.monolith.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID sourceAccountId;

    @Column(nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        completedAt = now;
    }

    UUID getId() { return id; }
    UUID getSourceAccountId() { return sourceAccountId; }
    void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    UUID getTargetAccountId() { return targetAccountId; }
    void setTargetAccountId(UUID targetAccountId) { this.targetAccountId = targetAccountId; }
    BigDecimal getAmount() { return amount; }
    void setAmount(BigDecimal amount) { this.amount = amount; }
    Instant getCreatedAt() { return createdAt; }
    Instant getCompletedAt() { return completedAt; }
}
