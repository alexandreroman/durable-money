package io.temporal.demos.durablemoney.account;

import io.temporal.demos.durablemoney.account.DlqMessage.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class DlqAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DlqAdminService.class);

    private final DlqMessageRepository repository;
    private final RabbitTemplate rabbitTemplate;

    DlqAdminService(DlqMessageRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional(readOnly = true)
    List<DlqMessage> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    List<DlqMessage> listByStatus(Status status) {
        return repository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    DlqMessage get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DlqMessageNotFoundException(id));
    }

    DlqMessage replay(UUID id) {
        var dlq = repository.findById(id).orElseThrow(() -> new DlqMessageNotFoundException(id));
        if (dlq.status() != Status.PARKED) {
            throw new DlqAlreadyResolvedException(id, dlq.status());
        }

        var props = new MessageProperties();
        props.setContentType(dlq.contentType() != null ? dlq.contentType() : MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        var message = new Message(dlq.payload().getBytes(StandardCharsets.UTF_8), props);
        // Publish-then-update: a successful publish followed by a failed DB update leaves the row PARKED, so the
        // operator simply retries; consumer-side idempotency on (transfer_id, operation) prevents double effects.
        rabbitTemplate.send(dlq.originalExchange(), dlq.originalRoutingKey(), message);

        var now = Instant.now();
        repository.updateStatus(id, Status.REPLAYED, now);
        LOGGER.info("Replayed DLQ message {} to {}/{}", id, dlq.originalExchange(), dlq.originalRoutingKey());
        return repository.findById(id).orElseThrow();
    }

    DlqMessage discard(UUID id) {
        var dlq = repository.findById(id).orElseThrow(() -> new DlqMessageNotFoundException(id));
        if (dlq.status() != Status.PARKED) {
            throw new DlqAlreadyResolvedException(id, dlq.status());
        }
        var now = Instant.now();
        repository.updateStatus(id, Status.DISCARDED, now);
        LOGGER.info("Discarded DLQ message {}", id);
        return repository.findById(id).orElseThrow();
    }
}
