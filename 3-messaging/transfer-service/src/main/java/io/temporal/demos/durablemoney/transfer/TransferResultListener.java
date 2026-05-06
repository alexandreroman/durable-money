package io.temporal.demos.durablemoney.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransferResultListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferResultListener.class);

    private final TransferRepository transferRepository;
    private final RabbitTemplate rabbitTemplate;

    TransferResultListener(TransferRepository transferRepository, RabbitTemplate rabbitTemplate) {
        this.transferRepository = transferRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "transfer.results")
    @Transactional
    void handleResult(AccountResultMessage result) {
        var transfer = transferRepository.findById(result.transferId())
                .orElseThrow(() -> new TransferNotFoundException(result.transferId()));

        if (transfer.status() == TransferStatus.COMPLETED || transfer.status() == TransferStatus.FAILED) {
            LOGGER.info("Ignoring result for transfer {} already in terminal state {}",
                    result.transferId(), transfer.status());
            return;
        }

        switch (result.type()) {
            case DEBIT -> {
                if (result.success()) {
                    transferRepository.markCrediting(result.transferId());
                    var creditCmd = new AccountCommandMessage(
                            result.transferId(), transfer.targetAccountId(), transfer.amount(),
                            CommandType.CREDIT);
                    rabbitTemplate.convertAndSend("money.exchange", "account.commands", creditCmd);
                    LOGGER.info("Debit succeeded, sending credit for transfer {}", result.transferId());
                } else {
                    transferRepository.markFailed(result.transferId(), result.errorMessage());
                    LOGGER.warn("Debit failed for transfer {}: {}",
                            result.transferId(), result.errorMessage());
                }
            }
            case CREDIT -> {
                if (result.success()) {
                    transferRepository.markCompleted(result.transferId());
                    LOGGER.info("Transfer {} completed successfully", result.transferId());
                } else {
                    // ⚠️ Credit failed but debit already succeeded — money is lost without compensation.
                    // Messages that cannot be processed are sent to the DLQ for manual replay.
                    transferRepository.markFailed(result.transferId(), result.errorMessage());
                    LOGGER.error("Credit failed for transfer {} — inconsistent state: {}",
                            result.transferId(), result.errorMessage());
                }
            }
        }
    }
}
