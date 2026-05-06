package io.temporal.demos.durablemoney.transfer;

import jakarta.persistence.EntityNotFoundException;
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
        var transfer = new Transfer();
        transfer.setSourceAccountId(sourceAccountId);
        transfer.setTargetAccountId(targetAccountId);
        transfer.setAmount(amount);
        transfer.setStatus(TransferStatus.DEBITING);
        transfer = transferRepository.save(transfer);

        var cmd = new AccountCommandMessage(transfer.getId(), sourceAccountId, amount, CommandType.DEBIT);
        rabbitTemplate.convertAndSend("money.exchange", "account.commands", cmd);

        return transfer;
    }

    @Transactional(readOnly = true)
    Transfer getTransfer(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found: " + id));
    }
}
