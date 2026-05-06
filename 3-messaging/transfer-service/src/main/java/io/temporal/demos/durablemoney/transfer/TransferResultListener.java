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
                .orElseThrow(() -> new IllegalStateException("Transfer not found: " + result.transferId()));

        if (transfer.getStatus() == TransferStatus.COMPLETED || transfer.getStatus() == TransferStatus.FAILED) {
            LOGGER.info("Ignoring result for transfer {} already in terminal state {}",
                    result.transferId(), transfer.getStatus());
            return;
        }

        switch (result.type()) {
            case DEBIT -> {
                if (result.success()) {
                    transfer.setStatus(TransferStatus.CREDITING);
                    transferRepository.save(transfer);
                    var creditCmd = new AccountCommandMessage(
                        result.transferId(), transfer.getTargetAccountId(), transfer.getAmount(), CommandType.CREDIT);
                    rabbitTemplate.convertAndSend("money.exchange", "account.commands", creditCmd);
                    LOGGER.info("Debit succeeded, sending credit for transfer {}", result.transferId());
                } else {
                    transfer.setStatus(TransferStatus.FAILED);
                    transfer.setErrorMessage(result.errorMessage());
                    transferRepository.save(transfer);
                    LOGGER.warn("Debit failed for transfer {}: {}", result.transferId(), result.errorMessage());
                }
            }
            case CREDIT -> {
                if (result.success()) {
                    transfer.setStatus(TransferStatus.COMPLETED);
                    LOGGER.info("Transfer {} completed successfully", result.transferId());
                } else {
                    // ⚠️ Credit failed but debit already succeeded — money is lost without compensation.
                    // Messages that cannot be processed are sent to the DLQ for manual replay or investigation.
                    transfer.setStatus(TransferStatus.FAILED);
                    transfer.setErrorMessage(result.errorMessage());
                    LOGGER.error("Credit failed for transfer {} — inconsistent state: {}", result.transferId(), result.errorMessage());
                }
                transferRepository.save(transfer);
            }
        }
    }
}
