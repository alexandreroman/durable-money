package io.temporal.demos.durablemoney.transfer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class AccountClient {

    private final RestClient restClient;

    AccountClient(@Value("${account.service.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    void debit(UUID accountId, BigDecimal amount, UUID transferId) {
        restClient.post()
            .uri("/accounts/{id}/debit", accountId)
            .body(new DebitCreditRequest(amount, transferId))
            .retrieve()
            .toBodilessEntity();
    }

    void credit(UUID accountId, BigDecimal amount, UUID transferId) {
        restClient.post()
            .uri("/accounts/{id}/credit", accountId)
            .body(new DebitCreditRequest(amount, transferId))
            .retrieve()
            .toBodilessEntity();
    }
}
