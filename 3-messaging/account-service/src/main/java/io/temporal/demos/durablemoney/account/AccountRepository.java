package io.temporal.demos.durablemoney.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface AccountRepository extends JpaRepository<Account, UUID> {
    /**
     * Loads an account with a row-level write lock ({@code SELECT ... FOR UPDATE}).
     *
     * <p>The RabbitMQ listener container processes account commands concurrently across its
     * thread pool, so two debits or credits against the same account can race. The pessimistic
     * lock serializes per-account updates and prevents lost balance writes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);
}
