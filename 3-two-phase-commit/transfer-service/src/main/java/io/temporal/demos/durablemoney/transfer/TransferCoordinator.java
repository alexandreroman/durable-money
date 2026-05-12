package io.temporal.demos.durablemoney.transfer;

import com.github.f4b6a3.uuid.UuidCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOG = LoggerFactory.getLogger(TransferCoordinator.class);

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

        var attempted = new ArrayList<String>();
        BusinessFailure businessFailure = null;
        String decision;
        try {
            // Track the xid BEFORE the HTTP call: a timeout may still have completed the
            // server-side PREPARE TRANSACTION, and we must leave it to recovery to finalize.
            attempted.add(debitXid);
            try {
                accountClient.prepareDebit(sourceAccountId, amount, debitXid);
            } catch (BusinessException be) {
                attempted.remove(debitXid);
                businessFailure = new BusinessFailure(be.status, be.getMessage());
                throw be;
            }

            attempted.add(creditXid);
            try {
                accountClient.prepareCredit(targetAccountId, amount, creditXid);
            } catch (BusinessException be) {
                attempted.remove(creditXid);
                businessFailure = new BusinessFailure(be.status, be.getMessage());
                throw be;
            }

            // Last-participant rule: prepare the local journal last so we never lock our own
            // row when a remote prepare has already failed.
            attempted.add(journalXid);
            insertJournalAndPrepare(transferId, sourceAccountId, targetAccountId, amount,
                    createdAt, journalXid);

            decision = "COMMIT";
        } catch (BusinessException be) {
            decision = "ABORT";
        } catch (Exception e) {
            decision = "ABORT";
            if (businessFailure == null) {
                businessFailure = new BusinessFailure(503, "transfer in flight: " + e.getMessage());
            }
            LOG.warn("phase 1 infrastructure failure for {}: {}", transferId, e.toString());
        }

        decisionRepository.record(transferId, decision, participantsJson(attempted),
                sourceAccountId, targetAccountId, amount);

        var allDone = decision.equals("COMMIT")
                ? commitAllBestEffort(attempted, journalXid)
                : rollbackAllBestEffort(attempted, journalXid);

        if (allDone) {
            decisionRepository.markFinalized(transferId);
        }

        if (decision.equals("COMMIT")) {
            if (allDone) {
                transferRepository.markCompleted(transferId, "COMMITTED", Instant.now());
            }
            return Result.success(transferId, sourceAccountId, targetAccountId, amount, createdAt);
        }
        insertAbortedJournalIdempotent(transferId, sourceAccountId, targetAccountId, amount, createdAt);
        return Result.failure(transferId, businessFailure);
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

    private void insertAbortedJournalIdempotent(UUID transferId, UUID source, UUID target,
                                                BigDecimal amount, Instant createdAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO transfers (id, source_account_id, target_account_id, amount, " +
                             "status, created_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                             "ON CONFLICT (id) DO NOTHING")) {
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

    private boolean commitAllBestEffort(List<String> attempted, String journalXid) {
        int failures = 0;
        for (var xid : attempted) {
            try {
                if (xid.equals(journalXid)) {
                    runLocalCommandIfPrepared(dataSource, xid, "COMMIT PREPARED");
                } else {
                    accountClient.commit(xid);
                }
            } catch (Exception e) {
                failures++;
                LOG.warn("commit failed for {}: {}", xid, e.toString());
            }
        }
        return failures == 0;
    }

    private boolean rollbackAllBestEffort(List<String> attempted, String journalXid) {
        int failures = 0;
        for (var xid : attempted) {
            try {
                if (xid.equals(journalXid)) {
                    runLocalCommandIfPrepared(dataSource, xid, "ROLLBACK PREPARED");
                } else {
                    accountClient.rollback(xid);
                }
            } catch (Exception e) {
                failures++;
                LOG.warn("rollback failed for {}: {}", xid, e.toString());
            }
        }
        return failures == 0;
    }

    static boolean runLocalCommandIfPrepared(DataSource dataSource, String xid, String command) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT 1 FROM pg_prepared_xacts WHERE gid = ?")) {
                st.setString(1, xid);
                try (var rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                }
            }
            try (Statement st = conn.createStatement()) {
                st.execute(command + " '" + xid + "'");
            }
            return true;
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
