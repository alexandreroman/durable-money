package io.temporal.demos.durablemoney.transfer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
class TransferService {
    private final TransferRepository transferRepository;
    private final RabbitTemplate rabbitTemplate;

    TransferService(TransferRepository transferRepository, RabbitTemplate rabbitTemplate) {
        this.transferRepository = transferRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    Transfer initiateTransfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        var transfer = transferRepository.insertDebiting(sourceAccountId, targetAccountId, amount);
        var cmd = new AccountCommandMessage(transfer.id(), sourceAccountId, amount, CommandType.DEBIT);
        rabbitTemplate.convertAndSend("money.exchange", "account.commands", cmd);
        return transfer;
    }

    @Transactional(readOnly = true)
    Transfer getTransfer(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }
}
