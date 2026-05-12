package io.temporal.demos.durablemoney.transfer;

import io.temporal.demos.durablemoney.transfer.TransferDecisionRepository.PendingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Instant;

@Component
class TwoPhaseCommitRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(TwoPhaseCommitRecovery.class);
    private static final int BATCH_LIMIT = 50;

    private final TransferDecisionRepository decisionRepository;
    private final TransferRepository transferRepository;
    private final AccountClient accountClient;
    private final DataSource dataSource;

    TwoPhaseCommitRecovery(TransferDecisionRepository decisionRepository,
                           TransferRepository transferRepository,
                           AccountClient accountClient,
                           DataSource dataSource) {
        this.decisionRepository = decisionRepository;
        this.transferRepository = transferRepository;
        this.accountClient = accountClient;
        this.dataSource = dataSource;
    }

    @Scheduled(fixedDelayString = "${twopc.recovery.delay:30s}",
            initialDelayString = "${twopc.recovery.initial-delay:10s}")
    void recover() {
        var pending = decisionRepository.findUnfinalized(BATCH_LIMIT);
        if (pending.isEmpty()) {
            return;
        }
        int recovered = 0;
        for (var p : pending) {
            try {
                if (finalizeDecision(p)) {
                    recovered++;
                }
            } catch (Exception e) {
                LOG.warn("recovery: skipping {} after unexpected error: {}",
                        p.transferId(), e.toString());
            }
        }
        if (recovered > 0) {
            LOG.info("recovery: finalized {} of {} pending decision(s)", recovered, pending.size());
        }
    }

    private boolean finalizeDecision(PendingDecision p) {
        int failures = 0;
        for (var xid : p.participants()) {
            try {
                if (xid.endsWith("-journal")) {
                    TransferCoordinator.runLocalCommandIfPrepared(dataSource, xid,
                            p.decision().equals("COMMIT") ? "COMMIT PREPARED" : "ROLLBACK PREPARED");
                } else if (p.decision().equals("COMMIT")) {
                    accountClient.commit(xid);
                } else {
                    accountClient.rollback(xid);
                }
            } catch (Exception e) {
                failures++;
                LOG.warn("recovery: finalize {} failed: {}", xid, e.toString());
            }
        }
        if (failures > 0) {
            return false;
        }
        if (p.decision().equals("COMMIT")) {
            transferRepository.markCompleted(p.transferId(), "COMMITTED", Instant.now());
        } else {
            transferRepository.markAborted(p.transferId(), p.sourceAccountId(),
                    p.targetAccountId(), p.amount(), p.decidedAt());
        }
        decisionRepository.markFinalized(p.transferId());
        return true;
    }
}
