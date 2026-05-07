package io.temporal.demos.durablemoney.transfer;

import io.temporal.demos.durablemoney.transfer.DlqMessage.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class DlqMessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DlqMessageListener.class);

    private final DlqMessageRepository repository;
    private final ObjectMapper objectMapper;

    DlqMessageListener(DlqMessageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "transfer.results.dlq")
    void onDlqMessage(Message msg) {
        var id = UUID.randomUUID();
        var props = msg.getMessageProperties();
        var payload = new String(msg.getBody(), StandardCharsets.UTF_8);
        var contentType = props.getContentType();

        // x-death is set by RabbitMQ on dead-lettered messages; first entry describes the most recent rejection.
        @SuppressWarnings("unchecked")
        var xDeath = (List<Map<String, Object>>) props.getHeaders().get("x-death");
        String failureReason = null;
        int failureCount = 0;
        String originalExchange = "";
        String originalRoutingKey = "";
        if (xDeath != null && !xDeath.isEmpty()) {
            var entry = xDeath.getFirst();
            failureReason = (String) entry.get("reason");
            var count = entry.get("count");
            if (count instanceof Number n) {
                failureCount = n.intValue();
            }
            originalExchange = (String) entry.getOrDefault("exchange", "");
            @SuppressWarnings("unchecked")
            var routingKeys = (List<String>) entry.get("routing-keys");
            if (routingKeys != null && !routingKeys.isEmpty()) {
                originalRoutingKey = routingKeys.getFirst();
            }
        } else {
            LOGGER.warn("DLQ message {} has no x-death header; original exchange/routing key unknown",
                    id);
        }

        UUID transferId = null;
        try {
            var node = objectMapper.readTree(msg.getBody()).path("transferId");
            if (!node.isMissingNode() && !node.isNull()) {
                transferId = UUID.fromString(node.asString());
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract transferId from DLQ payload", e);
        }

        var now = Instant.now();
        var dlq = new DlqMessage(id, transferId, originalExchange, originalRoutingKey,
                failureReason, failureCount, payload, contentType, Status.PARKED, now, now);
        repository.insert(dlq);

        LOGGER.warn("Parked DLQ message {} from {}/{} (transfer={}, reason={}, count={})",
                id, originalExchange, originalRoutingKey, transferId, failureReason, failureCount);
    }
}
