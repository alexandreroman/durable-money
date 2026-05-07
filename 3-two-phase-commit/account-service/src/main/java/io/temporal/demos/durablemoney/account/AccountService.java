package io.temporal.demos.durablemoney.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Application service for the {@code accounts} table.
 *
 * <p>Two distinct paradigms coexist here on purpose:
 * <ul>
 *   <li>CRUD operations ({@link #createAccount}, {@link #getAccount}) use Spring's
 *       {@code @Transactional} and {@link AccountRepository}'s {@link JdbcClient}.
 *   <li>2PC participant operations ({@link #prepareDebit}, {@link #prepareCredit},
 *       {@link #commit}, {@link #rollback}) bypass {@code @Transactional} and drive
 *       a raw JDBC {@link Connection} directly. Reason: {@code PREPARE TRANSACTION} is
 *       not a Spring-managed completion — it terminates the JDBC transaction in the
 *       "prepared" state, durably persisted in {@code pg_prepared_xacts} until a later
 *       {@code COMMIT PREPARED} or {@code ROLLBACK PREPARED}, possibly from a different
 *       connection. Spring's transaction abstraction does not model that, so the
 *       lifecycle is controlled imperatively.
 * </ul>
 */
@Service
class AccountService {
    private static final Pattern XID_PATTERN =
            Pattern.compile("^transfer-[0-9a-f-]{36}-(debit|credit|journal)$");

    private final AccountRepository accountRepository;
    private final DataSource dataSource;

    AccountService(AccountRepository accountRepository, DataSource dataSource) {
        this.accountRepository = accountRepository;
        this.dataSource = dataSource;
    }

    @Transactional
    Account createAccount(String owner, BigDecimal initialBalance) {
        return accountRepository.insert(owner, initialBalance);
    }

    @Transactional(readOnly = true)
    Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    void prepareDebit(UUID accountId, BigDecimal amount, String xid) {
        requireValidXid(xid);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rowCount;
                try (PreparedStatement st = conn.prepareStatement(
                        "UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?")) {
                    st.setBigDecimal(1, amount);
                    st.setObject(2, accountId);
                    st.setBigDecimal(3, amount);
                    rowCount = st.executeUpdate();
                }
                if (rowCount == 0) {
                    conn.rollback();
                    throw new InsufficientFundsException(
                            "Insufficient funds or unknown account: " + accountId);
                }
                try (Statement st = conn.createStatement()) {
                    st.execute("PREPARE TRANSACTION '" + xid + "'");
                }
            } catch (RuntimeException | SQLException e) {
                safeRollback(conn);
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    void prepareCredit(UUID accountId, BigDecimal amount, String xid) {
        requireValidXid(xid);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rowCount;
                try (PreparedStatement st = conn.prepareStatement(
                        "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                    st.setBigDecimal(1, amount);
                    st.setObject(2, accountId);
                    rowCount = st.executeUpdate();
                }
                if (rowCount == 0) {
                    conn.rollback();
                    throw new AccountNotFoundException(accountId);
                }
                try (Statement st = conn.createStatement()) {
                    st.execute("PREPARE TRANSACTION '" + xid + "'");
                }
            } catch (RuntimeException | SQLException e) {
                safeRollback(conn);
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    void commit(String xid) {
        requireValidXid(xid);
        finalizePrepared(xid, "COMMIT PREPARED");
    }

    void rollback(String xid) {
        requireValidXid(xid);
        finalizePrepared(xid, "ROLLBACK PREPARED");
    }

    private void finalizePrepared(String xid, String command) {
        try (Connection conn = dataSource.getConnection()) {
            // Idempotency: if the prepared xact is gone, the operation has already been finalized.
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT 1 FROM pg_prepared_xacts WHERE gid = ?")) {
                st.setString(1, xid);
                try (var rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                }
            }
            try (Statement st = conn.createStatement()) {
                st.execute(command + " '" + xid + "'");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignore) {
            // best-effort
        }
    }

    private static String requireValidXid(String xid) {
        if (xid == null || !XID_PATTERN.matcher(xid).matches()) {
            throw new IllegalArgumentException("Invalid xid: " + xid);
        }
        return xid;
    }
}
