package io.temporal.demos.durablemoney.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getSourceAccountId() { return sourceAccountId; }
    void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    UUID getTargetAccountId() { return targetAccountId; }
    void setTargetAccountId(UUID targetAccountId) { this.targetAccountId = targetAccountId; }
    BigDecimal getAmount() { return amount; }
    void setAmount(BigDecimal amount) { this.amount = amount; }
    TransferStatus getStatus() { return status; }
    void setStatus(TransferStatus status) { this.status = status; }
    String getErrorMessage() { return errorMessage; }
    void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
