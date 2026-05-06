package io.temporal.demos.durablemoney.transfer;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    TransferResponse initiateTransfer(TransferRequest request) {
        var transfer = new Transfer();
        transfer.setSourceAccountId(request.sourceAccountId());
        transfer.setTargetAccountId(request.targetAccountId());
        transfer.setAmount(request.amount());
        transfer.setStatus(TransferStatus.DEBITING);
        transfer = transferRepository.save(transfer);

        var cmd = new AccountCommandMessage(
            transfer.getId(), request.sourceAccountId(), request.amount(), "DEBIT");
        rabbitTemplate.convertAndSend("money.exchange", "account.commands", cmd);

        return TransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    TransferResponse getTransfer(UUID id) {
        return transferRepository.findById(id)
            .map(TransferResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Transfer not found: " + id));
    }
}
