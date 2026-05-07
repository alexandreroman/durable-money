package io.temporal.demos.durablemoney.transfer;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
class TransferCoordinator {
    private final DataSource dataSource;
    private final AccountClient accountClient;
    private final TransferDecisionRepository decisionRepository;
    private final TransferRepository transferRepository;

    TransferCoordinator(DataSource dataSource,
                        AccountClient accountClient,
                        TransferDecisionRepository decisionRepository,
                        TransferRepository transferRepository) {
        this.dataSource = dataSource;
        this.accountClient = accountClient;
        this.decisionRepository = decisionRepository;
        this.transferRepository = transferRepository;
    }

    Result execute(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        var transferId = UuidCreator.getTimeOrderedEpoch();
        var debitXid = xid(transferId, "debit");
        var creditXid = xid(transferId, "credit");
        var journalXid = xid(transferId, "journal");
        var createdAt = Instant.now();

        var prepared = new ArrayList<String>();
        BusinessFailure businessFailure = null;
        try {
            accountClient.prepareDebit(sourceAccountId, amount, debitXid);
            prepared.add(debitXid);

            accountClient.prepareCredit(targetAccountId, amount, creditXid);
            prepared.add(creditXid);

            insertJournalAndPrepare(transferId, sourceAccountId, targetAccountId, amount,
                    createdAt, journalXid);
            prepared.add(journalXid);
        } catch (BusinessException e) {
            businessFailure = new BusinessFailure(e.status, e.getMessage());
        }

        var decision = businessFailure == null ? "COMMIT" : "ABORT";
        decisionRepository.record(transferId, decision, participantsJson(prepared));

        if (decision.equals("COMMIT")) {
            commitAll(prepared, journalXid);
            transferRepository.markCompleted(transferId, "COMMITTED", Instant.now());
            return Result.success(transferId, sourceAccountId, targetAccountId, amount, createdAt);
        } else {
            rollbackAll(prepared, journalXid);
            // No transfers row was committed (journal insert was rolled back if it ran), so
            // we record an aborted journal row outside the protocol for observability.
            insertAbortedJournal(transferId, sourceAccountId, targetAccountId, amount, createdAt);
            return Result.failure(transferId, businessFailure);
        }
    }

    private void insertJournalAndPrepare(UUID transferId, UUID source, UUID target,
                                         BigDecimal amount, Instant createdAt, String xid) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement st = conn.prepareStatement(
                        "INSERT INTO transfers (id, source_account_id, target_account_id, amount, " +
                                "status, created_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, NULL)")) {
                    st.setObject(1, transferId);
                    st.setObject(2, source);
                    st.setObject(3, target);
                    st.setBigDecimal(4, amount);
                    st.setString(5, "PREPARED");
                    st.setObject(6, createdAt.atOffset(ZoneOffset.UTC));
                    st.executeUpdate();
                }
                try (Statement st = conn.createStatement()) {
                    st.execute("PREPARE TRANSACTION '" + xid + "'");
                }
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) { }
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void insertAbortedJournal(UUID transferId, UUID source, UUID target,
                                      BigDecimal amount, Instant createdAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO transfers (id, source_account_id, target_account_id, amount, " +
                             "status, created_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            st.setObject(1, transferId);
            st.setObject(2, source);
            st.setObject(3, target);
            st.setBigDecimal(4, amount);
            st.setString(5, "ABORTED");
            st.setObject(6, createdAt.atOffset(ZoneOffset.UTC));
            st.setObject(7, Instant.now().atOffset(ZoneOffset.UTC));
            st.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void commitAll(List<String> prepared, String journalXid) {
        for (var xid : prepared) {
            if (xid.equals(journalXid)) {
                runLocal("COMMIT PREPARED '" + xid + "'");
            } else {
                accountClient.commit(xid);
            }
        }
    }

    private void rollbackAll(List<String> prepared, String journalXid) {
        for (var xid : prepared) {
            if (xid.equals(journalXid)) {
                runLocal("ROLLBACK PREPARED '" + xid + "'");
            } else {
                accountClient.rollback(xid);
            }
        }
    }

    private void runLocal(String command) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(command);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String xid(UUID transferId, String role) {
        return "transfer-" + transferId + "-" + role;
    }

    private static String participantsJson(List<String> xids) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < xids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"').append(xids.get(i)).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    sealed interface Result permits Success, Failure {
        UUID transferId();

        static Result success(UUID id, UUID src, UUID tgt, BigDecimal amount, Instant createdAt) {
            return new Success(id, src, tgt, amount, createdAt);
        }

        static Result failure(UUID id, BusinessFailure failure) {
            return new Failure(id, failure);
        }
    }

    record Success(UUID transferId, UUID sourceAccountId, UUID targetAccountId,
                   BigDecimal amount, Instant createdAt) implements Result {
    }

    record Failure(UUID transferId, BusinessFailure cause) implements Result {
    }

    record BusinessFailure(int status, String detail) {
    }

    static class BusinessException extends RuntimeException {
        final int status;

        BusinessException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
