package io.temporal.demos.durablemoney.account;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Owns the raw JDBC lifecycle for the 2PC prepare/commit/rollback phases.
 *
 * <p>This class deliberately bypasses Spring's {@code @Transactional} machinery: it must control
 * the connection until {@code PREPARE TRANSACTION} (or its commit/rollback counterpart) is issued.
 * That SQL primitive does not support bindable parameters, so {@code xid} is concatenated into the
 * statement after strict regex validation in {@link XidValidator}.
 */
@Service
class PreparedTransactionService {
    private final DataSource dataSource;

    PreparedTransactionService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void prepareDebit(UUID accountId, BigDecimal amount, String xid) {
        XidValidator.requireValid(xid);
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
        XidValidator.requireValid(xid);
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
        XidValidator.requireValid(xid);
        finalizePrepared(xid, "COMMIT PREPARED");
    }

    void rollback(String xid) {
        XidValidator.requireValid(xid);
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
}
