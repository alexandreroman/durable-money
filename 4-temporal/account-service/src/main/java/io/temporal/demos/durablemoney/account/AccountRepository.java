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
     * <p>Required to prevent lost updates on concurrent debits/credits: under PostgreSQL's default
     * READ COMMITTED isolation, two transactions can both read {@code balance=100}, both pass the
     * "sufficient funds" check, and both write {@code balance=0} — silently losing money or
     * producing a negative balance. The pessimistic lock serializes access to the row.
     *
     * <p>Alternatives considered: optimistic locking via {@code @Version} (retry on conflict), or a
     * single atomic {@code UPDATE ... SET balance = balance - ? WHERE balance >= ?}. Pessimistic
     * locking was chosen here for clarity — this module exists to showcase the ACID baseline.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);
}
